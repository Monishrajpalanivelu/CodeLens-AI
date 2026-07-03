package com.codelens.ai.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "code_entities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private Repository repository;

    // UUID string returned by Spring AI's vectorStore.add() — links this
    // row to its embedding in the vector_store table. Nullable because
    // a row can exist before it's been embedded.
    @Column(name = "vector_doc_id", length = 36)
    private String vectorDocId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private EntityType entityType;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "end_line")
    private Integer endLine;

    @Column(name = "source_code", columnDefinition = "TEXT")
    private String sourceCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum EntityType {
        CLASS,
        FUNCTION
    }
}