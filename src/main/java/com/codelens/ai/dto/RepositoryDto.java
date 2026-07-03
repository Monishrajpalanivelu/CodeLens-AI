package com.codelens.ai.dto;

import com.codelens.ai.model.Repository;

import java.time.LocalDateTime;

public record RepositoryDto(
        Long id,
        String name,
        String githubUrl,
        String status,
        LocalDateTime indexedAt,
        LocalDateTime createdAt
) {
    // Static factory method — converts JPA entity to DTO.
    // Controllers never return raw JPA entities — that would
    // expose DB internals and cause lazy-loading issues when
    // Jackson tries to serialize the owner User field.
    public static RepositoryDto from(Repository repo) {
        return new RepositoryDto(
                repo.getId(),
                repo.getName(),
                repo.getGithubUrl(),
                repo.getStatus(),
                repo.getIndexedAt(),
                repo.getCreatedAt()
        );
    }
}