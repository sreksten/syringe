package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter14;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ConversationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.Serializable;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("14 - Scopes in CDI-Full Test")
@Execution(ExecutionMode.SAME_THREAD)
public class ScopesInCDIFullTest {

    @Test
    @DisplayName("14.1. Built-in scope types in CDI Full - @SessionScoped and @ConversationScoped are built-in scopes in CDI Full")
    public void shouldRecognizeSessionAndConversationAsBuiltInScopesInCdiFull() {
        Syringe syringe = newSyringe(SessionScopedBean.class, ConversationScopedBean.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        assertTrue(beanManager.isScope(SessionScoped.class));
        assertTrue(beanManager.isScope(ConversationScoped.class));
        assertTrue(beanManager.isNormalScope(SessionScoped.class));
        assertTrue(beanManager.isNormalScope(ConversationScoped.class));
    }

    @Test
    @DisplayName("14.2. Bean defining annotations in CDI Full - @SessionScoped and @ConversationScoped are bean defining annotations")
    public void shouldTreatSessionAndConversationAsBeanDefiningAnnotations() {
        Syringe syringe = newSyringe(SessionScopedBean.class, ConversationScopedBean.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> sessionBeans = beanManager.getBeans(SessionScopedBean.class);
        Set<Bean<?>> conversationBeans = beanManager.getBeans(ConversationScopedBean.class);

        assertEquals(1, sessionBeans.size());
        assertEquals(1, conversationBeans.size());
    }

    @Test
    @DisplayName("14.2.1. Built-in stereotypes in CDI Full - @Decorator is a built-in special-purpose stereotype")
    public void shouldRecognizeDecoratorAsBuiltInStereotype() {
        Syringe syringe = newSyringe(GreetingServiceImpl.class, DecoratorDependent.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        assertTrue(beanManager.isStereotype(Decorator.class));
    }

    @Test
    @DisplayName("14.1. Built-in scope types in CDI Full - if a decorator has any scope other than @Dependent, non-portable behavior results")
    public void shouldFailForDecoratorWithNonDependentScope() {
        Syringe syringe = newSyringeIncludingInvalidDecorator(GreetingServiceImpl.class, DecoratorWithApplicationScope.class);
        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(DecoratorWithApplicationScope.class);
        return syringe;
    }

    private Syringe newSyringeIncludingInvalidDecorator(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    public interface GreetingService {
        String greet();
    }

    @Dependent
    public static class GreetingServiceImpl implements GreetingService {
        @Override
        public String greet() {
            return "hello";
        }
    }

    @Decorator
    @Dependent
    @Priority(1)
    public static class DecoratorDependent implements GreetingService {
        @Inject
        @Delegate
        GreetingService delegate;

        @Override
        public String greet() {
            return delegate.greet() + "-decorated";
        }
    }

    @Decorator
    @ApplicationScoped
    public static class DecoratorWithApplicationScope implements GreetingService {
        @Inject
        @Delegate
        GreetingService delegate;

        @Override
        public String greet() {
            return delegate.greet() + "-invalid";
        }
    }

    @SessionScoped
    public static class SessionScopedBean implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @ConversationScoped
    public static class ConversationScopedBean implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
