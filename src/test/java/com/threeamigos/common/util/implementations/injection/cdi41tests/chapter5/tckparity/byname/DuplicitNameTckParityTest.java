package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.tckparity.byname;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.inject.Named;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("5.3.1 - TCK parity for DuplicitNameTest")
class DuplicitNameTckParityTest {

    @Test
    @DisplayName("DuplicitNameTest - duplicate @Named beans cause deployment problem")
    void shouldFailDeploymentForDuplicateNamedBeans() {
        Syringe syringe = new Syringe();
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        try {
            syringe.initialize();
            syringe.addDiscoveredClass(Cod.class, BeanArchiveMode.EXPLICIT);
            syringe.addDiscoveredClass(Sole.class, BeanArchiveMode.EXPLICIT);

            assertThrows(DefinitionException.class, syringe::start);
            String definitionErrors = String.join("\n", syringe.getKnowledgeBase().getDefinitionErrors());
            assertTrue(definitionErrors.contains("Ambiguous bean name"));
            assertTrue(definitionErrors.contains("whitefish"));
        } finally {
            syringe.shutdown();
        }
    }

    @Named("whitefish")
    @Dependent
    static class Cod {
    }

    @Named("whitefish")
    @Dependent
    static class Sole {
    }
}
