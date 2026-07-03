package com.codelens.ai.dto;

public record AuthResponse(
        String token,
        String username,
        String email
) {}