package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter20.par201decoratorbeans;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.annotation.PreDestroy;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("20.1 - Decorator beans test")
@Execution(ExecutionMode.SAME_THREAD)
public class DecoratorBeansTest {

    @Test
    @DisplayName("20.1 - Decorator intercepts business method invocations for decorated managed bean types")
    void shouldDecorateManagedBeanBusinessMethodInvocations() {
        DecoratorRecorder.reset();
        Syringe syringe = newSyringe(DecoratedServiceImpl.class, TrackingDecorator.class);
        syringe.setup();

        DecoratedService service = syringe.inject(DecoratedService.class);
        assertTrue(service.ping().startsWith("decorated:service:"));
        assertEquals(Arrays.asList("decorator-before", "service-business", "decorator-after"), DecoratorRecorder.events());
    }

    @Test
    @DisplayName("20.1 - Decorators are not automatically applied to producer method return values")
    void shouldNotAutomaticallyDecorateProducerMethodReturnValue() {
        DecoratorRecorder.reset();
        Syringe syringe = newSyringe(ProducedServiceMethodProducer.class, ProducedServiceConsumer.class, TrackingDecorator.class);
        syringe.setup();

        ProducedServiceConsumer consumer = syringe.inject(ProducedServiceConsumer.class);
        assertEquals("produced-method", consumer.callProducedService());
        assertEquals(Arrays.asList("produced-method-business"), DecoratorRecorder.events());
    }

    @Test
    @DisplayName("20.1 - Decorators are not automatically applied to producer field values")
    void shouldNotAutomaticallyDecorateProducerFieldValue() {
        DecoratorRecorder.reset();
        Syringe syringe = newSyringe(ProducedServiceFieldProducer.class, ProducedServiceConsumer.class, TrackingDecorator.class);
        syringe.setup();

        ProducedServiceConsumer consumer = syringe.inject(ProducedServiceConsumer.class);
        assertEquals("produced-field", consumer.callProducedService());
        assertEquals(Arrays.asList("produced-field-business"), DecoratorRecorder.events());
    }

    @Test
    @DisplayName("20.1 - Decorator instance is a dependent object of the decorated object")
    void shouldCreateDistinctDecoratorInstancesForDistinctDecoratedInstances() {
        DecoratorRecorder.reset();
        Syringe syringe = newSyringe(DecoratedServiceImpl.class, TrackingDecorator.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        DecoratedService first = (DecoratedService) beanManager.getReference(
                beanManager.resolve(beanManager.getBeans(DecoratedService.class)),
                DecoratedService.class,
                beanManager.createCreationalContext(null));
        DecoratedService second = (DecoratedService) beanManager.getReference(
                beanManager.resolve(beanManager.getBeans(DecoratedService.class)),
                DecoratedService.class,
                beanManager.createCreationalContext(null));

        String firstResult = first.ping();
        String secondResult = second.ping();

        String firstId = firstResult.substring(firstResult.lastIndexOf(':') + 1);
        String secondId = secondResult.substring(secondResult.lastIndexOf(':') + 1);

        assertNotEquals(firstId, secondId);
    }

    @Test
    @DisplayName("20.1 - Decorator decorated types include only interface bean types, excluding Serializable and class hierarchy")
    void shouldExposeOnlyInterfaceDecoratedTypesExcludingSerializableAndClassHierarchy() {
        Syringe syringe = newSyringe(MultiTypeDecorator.class, DecoratedServiceImpl.class);
        syringe.setup();

        DecoratorInfo info = syringe.getKnowledgeBase().getDecoratorInfos().stream()
                .filter(d -> d.getDecoratorClass().equals(MultiTypeDecorator.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Decorator info not found"));

        Set<String> typeNames = new HashSet<String>();
        for (Type type : info.getDecoratedTypes()) {
            typeNames.add(type.getTypeName());
        }

        assertTrue(typeNames.contains(DecoratedService.class.getName()));
        assertFalse(typeNames.contains(Serializable.class.getName()));
        assertFalse(typeNames.contains(DecoratorBase.class.getName()));
        assertFalse(typeNames.contains(MultiTypeDecorator.class.getName()));
    }

    @Test
    @DisplayName("20.1 - Decorator with empty decorated types is a definition error")
    void shouldRejectDecoratorWithEmptyDecoratedTypes() {
        Syringe syringe = newSyringe(SerializableOnlyDecorator.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.1 - Decorator declaring scope other than @Dependent is a definition error")
    void shouldRejectDecoratorWithNonDependentScope() {
        Syringe syringe = newSyringe(NonDependentDecorator.class, DecoratedServiceImpl.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.1 - Built-in beans other than BeanManager may be associated with decorators")
    void shouldAllowDecoratingBuiltInBeanOtherThanBeanManager() {
        DecoratorRecorder.reset();
        Syringe syringe = newSyringe(RequestContextControllerDecorator.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        jakarta.enterprise.inject.spi.Bean<?> bean = beanManager.resolve(beanManager.getBeans(RequestContextController.class));
        RequestContextController controller = (RequestContextController) beanManager.getReference(
                bean,
                RequestContextController.class,
                beanManager.createCreationalContext(bean)
        );
        boolean activated = controller.activate();
        if (activated) {
            controller.deactivate();
        }
        assertTrue(DecoratorRecorder.events().contains("request-context-decorator-invoked"));
    }

    @Test
    @DisplayName("20.1.1 - A decorator is declared using the @jakarta.decorator.Decorator stereotype")
    void shouldDeclareDecoratorUsingDecoratorStereotype() {
        Syringe syringe = newSyringe(DecoratedServiceImpl.class, TrackingDecorator.class);
        syringe.setup();

        boolean declared = syringe.getKnowledgeBase().getDecoratorInfos().stream()
                .anyMatch(info -> info.getDecoratorClass().equals(TrackingDecorator.class));

        assertTrue(declared);
    }

    @Test
    @DisplayName("20.1.1 - Decorator class may be abstract")
    void shouldAllowAbstractDecoratorClass() {
        Syringe syringe = newSyringe(DecoratedServiceImpl.class, AbstractPassThroughDecorator.class);
        syringe.setup();
        boolean declared = syringe.getKnowledgeBase().getDecoratorInfos().stream()
                .anyMatch(info -> info.getDecoratorClass().equals(AbstractPassThroughDecorator.class));
        assertTrue(declared);
    }

    @Test
    @DisplayName("20.1.2 - Delegate injection point may be an injected field")
    void shouldSupportFieldDelegateInjectionPoint() {
        DecoratorRecorder.reset();
        Syringe syringe = newSyringe(DecoratedServiceImpl.class, TrackingDecorator.class);
        syringe.setup();

        syringe.inject(DecoratedService.class).ping();
        assertTrue(DecoratorRecorder.events().contains("decorator-before"));
    }

    @Test
    @DisplayName("20.1.2 - Delegate injection point may be a bean constructor parameter")
    void shouldSupportConstructorDelegateInjectionPoint() {
        DecoratorRecorder.reset();
        Syringe syringe = newSyringe(DecoratedServiceImpl.class, ConstructorDelegateDecorator.class);
        syringe.setup();

        assertEquals("ctor:service", syringe.inject(DecoratedService.class).ping());
    }

    @Test
    @DisplayName("20.1.2 - Delegate injection point may be an initializer method parameter")
    void shouldSupportInitializerMethodDelegateInjectionPoint() {
        DecoratorRecorder.reset();
        Syringe syringe = newSyringe(DecoratedServiceImpl.class, InitializerDelegateDecorator.class);
        syringe.setup();

        assertEquals("init:service", syringe.inject(DecoratedService.class).ping());
    }

    @Test
    @DisplayName("20.1.2 - Decorator with no delegate injection point is a definition error")
    void shouldRejectDecoratorWithoutDelegateInjectionPoint() {
        Syringe syringe = newSyringe(NoDelegateDecorator.class, DecoratedServiceImpl.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.1.2 - Decorator with more than one delegate injection point is a definition error")
    void shouldRejectDecoratorWithMultipleDelegateInjectionPoints() {
        Syringe syringe = newSyringe(MultipleDelegatesDecorator.class, DecoratedServiceImpl.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.1.2 - Bean class that is not a decorator may not declare @Delegate injection points")
    void shouldRejectDelegateInjectionPointOnNonDecoratorBean() {
        Syringe syringe = newSyringe(NonDecoratorWithDelegateField.class, DecoratedServiceImpl.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.1.2 - @Delegate must be on injected field/initializer parameter/bean constructor parameter")
    void shouldRejectDelegateOnNonInjectedMember() {
        Syringe syringe = newSyringe(InvalidDelegateLocationDecorator.class, DecoratedServiceImpl.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.1.3 - Delegate type must implement or extend every decorated type with same type parameters")
    void shouldRejectDecoratorWithIncompatibleDelegateTypeParameters() {
        Syringe syringe = newSyringe(StringGenericServiceBean.class, WrongParameterizedDelegateDecorator.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.1.3 - Decorator is not required to implement the delegate type")
    void shouldAllowDecoratorThatDoesNotImplementDelegateType() {
        Syringe syringe = newSyringe(AdvancedLoggerBean.class, BaseLoggerDecorator.class);
        syringe.setup();

        BaseLogger logger = syringe.inject(BaseLogger.class);
        assertEquals("decorated:base", logger.log());
    }

    @Test
    @DisplayName("20.1.3 - Abstract decorator may omit methods of decorated types")
    void shouldAllowAbstractDecoratorThatOmitsDecoratedTypeMethods() {
        Syringe syringe = newSyringe(MultiMethodServiceBean.class, AbstractPartialMultiMethodDecorator.class);
        syringe.setup();

        boolean declared = syringe.getKnowledgeBase().getDecoratorInfos().stream()
                .anyMatch(info -> info.getDecoratorClass().equals(AbstractPartialMultiMethodDecorator.class));
        assertTrue(declared);
    }

    @Test
    @DisplayName("20.1.3 - Abstract methods not declared by decorated types are a definition error")
    void shouldRejectDecoratorWithExtraAbstractMethodsOutsideDecoratedTypes() {
        Syringe syringe = newSyringe(DecoratedServiceImpl.class, InvalidAbstractMethodDecorator.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("20.1.3 - Decorator intercepts decorated-type methods implemented by decorator bean class")
    void shouldInterceptDecoratedMethodsImplementedByDecoratorClass() {
        DecoratorRecorder.reset();
        Syringe syringe = newSyringe(DualMethodServiceBean.class, DualMethodDecorator.class);
        syringe.setup();

        DualMethodService service = syringe.inject(DualMethodService.class);
        assertEquals("decorated-left", service.left());
        assertEquals("decorated-right", service.right());
        assertTrue(DecoratorRecorder.events().contains("dual-decorator-left"));
        assertTrue(DecoratorRecorder.events().contains("dual-decorator-right"));
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

    private Collection<Class<?>> allFixtureTypes() {
        return Arrays.<Class<?>>asList(
                DecoratedService.class,
                DecoratedServiceImpl.class,
                TrackingDecorator.class,
                ProducedServiceMethodProducer.class,
                ProducedServiceFieldProducer.class,
                ProducedServiceConsumer.class,
                MultiTypeDecorator.class,
                DecoratorBase.class,
                SerializableOnlyDecorator.class,
                NonDependentDecorator.class,
                RequestContextControllerDecorator.class,
                RequestContextClient.class,
                AbstractPassThroughDecorator.class,
                ConstructorDelegateDecorator.class,
                InitializerDelegateDecorator.class,
                NoDelegateDecorator.class,
                MultipleDelegatesDecorator.class,
                NonDecoratorWithDelegateField.class,
                InvalidDelegateLocationDecorator.class,
                GenericService.class,
                StringGenericServiceBean.class,
                WrongParameterizedDelegateDecorator.class,
                BaseLogger.class,
                AdvancedLogger.class,
                AdvancedLoggerBean.class,
                BaseLoggerDecorator.class,
                MultiMethodService.class,
                MultiMethodServiceBean.class,
                AbstractPartialMultiMethodDecorator.class,
                InvalidAbstractMethodDecorator.class,
                DualMethodService.class,
                DualMethodServiceBean.class,
                DualMethodDecorator.class
        );
    }

    static class DecoratorRecorder {
        private static final List<String> EVENTS = new ArrayList<String>();

        static synchronized void reset() {
            EVENTS.clear();
        }

        static synchronized void record(String marker) {
            EVENTS.add(marker);
        }

        static synchronized List<String> events() {
            return new ArrayList<String>(EVENTS);
        }
    }

    public interface DecoratedService {
        String ping();
    }

    @Dependent
    public static class DecoratedServiceImpl implements DecoratedService {
        @Override
        public String ping() {
            DecoratorRecorder.record("service-business");
            return "service";
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 10)
    public static class TrackingDecorator implements DecoratedService {
        @Inject
        @Delegate
        DecoratedService delegate;

        private final String id = UUID.randomUUID().toString();

        @Override
        public String ping() {
            DecoratorRecorder.record("decorator-before");
            try {
                return "decorated:" + delegate.ping() + ":" + id;
            } finally {
                DecoratorRecorder.record("decorator-after");
            }
        }

        @PreDestroy
        void preDestroy() {
            // no-op lifecycle marker point for dependent decorator instances
        }
    }

    @Dependent
    public static class ProducedServiceMethodProducer {
        @Produces
        @Dependent
        DecoratedService produceService() {
            return new DecoratedService() {
                @Override
                public String ping() {
                    DecoratorRecorder.record("produced-method-business");
                    return "produced-method";
                }
            };
        }
    }

    @Dependent
    public static class ProducedServiceFieldProducer {
        @Produces
        @Dependent
        DecoratedService produced = new DecoratedService() {
            @Override
            public String ping() {
                DecoratorRecorder.record("produced-field-business");
                return "produced-field";
            }
        };
    }

    @Dependent
    public static class ProducedServiceConsumer {
        @Inject
        DecoratedService producedService;

        String callProducedService() {
            return producedService.ping();
        }
    }

    public static abstract class DecoratorBase {
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 20)
    public static class MultiTypeDecorator extends DecoratorBase implements DecoratedService, Serializable {
        @Inject
        @Delegate
        DecoratedService delegate;

        @Override
        public String ping() {
            return delegate.ping();
        }
    }

    @Decorator
    @Dependent
    public static class SerializableOnlyDecorator implements Serializable {
        @Inject
        @Delegate
        Serializable delegate;
    }

    @Decorator
    @ApplicationScoped
    public static class NonDependentDecorator implements DecoratedService {
        @Inject
        @Delegate
        DecoratedService delegate;

        @Override
        public String ping() {
            return delegate.ping();
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 30)
    public static class RequestContextControllerDecorator implements RequestContextController {
        @Inject
        @Delegate
        RequestContextController delegate;

        @Override
        public boolean activate() {
            DecoratorRecorder.record("request-context-decorator-invoked");
            return delegate.activate();
        }

        @Override
        public void deactivate() {
            delegate.deactivate();
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 40)
    public static abstract class AbstractPassThroughDecorator implements DecoratedService {
        @Inject
        @Delegate
        DecoratedService delegate;

        @Override
        public String ping() {
            return delegate.ping();
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 50)
    public static class ConstructorDelegateDecorator implements DecoratedService {
        private final DecoratedService delegate;

        @Inject
        public ConstructorDelegateDecorator(@Delegate DecoratedService delegate) {
            this.delegate = delegate;
        }

        @Override
        public String ping() {
            return "ctor:" + delegate.ping();
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 60)
    public static class InitializerDelegateDecorator implements DecoratedService {
        private DecoratedService delegate;

        @Inject
        void init(@Delegate DecoratedService delegate) {
            this.delegate = delegate;
        }

        @Override
        public String ping() {
            return "init:" + delegate.ping();
        }
    }

    @Decorator
    @Dependent
    public static class NoDelegateDecorator implements DecoratedService {
        @Override
        public String ping() {
            return "no-delegate";
        }
    }

    @Decorator
    @Dependent
    public static class MultipleDelegatesDecorator implements DecoratedService {
        @Inject
        @Delegate
        DecoratedService first;

        @Inject
        @Delegate
        DecoratedService second;

        @Override
        public String ping() {
            return first.ping() + second.ping();
        }
    }

    @Dependent
    public static class NonDecoratorWithDelegateField {
        @Inject
        @Delegate
        DecoratedService invalidDelegate;
    }

    @Decorator
    @Dependent
    public static class InvalidDelegateLocationDecorator implements DecoratedService {
        @Inject
        @Delegate
        DecoratedService validDelegate;

        @Delegate
        DecoratedService invalidLocation;

        @Override
        public String ping() {
            return validDelegate.ping();
        }
    }

    public interface GenericService<T> {
        T value();
    }

    @Dependent
    public static class StringGenericServiceBean implements GenericService<String> {
        @Override
        public String value() {
            return "generic";
        }
    }

    @Decorator
    @Dependent
    public static class WrongParameterizedDelegateDecorator implements GenericService<String> {
        @Inject
        @Delegate
        GenericService<Integer> delegate;

        @Override
        public String value() {
            return "invalid";
        }
    }

    public interface BaseLogger {
        String log();
    }

    public interface AdvancedLogger extends BaseLogger {
        String advanced();
    }

    @Dependent
    public static class AdvancedLoggerBean implements AdvancedLogger {
        @Override
        public String log() {
            return "base";
        }

        @Override
        public String advanced() {
            return "advanced";
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 70)
    public static class BaseLoggerDecorator implements BaseLogger {
        @Inject
        @Delegate
        AdvancedLogger delegate;

        @Override
        public String log() {
            return "decorated:" + delegate.log();
        }
    }

    public interface MultiMethodService {
        String first();

        String second();
    }

    @Dependent
    public static class MultiMethodServiceBean implements MultiMethodService {
        @Override
        public String first() {
            return "first";
        }

        @Override
        public String second() {
            return "second";
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 80)
    public static abstract class AbstractPartialMultiMethodDecorator implements MultiMethodService {
        @Inject
        @Delegate
        MultiMethodService delegate;

        @Override
        public String first() {
            return "decorated-" + delegate.first();
        }
    }

    @Decorator
    @Dependent
    public static abstract class InvalidAbstractMethodDecorator implements DecoratedService {
        @Inject
        @Delegate
        DecoratedService delegate;

        @Override
        public String ping() {
            return delegate.ping();
        }

        abstract String nonDecoratedAbstractMethod();
    }

    public interface DualMethodService {
        String left();

        String right();
    }

    @Dependent
    public static class DualMethodServiceBean implements DualMethodService {
        @Override
        public String left() {
            DecoratorRecorder.record("dual-bean-left");
            return "left";
        }

        @Override
        public String right() {
            DecoratorRecorder.record("dual-bean-right");
            return "right";
        }
    }

    @Decorator
    @Dependent
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 90)
    public static class DualMethodDecorator implements DualMethodService {
        @Inject
        @Delegate
        DualMethodService delegate;

        @Override
        public String left() {
            DecoratorRecorder.record("dual-decorator-left");
            return "decorated-" + delegate.left();
        }

        @Override
        public String right() {
            DecoratorRecorder.record("dual-decorator-right");
            return "decorated-" + delegate.right();
        }
    }

    @Dependent
    public static class RequestContextClient {
        @Inject
        RequestContextController requestContextController;

        void toggle() {
            boolean activated = requestContextController.activate();
            if (activated) {
                requestContextController.deactivate();
            }
        }
    }
}
