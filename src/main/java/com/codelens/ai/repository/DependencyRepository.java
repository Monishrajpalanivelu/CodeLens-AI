package com.codelens.ai.repository;

import com.codelens.ai.model.Dependency;
import com.codelens.ai.model.DependencyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DependencyRepository extends JpaRepository<Dependency, DependencyId> {

    // All edges where this entity is the CALLER (A → B, A → C)
    // Used when building the forward graph
    List<Dependency> findByFromEntityId(Long fromEntityId);

    // All edges where this entity is the CALLEE (X → A, Y → A)
    // Used when building the reverse graph for BFS impact analysis
    List<Dependency> findByToEntityId(Long toEntityId);

    // Load ALL edges for a repo in one query — much more efficient
    // than loading entity by entity when building the full graph.
    // Joins through code_entities to filter by repo_id.
    // idx_deps_from and idx_deps_to indexes (from V2 migration) make
    // this fast even with 50,000+ edges.
    @Query("SELECT d FROM Dependency d " +
           "JOIN CodeEntity e ON e.id = d.fromEntityId " +
           "WHERE e.repository.id = :repoId")
    List<Dependency> findByRepoId(@Param("repoId") Long repoId);

    // Delete all dependency edges for a repo — used before re-indexing
    // to prevent duplicate edges from accumulating
    @Query("DELETE FROM Dependency d " +
           "WHERE d.fromEntityId IN " +
           "(SELECT e.id FROM CodeEntity e WHERE e.repository.id = :repoId)")
    @org.springframework.data.jpa.repository.Modifying
    void deleteByRepoId(@Param("repoId") Long repoId);
}