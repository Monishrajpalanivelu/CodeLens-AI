package com.codelens.ai.service;

import com.codelens.ai.graph.DependencyGraph;
import com.codelens.ai.graph.ImpactResult;
import com.codelens.ai.graph.DependencyGraph.RepoGraph;
import com.codelens.ai.repository.CodeEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphService {

    private final DependencyGraph dependencyGraph;
    private final CodeEntityRepository entityRepository;

    /**
     * BFS impact analysis — answers:
     * "If I change entityId, what else breaks?"
     *
     * Uses the REVERSE dependency graph:
     * - Normal graph: "A calls B" means A → B
     * - Reverse graph: "A calls B" means B → A
     * - BFS on reverse: starting from B, find everything
     *   that transitively depends on B
     *
     * Time complexity: O(V + E)
     * - V = number of entities in the graph
     * - E = number of dependency edges
     * For 10,000 methods + 50,000 edges: <5ms in JVM
     *
     * @param repoId       which repo's graph to traverse
     * @param rootEntityId the entity being changed
     * @param maxDepth     how many hops to follow (default 3, max 10)
     */
    public ImpactResult analyzeImpact(Long repoId,
                                       Long rootEntityId,
                                       int maxDepth) {
        log.info("BFS impact analysis: repo={} entity={} maxDepth={}",
                repoId, rootEntityId, maxDepth);

        long startTime = System.currentTimeMillis();

        // Load graph from cache (or DB on first access)
        DependencyGraph.RepoGraph graph =
                dependencyGraph.getOrLoad(repoId);

        // impactLayers: depth → list of entity IDs at that depth
        // LinkedHashMap preserves insertion order (layer 1 before layer 2)
        Map<Integer, List<Long>> impactLayers = new LinkedHashMap<>();

        // Standard BFS setup
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();

        // Start BFS from the root entity
        visited.add(rootEntityId);
        queue.offer(rootEntityId);

        int depth = 0;

        /**
         * BFS loop — processes one level (depth) per outer iteration.
         *
         * Why process level by level instead of node by node?
         * We need to know WHICH LAYER each node belongs to.
         * Processing levelSize nodes at a time ensures all nodes
         * at depth N are processed before moving to depth N+1.
         *
         * Example:
         * Depth 0: [methodA]          ← root (not in results)
         * Depth 1: [methodB, methodC] ← direct callers
         * Depth 2: [methodD]          ← callers of callers
         */
        while (!queue.isEmpty() && depth < maxDepth) {

            // How many nodes are at the current depth level
            int levelSize = queue.size();
            List<Long> currentLayer = new ArrayList<>();

            for (int i = 0; i < levelSize; i++) {
                Long current = queue.poll();

                // Get all entities that call 'current'
                // (reverse graph traversal)
                for (Long dependent : graph.getDependents(current)) {
                    if (visited.add(dependent)) {
                        // visited.add() returns true if newly added
                        // (false if already visited — prevents cycles)
                        queue.offer(dependent);
                        currentLayer.add(dependent);
                    }
                }
            }

            // Only record this layer if it has results
            if (!currentLayer.isEmpty()) {
                impactLayers.put(depth + 1, currentLayer);
            }

            depth++;
        }

        // Collect all affected entity IDs (visited minus the root)
        Set<Long> affectedIds = new HashSet<>(visited);
        affectedIds.remove(rootEntityId);

        // Fetch full entity details in one DB query (not N queries)
        var affectedEntities = entityRepository.findAllById(affectedIds);

        List<com.codelens.ai.dto.CodeEntityDto> dtos = affectedEntities.stream()
                .map(com.codelens.ai.dto.CodeEntityDto::from)
                .toList();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("BFS complete: {} affected entities in {}ms", 
                affectedIds.size(), elapsed);

        return new ImpactResult(
                rootEntityId,
                impactLayers,
                dtos,
                affectedIds.size()
        );
    }

    /**
     * Convenience method — get direct dependents only (depth=1).
     * Useful for a quick "what directly calls this?" check.
     */
    public Set<Long> getDirectDependents(Long repoId, Long entityId) {
        return dependencyGraph
                .getOrLoad(repoId)
                .getDependents(entityId);
    }

    /**
     * Convenience method — get direct dependencies only.
     * "What does this method call directly?"
     */
    public Set<Long> getDirectDependencies(Long repoId, Long entityId) {
        return dependencyGraph
                .getOrLoad(repoId)
                .getDependencies(entityId);
    }
}