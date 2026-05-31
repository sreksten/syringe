package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par62contextinterface;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("6.2 - Context interface")
public class ContextInterfaceTest {

    @Test
    @DisplayName("6.2 - getScope() returns the scope type and isActive() reflects context activity")
    void shouldExposeScopeTypeAndActivityState() {
        Syringe syringe = newSyringe(RequestContextBean.class);
        BeanManager beanManager = syringe.getBeanManager();
        BeanManagerImpl beanManagerImpl = (BeanManagerImpl) beanManager;
        ContextManager contextManager = beanManagerImpl.getContextManager();

        Context requestContext = beanManager.getContext(RequestScoped.class);
        assertEquals(RequestScoped.class, requestContext.getScope());
        assertFalse(requestContext.isActive());

        contextManager.activateRequest();
        assertTrue(requestContext.isActive());
        contextManager.deactivateRequest();
        assertFalse(requestContext.isActive());
    }

    @Test
    @DisplayName("6.2 - get(bean) returns null when no instance exists and no CreationalContext is given")
    void shouldReturnNullWithoutCreationalContextWhenNoExistingInstance() {
        Syringe syringe = newSyringe(RequestContextBean.class);
        BeanManager beanManager = syringe.getBeanManager();
        BeanManagerImpl beanManagerImpl = (BeanManagerImpl) beanManager;
        ContextManager contextManager = beanManagerImpl.getContextManager();

        Context requestContext = beanManager.getContext(RequestScoped.class);
        Bean<RequestContextBean> bean = resolveBean(beanManager, RequestContextBean.class);

        contextManager.activateRequest();
        assertNull(requestContext.get(bean));
        contextManager.deactivateRequest();
    }

    @Test
    @DisplayName("6.2 - get(bean, CreationalContext) creates instance and get(bean) returns existing instance")
    void shouldCreateAndReturnExistingContextualInstance() {
        Syringe syringe = newSyringe(RequestContextBean.class);
        BeanManager beanManager = syringe.getBeanManager();
        BeanManagerImpl beanManagerImpl = (BeanManagerImpl) beanManager;
        ContextManager contextManager = beanManagerImpl.getContextManager();

        Context requestContext = beanManager.getContext(RequestScoped.class);
        Bean<RequestContextBean> bean = resolveBean(beanManager, RequestContextBean.class);

        contextManager.activateRequest();
        CreationalContext<RequestContextBean> creationalContext = beanManager.createCreationalContext(bean);
        RequestContextBean created = requestContext.get(bean, creationalContext);
        RequestContextBean existing = requestContext.get(bean);

        assertNotNull(created);
        assertSame(created, existing);
        contextManager.deactivateRequest();
    }

    @Test
    @DisplayName("6.2 - inactive context throws ContextNotActiveException for get() and destroy()")
    void shouldThrowContextNotActiveExceptionWhenContextInactive() {
        Syringe syringe = newSyringe(RequestContextBean.class);
        BeanManager beanManager = syringe.getBeanManager();
        Context requestContext = beanManager.getContext(RequestScoped.class);
        Bean<RequestContextBean> bean = resolveBean(beanManager, RequestContextBean.class);
        CreationalContext<RequestContextBean> creationalContext = beanManager.createCreationalContext(bean);

        assertThrows(ContextNotActiveException.class, () -> requestContext.get(bean));
        assertThrows(ContextNotActiveException.class, () -> requestContext.get(bean, creationalContext));

        AlterableContext alterableContext = (AlterableContext) requestContext;
        assertThrows(ContextNotActiveException.class, () -> alterableContext.destroy(bean));
    }

    @Test
    @DisplayName("6.2 - AlterableContext.destroy() removes contextual instance and destroyed instance is not returned")
    void shouldDestroyAndRemoveContextualInstance() {
        ApplicationContextBean.reset();
        Syringe syringe = newSyringe(ApplicationContextBean.class);
        BeanManager beanManager = syringe.getBeanManager();

        Context appContext = beanManager.getContext(ApplicationScoped.class);
        AlterableContext alterableContext = (AlterableContext) appContext;
        Bean<ApplicationContextBean> bean = resolveBean(beanManager, ApplicationContextBean.class);

        CreationalContext<ApplicationContextBean> creationalContext = beanManager.createCreationalContext(bean);
        ApplicationContextBean first = appContext.get(bean, creationalContext);
        assertNotNull(first);
        assertSame(first, appContext.get(bean));

        alterableContext.destroy(bean);
        assertEquals(1, ApplicationContextBean.preDestroyCalls());
        assertNull(appContext.get(bean));

        ApplicationContextBean second = appContext.get(bean, beanManager.createCreationalContext(bean));
        assertNotNull(second);
        assertTrue(first != second);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> beanType) {
        Set<Bean<?>> beans = beanManager.getBeans(beanType);
        return (Bean<T>) beanManager.resolve((Set) beans);
    }

    @RequestScoped
    public static class RequestContextBean {
    }

    @ApplicationScoped
    public static class ApplicationContextBean {
        private static final AtomicInteger PRE_DESTROY_CALLS = new AtomicInteger(0);

        public static void reset() {
            PRE_DESTROY_CALLS.set(0);
        }

        public static int preDestroyCalls() {
            return PRE_DESTROY_CALLS.get();
        }

        @PreDestroy
        void preDestroy() {
            PRE_DESTROY_CALLS.incrementAndGet();
        }
    }
}
