package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter11.par111beancontainer;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.SyringeCDIProvider;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.CDIProvider;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("11.1.1 - CDI.current() provider lookup")
@Isolated
class CdiCurrentDynamicLookupTest {

    private CdiStateSnapshot cdiStateSnapshot;

    @BeforeEach
    void captureCdiState() throws Exception {
        cdiStateSnapshot = CdiStateSnapshot.capture();
    }

    @AfterEach
    void restoreCdiState() throws Exception {
        if (cdiStateSnapshot != null) {
            cdiStateSnapshot.restore();
        }
        SyringeCDIProvider.unregisterThreadLocalCDI();
        SyringeCDIProvider.unregisterGlobalCDI();
    }

    @Test
    @DisplayName("11.1.1 / 5.6 - CDI.current().select(...) returns an unsatisfied Instance when no default bean matches")
    void shouldReturnUnsatisfiedInstanceFromCdiCurrentSelectWhenDefaultBeanIsMissing() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler());
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        try {
            syringe.initialize();
            syringe.addDiscoveredClass(QualifiedOnlyAsyncProcessor.class, BeanArchiveMode.EXPLICIT);
            syringe.start();

            CDI.setCDIProvider(new SyringeCDIProvider());
            SyringeCDIProvider.unregisterThreadLocalCDI();

            Instance<AsyncProcessor> processors = CDI.current().select(AsyncProcessor.class);
            assertTrue(processors.isUnsatisfied());
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("11.1.1 / 5.6 - CDI.current().select(...) works even when another provider was configured earlier")
    void shouldOverridePreconfiguredProviderThatCannotAccessContainer() throws Exception {
        setConfiguredProvider(new NullReturningProvider());

        Syringe syringe = new Syringe(new InMemoryMessageHandler());
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        try {
            syringe.initialize();
            syringe.addDiscoveredClass(QualifiedOnlyAsyncProcessor.class, BeanArchiveMode.EXPLICIT);
            syringe.start();

            SyringeCDIProvider.unregisterThreadLocalCDI();
            Instance<AsyncProcessor> processors = CDI.current().select(AsyncProcessor.class);
            assertTrue(processors.isUnsatisfied());
        } finally {
            syringe.shutdown();
        }
    }

    private static void setDiscoveredProviders(Set<CDIProvider> providers) throws Exception {
        Field field = CDI.class.getDeclaredField("discoveredProviders");
        field.setAccessible(true);
        field.set(null, providers);
    }

    private static void setConfiguredProvider(CDIProvider provider) throws Exception {
        Field field = CDI.class.getDeclaredField("configuredProvider");
        field.setAccessible(true);
        field.set(null, provider);
    }

    private static final class CdiStateSnapshot {
        private final CDIProvider configuredProvider;
        private final Set<CDIProvider> discoveredProviders;

        private CdiStateSnapshot(CDIProvider configuredProvider, Set<CDIProvider> discoveredProviders) {
            this.configuredProvider = configuredProvider;
            this.discoveredProviders = discoveredProviders;
        }

        @SuppressWarnings("unchecked")
        static CdiStateSnapshot capture() throws Exception {
            Field configuredField = CDI.class.getDeclaredField("configuredProvider");
            configuredField.setAccessible(true);
            Field discoveredField = CDI.class.getDeclaredField("discoveredProviders");
            discoveredField.setAccessible(true);
            return new CdiStateSnapshot(
                    (CDIProvider) configuredField.get(null),
                    (Set<CDIProvider>) discoveredField.get(null)
            );
        }

        void restore() throws Exception {
            setConfiguredProvider(configuredProvider);
            setDiscoveredProviders(discoveredProviders);
        }
    }

    interface AsyncProcessor {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    @interface SpecialAsync {
    }

    @Dependent
    @SpecialAsync
    static class QualifiedOnlyAsyncProcessor implements AsyncProcessor {
    }

    private static final class NullReturningProvider implements CDIProvider {
        @Override
        public CDI<Object> getCDI() {
            return null;
        }
    }
}
