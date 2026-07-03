package com.codelens.ai.controller;

import com.codelens.ai.dto.CreateRepoRequest;
import com.codelens.ai.dto.RepositoryDto;
import com.codelens.ai.dto.CodeEntityDto;
import com.codelens.ai.dto.GraphDto;
import com.codelens.ai.model.Repository;
import com.codelens.ai.repository.RepositoryRepository;
import com.codelens.ai.repository.UserRepository;
import com.codelens.ai.repository.CodeEntityRepository;
import com.codelens.ai.repository.DependencyRepository;
import com.codelens.ai.service.IndexingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class RepoController {

    private final RepositoryRepository repoRepository;
    private final UserRepository userRepository;
    private final IndexingService indexingService;
    private final CodeEntityRepository codeEntityRepository;
    private final DependencyRepository dependencyRepository;

    @PostMapping
    public ResponseEntity<RepositoryDto> createRepo(
            @Valid @RequestBody CreateRepoRequest request,
            Authentication auth) {

        // Prevent duplicate submissions
        if (repoRepository.existsByGithubUrl(request.githubUrl())) {
            return ResponseEntity.badRequest().build();
        }

        // Extract repo name from URL
        // "https://github.com/spring-projects/spring-boot" → "spring-boot"
        String name = request.githubUrl()
                .substring(request.githubUrl().lastIndexOf('/') + 1);

        var owner = userRepository.findByUsername(auth.getName())
                .orElseThrow();

        Repository repo = Repository.builder()
                .name(name)
                .githubUrl(request.githubUrl())
                .owner(owner)
                .status("PENDING")
                .build();

        repoRepository.save(repo);

        // Fire-and-forget — returns 202 immediately,
        // indexing runs on the "indexingExecutor" thread pool
        indexingService.indexRepositoryAsync(repo.getId(), repo.getGithubUrl());

        // 202 Accepted = "request received, processing in background"
        return ResponseEntity.accepted().body(RepositoryDto.from(repo));
    }

    @GetMapping
    public ResponseEntity<List<RepositoryDto>> getMyRepos(Authentication auth) {
        var owner = userRepository.findByUsername(auth.getName())
                .orElseThrow();

        List<RepositoryDto> repos = repoRepository
                .findByOwnerId(owner.getId())
                .stream()
                .map(RepositoryDto::from)
                .toList();

        return ResponseEntity.ok(repos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepositoryDto> getRepo(@PathVariable Long id) {
        return repoRepository.findById(id)
                .map(RepositoryDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Poll this endpoint to check indexing progress
    @GetMapping("/{id}/status")
    public ResponseEntity<String> getStatus(@PathVariable Long id) {
        return repoRepository.findById(id)
                .map(repo -> ResponseEntity.ok(repo.getStatus()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/entities/{entityId}")
    @Transactional(readOnly = true)
    public ResponseEntity<CodeEntityDto> getCodeEntity(
            @PathVariable Long entityId,
            Authentication auth) {
        var user = userRepository.findByUsername(auth.getName()).orElseThrow();
        var entity = codeEntityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("Entity not found"));

        // Verify the user owns the repository containing this entity
        if (!entity.getRepository().getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(CodeEntityDto.from(entity));
    }

    @GetMapping("/{id}/graph")
    @Transactional(readOnly = true)
    public ResponseEntity<GraphDto> getGraph(
            @PathVariable Long id,
            Authentication auth) {
        var user = userRepository.findByUsername(auth.getName()).orElseThrow();
        var repo = repoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        // Verify the user owns the repository
        if (!repo.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        var entities = codeEntityRepository.findByRepositoryId(id);
        var dependencies = dependencyRepository.findByRepoId(id);

        var nodes = entities.stream()
                .map(e -> new GraphDto.GraphNodeDto(e.getId(), e.getName(), e.getEntityType().name(), e.getFilePath()))
                .toList();

        var edges = dependencies.stream()
                .map(d -> new GraphDto.GraphEdgeDto(d.getFromEntityId(), d.getToEntityId(), d.getDependencyType()))
                .toList();

        return ResponseEntity.ok(new GraphDto(nodes, edges));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<RepositoryDto> retryRepo(
            @PathVariable Long id,
            Authentication auth) {
        var user = userRepository.findByUsername(auth.getName()).orElseThrow();
        var repo = repoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        if (!repo.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        // 1. Clean up any failed state records
        indexingService.cleanRepositoryData(id);

        // 2. Reset status to PENDING
        repoRepository.updateStatus(id, "PENDING");

        // 3. Re-trigger async indexing
        indexingService.indexRepositoryAsync(id, repo.getGithubUrl());

        return ResponseEntity.ok(RepositoryDto.from(repo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRepo(
            @PathVariable Long id,
            Authentication auth) {
        var user = userRepository.findByUsername(auth.getName()).orElseThrow();
        var repo = repoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        if (!repo.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        indexingService.deleteRepository(id);

        return ResponseEntity.noContent().build();
    }
}