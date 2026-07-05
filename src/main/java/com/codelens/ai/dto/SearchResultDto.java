package com.codelens.ai.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;

/**
 * Serializable is required because Redis serializes this object
 * to JSON and back.
 *
 * @Jacksonized is required to make Lombok's @Builder work with Jackson
 *              deserialization (used by GenericJackson2JsonRedisSerializer).
 *              It does two things automatically:
 *              1. Points Jackson to use SearchResultDtoBuilder for
 *              deserialization.
 *              2. Adds @JsonPOJOBuilder(withPrefix="") so Jackson knows the
 *              builder
 *              setter methods are named "entityId()" not "withEntityId()".
 *              Without it: "Unrecognized field entityId (0 known properties)"
 *              error.
 */
@Builder
@Jacksonized
public record SearchResultDto(
                Long entityId,
                String name,
                String filePath,
                String entityType,
                double score, // cosine similarity score 0.0 - 1.0
                String snippet // first 200 chars of source code
) implements Serializable {
}
