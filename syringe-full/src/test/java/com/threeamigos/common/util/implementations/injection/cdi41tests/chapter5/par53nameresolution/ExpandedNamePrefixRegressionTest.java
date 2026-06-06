package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par53nameresolution;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.inject.Named;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("5.3.1 - Expanded name prefix regression")
class ExpandedNamePrefixRegressionTest {

    @Test
    @DisplayName("5.3.1 - Bean names 'foo' and 'foo.bar.baz' cause deployment problem due to prefix conflict")
    void shouldFailDeploymentForExpandedNamePrefixConflict() {
        Syringe syringe = new Syringe();
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        try {
            syringe.initialize();
            syringe.addDiscoveredClass(Foo.class, BeanArchiveMode.EXPLICIT);
            syringe.addDiscoveredClass(FooBarBaz.class, BeanArchiveMode.EXPLICIT);

            assertThrows(DefinitionException.class, syringe::start);
            String definitionErrors = String.join("\n", syringe.getKnowledgeBase().getDefinitionErrors());
            assertTrue(definitionErrors.contains("foo"));
            assertTrue(definitionErrors.contains("foo.bar.baz"));
        } finally {
            syringe.shutdown();
        }
    }

    @Named
    @Dependent
    static class Foo {
    }

    @Named("foo.bar.baz")
    @Dependent
    static class FooBarBaz {
    }
}
