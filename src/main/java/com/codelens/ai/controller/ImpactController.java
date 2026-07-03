package com.codelens.ai.controller;

import com.codelens.ai.graph.ImpactResult;
import com.codelens.ai.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/impact")
@RequiredArgsConstructor
public class ImpactController {

    private final GraphService graphService;

    /**
     * GET /api/impact/{entityId}?repoId=1&maxDepth=3
     *
     * Returns layered BFS result:
     * {
     *   "rootEntityId": 42,
     *   "impactLayers": {
     *     "1": [55, 61],       <- direct callers
     *     "2": [78, 92, 103]   <- callers of callers
     *   },
     *   "affectedEntities": [...],
     *   "totalAffected": 5
     * }
     */
    @GetMapping("/{entityId}")
    public ResponseEntity<ImpactResult> getImpact(
            @PathVariable Long entityId,
            @RequestParam Long repoId,
            @RequestParam(defaultValue = "3") int maxDepth) {

        // Hard cap — prevents BFS from traversing the entire graph
        // on a deeply interconnected codebase
        if (maxDepth > 10) maxDepth = 10;

        ImpactResult result =
                graphService.analyzeImpact(repoId, entityId, maxDepth);

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/impact/{entityId}/direct?repoId=1
     *
     * Quick check — just direct callers, no BFS traversal.
     * Faster than full impact analysis for simple lookups.
     */
    @GetMapping("/{entityId}/direct")
    public ResponseEntity<?> getDirectDependents(
            @PathVariable Long entityId,
            @RequestParam Long repoId) {

        return ResponseEntity.ok(
                graphService.getDirectDependents(repoId, entityId));
    }
}