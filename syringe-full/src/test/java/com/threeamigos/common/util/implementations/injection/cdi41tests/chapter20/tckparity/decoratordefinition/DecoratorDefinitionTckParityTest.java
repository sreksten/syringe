package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter20.tckparity.decoratordefinition;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("20.1 - TCK parity for DecoratorDefinitionTest")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
public class DecoratorDefinitionTckParityTest {

    @Test
    @DisplayName("DecoratorDefinitionTest - decorator is resolved and exposed as Decorator SPI")
    void shouldResolveDecoratorAsManagedDecoratorBean() {
        Syringe syringe = newSyringe(MockLogger.class, TimestampLogger.class);
        try {
            addBeansXmlDecorators(
                    syringe,
                    BazDecorator1.class,
                    BazDecorator2.class,
                    FooDecorator.class,
                    TimestampLogger.class,
                    ChargeDecorator.class
            );
            syringe.start();

            List<jakarta.enterprise.inject.spi.Decorator<?>> decorators = syringe.getBeanManager()
                    .resolveDecorators(MockLogger.TYPES);
            assertEquals(1, decorators.size());
            assertTrue(decorators.get(0) instanceof jakarta.enterprise.inject.spi.Decorator<?>);
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("DecoratorDefinitionTest - decorated types are computed correctly")
    void shouldResolveDecoratedTypes() {
        Syringe syringe = newSyringe(FooBarImpl.class, FooDecorator.class);
        try {
            addBeansXmlDecorators(syringe, FooDecorator.class);
            syringe.start();

            List<jakarta.enterprise.inject.spi.Decorator<?>> decorators = syringe.getBeanManager()
                    .resolveDecorators(FooBar.TYPES);
            assertEquals(1, decorators.size());

            Set<Type> decoratedTypes = decorators.get(0).getDecoratedTypes();
            assertEquals(4, decoratedTypes.size());
            assertTrue(decoratedTypes.contains(Foo.class));
            assertTrue(decoratedTypes.contains(Bar.class));
            assertTrue(decoratedTypes.contains(Baz.class));
            assertTrue(decoratedTypes.contains(Boo.class));
            assertFalse(decoratedTypes.contains(Serializable.class));
            assertFalse(decoratedTypes.contains(FooDecorator.class));
            assertFalse(decoratedTypes.contains(AbstractFooDecorator.class));
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("DecoratorDefinitionTest - delegate injection point metadata is exposed")
    void shouldExposeDelegateInjectionPointMetadata() {
        Syringe syringe = newSyringe(MockLogger.class, TimestampLogger.class);
        try {
            addBeansXmlDecorators(syringe, TimestampLogger.class);
            syringe.start();

            List<jakarta.enterprise.inject.spi.Decorator<?>> decorators = syringe.getBeanManager()
                    .resolveDecorators(Logger.TYPES);
            assertEquals(1, decorators.size());

            jakarta.enterprise.inject.spi.Decorator<?> decorator = decorators.get(0);
            assertEquals(1, decorator.getInjectionPoints().size());
            InjectionPoint delegateInjectionPoint = decorator.getInjectionPoints().iterator().next();
            assertEquals(Logger.class, delegateInjectionPoint.getType());
            assertTrue(delegateInjectionPoint.getAnnotated().isAnnotationPresent(Delegate.class));
            assertEquals(Logger.class, decorator.getDelegateType());
            assertEquals(1, decorator.getDelegateQualifiers().size());
            assertTrue(decorator.getDelegateQualifiers().contains(Default.Literal.INSTANCE));
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("DecoratorDefinitionTest - decorator need not implement delegate type directly")
    void shouldResolveDecoratorsWhenDecoratorDoesNotImplementDelegateType() {
        Syringe syringe = newSyringe(BaztImpl.class, BazDecorator1.class, BazDecorator2.class);
        try {
            addBeansXmlDecorators(syringe, BazDecorator1.class, BazDecorator2.class);
            syringe.start();

            List<jakarta.enterprise.inject.spi.Decorator<?>> decorators = syringe.getBeanManager()
                    .resolveDecorators(Bazt.TYPES);
            assertEquals(2, decorators.size());
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("DecoratorDefinitionTest - decorator ordering follows beans.xml")
    void shouldRespectDecoratorOrderingInBeansXml() {
        Syringe syringe = newSyringe(BaztImpl.class, BazDecorator1.class, BazDecorator2.class);
        try {
            addBeansXmlDecorators(syringe, BazDecorator1.class, BazDecorator2.class);
            syringe.start();

            List<jakarta.enterprise.inject.spi.Decorator<?>> decorators = syringe.getBeanManager()
                    .resolveDecorators(Bazt.TYPES);
            assertEquals(2, decorators.size());
            assertTrue(decorators.get(0).getTypes().contains(BazDecorator1.class));
            assertTrue(decorators.get(1).getTypes().contains(BazDecorator2.class));
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("DecoratorDefinitionTest - non-enabled decorators are not resolved")
    void shouldNotResolveDecoratorsNotEnabledInBeansXml() {
        Syringe syringe = newSyringe(FieldImpl.class, FieldDecorator.class);
        try {
            addBeansXmlDecorators(syringe, FooDecorator.class);
            syringe.start();

            List<jakarta.enterprise.inject.spi.Decorator<?>> decorators = syringe.getBeanManager()
                    .resolveDecorators(Field.TYPES);
            assertEquals(0, decorators.size());
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("DecoratorDefinitionTest - each enabled decorator appears in resolution")
    void shouldResolveEachEnabledDecoratorLikeTckSmokeCheck() {
        Syringe syringe = newSyringe(
                MockLogger.class, TimestampLogger.class,
                FooBarImpl.class, FooDecorator.class,
                BaztImpl.class, BazDecorator1.class, BazDecorator2.class,
                FieldImpl.class, FieldDecorator.class,
                BankAccount.class, ChargeDecorator.class
        );
        try {
            addBeansXmlDecorators(
                    syringe,
                    BazDecorator1.class,
                    BazDecorator2.class,
                    FooDecorator.class,
                    TimestampLogger.class,
                    ChargeDecorator.class
            );
            syringe.start();

            BeanManager beanManager = syringe.getBeanManager();
            assertFalse(beanManager.resolveDecorators(MockLogger.TYPES).isEmpty());
            assertFalse(beanManager.resolveDecorators(FooBar.TYPES).isEmpty());
            assertFalse(beanManager.resolveDecorators(Logger.TYPES).isEmpty());
            assertEquals(2, beanManager.resolveDecorators(Bazt.TYPES).size());
            assertTrue(beanManager.resolveDecorators(Field.TYPES).isEmpty());
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("DecoratorDefinitionTest - resolveDecorators rejects duplicate qualifier")
    void shouldRejectDuplicateBindingOnResolveDecorators() {
        Syringe syringe = newSyringe(FooBarImpl.class, FooDecorator.class);
        try {
            addBeansXmlDecorators(syringe, FooDecorator.class);
            syringe.start();

            Annotation binding = new Meta.Literal();
            assertThrows(
                    IllegalArgumentException.class,
                    () -> syringe.getBeanManager().resolveDecorators(FooBar.TYPES, binding, binding)
            );
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("DecoratorDefinitionTest - resolveDecorators rejects non-qualifier annotation")
    void shouldRejectNonQualifierOnResolveDecorators() {
        Syringe syringe = newSyringe(FooBarImpl.class, FooDecorator.class);
        try {
            addBeansXmlDecorators(syringe, FooDecorator.class);
            syringe.start();

            Annotation binding = new NonMeta.Literal();
            assertThrows(
                    IllegalArgumentException.class,
                    () -> syringe.getBeanManager().resolveDecorators(FooBar.TYPES, binding)
            );
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("DecoratorDefinitionTest - resolveDecorators rejects empty type set")
    void shouldRejectEmptyTypeSetOnResolveDecorators() {
        Syringe syringe = newSyringe(FooBarImpl.class, FooDecorator.class);
        try {
            addBeansXmlDecorators(syringe, FooDecorator.class);
            syringe.start();

            assertThrows(
                    IllegalArgumentException.class,
                    () -> syringe.getBeanManager().resolveDecorators(Collections.<Type>emptySet(), new NonMeta.Literal())
            );
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("DecoratorDefinitionTest - abstract decorator can omit method and delegate implementation is provided")
    void shouldProvideImplicitDelegateImplementationForAbstractDecorator() {
        ChargeDecorator.reset();
        Syringe syringe = newSyringe(BankAccount.class, ChargeDecorator.class);
        try {
            addBeansXmlDecorators(syringe, ChargeDecorator.class);
            syringe.start();

            Account account = syringe.inject(Account.class);
            account.deposit(100);
            assertEquals(0, ChargeDecorator.charged);

            account.withdraw(50);
            assertEquals(5, ChargeDecorator.charged);
            assertEquals(45, account.getBalance());
        } finally {
            syringe.shutdown();
        }
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe();
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.initialize();
        for (Class<?> clazz : classes) {
            syringe.addDiscoveredClass(clazz, BeanArchiveMode.EXPLICIT);
        }
        return syringe;
    }

    private void addBeansXmlDecorators(Syringe syringe, Class<?>... decoratorClasses) {
        StringBuilder decorators = new StringBuilder();
        for (Class<?> decoratorClass : decoratorClasses) {
            decorators.append("<class>").append(decoratorClass.getName()).append("</class>");
        }

        String xml = "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd\" " +
                "version=\"3.0\">" +
                "<decorators>" + decorators + "</decorators>" +
                "</beans>";

        BeansXml beansXml = new BeansXmlParser().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
        );
        syringe.getKnowledgeBase().addBeansXml(beansXml);
    }

    interface Foo extends Baz, Serializable {
    }

    interface Bar {
    }

    interface Baz {
    }

    interface Boo {
    }

    interface FooBar extends Foo, Bar, Boo {
        Set<Type> TYPES = new HashSet<Type>(Arrays.<Type>asList(FooBar.class));
    }

    @Dependent
    static class FooBarImpl implements FooBar {
        private static final long serialVersionUID = 1L;
    }

    static class AbstractFooDecorator implements Boo {
        private static final long serialVersionUID = 1L;
    }

    @Decorator
    @Meta
    static class FooDecorator extends AbstractFooDecorator implements Foo, Bar {
        private static final long serialVersionUID = 1L;

        @Inject
        @Delegate
        FooBar foobar;
    }

    interface Bazt extends Baz {
        Set<Type> TYPES = new HashSet<Type>(Arrays.<Type>asList(Bazt.class));
    }

    @Dependent
    static class BaztImpl implements Bazt {
    }

    @Decorator
    static class BazDecorator1 implements Baz {
        @Inject
        @Delegate
        Bazt bazt;
    }

    @Decorator
    static class BazDecorator2 implements Baz {
        @Inject
        @Delegate
        Bazt bazt;
    }

    interface Field {
        Set<Type> TYPES = new HashSet<Type>(Arrays.<Type>asList(Field.class));
    }

    @Dependent
    static class FieldImpl implements Field {
    }

    @Decorator
    static class FieldDecorator implements Field {
        @Inject
        @Delegate
        Field field;
    }

    interface Logger {
        Set<Type> TYPES = new HashSet<Type>(Arrays.<Type>asList(Logger.class));

        void log(String value);
    }

    @Dependent
    static class MockLogger implements Logger {
        @Override
        public void log(String value) {
            // no-op
        }
    }

    @Decorator
    abstract static class TimestampLogger implements Logger {
        @Inject
        @Delegate
        Logger logger;
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @jakarta.inject.Qualifier
    @interface Meta {
        class Literal extends AnnotationLiteral<Meta> implements Meta {
            private static final long serialVersionUID = 1L;
        }
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface NonMeta {
        class Literal extends AnnotationLiteral<NonMeta> implements NonMeta {
            private static final long serialVersionUID = 1L;
        }
    }

    public interface Account {
        void withdraw(int amount);

        void deposit(int amount);

        int getBalance();
    }

    @Dependent
    public static class BankAccount implements Account {
        private int balance;

        @Override
        public void withdraw(int amount) {
            balance -= amount;
        }

        @Override
        public void deposit(int amount) {
            balance += amount;
        }

        @Override
        public int getBalance() {
            return balance;
        }
    }

    @Decorator
    public abstract static class ChargeDecorator implements Account {
        private static final int WITHDRAWAL_CHARGE = 5;
        static int charged;

        @Inject
        @Delegate
        private Account account;

        @Override
        public void withdraw(int amount) {
            account.withdraw(amount + WITHDRAWAL_CHARGE);
            charged += WITHDRAWAL_CHARGE;
        }

        @Override
        public abstract void deposit(int amount);

        static void reset() {
            charged = 0;
        }
    }
}
