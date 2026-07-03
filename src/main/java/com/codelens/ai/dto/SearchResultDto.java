package com.codelens.ai.dto;

import lombok.Builder;

import java.io.Serializable;

/**
 * Serializable is required because Redis serializes this object
 * to JSON and back. Without it, the cache will fail at runtime
 * when trying to store the result.
 */
@Builder
public record SearchResultDto(
        Long entityId,
        String name,
        String filePath,
        String entityType,
        double score,       // cosine similarity score 0.0 - 1.0
        String snippet      // first 200 chars of source code
) implements Serializable {}