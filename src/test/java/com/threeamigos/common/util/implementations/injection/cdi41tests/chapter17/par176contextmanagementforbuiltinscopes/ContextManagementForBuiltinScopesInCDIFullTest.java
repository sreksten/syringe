package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter17.par176contextmanagementforbuiltinscopes;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.scopes.ConversationImpl;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.Conversation;
import jakarta.enterprise.context.ConversationScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.Annotation;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName( "17.6 - Context management for builtin scopes in CDI")
@Execution(ExecutionMode.SAME_THREAD)
public class ContextManagementForBuiltinScopesInCDIFullTest {

    @Test
    @DisplayName("17.6.1 - The session context is provided by a built-in context object for @SessionScoped")
    void shouldProvideBuiltInSessionContextObject() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), BootstrapAnchor.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Context sessionContext = beanManager.getContext(SessionScoped.class);

        assertNotNull(sessionContext);
        assertEquals(SessionScoped.class, sessionContext.getScope());
    }

    @Test
    @DisplayName("17.6.2 - The conversation context is provided by a built-in context object for @ConversationScoped")
    void shouldProvideBuiltInConversationContextObject() {
        Syringe syringe = newSyringe();

        BeanManager beanManager = syringe.getBeanManager();
        Context conversationContext = beanManager.getContext(ConversationScoped.class);

        assertNotNull(conversationContext);
        assertEquals(ConversationScoped.class, conversationContext.getScope());
    }

    @Test
    @DisplayName("17.6.3 - The container provides built-in Conversation bean metadata (type, @RequestScoped, @Default, name)")
    void shouldProvideBuiltInConversationBeanMetadata() {
        Syringe syringe = newSyringe();
        BeanManager beanManager = syringe.getBeanManager();

        Set<Bean<?>> beans = beanManager.getBeans(Conversation.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Bean<Conversation> bean = (Bean<Conversation>) beanManager.resolve((Set) beans);

        assertNotNull(bean);
        assertEquals("jakarta.enterprise.context.conversation", bean.getName());
        assertEquals("jakarta.enterprise.context.RequestScoped", bean.getScope().getName());
        assertTrue(bean.getTypes().contains(Conversation.class));
        assertTrue(containsQualifier(bean.getQualifiers(), Default.class.getName()));
    }

    @Test
    @DisplayName("17.6.3 - begin() promotes transient conversation to long-running and generates identifier when absent")
    void shouldBeginLongRunningConversationWithGeneratedIdentifier() {
        Syringe syringe = newSyringe();
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().beginConversation("ctx-generated");
        try {
            Conversation conversation = getConversation(beanManager);
            assertTrue(conversation.isTransient());
            assertNull(conversation.getId());

            conversation.begin();

            assertFalse(conversation.isTransient());
            assertNotNull(conversation.getId());
            assertFalse(conversation.getId().trim().isEmpty());
        } finally {
            beanManager.getContextManager().endConversation("ctx-generated");
            ConversationImpl.clearCurrentConversation();
        }
    }

    @Test
    @DisplayName("17.6.3 - begin(String) uses explicit identifier and begin() on long-running conversation throws IllegalStateException")
    void shouldSupportExplicitConversationIdentifierAndRejectSecondBegin() {
        Syringe syringe = newSyringe();
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().beginConversation("ctx-explicit");
        try {
            Conversation conversation = getConversation(beanManager);
            conversation.begin("custom-conversation-id");

            assertFalse(conversation.isTransient());
            assertEquals("custom-conversation-id", conversation.getId());
            assertThrows(IllegalStateException.class, conversation::begin);
        } finally {
            beanManager.getContextManager().endConversation("ctx-explicit");
            ConversationImpl.clearCurrentConversation();
        }
    }

    @Test
    @DisplayName("17.6.3 - end() makes long-running conversation transient and throws IllegalStateException when already transient")
    void shouldEndConversationAndRejectEndOnTransientConversation() {
        Syringe syringe = newSyringe();
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().beginConversation("ctx-end");
        try {
            Conversation conversation = getConversation(beanManager);
            conversation.begin("end-id");
            assertFalse(conversation.isTransient());

            conversation.end();

            beanManager.getContextManager().beginConversation("ctx-end-followup");
            assertTrue(conversation.isTransient());
            assertNull(conversation.getId());
            assertThrows(IllegalStateException.class, conversation::end);
        } finally {
            beanManager.getContextManager().endConversation("ctx-end");
            beanManager.getContextManager().endConversation("ctx-end-followup");
            ConversationImpl.clearCurrentConversation();
        }
    }

    @Test
    @DisplayName("17.6.3 - getTimeout()/setTimeout() expose and update current conversation timeout")
    void shouldReadAndUpdateConversationTimeout() {
        Syringe syringe = newSyringe();
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().beginConversation("ctx-timeout");
        try {
            Conversation conversation = getConversation(beanManager);
            long initialTimeout = conversation.getTimeout();
            assertTrue(initialTimeout > 0);

            conversation.setTimeout(12345L);
            assertEquals(12345L, conversation.getTimeout());
        } finally {
            beanManager.getContextManager().endConversation("ctx-timeout");
            ConversationImpl.clearCurrentConversation();
        }
    }

    @Test
    @DisplayName("17.6.3 - begin(String) with duplicate long-running identifier throws IllegalArgumentException")
    void shouldRejectDuplicateExplicitConversationIdentifier() {
        Syringe syringe = newSyringe();
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();

        beanManager.getContextManager().beginConversation("ctx-dup-1");
        Conversation firstConversation = getConversation(beanManager);
        firstConversation.begin("duplicate-id");

        // Simulate a separate transient conversation while the first long-running one still exists.
        ConversationImpl.clearCurrentConversation();
        beanManager.getContextManager().beginConversation("ctx-dup-2");
        try {
            Conversation secondConversation = getConversation(beanManager);
            assertThrows(IllegalArgumentException.class, () -> secondConversation.begin("duplicate-id"));
        } finally {
            beanManager.getContextManager().endConversation("ctx-dup-1");
            beanManager.getContextManager().endConversation("ctx-dup-2");
            ConversationImpl.clearCurrentConversation();
        }
    }

    @Test
    @DisplayName("17.6.3 - Calling Conversation methods when conversation scope is inactive throws ContextNotActiveException")
    void shouldThrowContextNotActiveExceptionWhenConversationScopeInactive() {
        Syringe syringe = newSyringe();
        Conversation conversation = getConversation((BeanManagerImpl) syringe.getBeanManager());

        assertThrows(ContextNotActiveException.class, conversation::begin);
        assertThrows(ContextNotActiveException.class, () -> conversation.begin("id"));
        assertThrows(ContextNotActiveException.class, conversation::end);
        assertThrows(ContextNotActiveException.class, conversation::getId);
        assertThrows(ContextNotActiveException.class, conversation::getTimeout);
        assertThrows(ContextNotActiveException.class, () -> conversation.setTimeout(5000L));
        assertThrows(ContextNotActiveException.class, conversation::isTransient);
    }

    private Syringe newSyringe() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), BootstrapAnchor.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    private Conversation getConversation(BeanManagerImpl beanManager) {
        Set<Bean<?>> beans = beanManager.getBeans(Conversation.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Bean<Conversation> bean = (Bean<Conversation>) beanManager.resolve((Set) beans);
        return bean.create(beanManager.createCreationalContext(bean));
    }

    private boolean containsQualifier(Set<Annotation> qualifiers, String annotationClassName) {
        for (Annotation qualifier : qualifiers) {
            if (annotationClassName.equals(qualifier.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    @ApplicationScoped
    public static class BootstrapAnchor {
    }
}
