package com.codelens.ai.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite primary key for Dependency. @IdClass requires this to:
 * - implement Serializable
 * - have a no-args constructor
 * - have fields matching the @Id fields in Dependency by name
 * - implement equals()/hashCode() (Lombok @EqualsAndHashCode handles this)
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DependencyId implements Serializable {
    private Long fromEntityId;
    private Long toEntityId;
}