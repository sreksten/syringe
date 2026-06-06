package com.threeamigos.common.util.implementations.injection;

import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.testpackages.forcedarchive.AnnotatedOrderService;
import com.threeamigos.common.util.implementations.injection.testpackages.forcedarchive.PlainOrderService;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.spi.Bean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Syringe - Forced BeanArchiveMode")
class SyringeBeanArchiveModeOverrideTest {

    @Test
    @DisplayName("Default mode keeps implicit discovery behavior")
    void defaultModeUsesImplicitDiscovery() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), PlainOrderService.class);

        try {
            syringe.setup();

            assertTrue(hasBean(syringe, AnnotatedOrderService.class),
                    "AnnotatedOrderService should be discovered in implicit mode");
            assertFalse(hasBean(syringe, PlainOrderService.class),
                    "PlainOrderService should not be discovered in implicit mode");
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("Forced explicit mode discovers classes without bean-defining annotations")
    void forcedExplicitModeDiscoversPlainClasses() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), PlainOrderService.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        try {
            syringe.setup();

            assertTrue(hasBean(syringe, AnnotatedOrderService.class),
                    "AnnotatedOrderService should be discovered in forced explicit mode");
            assertTrue(hasBean(syringe, PlainOrderService.class),
                    "PlainOrderService should be discovered in forced explicit mode");
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("Forced implicit mode keeps annotated-only discovery")
    void forcedImplicitModeKeepsAnnotatedOnlyDiscovery() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), PlainOrderService.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.IMPLICIT);

        try {
            syringe.setup();

            assertTrue(hasBean(syringe, AnnotatedOrderService.class),
                    "AnnotatedOrderService should be discovered in forced implicit mode");
            assertFalse(hasBean(syringe, PlainOrderService.class),
                    "PlainOrderService should not be discovered in forced implicit mode");
        } finally {
            syringe.shutdown();
        }
    }

    private boolean hasBean(Syringe syringe, Class<?> beanClass) {
        for (Bean<?> bean : syringe.getKnowledgeBase().getValidBeans()) {
            if (bean.getBeanClass().equals(beanClass)) {
                return true;
            }
        }
        return false;
    }
}
