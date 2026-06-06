package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter13.par134shutdown;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ConversationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.spi.BeanManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName( "13.4 - Shutdown tests")
@Execution(ExecutionMode.SAME_THREAD)
public class ShutdownTest {

    @Test
    @DisplayName("13.4 - When application stops, container destroys all contexts")
    public void shouldDestroyAllContextsOnShutdown() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), RootBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        ContextManager contextManager =
                ((com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl) beanManager).getContextManager();

        contextManager.activateRequest();
        syringe.inject(RequestScopedBean.class);

        syringe.shutdown();

        assertThrows(IllegalStateException.class, () -> contextManager.getContext(ApplicationScoped.class));
        assertThrows(IllegalStateException.class, () -> contextManager.getContext(RequestScoped.class));
        assertThrows(IllegalStateException.class, () -> contextManager.getContext(SessionScoped.class));
        assertThrows(IllegalStateException.class, () -> contextManager.getContext(ConversationScoped.class));
        assertThrows(IllegalStateException.class, () -> contextManager.getContext(Dependent.class));
    }

    @ApplicationScoped
    public static class RootBean {
    }

    @RequestScoped
    public static class RequestScopedBean {
    }
}
