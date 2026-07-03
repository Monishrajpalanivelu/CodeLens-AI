package com.codelens.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateRepoRequest(

        @NotBlank(message = "GitHub URL is required")
        @Pattern(
                regexp = "https://github\\.com/[\\w.-]+/[\\w.-]+",
                message = "Must be a valid GitHub URL: https://github.com/owner/repo"
        )
        String githubUrl
) {}