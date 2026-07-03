package com.codelens.ai.repository;

import com.codelens.ai.model.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Repository
public interface RepositoryRepository extends JpaRepository<Repository, Long> {

    // All repos belonging to a user — for "my repos" listing
    List<Repository> findByOwnerId(Long ownerId);

    // Check if this URL already indexed — prevents duplicate submissions
    Optional<Repository> findByGithubUrl(String githubUrl);

    boolean existsByGithubUrl(String githubUrl);

    // @Query needed here because we're doing an UPDATE, not a SELECT.
    // Spring Data can't derive UPDATE statements from method names.
    // @Modifying tells Spring this query changes data (not just reads).
    // clearAutomatically = true clears the JPA first-level cache after
    // the update — without this, repo.getStatus() would still return
    // the old value even after the DB was updated.
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Repository r SET r.status = :status WHERE r.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    // Same pattern — update indexedAt timestamp when indexing completes
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Repository r SET r.status = :status, " +
           "r.indexedAt = :indexedAt WHERE r.id = :id")
    void updateStatusAndIndexedAt(@Param("id") Long id,
                                   @Param("status") String status,
                                   @Param("indexedAt") LocalDateTime indexedAt);
}