package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter15.par153specialization;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.decorator.Delegate;
import jakarta.decorator.Decorator;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Specializes;
import jakarta.enterprise.inject.Typed;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.io.Serializable;
import java.util.Set;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("15.3 - Specialization")
@Execution(ExecutionMode.SAME_THREAD)
public class SpecializationTest {

    @Test
    @DisplayName("15.3 - Selected alternative resolves ambiguous @Default injection, but qualifier-specific injection still resolves to non-alternative bean when qualifier is not shared")
    public void shouldResolveDefaultToSelectedAlternativeButKeepQualifierSpecificResolution() {
        Syringe syringe = newSyringe(
                AsynchronousService.class,
                MockAsynchronousService.class,
                ServiceConsumer.class);
        setupOrFailWithDetails(syringe);

        ServiceConsumer consumer = syringe.inject(ServiceConsumer.class);
        assertTrue(consumer.defaultService instanceof MockAsynchronousService);
        assertTrue(consumer.asynchronousService instanceof AsynchronousService);
        assertEquals("mock", consumer.defaultService.kind());
        assertEquals("async", consumer.asynchronousService.kind());
    }

    @Test
    @DisplayName("15.3 - When enabled bean specializes a second bean, observer methods of specialized bean are not called")
    public void shouldNotCallObserverMethodOnSpecializedBean() {
        SpecializationObserverRecorder.reset();

        Syringe syringe = newSyringe(
                BaseSpecializedService.class,
                SpecializingService.class,
                TriggerBean.class);
        syringe.enableAlternative(SpecializingService.class);
        setupOrFailWithDetails(syringe);

        TriggerBean trigger = syringe.inject(TriggerBean.class);
        trigger.fire();

        assertEquals(0, SpecializationObserverRecorder.baseObserverCalls);
        assertEquals(1, SpecializationObserverRecorder.specializingObserverCalls);
    }

    @Test
    @DisplayName("15.3.1 - Direct and indirect specialization: specializing bean inherits qualifiers and bean name transitively")
    public void shouldInheritQualifiersAndNameThroughTransitiveSpecialization() {
        Syringe syringe = newSyringeForTransitiveSpecialization(
                NamedAsynchronousService.class,
                MidSpecializingService.class,
                FinalSpecializingService.class,
                NamedServiceConsumer.class);
        syringe.enableAlternative(FinalSpecializingService.class);
        setupOrFailWithDetails(syringe);

        NamedServiceConsumer consumer = syringe.inject(NamedServiceConsumer.class);
        assertEquals(FinalSpecializingService.class, consumer.byQualifier.getClass());
        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> namedBeans = beanManager.getBeans("asyncService");
        Bean<?> resolved = beanManager.resolve((Set) namedBeans);
        assertEquals(FinalSpecializingService.class, resolved.getBeanClass());
    }

    @Test
    @DisplayName("15.3.1 - Direct and indirect specialization: specializing bean missing bean type of specialized bean is a definition error")
    public void shouldFailWhenSpecializingBeanMissesSpecializedBeanType() {
        Syringe syringe = newSyringeForMissingTypeInvalid(
                BaseTypedService.class,
                MissingTypeSpecializingService.class);
        syringe.enableAlternative(MissingTypeSpecializingService.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("15.3.1 - Direct and indirect specialization: if specialized bean has name, specializing bean may not declare explicit bean name")
    public void shouldFailWhenSpecializingBeanDeclaresExplicitNameWhileInheritedNameExists() {
        Syringe syringe = newSyringeForExplicitNameInvalid(
                NamedAsynchronousService.class,
                ExplicitlyNamedSpecializingService.class);
        syringe.enableAlternative(ExplicitlyNamedSpecializingService.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("15.3.1 - Direct and indirect specialization: @Specializes on interceptor is non-portable and throws NonPortableBehaviourException")
    public void shouldFailWhenInterceptorSpecializes() {
        Syringe syringe = newSyringeForInterceptorInvalid(
                InterceptorTargetBean.class,
                SpecializingInterceptor.class);
        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("15.3.1 - Direct and indirect specialization: @Specializes on decorator is non-portable and throws NonPortableBehaviourException")
    public void shouldFailWhenDecoratorSpecializes() {
        Syringe syringe = newSyringeForDecoratorInvalid(
                DecoratedServiceImpl.class,
                BaseDecorator.class,
                SpecializingDecorator.class);
        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(MissingTypeSpecializingService.class);
        syringe.exclude(ExplicitlyNamedSpecializingService.class);
        syringe.exclude(SpecializingInterceptor.class);
        syringe.exclude(SpecializingDecorator.class);
        syringe.exclude(NamedAsynchronousService.class);
        syringe.exclude(MidSpecializingService.class);
        syringe.exclude(FinalSpecializingService.class);
        syringe.exclude(NamedServiceConsumer.class);
        return syringe;
    }

    private Syringe newSyringeIncludingInvalid(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(SpecializingInterceptor.class);
        syringe.exclude(SpecializingDecorator.class);
        syringe.exclude(MissingTypeSpecializingService.class);
        syringe.exclude(ExplicitlyNamedSpecializingService.class);
        syringe.exclude(NamedAsynchronousService.class);
        syringe.exclude(MidSpecializingService.class);
        syringe.exclude(FinalSpecializingService.class);
        syringe.exclude(NamedServiceConsumer.class);
        return syringe;
    }

    private Syringe newSyringeForTransitiveSpecialization(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(SpecializingInterceptor.class);
        syringe.exclude(SpecializingDecorator.class);
        syringe.exclude(MissingTypeSpecializingService.class);
        syringe.exclude(ExplicitlyNamedSpecializingService.class);
        syringe.exclude(AsynchronousService.class);
        syringe.exclude(MockAsynchronousService.class);
        syringe.exclude(ServiceConsumer.class);
        syringe.exclude(BaseSpecializedService.class);
        syringe.exclude(SpecializingService.class);
        syringe.exclude(TriggerBean.class);
        return syringe;
    }

    private Syringe newSyringeForMissingTypeInvalid(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(SpecializingInterceptor.class);
        syringe.exclude(SpecializingDecorator.class);
        syringe.exclude(ExplicitlyNamedSpecializingService.class);
        syringe.exclude(NamedAsynchronousService.class);
        syringe.exclude(MidSpecializingService.class);
        syringe.exclude(FinalSpecializingService.class);
        syringe.exclude(NamedServiceConsumer.class);
        return syringe;
    }

    private Syringe newSyringeForExplicitNameInvalid(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(SpecializingInterceptor.class);
        syringe.exclude(SpecializingDecorator.class);
        syringe.exclude(MissingTypeSpecializingService.class);
        syringe.exclude(MidSpecializingService.class);
        syringe.exclude(FinalSpecializingService.class);
        syringe.exclude(NamedServiceConsumer.class);
        return syringe;
    }

    private Syringe newSyringeForInterceptorInvalid(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(SpecializingDecorator.class);
        syringe.exclude(MissingTypeSpecializingService.class);
        syringe.exclude(ExplicitlyNamedSpecializingService.class);
        syringe.exclude(NamedAsynchronousService.class);
        syringe.exclude(MidSpecializingService.class);
        syringe.exclude(FinalSpecializingService.class);
        syringe.exclude(NamedServiceConsumer.class);
        return syringe;
    }

    private Syringe newSyringeForDecoratorInvalid(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(SpecializingInterceptor.class);
        syringe.exclude(MissingTypeSpecializingService.class);
        syringe.exclude(ExplicitlyNamedSpecializingService.class);
        syringe.exclude(NamedAsynchronousService.class);
        syringe.exclude(MidSpecializingService.class);
        syringe.exclude(FinalSpecializingService.class);
        syringe.exclude(NamedServiceConsumer.class);
        return syringe;
    }

    public interface Service {
        String kind();
    }

    @Qualifier
    @Retention(RUNTIME)
    @Target({TYPE, FIELD, PARAMETER})
    public @interface Asynchronous {
    }

    @Dependent
    @Default
    @Asynchronous
    public static class AsynchronousService implements Service {
        @Override
        public String kind() {
            return "async";
        }
    }

    @Alternative
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 100)
    @Dependent
    public static class MockAsynchronousService extends AsynchronousService {
        @Override
        public String kind() {
            return "mock";
        }
    }

    @Dependent
    public static class ServiceConsumer {
        @Inject
        Service defaultService;

        @Inject
        @Asynchronous
        Service asynchronousService;
    }

    @Dependent
    public static class BaseSpecializedService {
        void observe(@Observes SpecializationPing ping) {
            SpecializationObserverRecorder.baseObserverCalls++;
        }
    }

    @Specializes
    @Alternative
    @Dependent
    public static class SpecializingService extends BaseSpecializedService {
        @Override
        void observe(@Observes SpecializationPing ping) {
            SpecializationObserverRecorder.specializingObserverCalls++;
        }
    }

    @Dependent
    public static class TriggerBean {
        @Inject
        jakarta.enterprise.event.Event<SpecializationPing> event;

        void fire() {
            event.fire(new SpecializationPing());
        }
    }

    public static class SpecializationPing {
    }

    @Dependent
    @Named("asyncService")
    @Asynchronous
    public static class NamedAsynchronousService implements Service {
        @Override
        public String kind() {
            return "named-async";
        }
    }

    @Specializes
    @Alternative
    @Dependent
    public static class MidSpecializingService extends NamedAsynchronousService {
        @Override
        public String kind() {
            return "mid";
        }
    }

    @Specializes
    @Alternative
    @Dependent
    public static class FinalSpecializingService extends MidSpecializingService {
        @Override
        public String kind() {
            return "final";
        }
    }

    @Dependent
    public static class NamedServiceConsumer {
        @Inject
        @Asynchronous
        Service byQualifier;
    }

    @Dependent
    public static class BaseTypedService implements Service, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public String kind() {
            return "base-typed";
        }
    }

    @Specializes
    @Alternative
    @Dependent
    @Typed(MissingTypeSpecializingService.class)
    public static class MissingTypeSpecializingService extends BaseTypedService {
        @Override
        public String kind() {
            return "missing-type";
        }
    }

    @Specializes
    @Alternative
    @Dependent
    @Named("otherName")
    public static class ExplicitlyNamedSpecializingService extends NamedAsynchronousService {
        @Override
        public String kind() {
            return "explicit-name";
        }
    }

    @InterceptorBindingMarker
    @Dependent
    public static class InterceptorTargetBean {
        public String ping() {
            return "pong";
        }
    }

    @jakarta.interceptor.InterceptorBinding
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public @interface InterceptorBindingMarker {
    }

    @Interceptor
    @Specializes
    @InterceptorBindingMarker
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 300)
    public static class SpecializingInterceptor {
        @AroundInvoke
        public Object around(InvocationContext context) throws Exception {
            return context.proceed();
        }
    }

    public interface DecoratedService {
        String ping();
    }

    @Dependent
    public static class DecoratedServiceImpl implements DecoratedService {
        @Override
        public String ping() {
            return "impl";
        }
    }

    @Decorator
    @Dependent
    public static class BaseDecorator implements DecoratedService {
        @Inject
        @Delegate
        DecoratedService delegate;

        @Override
        public String ping() {
            return delegate.ping();
        }
    }

    @Decorator
    @Specializes
    @Dependent
    public static class SpecializingDecorator extends BaseDecorator {
    }

    static class SpecializationObserverRecorder {
        static int baseObserverCalls;
        static int specializingObserverCalls;

        static void reset() {
            baseObserverCalls = 0;
            specializingObserverCalls = 0;
        }
    }

    private void setupOrFailWithDetails(Syringe syringe) {
        try {
            syringe.setup();
        } catch (RuntimeException e) {
            throw new AssertionError(
                    "Syringe setup failed."
                            + "\nDefinition errors: " + syringe.getKnowledgeBase().getDefinitionErrors()
                            + "\nInjection errors: " + syringe.getKnowledgeBase().getInjectionErrors()
                            + "\nGeneric errors: " + syringe.getKnowledgeBase().getErrors(),
                    e
            );
        }
    }
}
