package com.codelens.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.mockito.Mockito;

@SpringBootTest
class AiApplicationTests {

    @Test
    void contextLoads() {
    }

    @TestConfiguration
    static class Config {
        @Bean
        public PgVectorStore pgVectorStore() {
            return Mockito.mock(PgVectorStore.class);
        }
    }
}

