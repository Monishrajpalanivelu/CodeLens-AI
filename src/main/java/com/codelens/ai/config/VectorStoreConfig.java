package com.codelens.ai.config;

import com.codelens.ai.ai.GeminiEmbeddingService;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Profile("!test")
public class VectorStoreConfig {

    @Bean
    public PgVectorStore vectorStore(JdbcTemplate jdbcTemplate,
                                      GeminiEmbeddingService embeddingService) {
        return PgVectorStore.builder(jdbcTemplate, embeddingService)
                .dimensions(768)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true) // Spring AI creates its own vector_store table on first boot
                .build();
    }
}