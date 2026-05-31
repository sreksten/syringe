package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.par177contextmanagementforcustomscopes;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.context.RequestScoped;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("17.7 - Context management for custom scopes in CDI")
public class ContextManagementForCustomScopesInCDIFullTest {

    @Test
    @DisplayName("17.7 - A portable extension may define a custom context object for a built-in scope")
    void shouldAllowPortableExtensionToProvideContextForBuiltInRequestScope() {
        BuiltInRequestContextExtension.reset();

        Syringe syringe = newSyringe(
                PortableExtensionAnchor.class,
                RequestScopedProbe.class
        );
        syringe.addExtension(BuiltInRequestContextExtension.class.getName());
        assertDoesNotThrow(syringe::setup);

        BeanManager beanManager = syringe.getBeanManager();
        Bean<RequestScopedProbe> bean = resolveBean(beanManager, RequestScopedProbe.class);
        beanManager.getContext(RequestScoped.class).get(bean, beanManager.createCreationalContext(bean));

        RecordingRequestContext context = BuiltInRequestContextExtension.registeredContext();
        assertNotNull(context);
        assertTrue(context.getCalls() >= 1);
    }

    @Test
    @DisplayName("17.7 - A portable extension may define a custom context object for a custom scope")
    void shouldAllowPortableExtensionToProvideContextForCustomScope() {
        CustomScopeContextExtension.reset();

        Syringe syringe = newSyringe(
                PortableExtensionAnchor.class,
                RemoteScopedProbe.class
        );
        syringe.addExtension(CustomScopeContextExtension.class.getName());
        assertDoesNotThrow(syringe::setup);

        BeanManager beanManager = syringe.getBeanManager();
        Bean<RemoteScopedProbe> bean = resolveBean(beanManager, RemoteScopedProbe.class);
        beanManager.getContext(RemoteRequestScoped.class).get(bean, beanManager.createCreationalContext(bean));

        RecordingRemoteScopeContext context = CustomScopeContextExtension.registeredContext();
        assertNotNull(context);
        assertTrue(context.getCalls() >= 1);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> beanType) {
        Set<Bean<?>> beans = beanManager.getBeans(beanType);
        return (Bean<T>) beanManager.resolve((Set) beans);
    }

    @ApplicationScoped
    public static class PortableExtensionAnchor {
    }

    @RequestScoped
    public static class RequestScopedProbe {
    }

    @RemoteRequestScoped
    public static class RemoteScopedProbe {
    }

    @NormalScope
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    public @interface RemoteRequestScoped {
    }

    public static class BuiltInRequestContextExtension implements Extension {
        private static volatile RecordingRequestContext REGISTERED;

        static void reset() {
            REGISTERED = null;
        }

        static RecordingRequestContext registeredContext() {
            return REGISTERED;
        }

        public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
            RecordingRequestContext context = new RecordingRequestContext();
            REGISTERED = context;
            afterBeanDiscovery.addContext(context);
        }
    }

    public static class CustomScopeContextExtension implements Extension {
        private static volatile RecordingRemoteScopeContext REGISTERED;

        static void reset() {
            REGISTERED = null;
        }

        static RecordingRemoteScopeContext registeredContext() {
            return REGISTERED;
        }

        public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
            RecordingRemoteScopeContext context = new RecordingRemoteScopeContext();
            REGISTERED = context;
            afterBeanDiscovery.addContext(context);
        }
    }

    static class RecordingRequestContext implements Context {
        private final ConcurrentMap<Contextual<?>, Object> instances = new ConcurrentHashMap<Contextual<?>, Object>();
        private volatile int getCalls;

        @Override
        public Class<? extends java.lang.annotation.Annotation> getScope() {
            return RequestScoped.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
            getCalls++;
            Object existing = instances.get(contextual);
            if (existing != null) {
                return (T) existing;
            }
            T created = contextual.create(creationalContext);
            if (created != null) {
                instances.put(contextual, created);
            }
            return created;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Contextual<T> contextual) {
            return (T) instances.get(contextual);
        }

        @Override
        public boolean isActive() {
            return true;
        }

        int getCalls() {
            return getCalls;
        }
    }

    static class RecordingRemoteScopeContext implements Context {
        private final ConcurrentMap<Contextual<?>, Object> instances = new ConcurrentHashMap<Contextual<?>, Object>();
        private volatile int getCalls;

        @Override
        public Class<? extends java.lang.annotation.Annotation> getScope() {
            return RemoteRequestScoped.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
            getCalls++;
            Object existing = instances.get(contextual);
            if (existing != null) {
                return (T) existing;
            }
            T created = contextual.create(creationalContext);
            if (created != null) {
                instances.put(contextual, created);
            }
            return created;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Contextual<T> contextual) {
            return (T) instances.get(contextual);
        }

        @Override
        public boolean isActive() {
            return true;
        }

        int getCalls() {
            return getCalls;
        }
    }
}
