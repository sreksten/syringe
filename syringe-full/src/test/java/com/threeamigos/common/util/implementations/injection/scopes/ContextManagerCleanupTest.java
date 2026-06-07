package com.threeamigos.common.util.implementations.injection.scopes;

import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ConversationScoped;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("ContextManager cleanup")
class ContextManagerCleanupTest {

    @Test
    @DisplayName("destroyAll destroys all registered contexts for the same custom scope")
    void destroyAllShouldDestroyEveryRegisteredCustomScopeContext() {
        ContextManager contextManager = new ContextManager(new InMemoryMessageHandler());
        CountingScopeContext first = new CountingScopeContext();
        CountingScopeContext second = new CountingScopeContext();

        contextManager.registerContext(TestScoped.class, first);
        contextManager.registerContext(TestScoped.class, second);
        contextManager.destroyAll();

        assertEquals(1, first.getDestroyCalls());
        assertEquals(1, second.getDestroyCalls());
    }

    @Test
    @DisplayName("replacing ConversationScoped context destroys previous built-in context")
    void replacingBuiltInConversationScopedContextShouldDestroyPreviousContext() {
        ContextManager contextManager = new ContextManager(new InMemoryMessageHandler());
        CountingScopeContext previousBuiltIn = new CountingScopeContext();
        CountingScopeContext replacement = new CountingScopeContext();

        contextManager.registerContext(ConversationScoped.class, previousBuiltIn);
        contextManager.registerContext(ConversationScoped.class, replacement);

        assertEquals(1, previousBuiltIn.getDestroyCalls());

        contextManager.destroyAll();
        assertEquals(1, replacement.getDestroyCalls());
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({TYPE, METHOD, FIELD})
    private @interface TestScoped {
    }

    private static final class CountingScopeContext implements ScopeContext {
        private int destroyCalls;
        private boolean active = true;

        @Override
        public <T> T get(Bean<T> bean, CreationalContext<T> creationalContext) {
            return null;
        }

        @Override
        public <T> T getIfExists(Bean<T> bean) {
            return null;
        }

        @Override
        public void destroy() {
            destroyCalls++;
            active = false;
        }

        @Override
        public void destroy(Contextual<?> contextual) {
            // no-op for test context
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public boolean isPassivationCapable() {
            return false;
        }

        int getDestroyCalls() {
            return destroyCalls;
        }
    }
}
