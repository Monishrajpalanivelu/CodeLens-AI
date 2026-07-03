package com.codelens.ai.graph;

import com.codelens.ai.dto.CodeEntityDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Result of a BFS impact analysis.
 *
 * impactLayers shows blast radius level by level:
 * {
 *   1: [methodB, methodC],        <- direct callers of the changed method
 *   2: [methodD, methodE, methodF] <- callers of methodB and methodC
 * }
 *
 * This layered view is why BFS is better than DFS here —
 * DFS would give you a deep chain but not the level structure.
 */
@Getter
@AllArgsConstructor
public class ImpactResult {

    // The entity whose change we're analyzing
    private final Long rootEntityId;

    // Layer number → list of affected entity IDs at that depth
    // Layer 1 = direct dependents, Layer 2 = their dependents, etc.
    private final Map<Integer, List<Long>> impactLayers;

    // Full entity details for all affected entities
    // (so the client doesn't need a second API call)
    private final List<CodeEntityDto> affectedEntities;

    // Total count — quick summary number for the UI
    private final int totalAffected;
}