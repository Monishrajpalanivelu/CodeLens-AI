package com.codelens.ai.graph;

import com.codelens.ai.repository.DependencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * In-memory graph store with bounded LRU eviction.
 *
 * Why in-memory instead of querying DB every time?
 * BFS needs to traverse potentially thousands of edges.
 * Each DB query adds ~5ms. For a graph with 10 hops that's
 * 50ms+ of pure DB overhead per BFS step.
 * In-memory traversal runs in microseconds.
 *
 * Why bounded LRU (max 10 repos)?
 * Bug 7 fix from blueprint — unbounded HashMap with
 * 100k functions + 500k edges = several GB of RAM.
 * LRU evicts the least recently used repo when
 * the cache exceeds 10 entries.
 *
 * Why not Redis for this?
 * Graph traversal needs random access to adjacency lists
 * in a tight loop. Redis round-trips (~1ms each) would
 * be slower than DB queries. In-memory is the right
 * choice for graph traversal specifically.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DependencyGraph {

    private final DependencyRepository dependencyRepository;

    /**
     * LRU cache: repoId → RepoGraph
     * LinkedHashMap with accessOrder=true gives LRU behavior.
     * removeEldestEntry evicts when size exceeds 10.
     * synchronizedMap makes it thread-safe for concurrent requests.
     */
    private final Map<Long, RepoGraph> cache =
            Collections.synchronizedMap(
                    new LinkedHashMap<>(16, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(
                                Map.Entry<Long, RepoGraph> eldest) {
                            boolean shouldEvict = size() > 10;
                            if (shouldEvict) {
                                log.debug("Evicting graph for repo {}",
                                        eldest.getKey());
                            }
                            return shouldEvict;
                        }
                    }
            );

    /**
     * Get the graph for a repo — loads from DB if not cached.
     * computeIfAbsent is atomic — only one thread loads the graph
     * even if multiple requests arrive simultaneously.
     */
    public RepoGraph getOrLoad(Long repoId) {
        return cache.computeIfAbsent(repoId, id -> {
            log.info("Loading dependency graph for repo {} from DB", id);
            RepoGraph graph = new RepoGraph();

            dependencyRepository.findByRepoId(id)
                    .forEach(dep -> graph.addEdge(
                            dep.getFromEntityId(),
                            dep.getToEntityId()
                    ));

            log.info("Loaded graph for repo {}: {} edges",
                    id, graph.edgeCount());
            return graph;
        });
    }

    /**
     * Invalidate cached graph — called after re-indexing a repo
     * so the next BFS request loads fresh edges from DB.
     */
    public void invalidate(Long repoId) {
        cache.remove(repoId);
        log.debug("Invalidated graph cache for repo {}", repoId);
    }

    /**
     * Bidirectional adjacency list for one repository.
     *
     * forward: A → {B, C}  means "A calls B and C"
     * reverse: B → {A}     means "A calls B" (B is called by A)
     *
     * BFS impact analysis uses the REVERSE graph:
     * "who calls X?" traverses reverse edges from X.
     */
    public static class RepoGraph {

        // forward[A] = set of entities A depends on (A calls these)
        private final Map<Long, Set<Long>> forward = new HashMap<>();

        // reverse[B] = set of entities that depend on B (these call B)
        private final Map<Long, Set<Long>> reverse = new HashMap<>();

        private int edgeCount = 0;

        public void addEdge(Long from, Long to) {
            forward.computeIfAbsent(from, k -> new HashSet<>()).add(to);
            reverse.computeIfAbsent(to, k -> new HashSet<>()).add(from);
            edgeCount++;
        }

        /**
         * "Who calls this entity?" — used by BFS for impact analysis.
         * Returns empty set (not null) if no dependents found.
         */
        public Set<Long> getDependents(Long entityId) {
            return reverse.getOrDefault(entityId, Set.of());
        }

        /**
         * "What does this entity call?" — forward direction.
         * Less used but useful for "what does this method depend on?"
         */
        public Set<Long> getDependencies(Long entityId) {
            return forward.getOrDefault(entityId, Set.of());
        }

        public int edgeCount() {
            return edgeCount;
        }

        public boolean contains(Long entityId) {
            return forward.containsKey(entityId) ||
                    reverse.containsKey(entityId);
        }
    }
}