package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par50introduction;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par50introduction.circular.A;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.spi.DeploymentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName( "5 - Circularities tests")
public class CircularitiesTest {

    @Test
    @DisplayName("5 - Circular dependency is reported as A -> B -> A and interrupts deployment")
    void circularDependencyIsReportedAndInterruptsDeployment() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), A.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DeploymentException.class, syringe::setup);

        String injectionErrors = String.join("\n", syringe.getKnowledgeBase().getInjectionErrors());
        assertTrue(injectionErrors.contains("Circular dependency detected: A -> B -> A"));
    }
}
