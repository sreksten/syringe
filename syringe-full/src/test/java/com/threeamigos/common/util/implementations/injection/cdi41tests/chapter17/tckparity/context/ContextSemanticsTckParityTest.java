package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.tckparity.context;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("17.2/17.6 - TCK parity for context semantics")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class ContextSemanticsTckParityTest {

    @Test
    @DisplayName("17.2 / DestroyForSameCreationalContextTest - context destruction releases the same CreationalContext")
    void shouldMatchDestroyForSameCreationalContextTest() {
        Syringe syringe = newSyringe(AnotherRequestBean.class);
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        Context requestContext = beanManager.getContext(RequestScoped.class);
        Bean<AnotherRequestBean> bean = resolveBean(beanManager, AnotherRequestBean.class);

        beanManager.getContextManager().activateRequest();
        TrackingCreationalContext<AnotherRequestBean> trackingCc =
                new TrackingCreationalContext<AnotherRequestBean>(beanManager.createCreationalContext(bean));
        try {
            AnotherRequestBean instance = requestContext.get(bean, trackingCc);
            assertNotNull(instance);
            instance.ping();
        } finally {
            beanManager.getContextManager().deactivateRequest();
        }

        assertTrue(trackingCc.released.get());
    }

    @Test
    @DisplayName("17.2 / DestroyedInstanceReturnedByGetTest - destroyed contextual instance is not returned by Context.get(bean)")
    void shouldMatchDestroyedInstanceReturnedByGetTest() {
        Syringe syringe = newSyringe(MyRequestBean.class);
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        Context requestContext = beanManager.getContext(RequestScoped.class);
        Bean<MyRequestBean> bean = resolveBean(beanManager, MyRequestBean.class);

        beanManager.getContextManager().activateRequest();
        try {
            MyRequestBean created = requestContext.get(bean, beanManager.createCreationalContext(bean));
            assertNotNull(created);
        } finally {
            beanManager.getContextManager().deactivateRequest();
        }

        beanManager.getContextManager().activateRequest();
        try {
            assertNull(requestContext.get(bean));
        } finally {
            beanManager.getContextManager().deactivateRequest();
        }
    }

    @Test
    @DisplayName("17.2 / AlterableContextTest - AlterableContext.destroy(bean) destroys and removes contextual instance")
    void shouldMatchAlterableContextTest() {
        DestroyableApplicationBean.reset();
        Syringe syringe = newSyringe(DestroyableApplicationBean.class);
        BeanManager beanManager = syringe.getBeanManager();
        Bean<DestroyableApplicationBean> bean = resolveBean(beanManager, DestroyableApplicationBean.class);
        AlterableContext context = (AlterableContext) beanManager.getContext(ApplicationScoped.class);

        DestroyableApplicationBean first = context.get(bean, beanManager.createCreationalContext(bean));
        assertNotNull(first);
        context.destroy(bean);
        assertTrue(DestroyableApplicationBean.destroyed.get() >= 1);
        assertNull(context.get(bean));
    }

    @Test
    @DisplayName("17.2 / ContextDestroysBeansTest - destroying active context destroys contextual instances")
    void shouldMatchContextDestroysBeansTest() {
        SessionDestroyProbe.reset();
        Syringe syringe = newSyringe(SessionDestroyProbe.class);
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        Bean<SessionDestroyProbe> bean = resolveBean(beanManager, SessionDestroyProbe.class);
        Context sessionContext = beanManager.getContext(SessionScoped.class);

        beanManager.getContextManager().activateSession("context-destroys-beans");
        String sessionId = "context-destroys-beans";
        try {
            SessionDestroyProbe probe = sessionContext.get(bean, beanManager.createCreationalContext(bean));
            probe.ping();
        } finally {
            beanManager.getContextManager().invalidateSession(sessionId);
        }
        assertEquals(1, SessionDestroyProbe.destroyCount.get());
    }

    @Test
    @DisplayName("17.2 / ContextTest - BeanManager returns built-in contexts and throws for unregistered scope")
    void shouldMatchContextTestBuiltInAndUnregisteredScopes() {
        Syringe syringe = newSyringe(BootstrapAnchor.class);
        BeanManager beanManager = syringe.getBeanManager();

        assertNotNull(beanManager.getContext(Dependent.class));
        assertNotNull(beanManager.getContext(RequestScoped.class));
        assertNotNull(beanManager.getContext(SessionScoped.class));
        assertNotNull(beanManager.getContext(ApplicationScoped.class));

        assertThrows(ContextNotActiveException.class, () -> beanManager.getContext(Unregistered.class));
    }

    @Test
    @DisplayName("17.2 / ContextTest - BeanManager.getContext throws IllegalStateException when more than one active context exists")
    void shouldMatchContextTestTooManyActiveContexts() {
        Syringe syringe = createSyringe(BootstrapAnchor.class);
        syringe.addExtension(DuplicateDummyContextExtension.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        assertThrows(IllegalStateException.class, () -> beanManager.getContext(DummyScoped.class));
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = createSyringe(classes);
        syringe.setup();
        return syringe;
    }

    private Syringe createSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), classes);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> type) {
        Set<Bean<?>> beans = beanManager.getBeans(type);
        return (Bean<T>) beanManager.resolve((Set) beans);
    }

    static final class TrackingCreationalContext<T> implements CreationalContext<T> {
        private final CreationalContext<T> delegate;
        private final AtomicBoolean released = new AtomicBoolean(false);

        TrackingCreationalContext(CreationalContext<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void push(T incompleteInstance) {
            delegate.push(incompleteInstance);
        }

        @Override
        public void release() {
            released.set(true);
            delegate.release();
        }
    }

    @RequestScoped
    public static class AnotherRequestBean {
        public void ping() {
        }
    }

    @RequestScoped
    public static class MyRequestBean {
    }

    @ApplicationScoped
    public static class DestroyableApplicationBean {
        static final AtomicInteger destroyed = new AtomicInteger(0);

        static void reset() {
            destroyed.set(0);
        }

        @PreDestroy
        void destroy() {
            destroyed.incrementAndGet();
        }
    }

    @SessionScoped
    public static class SessionDestroyProbe implements Serializable {
        private static final long serialVersionUID = 1L;
        static final AtomicInteger destroyCount = new AtomicInteger(0);

        static void reset() {
            destroyCount.set(0);
        }

        public void ping() {
        }

        @PreDestroy
        void destroy() {
            destroyCount.incrementAndGet();
        }
    }

    @ApplicationScoped
    public static class BootstrapAnchor {
    }

    @NormalScope
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface DummyScoped {
    }

    @NormalScope
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface Unregistered {
    }

    public static class DuplicateDummyContextExtension implements Extension {
        public void afterBeanDiscovery(@Observes AfterBeanDiscovery abd) {
            abd.addContext(new AlwaysActiveDummyContext());
            abd.addContext(new AlwaysActiveDummyContext());
        }
    }

    static final class AlwaysActiveDummyContext implements Context {
        @Override
        public Class<? extends java.lang.annotation.Annotation> getScope() {
            return DummyScoped.class;
        }

        @Override
        public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
            return null;
        }

        @Override
        public <T> T get(Contextual<T> contextual) {
            return null;
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }
}
