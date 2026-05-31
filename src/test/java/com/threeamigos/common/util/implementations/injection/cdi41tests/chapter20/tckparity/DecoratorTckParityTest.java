package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter20.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.ByteArrayInputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Chapter 20 - TCK parity for decorators")
@Execution(ExecutionMode.SAME_THREAD)
public class DecoratorTckParityTest {

    @Test
    @DisplayName("20.3 / CustomDecoratorMatchingBeanWithFinalClassTest - deployment fails when custom decorator matches final bean class")
    void shouldFailForCustomDecoratorMatchingFinalBeanClass() {
        Syringe syringe = newSyringe(
                Vehicle.class,
                FinalBus.class,
                VehicleDecorator.class,
                CustomDecoratorFinalBeanExtension.class
        );
        addBeansXmlDecorators(syringe, VehicleDecorator.class.getName());
        syringe.addExtension(CustomDecoratorFinalBeanExtension.class.getName());

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.3 / FinalBeanClassTest - deployment fails when decorator applies to final managed bean class")
    void shouldFailForFinalManagedBeanClassWithDecorator() {
        Syringe syringe = newSyringe(
                Logger.class,
                FinalMockLogger.class,
                TimestampLoggerDecorator.class
        );
        addBeansXmlDecorators(syringe, TimestampLoggerDecorator.class.getName());

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.1 / BuiltinInstanceDecoratorTest - built-in Instance decorator is resolved")
    void shouldResolveBuiltinInstanceDecorator() {
        Syringe syringe = newSyringe(Mule.class, MuleInstanceDecorator.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Type instanceType = new TypeLiteral<Instance<Mule>>() {
        }.getType();
        Set<Type> types = Collections.singleton(instanceType);
        java.util.List<jakarta.enterprise.inject.spi.Decorator<?>> decorators =
                beanManager.resolveDecorators(types, Default.Literal.INSTANCE);

        assertEquals(1, decorators.size());
        assertEquals(MuleInstanceDecorator.class, decorators.get(0).getBeanClass());
    }

    @Test
    @DisplayName("20.4 / BuiltinInstanceDecoratorTest - built-in Instance decorator is invoked")
    void shouldInvokeBuiltinInstanceDecorator() {
        Syringe syringe = newSyringe(Mule.class, MuleInstanceDecorator.class, MuleConsumer.class);
        syringe.setup();

        MuleConsumer consumer = syringe.inject(MuleConsumer.class);
        assertTrue(consumer.instance.isAmbiguous());
        assertNotNull(consumer.instance.get());
    }

    @Test
    @DisplayName("20.1 / DecoratorDefinitionTest - decorator metadata and delegate injection point are exposed")
    void shouldExposeDecoratorDefinitionMetadata() {
        Syringe syringe = newSyringe(Logger.class, LoggerBean.class, TimestampLoggerDecorator.class);
        addBeansXmlDecorators(syringe, TimestampLoggerDecorator.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        java.util.List<jakarta.enterprise.inject.spi.Decorator<?>> decorators =
                beanManager.resolveDecorators(Collections.<Type>singleton(Logger.class), Default.Literal.INSTANCE);

        assertEquals(1, decorators.size());
        jakarta.enterprise.inject.spi.Decorator<?> decorator = decorators.get(0);
        assertTrue(decorator.getDecoratedTypes().contains(Logger.class));
        assertEquals(Logger.class, decorator.getDelegateType());
        assertEquals(1, decorator.getInjectionPoints().size());
        InjectionPoint injectionPoint = decorator.getInjectionPoints().iterator().next();
        assertTrue(injectionPoint.isDelegate());
        assertTrue(injectionPoint.getAnnotated().isAnnotationPresent(Delegate.class));
    }

    @Test
    @DisplayName("20.1 / NonDependentDecoratorTest - non-@Dependent decorator is definition error")
    void shouldRejectNonDependentDecorator() {
        Syringe syringe = newSyringe(FooService.class, FooServiceBean.class, NonDependentFooServiceDecorator.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.1 / DecoratorDefinitionTest - resolveDecorators rejects duplicate qualifier arguments")
    void shouldRejectDuplicateQualifierOnResolveDecorators() {
        Syringe syringe = newSyringe(Logger.class, LoggerBean.class, TimestampLoggerDecorator.class);
        addBeansXmlDecorators(syringe, TimestampLoggerDecorator.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Annotation qualifier = Default.Literal.INSTANCE;
        assertThrows(IllegalArgumentException.class,
                () -> beanManager.resolveDecorators(Collections.<Type>singleton(Logger.class), qualifier, qualifier));
    }

    @Test
    @DisplayName("20.1 / DecoratorDefinitionTest - resolveDecorators rejects annotations that are not qualifiers")
    void shouldRejectNonQualifierOnResolveDecorators() {
        Syringe syringe = newSyringe(Logger.class, LoggerBean.class, TimestampLoggerDecorator.class);
        addBeansXmlDecorators(syringe, TimestampLoggerDecorator.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        assertThrows(IllegalArgumentException.class,
                () -> beanManager.resolveDecorators(Collections.<Type>singleton(Logger.class), NotAQualifier.Literal.INSTANCE));
    }

    @Test
    @DisplayName("20.1 / DecoratorDefinitionTest - resolveDecorators rejects empty type set")
    void shouldRejectEmptyTypeSetOnResolveDecorators() {
        Syringe syringe = newSyringe(Logger.class, LoggerBean.class, TimestampLoggerDecorator.class);
        addBeansXmlDecorators(syringe, TimestampLoggerDecorator.class.getName());
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        assertThrows(IllegalArgumentException.class,
                () -> beanManager.resolveDecorators(Collections.<Type>emptySet(), Default.Literal.INSTANCE));
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        for (Class<?> fixture : allFixtureTypes()) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        return syringe;
    }

    private Set<Class<?>> allFixtureTypes() {
        return new HashSet<Class<?>>(Arrays.<Class<?>>asList(
                Vehicle.class,
                FinalBus.class,
                VehicleDecorator.class,
                CustomDecoratorFinalBeanExtension.class,
                CustomVehicleDecorator.class,
                Logger.class,
                FinalMockLogger.class,
                LoggerBean.class,
                TimestampLoggerDecorator.class,
                Mule.class,
                MuleInstanceDecorator.class,
                MuleConsumer.class,
                FooService.class,
                FooServiceBean.class,
                NonDependentFooServiceDecorator.class
        ));
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
        BeansXml beansXml = new BeansXmlParser().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        syringe.getKnowledgeBase().addBeansXml(beansXml);
    }

    public interface Vehicle {
        void start();
    }

    @Dependent
    public static final class FinalBus implements Vehicle {
        @Override
        public void start() {
        }
    }

    public static class VehicleDecorator implements Vehicle {
        @Inject
        @Delegate
        Vehicle delegate;

        @Override
        public void start() {
            delegate.start();
        }
    }

    public static class CustomDecoratorFinalBeanExtension implements Extension {
        public void addDecorator(@Observes AfterBeanDiscovery event, BeanManager beanManager) {
            AnnotatedType<VehicleDecorator> type = beanManager.createAnnotatedType(VehicleDecorator.class);
            AnnotatedField<? super VehicleDecorator> field = type.getFields().iterator().next();
            event.addBean(new CustomVehicleDecorator(field, beanManager));
        }

        public void veto(@Observes ProcessAnnotatedType<VehicleDecorator> event) {
            event.veto();
        }
    }

    public static class CustomVehicleDecorator implements jakarta.enterprise.inject.spi.Decorator<VehicleDecorator> {
        private final AnnotatedField<? super VehicleDecorator> field;
        private final BeanManager beanManager;

        public CustomVehicleDecorator(AnnotatedField<? super VehicleDecorator> field, BeanManager beanManager) {
            this.field = field;
            this.beanManager = beanManager;
        }

        @Override
        public Set<Type> getDecoratedTypes() {
            return Collections.<Type>singleton(Vehicle.class);
        }

        @Override
        public Set<Annotation> getDelegateQualifiers() {
            return Collections.<Annotation>singleton(Default.Literal.INSTANCE);
        }

        @Override
        public Type getDelegateType() {
            return Vehicle.class;
        }

        @Override
        public Class<?> getBeanClass() {
            return VehicleDecorator.class;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.<InjectionPoint>singleton(new DelegateInjectionPoint());
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return Collections.<Annotation>singleton(Default.Literal.INSTANCE);
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return Dependent.class;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public Set<Type> getTypes() {
            return new HashSet<Type>(Arrays.<Type>asList(VehicleDecorator.class, Object.class));
        }

        @Override
        public boolean isAlternative() {
            return false;
        }

        @Override
        public VehicleDecorator create(CreationalContext<VehicleDecorator> creationalContext) {
            VehicleDecorator decorator = new VehicleDecorator();
            decorator.delegate = (Vehicle) beanManager.getInjectableReference(
                    getInjectionPoints().iterator().next(),
                    creationalContext
            );
            return decorator;
        }

        @Override
        public void destroy(VehicleDecorator instance, CreationalContext<VehicleDecorator> creationalContext) {
            if (creationalContext != null) {
                creationalContext.release();
            }
        }

        public class DelegateInjectionPoint implements InjectionPoint {
            @Override
            public Type getType() {
                return Vehicle.class;
            }

            @Override
            public Set<Annotation> getQualifiers() {
                return Collections.<Annotation>singleton(Default.Literal.INSTANCE);
            }

            @Override
            public Bean<?> getBean() {
                return CustomVehicleDecorator.this;
            }

            @Override
            public java.lang.reflect.Member getMember() {
                return field.getJavaMember();
            }

            @Override
            public Annotated getAnnotated() {
                return field;
            }

            @Override
            public boolean isDelegate() {
                return true;
            }

            @Override
            public boolean isTransient() {
                return false;
            }
        }
    }

    public interface Logger {
        void log(String value);
    }

    @Dependent
    public static final class FinalMockLogger implements Logger {
        @Override
        public void log(String value) {
        }
    }

    @Dependent
    public static class LoggerBean implements Logger {
        @Override
        public void log(String value) {
        }
    }

    @Decorator
    @Dependent
    @Priority(1500)
    public static class TimestampLoggerDecorator implements Logger {
        @Inject
        @Delegate
        Logger logger;

        @Override
        public void log(String value) {
            logger.log(value);
        }
    }

    @Dependent
    public static class Mule {
    }

    @Decorator
    @Dependent
    @Priority(100)
    public static abstract class MuleInstanceDecorator implements Instance<Mule> {
        @Inject
        @Delegate
        Instance<Mule> delegate;

        @Override
        public boolean isAmbiguous() {
            return true;
        }
    }

    @Dependent
    public static class MuleConsumer {
        @Inject
        Instance<Mule> instance;
    }

    public interface FooService {
        void ping();
    }

    @Dependent
    public static class FooServiceBean implements FooService {
        @Override
        public void ping() {
        }
    }

    @Decorator
    @jakarta.enterprise.context.RequestScoped
    @Priority(2000)
    public static class NonDependentFooServiceDecorator implements FooService {
        @Inject
        @Delegate
        FooService delegate;

        @Override
        public void ping() {
            delegate.ping();
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NotAQualifier {
        final class Literal extends AnnotationLiteral<NotAQualifier> implements NotAQualifier {
            public static final Literal INSTANCE = new Literal();
            private static final long serialVersionUID = 1L;
        }
    }
}
