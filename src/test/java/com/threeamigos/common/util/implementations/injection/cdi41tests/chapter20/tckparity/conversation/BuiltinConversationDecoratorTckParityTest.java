package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter20.tckparity.conversation;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.scopes.ConversationImpl;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Conversation;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.InjectionTargetFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("20.4 - TCK parity for built-in Conversation decorator")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class BuiltinConversationDecoratorTckParityTest {

    @Test
    @DisplayName("20.4 / BuiltinConversationDecoratorTest - Conversation decorator is resolved")
    void shouldMatchBuiltinConversationDecoratorTestResolution() {
        Syringe syringe = newSyringe();
        BeanManager beanManager = syringe.getBeanManager();

        List<jakarta.enterprise.inject.spi.Decorator<?>> decorators = beanManager.resolveDecorators(
                Collections.<Type>singleton(Conversation.class),
                Default.Literal.INSTANCE
        );
        assertEquals(1, decorators.size());
        assertEquals(ConversationDecoratorBean.class, decorators.get(0).getBeanClass());
    }

    @Test
    @DisplayName("20.4 / BuiltinConversationDecoratorTest - Conversation decorator is invoked on begin(String)")
    void shouldMatchBuiltinConversationDecoratorTestInvocation() {
        Syringe syringe = newSyringe();
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateRequest();
        beanManager.getContextManager().activateSession("conversation-session");
        beanManager.getContextManager().beginConversation("conversation-decorator");
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Bean<Conversation> conversationBean = (Bean<Conversation>) beanManager.resolve((Set) beanManager.getBeans(Conversation.class));
            List<jakarta.enterprise.inject.spi.Decorator<?>> beanDecorators = beanManager.resolveDecorators(
                    conversationBean.getTypes(),
                    conversationBean.getQualifiers().toArray(new java.lang.annotation.Annotation[0])
            );
            assertEquals(1, beanDecorators.size());
            ConversationClient client = getClient(beanManager);
            String cid = "foo";
            client.beginConversation(cid);
            assertFalse(client.isConversationTransient());
            assertEquals(cid, client.getDecoratedConversationId());
        } finally {
            beanManager.getContextManager().endConversation("conversation-decorator");
            beanManager.getContextManager().deactivateSession();
            beanManager.getContextManager().deactivateRequest();
            ConversationImpl.clearCurrentConversation();
        }
    }

    @Test
    @DisplayName("20.4 / BuiltinConversationDecoratorTest - Arquillian-style member injection of Conversation succeeds")
    void shouldSupportArquillianStyleMemberInjection() {
        Syringe syringe = newSyringe();
        BeanManager beanManager = syringe.getBeanManager();

        InjectionTargetFactory<ArquillianLikeTestCase> factory =
                beanManager.getInjectionTargetFactory(beanManager.createAnnotatedType(ArquillianLikeTestCase.class));
        InjectionTarget<ArquillianLikeTestCase> injectionTarget = factory.createInjectionTarget(null);
        ArquillianLikeTestCase testCase = new ArquillianLikeTestCase();

        assertDoesNotThrow(() -> injectionTarget.inject(testCase, beanManager.createCreationalContext(null)));
        assertNotNull(testCase.conversation);
        assertNotNull(testCase.conversationObserver);
    }

    private Syringe newSyringe() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(),
                ConversationClient.class, ConversationDecoratorBean.class, ConversationObserver.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        addBeansXmlDecorator(syringe, ConversationDecoratorBean.class.getName());
        syringe.setup();
        return syringe;
    }

    private void addBeansXmlDecorator(Syringe syringe, String decoratorClassName) {
        String xml = "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd\" " +
                "version=\"3.0\">" +
                "<decorators><class>" + decoratorClassName + "</class></decorators>" +
                "</beans>";
        BeansXml beansXml = new BeansXmlParser()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        syringe.getKnowledgeBase().addBeansXml(beansXml);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ConversationClient getClient(BeanManagerImpl beanManager) {
        Set<Bean<?>> beans = beanManager.getBeans(ConversationClient.class);
        Bean<ConversationClient> bean = (Bean<ConversationClient>) beanManager.resolve((Set) beans);
        return beanManager.getContext(RequestScoped.class).get(bean, beanManager.createCreationalContext(bean));
    }

    @RequestScoped
    public static class ConversationObserver {
        private String decoratedConversationId;

        public String getDecoratedConversationId() {
            return decoratedConversationId;
        }

        public void setDecoratedConversationId(String decoratedConversationId) {
            this.decoratedConversationId = decoratedConversationId;
        }
    }

    @Decorator
    public static abstract class ConversationDecoratorBean implements Conversation, Serializable {
        private static final long serialVersionUID = 1L;

        @Inject
        @Delegate
        Conversation conversation;

        @Inject
        ConversationObserver observer;

        @Override
        public void begin(String id) {
            conversation.begin(id);
            observer.setDecoratedConversationId(conversation.getId());
        }
    }

    @RequestScoped
    public static class ConversationClient {
        @Inject
        Conversation conversation;

        @Inject
        ConversationObserver observer;

        void beginConversation(String id) {
            conversation.begin(id);
        }

        boolean isConversationTransient() {
            return conversation.isTransient();
        }

        String getDecoratedConversationId() {
            return observer.getDecoratedConversationId();
        }

    }

    @Vetoed
    private static class ArquillianLikeTestCase {
        @Inject
        Conversation conversation;

        @Inject
        ConversationObserver conversationObserver;
    }
}
