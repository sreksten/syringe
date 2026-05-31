package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.par171contextualinterface;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("17.1 - Contextual interface in CDI full test")
public class ContextualInterfaceInCDIFullTest {

    @Test
    @DisplayName("17.1 - Portable extensions may define Contextual implementations that are not Beans")
    void shouldAllowPortableExtensionDefinedContextualThatIsNotBean() {
        PortableExtensionRegistry.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ExtensionAnchor.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addExtension(PortableContextualExtension.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        PortableContext context = PortableExtensionRegistry.context();
        PortablePayloadContextual contextual = PortableExtensionRegistry.contextual();

        assertNotNull(context);
        assertNotNull(contextual);
        assertFalse(contextual instanceof Bean);

        context.activate();
        try {
            CreationalContext<PortablePayload> creationalContext = beanManager.createCreationalContext(contextual);
            PortablePayload first = context.get(contextual, creationalContext);
            PortablePayload second = context.get(contextual);

            assertNotNull(first);
            assertSame(first, second);
            assertEquals(1, contextual.createCalls());
        } finally {
            context.deactivate();
        }

        assertEquals(1, contextual.destroyCalls());
    }

    @ApplicationScoped
    static class ExtensionAnchor {
    }

    @NormalScope
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    public @interface PortableScoped {
    }

    static class PortablePayload {
        private final String value = "portable-contextual";

        String value() {
            return value;
        }
    }

    static class PortablePayloadContextual implements Contextual<PortablePayload> {
        private int createCalls;
        private int destroyCalls;

        @Override
        public PortablePayload create(CreationalContext<PortablePayload> creationalContext) {
            createCalls++;
            return new PortablePayload();
        }

        @Override
        public void destroy(PortablePayload instance, CreationalContext<PortablePayload> creationalContext) {
            destroyCalls++;
            creationalContext.release();
        }

        int createCalls() {
            return createCalls;
        }

        int destroyCalls() {
            return destroyCalls;
        }
    }

    static class PortableContext implements Context {
        private final Map<Contextual<?>, Entry<?>> instances = new ConcurrentHashMap<Contextual<?>, Entry<?>>();
        private volatile boolean active;

        void activate() {
            active = true;
        }

        void deactivate() {
            if (!active) {
                return;
            }
            for (Entry<?> entry : instances.values()) {
                entry.destroy();
            }
            instances.clear();
            active = false;
        }

        @Override
        public Class<? extends java.lang.annotation.Annotation> getScope() {
            return PortableScoped.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
            if (!active) {
                return null;
            }
            Entry<?> existing = instances.get(contextual);
            if (existing != null) {
                return (T) existing.instance;
            }

            T created = contextual.create(creationalContext);
            if (created == null) {
                return null;
            }

            Entry<T> entry = new Entry<T>(contextual, created, creationalContext);
            instances.put(contextual, entry);
            return created;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Contextual<T> contextual) {
            if (!active) {
                return null;
            }
            Entry<?> entry = instances.get(contextual);
            return entry == null ? null : (T) entry.instance;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        private static class Entry<T> {
            private final Contextual<T> contextual;
            private final T instance;
            private final CreationalContext<T> creationalContext;

            private Entry(Contextual<T> contextual, T instance, CreationalContext<T> creationalContext) {
                this.contextual = contextual;
                this.instance = instance;
                this.creationalContext = creationalContext;
            }

            private void destroy() {
                contextual.destroy(instance, creationalContext);
            }
        }
    }

    public static class PortableContextualExtension implements Extension {
        public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
            PortableContext context = new PortableContext();
            PortablePayloadContextual contextual = new PortablePayloadContextual();
            PortableExtensionRegistry.register(context, contextual);
            afterBeanDiscovery.addContext(context);
        }
    }

    static class PortableExtensionRegistry {
        private static volatile PortableContext context;
        private static volatile PortablePayloadContextual contextual;

        static void register(PortableContext context, PortablePayloadContextual contextual) {
            PortableExtensionRegistry.context = context;
            PortableExtensionRegistry.contextual = contextual;
        }

        static PortableContext context() {
            return context;
        }

        static PortablePayloadContextual contextual() {
            return contextual;
        }

        static void reset() {
            context = null;
            contextual = null;
        }
    }
}
