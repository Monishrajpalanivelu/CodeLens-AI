package com.codelens.ai.ai;

import com.codelens.ai.model.CodeEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Wraps Spring AI's PgVectorStore.
 * Handles indexing entities into the vector store
 * and searching by semantic similarity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorSearchService {

    private final PgVectorStore vectorStore;
    private final GeminiEmbeddingService embeddingService;

    /**
     * Converts a CodeEntity into a Spring AI Document and stores
     * it in the vector_store table with its embedding.
     *
     * The metadata map is critical — it's how we filter search results
     * by repo. Spring AI stores this as JSON in vector_store.metadata.
     *
     * @return the UUID assigned by Spring AI — stored back in
     *         code_entities.vector_doc_id for cross-table linking
     */
    public String indexEntity(CodeEntity entity) {
        Document doc = new Document(
                // The text that gets embedded — the actual source code
                entity.getSourceCode(),
                // Metadata stored alongside the vector for filtering
                Map.of(
                        "entityId",   entity.getId().toString(),
                        "repoId",     entity.getRepository().getId().toString(),
                        "entityType", entity.getEntityType().name(),
                        "filePath",   entity.getFilePath(),
                        "name",       entity.getName()
                )
        );

        vectorStore.add(List.of(doc));
        log.debug("Indexed entity {} ({})", entity.getName(), doc.getId());

        // doc.getId() is the UUID Spring AI assigned to this document
        // in the vector_store table
        return doc.getId();
    }

    /**
     * Semantic similarity search within a specific repository.
     *
     * @param repoId    filter results to this repo only
     * @param query     natural language search query
     * @param topK      max number of results to return
     * @return ranked list of matching Documents (highest similarity first)
     */
    public List<Document> search(Long repoId, String query, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(0.65)
                        // Filter expression — Spring AI translates this
                        // into a Postgres JSON query on vector_store.metadata
                        .filterExpression("repoId == '" + repoId + "'")
                        .build()
        );
    }

    /**
     * Deletes a list of document IDs from the vector store.
     */
    public void deleteEntities(List<String> docIds) {
        try {
            vectorStore.delete(docIds);
            log.debug("Deleted {} documents from vector store", docIds.size());
        } catch (Exception e) {
            log.warn("Failed to delete documents from vector store: {}", e.getMessage());
        }
    }
}