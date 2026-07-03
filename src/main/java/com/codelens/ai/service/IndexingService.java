package com.codelens.ai.service;

import com.codelens.ai.ast.JavaAstService;
import com.codelens.ai.ai.VectorSearchService;
import com.codelens.ai.model.CodeEntity;
import com.codelens.ai.model.Dependency;
import com.codelens.ai.repository.CodeEntityRepository;
import com.codelens.ai.repository.DependencyRepository;
import com.codelens.ai.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingService {

    private final RepositoryRepository repoRepository;
    private final CodeEntityRepository entityRepository;
    private final DependencyRepository dependencyRepository;
    private final JavaAstService astService;
    private final VectorSearchService vectorSearch;
    private final GitHubService gitHubService;

    // Self-injection — required for @Transactional to work when calling
    // our own methods. Spring's @Transactional works via proxy — if you
    // call this.indexFileSafely() directly, you bypass the proxy and the
    // transaction annotation is silently ignored. Injecting self and calling
    // self.indexFileSafely() goes through the proxy correctly.
    // (Bug 2 + Bug 10 fix from blueprint)
    @Autowired
    @Lazy
    private IndexingService self;

    /**
     * Main entry point — called by RepoController after creating a repo.
     * Returns immediately (202 Accepted) — actual indexing happens on
     * the "indexingExecutor" thread pool defined in AsyncConfig.
     *
     * Flow:
     * 1. Mark repo as INDEXING
     * 2. Fetch all .java files from GitHub
     * 3. Parse + embed each file in parallel
     * 4. Build dependency edges
     * 5. Mark repo as DONE (or FAILED)
     */
    @Async("indexingExecutor")
    public CompletableFuture<Void> indexRepositoryAsync(Long repoId, String githubUrl) {
        log.info("Starting indexing for repo {} ({})", repoId, githubUrl);

        try {
            repoRepository.updateStatus(repoId, "INDEXING");

            List<GitHubService.FileContent> files =
                    gitHubService.fetchJavaFiles(githubUrl);

            log.info("Fetched {} Java files for repo {}", files.size(), repoId);

            // Bug 3 fix: each file gets its own @Transactional scope via
            // self.indexFileSafely(). If file 99 fails, files 1-98 are
            // already committed — not rolled back.
            // Bug 4 fix: files processed in parallel via CompletableFuture,
            // not a sequential for-loop.
            List<CompletableFuture<Void>> futures = files.stream()
                    .map(file -> CompletableFuture.runAsync(
                            () -> self.indexFileSafely(repoId, file)
                    ))
                    .toList();

            // Wait for ALL files to finish before building dependency edges
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Build dependency graph after all entities are saved
            self.buildDependencyEdges(repoId);

            repoRepository.updateStatusAndIndexedAt(repoId, "DONE", LocalDateTime.now());
            log.info("Indexing complete for repo {}", repoId);

        } catch (Exception e) {
            log.error("Indexing failed for repo {}: {}", repoId, e.getMessage(), e);
            repoRepository.updateStatus(repoId, "FAILED");
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Processes one file — parse, save entities, embed.
     * Catches any exception to let other files continue processing, but calls
     * indexFileWithRetry which retries transient transaction/database failures.
     */
    public void indexFileSafely(Long repoId, GitHubService.FileContent file) {
        try {
            self.indexFileWithRetry(repoId, file);
        } catch (Exception e) {
            log.warn("Skipping file {} after retries failed: {}", file.path(), e.getMessage());
        }
    }

    /**
     * Processes one file with automatic retry capability.
     * Run in its own transaction so failure here rolls back only this file's work.
     */
    @Transactional
    @Retryable(
            retryFor = Exception.class,
            noRetryFor = com.codelens.ai.ai.GeminiEmbeddingService.GeminiClientException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void indexFileWithRetry(Long repoId, GitHubService.FileContent file) {
        List<JavaAstService.ParsedEntity> parsed =
                astService.extractEntities(file.content(), file.path());

        if (parsed.isEmpty()) {
            log.debug("No entities extracted from {}", file.path());
            return;
        }

        com.codelens.ai.model.Repository repo =
                repoRepository.getReferenceById(repoId);

        for (JavaAstService.ParsedEntity p : parsed) {
            // Step 1: Save entity to code_entities (gets an ID assigned)
            CodeEntity entity = CodeEntity.builder()
                    .repository(repo)
                    .entityType(p.entityType())
                    .name(p.name())
                    .filePath(file.path())
                    .startLine(p.startLine())
                    .endLine(p.endLine())
                    .sourceCode(p.sourceCode())
                    .build();

            entityRepository.save(entity);

            // Step 2: Embed into vector_store, store UUID back
            // in code_entities.vector_doc_id
            try {
                String vectorDocId = vectorSearch.indexEntity(entity);
                entity.setVectorDocId(vectorDocId);
                entityRepository.save(entity);
            } catch (Exception e) {
                // Embedding failed (rate limit, API error etc.)
                // Entity is saved without a vector — won't appear in
                // semantic search but won't break the graph engine
                log.warn("Embedding failed for entity {} in {}: {}",
                        p.name(), file.path(), e.getMessage());
            }
        }

        log.debug("Indexed {} entities from {}", parsed.size(), file.path());
    }

    /**
     * Builds dependency edges between entities after all files are indexed.
     * Runs after all indexFileSafely() calls complete.
     *
     * Strategy: for each method's call list (e.g. ["findById", "save"]),
     * look up which entity in this repo has that name, and create an edge.
     *
     * Limitation: name-based matching — if two methods share a name in
     * different classes, we create edges to both. Full symbol resolution
     * would require the project's classpath. Documented trade-off.
     */
    @Transactional
    @Retryable(retryFor = Exception.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public void buildDependencyEdges(Long repoId) {
        log.info("Building dependency edges for repo {}", repoId);

        List<CodeEntity> allEntities = entityRepository.findByRepositoryId(repoId);

        // Build name → entity map for fast lookup
        // If duplicate names exist, last one wins (known limitation)
        Map<String, CodeEntity> nameToEntity = allEntities.stream()
                .collect(Collectors.toMap(
                        CodeEntity::getName,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        List<Dependency> edges = new ArrayList<>();

        // Build filePath -> Class entity map for fast lookup of declaring classes
        java.util.Map<String, CodeEntity> fileToClass = allEntities.stream()
                .filter(entity -> entity.getEntityType() == CodeEntity.EntityType.CLASS)
                .collect(Collectors.toMap(
                        CodeEntity::getFilePath,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        // Group entities by filePath to construct CONTAINS edges (Class -> Method)
        java.util.Map<String, List<CodeEntity>> entitiesByFile = allEntities.stream()
                .collect(Collectors.groupingBy(CodeEntity::getFilePath));

        for (java.util.Map.Entry<String, List<CodeEntity>> entry : entitiesByFile.entrySet()) {
            List<CodeEntity> fileEntities = entry.getValue();
            
            // Find class entity in this file
            CodeEntity classEntity = fileEntities.stream()
                    .filter(entity -> entity.getEntityType() == CodeEntity.EntityType.CLASS)
                    .findFirst()
                    .orElse(null);
                    
            if (classEntity != null) {
                // Connect class to all its functions
                for (CodeEntity f : fileEntities) {
                    if (f.getEntityType() == CodeEntity.EntityType.FUNCTION) {
                        edges.add(Dependency.builder()
                                .fromEntityId(classEntity.getId())
                                .toEntityId(f.getId())
                                .dependencyType("CONTAINS")
                                .build());
                    }
                }
            }
        }

        // Construct CALLS edges (Method -> Method) + DEPENDS_ON edges (Class -> Class)
        for (CodeEntity e : allEntities.stream()
                .filter(entity -> entity.getEntityType() == CodeEntity.EntityType.FUNCTION)
                .toList()) {

            CodeEntity fromEntity = nameToEntity.get(e.getName());
            if (fromEntity == null) continue;

            List<String> calls = astService.extractCallsFromMethod(e.getSourceCode());

            for (String calledName : calls) {
                CodeEntity toEntity = nameToEntity.get(calledName);
                if (toEntity != null &&
                        !toEntity.getId().equals(fromEntity.getId())) {
                    
                    // Method calls Method
                    edges.add(Dependency.builder()
                            .fromEntityId(fromEntity.getId())
                            .toEntityId(toEntity.getId())
                            .dependencyType("CALLS")
                            .build());

                    // Class depends on Class
                    CodeEntity fromClass = fileToClass.get(fromEntity.getFilePath());
                    CodeEntity toClass = fileToClass.get(toEntity.getFilePath());
                    if (fromClass != null && toClass != null && !fromClass.getId().equals(toClass.getId())) {
                        edges.add(Dependency.builder()
                                .fromEntityId(fromClass.getId())
                                .toEntityId(toClass.getId())
                                .dependencyType("DEPENDS_ON")
                                .build());
                    }
                }
            }
        }

        if (!edges.isEmpty()) {
            dependencyRepository.saveAll(edges);
            log.info("Saved {} dependency edges for repo {}", edges.size(), repoId);
        }
    }

    /**
     * Cleans up all indexed data for a repository, including database records
     * and vector store documents.
     */
    @Transactional
    public void cleanRepositoryData(Long repoId) {
        log.info("Cleaning up indexed data for repo {}", repoId);

        // 1. Fetch all entity records for the repository
        List<CodeEntity> entities = entityRepository.findByRepositoryId(repoId);
        List<String> docIds = entities.stream()
                .map(CodeEntity::getVectorDocId)
                .filter(id -> id != null && !id.trim().isEmpty())
                .toList();

        // 2. Remove from vector store
        if (!docIds.isEmpty()) {
            vectorSearch.deleteEntities(docIds);
        }

        // 3. Delete call dependency edges from database
        dependencyRepository.deleteByRepoId(repoId);

        // 4. Delete parsed entities from database
        entityRepository.deleteByRepositoryId(repoId);

        log.info("Cleanup complete for repo {}", repoId);
    }

    /**
     * Deletes a repository completely, including all its database records,
     * dependency edges, and vector store documents.
     */
    @Transactional
    public void deleteRepository(Long repoId) {
        log.info("Deleting repository {}", repoId);
        
        // 1. Clean up all indexed data (vector store, dependencies, entities)
        cleanRepositoryData(repoId);
        
        // 2. Delete the repository record itself
        repoRepository.deleteById(repoId);
        
        log.info("Repository {} fully deleted", repoId);
    }
}