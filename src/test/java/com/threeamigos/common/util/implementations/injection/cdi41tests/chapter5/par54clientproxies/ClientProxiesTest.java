package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par54clientproxies;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("5.4 - Client Proxies Test")
public class ClientProxiesTest {

    @Test
    @DisplayName("5.4 - Normal-scoped injection uses client proxy while @Dependent injection does not require proxy")
    void shouldInjectClientProxyForNormalScopeAndDirectReferenceForDependentScope() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ApplicationScopedConsumer.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        ApplicationScopedConsumer consumer = syringe.inject(ApplicationScopedConsumer.class);
        RequestScopedService requestScopedReference = consumer.getRequestScopedService();
        DependentService dependentReference = consumer.getDependentService();

        assertNotNull(requestScopedReference);
        assertNotNull(dependentReference);
        assertNotEquals(RequestScopedService.class, requestScopedReference.getClass(),
                "Normal-scoped reference should be a client proxy");
        assertEquals(DependentService.class, dependentReference.getClass(),
                "Dependent reference should be the concrete instance");
    }

    @Test
    @DisplayName("5.4.1 - Client proxy invocation resolves contextual instance on each method call")
    void shouldResolveContextualInstancePerInvocation() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ApplicationScopedConsumer.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        ApplicationScopedConsumer consumer = syringe.inject(ApplicationScopedConsumer.class);
        RequestScopedService requestScopedReference = consumer.getRequestScopedService();
        ContextManager contextManager = ((BeanManagerImpl) syringe.getBeanManager()).getContextManager();

        Throwable inactiveCall = assertThrows(Throwable.class, requestScopedReference::currentRequestInstanceId);
        assertContextInactiveOrIllegalState(inactiveCall);

        contextManager.activateRequest();
        try {
            warmUpRequestScopedInstance(syringe, RequestScopedService.class);
            String firstCallId = requestScopedReference.currentRequestInstanceId();
            String secondCallId = requestScopedReference.currentRequestInstanceId();
            assertEquals(firstCallId, secondCallId,
                    "Same active request should reuse the same contextual instance");
        } finally {
            contextManager.deactivateRequest();
        }

        contextManager.activateRequest();
        try {
            warmUpRequestScopedInstance(syringe, RequestScopedService.class);
            String newRequestId = requestScopedReference.currentRequestInstanceId();
            contextManager.deactivateRequest();

            contextManager.activateRequest();
            try {
                warmUpRequestScopedInstance(syringe, RequestScopedService.class);
                String anotherRequestId = requestScopedReference.currentRequestInstanceId();
                assertNotEquals(newRequestId, anotherRequestId,
                        "Different requests should resolve to different contextual instances");
            } finally {
                contextManager.deactivateRequest();
            }
        } finally {
            assertDoesNotThrow(contextManager::deactivateRequest);
        }
    }

    @Test
    @DisplayName("5.4.1 - Programmatic lookup of normal-scoped bean returns a client proxy")
    void shouldReturnClientProxyFromProgrammaticLookupForNormalScope() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), RequestScopedService.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        RequestScopedService reference = syringe.inject(RequestScopedService.class);
        assertNotNull(reference);
        assertNotEquals(RequestScopedService.class, reference.getClass());

        Throwable inactiveCall = assertThrows(Throwable.class, reference::currentRequestInstanceId);
        assertContextInactiveOrIllegalState(inactiveCall);
    }

    private static void assertContextInactiveOrIllegalState(Throwable throwable) {
        assertTrue(
                containsCause(throwable, ContextNotActiveException.class) ||
                        containsCause(throwable, IllegalStateException.class),
                "Expected ContextNotActiveException or IllegalStateException, but got: " + throwable
        );
    }

    private static boolean containsCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> void warmUpRequestScopedInstance(Syringe syringe, Class<T> beanClass) {
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(beanClass);
        Bean bean = beanManager.resolve((Set) beans);
        CreationalContext creationalContext = beanManager.createCreationalContext(bean);
        beanManager.getContext(RequestScoped.class).get((Contextual) bean, creationalContext);
    }
}
