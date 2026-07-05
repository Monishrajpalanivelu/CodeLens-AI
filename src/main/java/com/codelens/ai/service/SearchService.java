package com.codelens.ai.service;

import com.codelens.ai.ai.VectorSearchService;
import com.codelens.ai.dto.SearchResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

        private final VectorSearchService vectorSearch;

        /**
         * Semantic search within a repository.
         *
         * @Cacheable works like this:
         *            1. Build the cache key: "repoId:query:topK"
         *            e.g. "42:user authentication:10"
         *            2. Check Redis — if hit, return cached result immediately
         *            (this is the <15ms path — no Gemini call, no DB query)
         *            3. If miss — call vectorSearch.search(), store result in Redis,
         *            return result (~450ms — Gemini embed query + pgvector search)
         *
         *            Why cache search results and not embeddings?
         *            Because the expensive part is the full round trip:
         *            embed query → vector search → fetch metadata.
         *            Caching the final List<SearchResultDto> skips all of it.
         */
        @Cacheable(value = "searchResults", key = "#repoId + ':' + #query + ':' + #topK")
        public List<SearchResultDto> search(Long repoId, String query, int topK) {
                log.info("Cache miss — searching vectors for repo {} query '{}'",
                                repoId, query);

                List<Document> documents = vectorSearch.search(repoId, query, topK);

                return documents.stream()
                                .map(doc -> SearchResultDto.builder()
                                                .entityId(parseLong(doc.getMetadata().get("entityId")))
                                                .name((String) doc.getMetadata().get("name"))
                                                .filePath((String) doc.getMetadata().get("filePath"))
                                                .entityType((String) doc.getMetadata().get("entityType"))
                                                // getScore() = cosine similarity — higher is better
                                                // null-safe: default to 0.0 if Spring AI doesn't populate it
                                                .score(doc.getScore() != null ? doc.getScore() : 0.0)
                                                // Snippet: first 200 chars of source code
                                                // prevents huge method bodies overwhelming the UI
                                                .snippet(doc.getText() != null
                                                                ? doc.getText().substring(
                                                                                0,
                                                                                Math.min(200, doc.getText().length()))
                                                                : "")
                                                .build())
                                .collect(java.util.stream.Collectors.toList());
        }

        private Long parseLong(Object value) {
                if (value == null)
                        return null;
                if (value instanceof Long l)
                        return l;
                return Long.parseLong(value.toString());
        }
}