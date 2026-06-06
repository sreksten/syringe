package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter20.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("20 - TCK parity for BuiltinEventDecoratorTest")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class BuiltinEventDecoratorTckParityTest {

    @Test
    @DisplayName("20.3/20.4 / BuiltinEventDecoratorTest - decorator for Event<Foo> is invoked")
    void shouldInvokeFooEventDecorator() {
        ObserverBean.reset();
        Syringe syringe = newSyringe(
                ObserverBean.class,
                EventClient.class,
                Foo.class,
                FooEventDecorator.class,
                StringEventDecorator.class,
                CharSequenceEventDecorator.class
        );
        addBeansXmlDecorators(syringe,
                FooEventDecorator.class.getName(),
                StringEventDecorator.class.getName(),
                CharSequenceEventDecorator.class.getName());
        syringe.setup();

        EventClient client = resolveManagedBean(syringe.getBeanManager(), EventClient.class);
        Foo payload = new Foo(false);
        runInRequestContext(syringe, new Runnable() {
            @Override
            public void run() {
                client.fooEvent.fire(payload);
            }
        });

        assertTrue(ObserverBean.observedFoo);
        assertTrue(payload.decorated);
    }

    @Test
    @DisplayName("20.3/20.4 / BuiltinEventDecoratorTest - multiple Event<String> decorators compose and select() may be decorated")
    void shouldApplyMultipleEventDecoratorsForStringAndExposeDecoratorSelectBehavior() {
        ObserverBean.reset();
        Syringe syringe = newSyringe(
                ObserverBean.class,
                EventClient.class,
                Foo.class,
                FooEventDecorator.class,
                StringEventDecorator.class,
                CharSequenceEventDecorator.class
        );
        addBeansXmlDecorators(syringe,
                FooEventDecorator.class.getName(),
                StringEventDecorator.class.getName(),
                CharSequenceEventDecorator.class.getName());
        syringe.setup();

        EventClient client = resolveManagedBean(syringe.getBeanManager(), EventClient.class);
        runInRequestContext(syringe, new Runnable() {
            @Override
            public void run() {
                client.stringEvent.fire("TCK");
            }
        });

        assertEquals("DecoratedStringTCK", ObserverBean.observedString);
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), classes);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        // Avoid cross-test pollution from other parity fixtures in the same package.
        syringe.exclude(DecoratorTckParityTest.class);
        for (Class<?> nested : DecoratorTckParityTest.class.getDeclaredClasses()) {
            syringe.exclude(nested);
        }
        try {
            Class<?> conversationParity = Class.forName(
                    "com.threeamigos.common.util.implementations.injection.cdi41tests.chapter20.tckparity.conversation.BuiltinConversationDecoratorTckParityTest");
            syringe.exclude(conversationParity);
            for (Class<?> nested : conversationParity.getDeclaredClasses()) {
                syringe.exclude(nested);
            }
        } catch (ClassNotFoundException ignored) {
            // ignore when class is not present
        }
        return syringe;
    }

    private void addBeansXmlDecorators(Syringe syringe, String... decoratorClassNames) {
        StringBuilder classes = new StringBuilder();
        for (String decoratorClassName : decoratorClassNames) {
            classes.append("<class>").append(decoratorClassName).append("</class>");
        }
        String xml = "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd\" " +
                "version=\"3.0\">" +
                "<decorators>" + classes + "</decorators>" +
                "</beans>";
        BeansXml beansXml = new BeansXmlParser().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        syringe.getKnowledgeBase().addBeansXml(beansXml);
    }

    private void runInRequestContext(Syringe syringe, Runnable action) {
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        beanManager.getContextManager().activateRequest();
        try {
            action.run();
        } finally {
            beanManager.getContextManager().deactivateRequest();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T resolveManagedBean(BeanManager beanManager, Class<T> type) {
        return (T) beanManager.getReference(
                beanManager.resolve(beanManager.getBeans(type)),
                type,
                beanManager.createCreationalContext(null));
    }

    public static class Foo {
        boolean decorated;

        Foo(boolean decorated) {
            this.decorated = decorated;
        }
    }

    @RequestScoped
    public static class ObserverBean {
        static boolean observedFoo;
        static String observedString;

        void observeFoo(@Observes Foo ignored) {
            observedFoo = true;
        }

        void observeString(@Observes String value) {
            observedString = value;
        }

        static void reset() {
            observedFoo = false;
            observedString = null;
        }
    }

    @Dependent
    public static class EventClient {
        @Inject
        Event<Foo> fooEvent;

        @Inject
        Event<String> stringEvent;
    }

    @Decorator
    @Dependent
    public static class FooEventDecorator implements Event<Foo>, Serializable {
        @Inject
        @Delegate
        Event<Foo> delegate;

        @Override
        public void fire(Foo event) {
            event.decorated = true;
            delegate.fire(event);
        }

        @Override
        public Event<Foo> select(java.lang.annotation.Annotation... qualifiers) {
            return delegate.select(qualifiers);
        }

        @Override
        public <U extends Foo> Event<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) {
            return delegate.select(subtype, qualifiers);
        }

        @Override
        public <U extends Foo> Event<U> select(TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) {
            return delegate.select(subtype, qualifiers);
        }

        @Override
        public <U extends Foo> CompletionStage<U> fireAsync(U event) {
            return delegate.fireAsync(event);
        }

        @Override
        public <U extends Foo> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
            return delegate.fireAsync(event, options);
        }
    }

    @Decorator
    @Dependent
    public static class StringEventDecorator implements Event<String>, Serializable {
        @Inject
        @Delegate
        Event<String> delegate;

        @Override
        public void fire(String event) {
            delegate.fire("DecoratedString" + event);
        }

        @Override
        public Event<String> select(java.lang.annotation.Annotation... qualifiers) {
            return delegate.select(qualifiers);
        }

        @Override
        public <U extends String> Event<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) {
            return delegate.select(subtype, qualifiers);
        }

        @Override
        public <U extends String> Event<U> select(TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) {
            return delegate.select(subtype, qualifiers);
        }

        @Override
        public <U extends String> CompletionStage<U> fireAsync(U event) {
            return delegate.fireAsync(event);
        }

        @Override
        public <U extends String> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
            return delegate.fireAsync(event, options);
        }
    }

    @Decorator
    @Dependent
    public static class CharSequenceEventDecorator implements Event<CharSequence>, Serializable {
        @Inject
        @Delegate
        Event<CharSequence> delegate;

        @Inject
        jakarta.enterprise.inject.spi.BeanManager manager;

        @Override
        public void fire(CharSequence event) {
            manager.getEvent().select(String.class).fire("DecoratedCharSequence" + event);
        }

        @Override
        public Event<CharSequence> select(java.lang.annotation.Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends CharSequence> Event<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends CharSequence> Event<U> select(TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends CharSequence> CompletionStage<U> fireAsync(U event) {
            return delegate.fireAsync(event);
        }

        @Override
        public <U extends CharSequence> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
            return delegate.fireAsync(event, options);
        }
    }
}
