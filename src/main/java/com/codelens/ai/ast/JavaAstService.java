package com.codelens.ai.ast;

import com.codelens.ai.model.CodeEntity.EntityType;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class JavaAstService {

    /**
     * Main entry point — takes raw Java source code as a string,
     * returns a flat list of every class and method found in it.
     *
     * @param sourceCode raw .java file content
     * @param filePath   path within the repo (e.g. src/main/java/com/example/UserService.java)
     *                   used for display and deduplication
     * @return list of parsed entities — empty list if parsing fails (never throws)
     */
    public List<ParsedEntity> extractEntities(String sourceCode, String filePath) {
        List<ParsedEntity> entities = new ArrayList<>();

        try {
            // StaticJavaParser.parse() builds the full AST from the source string.
            // CompilationUnit = the root node of a Java file's AST.
            // Everything else (classes, methods, imports) hangs off this root.
            CompilationUnit cu = StaticJavaParser.parse(sourceCode);

            // --- Step 1: Extract Classes ---
            // findAll() does a deep traversal of the entire AST tree,
            // collecting every node that matches the given type.
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {

                // Skip interfaces — we only want concrete classes.
                // Interfaces have no method bodies to embed meaningfully.
                if (cls.isInterface()) {
                    return;
                }

                entities.add(ParsedEntity.builder()
                        .name(cls.getNameAsString())
                        .entityType(EntityType.CLASS)
                        // getBegin()/getEnd() return Optional<Position>
                        // Position has .line (1-based line number)
                        .startLine(cls.getBegin().map(p -> p.line).orElse(0))
                        .endLine(cls.getEnd().map(p -> p.line).orElse(0))
                        // toString() on a JavaParser node returns its source code
                        .sourceCode(cls.toString())
                        // Classes don't track call sites — only methods do
                        .calls(List.of())
                        .build());
            });

            // --- Step 2: Extract Methods + their call sites ---
            cu.findAll(MethodDeclaration.class).forEach(method -> {

                // findAll() inside a method node = only call expressions
                // WITHIN this method's body. Not the whole file.
                // This gives us the call graph edges: "methodA calls methodB"
                List<String> calls = method
                        .findAll(MethodCallExpr.class)
                        .stream()
                        // getNameAsString() = just the method name being called
                        // e.g. userRepo.findById(id) → "findById"
                        .map(MethodCallExpr::getNameAsString)
                        .distinct()   // same method called multiple times = one edge
                        .toList();

                entities.add(ParsedEntity.builder()
                        .name(method.getNameAsString())
                        .entityType(EntityType.FUNCTION)
                        .startLine(method.getBegin().map(p -> p.line).orElse(0))
                        .endLine(method.getEnd().map(p -> p.line).orElse(0))
                        .sourceCode(method.toString())
                        .calls(calls)
                        .build());
            });

        } catch (ParseProblemException e) {
            // JavaParser couldn't parse this file — likely generated code,
            // unusual syntax, or a file with encoding issues.
            // We log a warning and return empty list — the indexing pipeline
            // will skip this file but continue with all others.
            // Never throw here — one bad file must not fail the whole repo.
            log.warn("JavaParser failed on {}: {}", filePath, e.getMessage());
        }

        return entities;
    }

    /**
     * Helper to parse a standalone method declaration and extract all
     * method calls within its body.
     */
    public List<String> extractCallsFromMethod(String methodSource) {
        try {
            MethodDeclaration method = StaticJavaParser.parseMethodDeclaration(methodSource);
            return method.findAll(MethodCallExpr.class)
                    .stream()
                    .map(MethodCallExpr::getNameAsString)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to parse method declaration: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Represents one extracted class or method.
     * A plain record — no JPA annotations, no DB coupling.
     * This is the output of parsing, before anything is saved to DB.
     * IndexingService (Phase 4) converts these into CodeEntity objects.
     *
     * @Builder — lets IndexingService do:
     * ParsedEntity.builder().name("x").entityType(CLASS)...build()
     */
    @Builder
    public record ParsedEntity(
            String name,
            EntityType entityType,
            int startLine,
            int endLine,
            String sourceCode,
            List<String> calls    // method names this entity calls directly
    ) {}
}