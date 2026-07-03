package com.codelens.ai.repository;

import com.codelens.ai.model.CodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeEntityRepository extends JpaRepository<CodeEntity, Long> {

    // All entities in a repo — used by graph engine to load adjacency list
    List<CodeEntity> findByRepositoryId(Long repoId);

    // Find by name within a repo — used when building dependency edges
    // (JavaParser gives us method names, we resolve them to entity IDs)
    List<CodeEntity> findByRepositoryIdAndName(Long repoId, String name);

    // Find specific entity by name + file — more precise than name alone
    // (same method name can exist in multiple files)
    Optional<CodeEntity> findByRepositoryIdAndNameAndFilePath(
            Long repoId, String name, String filePath);

    // Count entities per repo — useful for progress tracking + README metrics
    long countByRepositoryId(Long repoId);

    @Query(value = "SELECT * FROM code_entities WHERE vector_doc_id = :vectorDocId",
           nativeQuery = true)
    Optional<CodeEntity> findByVectorDocId(@Param("vectorDocId") String vectorDocId);

    // Delete all entities for a repo — used during re-indexing / retrying
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByRepositoryId(Long repoId);
}