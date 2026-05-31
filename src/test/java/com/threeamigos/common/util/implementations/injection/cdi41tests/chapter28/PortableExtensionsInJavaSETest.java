package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter28;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName( "28 - Portable extensions in Java SE")
@Execution(ExecutionMode.SAME_THREAD)
public class PortableExtensionsInJavaSETest {

    @Test
    @DisplayName("28.1.1 - In Java SE, SeContainer is the preferred way to obtain BeanManager")
    void shouldObtainBeanManagerThroughSeContainer() {
        try (SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(Chapter28_1SimpleBean.class)
                .initialize()) {
            BeanManager beanManager = container.getBeanManager();
            assertNotNull(beanManager);
            assertEquals("ok", container.select(Chapter28_1SimpleBean.class).get().value());
        }
    }

    @Test
    @DisplayName("28.1.1 - In Java SE, CDI.current access remains available while container is running")
    void shouldAllowCdiCurrentAccessWhileContainerIsRunning() {
        try (SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(Chapter28_1SimpleBean.class)
                .initialize()) {
            BeanManager fromContainer = container.getBeanManager();
            BeanManager fromCdiCurrent = CDI.current().getBeanManager();

            assertNotNull(fromContainer);
            assertNotNull(fromCdiCurrent);
            assertEquals(fromContainer, fromCdiCurrent);
            assertEquals("ok", CDI.current().select(Chapter28_1SimpleBean.class).get().value());
        }
    }

    @Test
    @DisplayName("28.1.1 - In Java SE, CDI.current is unavailable after shutdown while SeContainer defines lifecycle")
    void shouldMakeCdiCurrentUnavailableAfterShutdown() {
        SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(Chapter28_1SimpleBean.class)
                .initialize();
        container.close();

        assertThrows(IllegalStateException.class, CDI::current);
    }

    @Dependent
    public static class Chapter28_1SimpleBean {
        public String value() {
            return "ok";
        }
    }
}
