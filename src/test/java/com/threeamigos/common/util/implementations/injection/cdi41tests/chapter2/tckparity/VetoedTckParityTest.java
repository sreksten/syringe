package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.tckparity.vetoedaquarium.Piranha;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("2 / TCK parity for @Vetoed behavior")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class VetoedTckParityTest {

    @Test
    @DisplayName("2 / VetoedTest - class-level @Vetoed class is not discovered and not available as bean")
    void shouldExcludeClassLevelVetoedTypes() {
        VetoRecorder.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), Shark.class);
        syringe.forceBeanArchiveMode(com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode.EXPLICIT);
        excludeSiblingParityFixtures(syringe);
        syringe.addExtension(VetoRecorder.class.getName());
        syringe.setup();

        assertFalse(VetoRecorder.getClasses().contains(Elephant.class));
        assertEquals(0, syringe.getBeanManager().getBeans(Elephant.class).size());
        assertTrue(VetoRecorder.getClasses().contains(Shark.class));
        assertEquals(1, syringe.getBeanManager().getBeans(Shark.class).size());
    }

    @Test
    @DisplayName("2 / VetoedTest - package-level @Vetoed excludes all classes in that package")
    void shouldExcludePackageLevelVetoedTypes() {
        VetoRecorder.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), Shark.class);
        syringe.forceBeanArchiveMode(com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode.EXPLICIT);
        excludeSiblingParityFixtures(syringe);
        syringe.addExtension(VetoRecorder.class.getName());
        syringe.setup();

        assertFalse(VetoRecorder.getClasses().contains(Piranha.class));
        assertEquals(0, syringe.getBeanManager().getBeans(Piranha.class).size());
        assertTrue(VetoRecorder.getClasses().contains(Shark.class));
        assertEquals(1, syringe.getBeanManager().getBeans(Shark.class).size());
    }

    private void excludeSiblingParityFixtures(Syringe syringe) {
        excludeParityClassAndNested(syringe,
                "com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.tckparity.AlternativeAndStereotypeTckParityTest");
        excludeParityClassAndNested(syringe,
                "com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.tckparity.ScopeAndQualifierDefinitionTckParityTest");
    }

    private void excludeParityClassAndNested(Syringe syringe, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            syringe.exclude(clazz);
            for (Class<?> nested : clazz.getDeclaredClasses()) {
                syringe.exclude(nested);
            }
        } catch (ClassNotFoundException ignored) {
        }
    }

    public static class VetoRecorder implements Extension {
        private static final Set<Class<?>> classes = new HashSet<Class<?>>();

        public static void reset() {
            classes.clear();
        }

        public static Set<Class<?>> getClasses() {
            return Collections.unmodifiableSet(classes);
        }

        public void observePat(@Observes ProcessAnnotatedType<?> event) {
            classes.add(event.getAnnotatedType().getJavaClass());
        }
    }

    @Vetoed
    public static class Elephant {
    }

    public static class Shark {
    }
}
