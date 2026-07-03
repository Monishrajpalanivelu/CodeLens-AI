package com.codelens.ai.dto;

import java.util.List;

public record GraphDto(
        List<GraphNodeDto> nodes,
        List<GraphEdgeDto> edges
) {
    public record GraphNodeDto(
            Long id,
            String name,
            String type,
            String filePath
    ) {}

    public record GraphEdgeDto(
            Long from,
            Long to,
            String type
    ) {}
}
