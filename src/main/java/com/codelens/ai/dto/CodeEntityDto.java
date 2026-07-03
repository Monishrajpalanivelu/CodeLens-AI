package com.codelens.ai.dto;

import com.codelens.ai.model.CodeEntity;

public record CodeEntityDto(
        Long id,
        String name,
        String filePath,
        String entityType,
        Integer startLine,
        Integer endLine,
        String sourceCode
) {
    public static CodeEntityDto from(CodeEntity entity) {
        return new CodeEntityDto(
                entity.getId(),
                entity.getName(),
                entity.getFilePath(),
                entity.getEntityType().name(),
                entity.getStartLine(),
                entity.getEndLine(),
                entity.getSourceCode()
        );
    }
}
