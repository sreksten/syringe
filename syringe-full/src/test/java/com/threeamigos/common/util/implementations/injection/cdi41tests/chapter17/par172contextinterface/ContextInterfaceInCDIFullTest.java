package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.par172contextinterface;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("17.2 - Context interface in CDI full test")
public class ContextInterfaceInCDIFullTest {

    @Test
    @DisplayName("17.2 - For passivating scopes, Context.get() and Context.destroy() must receive serializable Contextual and CreationalContext")
    void shouldRequireSerializableArgumentsForPassivatingScopeContextCalls() {
        PassivatingScopeContextExtension.reset();

        Syringe syringe = newSyringe(
                SessionScopedProbe.class,
                PortableExtensionAnchor.class
        );
        syringe.addExtension(PassivatingScopeContextExtension.class.getName());

        assertDoesNotThrow(syringe::setup);

        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateSession("17-2-passivating-scope");
        try {
            SessionScopedProbe probe = syringe.inject(SessionScopedProbe.class);
            assertEquals("pong", probe.ping());

            Bean<SessionScopedProbe> bean = resolveBean(beanManager, SessionScopedProbe.class);
            assertTrue(bean instanceof Serializable, bean.getClass().getName());
            AlterableContext context = (AlterableContext) beanManager.getContext(SessionScoped.class);
            context.destroy(bean);
        } finally {
            beanManager.getContextManager().deactivateSession();
        }

        PassivatingSessionContext context = PassivatingScopeContextExtension.registeredContext();
        assertNotNull(context);
        assertTrue(context.lastGetContextualSerializable());
        assertTrue(context.lastGetCreationalContextSerializable());
        assertTrue(context.lastDestroyContextualSerializable());
    }

    @Test
    @DisplayName("17.2 - Context interface may be called by portable extensions")
    void shouldAllowPortableExtensionsToCallContextInterface() {
        PortableContextCallerExtension.reset();

        Syringe syringe = newSyringe(PortableExtensionAnchor.class);
        syringe.addExtension(PortableContextCallerExtension.class.getName());
        syringe.setup();

        assertTrue(PortableContextCallerExtension.wasContextCallObserved());
    }

    @Test
    @DisplayName("17.2 - Portable extensions may register Context for built-in scopes via AfterBeanDiscovery")
    void shouldAllowPortableExtensionToRegisterContextForBuiltInScope() {
        BuiltInScopeContextExtension.reset();

        Syringe syringe = newSyringe(
                ApplicationScopedProbe.class,
                PortableExtensionAnchor.class
        );
        syringe.addExtension(BuiltInScopeContextExtension.class.getName());

        assertDoesNotThrow(syringe::setup);

        BeanManager beanManager = syringe.getBeanManager();
        Bean<ApplicationScopedProbe> bean = resolveBean(beanManager, ApplicationScopedProbe.class);
        Context context = beanManager.getContext(ApplicationScoped.class);
        context.get(bean, beanManager.createCreationalContext(bean));

        RecordingApplicationContext registered = BuiltInScopeContextExtension.registeredContext();
        assertNotNull(registered);
        assertTrue(registered.getCalls() >= 1);
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
    static class PortableExtensionAnchor {
    }

    @SessionScoped
    public static class SessionScopedProbe implements Serializable {
        public String ping() {
            return "pong";
        }
    }

    @ApplicationScoped
    public static class ApplicationScopedProbe {
    }

    public static class PassivatingScopeContextExtension implements Extension {
        private static volatile PassivatingSessionContext REGISTERED;

        static void reset() {
            REGISTERED = null;
        }

        static PassivatingSessionContext registeredContext() {
            return REGISTERED;
        }

        public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
            PassivatingSessionContext context = new PassivatingSessionContext();
            REGISTERED = context;
            afterBeanDiscovery.addContext(context);
        }
    }

    public static class PortableContextCallerExtension implements Extension {
        private static volatile boolean contextCallObserved;

        static void reset() {
            contextCallObserved = false;
        }

        static boolean wasContextCallObserved() {
            return contextCallObserved;
        }

        public void afterDeploymentValidation(@Observes AfterDeploymentValidation ignored, BeanManager beanManager) {
            Context context = beanManager.getContext(ApplicationScoped.class);
            contextCallObserved = context.isActive();
        }
    }

    public static class BuiltInScopeContextExtension implements Extension {
        private static volatile RecordingApplicationContext REGISTERED;

        static void reset() {
            REGISTERED = null;
        }

        static RecordingApplicationContext registeredContext() {
            return REGISTERED;
        }

        public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
            RecordingApplicationContext context = new RecordingApplicationContext();
            REGISTERED = context;
            afterBeanDiscovery.addContext(context);
        }
    }

    static class PassivatingSessionContext implements AlterableContext {
        private final Map<Contextual<?>, Entry<?>> instances = new ConcurrentHashMap<Contextual<?>, Entry<?>>();
        private volatile boolean lastGetContextualSerializable;
        private volatile boolean lastGetCreationalContextSerializable;
        private volatile boolean lastDestroyContextualSerializable;

        @Override
        public Class<? extends java.lang.annotation.Annotation> getScope() {
            return SessionScoped.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
            lastGetContextualSerializable = contextual instanceof Serializable;
            lastGetCreationalContextSerializable = creationalContext instanceof Serializable;

            Entry<?> existing = instances.get(contextual);
            if (existing != null) {
                return (T) existing.instance;
            }

            T created = contextual.create(creationalContext);
            if (created == null) {
                return null;
            }
            instances.put(contextual, new Entry<T>(contextual, created, creationalContext));
            return created;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Contextual<T> contextual) {
            Entry<?> entry = instances.get(contextual);
            return entry == null ? null : (T) entry.instance;
        }

        @Override
        public void destroy(Contextual<?> contextual) {
            lastDestroyContextualSerializable = contextual instanceof Serializable;

            Entry<?> entry = instances.remove(contextual);
            if (entry != null) {
                entry.destroy();
            }
        }

        @Override
        public boolean isActive() {
            return true;
        }

        boolean lastGetContextualSerializable() {
            return lastGetContextualSerializable;
        }

        boolean lastGetCreationalContextSerializable() {
            return lastGetCreationalContextSerializable;
        }

        boolean lastDestroyContextualSerializable() {
            return lastDestroyContextualSerializable;
        }
    }

    static class RecordingApplicationContext implements Context {
        private final Map<Contextual<?>, Entry<?>> instances = new ConcurrentHashMap<Contextual<?>, Entry<?>>();
        private volatile int getCalls;

        @Override
        public Class<? extends java.lang.annotation.Annotation> getScope() {
            return ApplicationScoped.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
            getCalls++;

            Entry<?> existing = instances.get(contextual);
            if (existing != null) {
                return (T) existing.instance;
            }

            T created = contextual.create(creationalContext);
            if (created == null) {
                return null;
            }
            instances.put(contextual, new Entry<T>(contextual, created, creationalContext));
            return created;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Contextual<T> contextual) {
            Entry<?> entry = instances.get(contextual);
            return entry == null ? null : (T) entry.instance;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        int getCalls() {
            return getCalls;
        }
    }

    static class Entry<T> {
        private final Contextual<T> contextual;
        private final T instance;
        private final CreationalContext<T> creationalContext;

        Entry(Contextual<T> contextual, T instance, CreationalContext<T> creationalContext) {
            this.contextual = contextual;
            this.instance = instance;
            this.creationalContext = creationalContext;
        }

        void destroy() {
            contextual.destroy(instance, creationalContext);
        }
    }
}
