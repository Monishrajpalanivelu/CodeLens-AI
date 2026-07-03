package com.codelens.ai.ast;

import com.codelens.ai.model.CodeEntity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaAstServiceTest {

    private final JavaAstService astService = new JavaAstService();

    private static final String SAMPLE_CODE = """
            public class UserService {

                public User findById(Long id) {
                    User user = userRepository.findById(id);
                    log.info("found user");
                    return user;
                }

                public void deleteUser(Long id) {
                    userRepository.deleteById(id);
                    auditService.log("deleted", id);
                }
            }
            """;

    @Test
    void shouldExtractClassAndMethods() {
        List<JavaAstService.ParsedEntity> entities =
                astService.extractEntities(SAMPLE_CODE, "UserService.java");

        // 1 class + 2 methods = 3 entities
        assertThat(entities).hasSize(3);
    }

    @Test
    void shouldExtractClassName() {
        List<JavaAstService.ParsedEntity> entities =
                astService.extractEntities(SAMPLE_CODE, "UserService.java");

        assertThat(entities)
                .filteredOn(e -> e.entityType() == EntityType.CLASS)
                .extracting(JavaAstService.ParsedEntity::name)
                .containsExactly("UserService");
    }

    @Test
    void shouldExtractMethodNames() {
        List<JavaAstService.ParsedEntity> entities =
                astService.extractEntities(SAMPLE_CODE, "UserService.java");

        assertThat(entities)
                .filteredOn(e -> e.entityType() == EntityType.FUNCTION)
                .extracting(JavaAstService.ParsedEntity::name)
                .containsExactlyInAnyOrder("findById", "deleteUser");
    }

    @Test
    void shouldExtractCallSites() {
        List<JavaAstService.ParsedEntity> entities =
                astService.extractEntities(SAMPLE_CODE, "UserService.java");

        JavaAstService.ParsedEntity findById = entities.stream()
                .filter(e -> e.name().equals("findById"))
                .findFirst().orElseThrow();

        // findById calls: userRepository.findById() and log.info()
        assertThat(findById.calls())
                .containsExactlyInAnyOrder("findById", "info");
    }

    @Test
    void shouldReturnEmptyListOnBadCode() {
        // Verifies the service never throws — bad files are skipped gracefully
        List<JavaAstService.ParsedEntity> entities =
                astService.extractEntities("this is not java code @@##", "bad.java");

        assertThat(entities).isEmpty();
    }
}