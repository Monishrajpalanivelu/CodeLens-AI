package com.codelens.ai.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dependencies")
@IdClass(DependencyId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dependency {

    @Id
    @Column(name = "from_entity_id")
    private Long fromEntityId;

    @Id
    @Column(name = "to_entity_id")
    private Long toEntityId;

    @Column(name = "dependency_type", length = 50)
    private String dependencyType;
}