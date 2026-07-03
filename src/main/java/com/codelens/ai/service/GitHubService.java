package com.codelens.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Fetches Java source files from a public GitHub repository
 * using the GitHub Contents API (no auth needed for public repos).
 *
 * API used:
 * GET https://api.github.com/repos/{owner}/{repo}/git/trees/HEAD?recursive=1
 * GET https://api.github.com/repos/{owner}/{repo}/contents/{path}
 */
@Service
@Slf4j
public class GitHubService {

    private final RestClient restClient;

    public GitHubService() {
        String token = System.getenv("GITHUB_TOKEN");
        var builder = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("User-Agent", "CodeLens-AI")
                .defaultHeader("Accept", "application/vnd.github+json");
                
        if (token != null && !token.trim().isEmpty()) {
            builder.defaultHeader("Authorization", "Bearer " + token.trim());
            log.info("GitHub API configured with authorization token.");
        }
        
        this.restClient = builder.build();
    }

    /**
     * Fetches all .java files from a GitHub repo URL.
     * URL format expected: https://github.com/{owner}/{repo}
     *
     * @return list of FileContent records — path + raw source code
     */
    @SuppressWarnings("unchecked")
    @Retryable(retryFor = Exception.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public List<FileContent> fetchJavaFiles(String githubUrl) {
        // Parse owner/repo from URL
        // "https://github.com/spring-projects/spring-boot" → ["spring-projects", "spring-boot"]
        String[] parts = githubUrl
                .replace("https://github.com/", "")
                .split("/");

        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid GitHub URL: " + githubUrl);
        }

        String owner = parts[0];
        String repo  = parts[1];

        log.info("Fetching file tree for {}/{}", owner, repo);

        // Step 1: Get the full file tree recursively
        // Returns a flat list of every file path in the repo
        Map<String, Object> tree = restClient.get()
                .uri("/repos/{owner}/{repo}/git/trees/HEAD?recursive=1", owner, repo)
                .retrieve()
                .body(Map.class);

        if (tree == null || !tree.containsKey("tree")) {
            log.warn("Empty tree response for {}/{}", owner, repo);
            return List.of();
        }

        List<Map<String, Object>> treeItems =
                (List<Map<String, Object>>) tree.get("tree");

        // Step 2: Filter to only .java files
        List<String> javaPaths = treeItems.stream()
                .filter(item -> "blob".equals(item.get("type")))
                .map(item -> (String) item.get("path"))
                .filter(path -> path.endsWith(".java"))
                .toList();

        log.info("Found {} Java files in {}/{}", javaPaths.size(), owner, repo);

        // Step 3: Fetch each file's content
        List<FileContent> files = new ArrayList<>();
        for (String path : javaPaths) {
            try {
                FileContent content = fetchFileContent(owner, repo, path);
                if (content != null) {
                    files.add(content);
                }
            } catch (Exception e) {
                // One file failing must not stop the whole repo
                log.warn("Failed to fetch {}: {}", path, e.getMessage());
            }
        }

        return files;
    }

    @SuppressWarnings("unchecked")
    private FileContent fetchFileContent(String owner, String repo, String path) {
        int attempts = 0;
        int maxAttempts = 3;
        long delay = 500;
        while (true) {
            try {
                Map<String, Object> response = restClient.get()
                        .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                        .retrieve()
                        .body(Map.class);

                if (response == null || !response.containsKey("content")) {
                    return null;
                }

                // GitHub returns file content as Base64-encoded string
                // with newlines every 60 chars — strip newlines before decoding
                String encoded = ((String) response.get("content"))
                        .replace("\n", "");
                String decoded = new String(Base64.getDecoder().decode(encoded));

                return new FileContent(path, decoded);
            } catch (Exception e) {
                attempts++;
                if (attempts >= maxAttempts) {
                    throw e;
                }
                log.warn("Failed to fetch content for {}, retrying (attempt {}/{}): {}", path, attempts, maxAttempts, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
                delay *= 1.5;
            }
        }
    }

    /**
     * Represents one fetched Java file.
     * path    = relative path within the repo
     * content = raw Java source code as a string
     */
    public record FileContent(String path, String content) {}
}