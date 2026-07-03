package com.codelens.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    /**
     * Dedicated thread pool for repository indexing.
     * Named "indexingExecutor" — matches @Async("indexingExecutor")
     * in IndexingService.
     *
     * Why separate from Tomcat threads?
     * Indexing 100 files takes 3+ minutes. If it ran on a Tomcat thread,
     * that thread would be blocked for 3 minutes — unable to serve any
     * other HTTP requests. With a dedicated pool, Tomcat threads are
     * free immediately after returning 202 Accepted.
     */
    @Bean(name = "indexingExecutor")
    public Executor indexingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core threads — always alive, waiting for work
        executor.setCorePoolSize(4);

        // Max threads — created if queue fills up
        executor.setMaxPoolSize(8);

        // Queue capacity — tasks wait here if all threads are busy
        executor.setQueueCapacity(100);

        // Thread name prefix — makes logs readable:
        // "indexing-1", "indexing-2" etc. instead of "pool-1-thread-1"
        executor.setThreadNamePrefix("indexing-");

        // On shutdown, wait for running tasks to finish
        // rather than killing them mid-file
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}