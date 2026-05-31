package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter18;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.InjectionTargetFactory;
import jakarta.enterprise.inject.spi.InterceptionFactory;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("18 - Lifecycle of contextual instances in CDI")
@Execution(ExecutionMode.SAME_THREAD)
public class LifecycleOfContextualInstancesInCDIFullTest {

    @Test
    @DisplayName("18.1 - Method invocation through contextual reference is a business method invocation and passes through method interceptors")
    void shouldTreatContextualReferenceInvocationAsBusinessMethodInvocation() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                InvocationRecorder.class,
                TrackedAroundInvokeInterceptor.class,
                TrackedLifecycleInterceptor.class,
                LifecycleAwareComponent.class
        );

        LifecycleAwareComponent component = syringe.inject(LifecycleAwareComponent.class);
        InvocationRecorder.resetBusiness();
        assertEquals("business-ok", component.businessMethod());

        assertTrue(InvocationRecorder.businessMethods().contains("businessMethod"));
    }

    @Test
    @DisplayName("18.1 - Invocation through non-contextual reference created by InjectionTarget.produce() is treated as a business method invocation")
    void shouldTreatInjectionTargetProducedInstanceInvocationAsBusinessMethodInvocation() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                InvocationRecorder.class,
                TrackedAroundInvokeInterceptor.class,
                TrackedLifecycleInterceptor.class,
                LifecycleAwareComponent.class
        );
        BeanManager beanManager = syringe.getBeanManager();

        LifecycleAwareComponent produced = produceNonContextualInstance(beanManager, LifecycleAwareComponent.class);
        InvocationRecorder.resetBusiness();
        produced.businessMethod();

        assertTrue(InvocationRecorder.businessMethods().contains("businessMethod"));
    }

    @Test
    @DisplayName("18.1 - Invocation through non-contextual reference enhanced with InterceptionFactory is treated as a business method invocation")
    void shouldTreatInterceptionFactoryEnhancedInvocationAsBusinessMethodInvocation() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                InvocationRecorder.class,
                TrackedAroundInvokeInterceptor.class,
                TrackedLifecycleInterceptor.class,
                PlainBusinessComponent.class
        );
        BeanManager beanManager = syringe.getBeanManager();

        jakarta.enterprise.context.spi.CreationalContext<PlainBusinessComponent> creationalContext =
                beanManager.createCreationalContext(null);
        InterceptionFactory<PlainBusinessComponent> interceptionFactory =
                beanManager.createInterceptionFactory(creationalContext, PlainBusinessComponent.class);
        interceptionFactory.configure().add(TrackedInvocationLiteral.INSTANCE);
        PlainBusinessComponent enhanced =
                interceptionFactory.createInterceptedInstance(new PlainBusinessComponent());

        InvocationRecorder.resetBusiness();
        assertEquals("plain-business", enhanced.businessMethod());
        assertTrue(InvocationRecorder.businessMethods().contains("businessMethod"));
    }

    @Test
    @DisplayName("18.1 - Container invocation of initializer methods is not a business method invocation")
    void shouldNotTreatInitializerInvocationAsBusinessMethodInvocation() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                InvocationRecorder.class,
                TrackedAroundInvokeInterceptor.class,
                TrackedLifecycleInterceptor.class,
                LifecycleAwareComponent.class
        );

        LifecycleAwareComponent component = syringe.inject(LifecycleAwareComponent.class);
        assertNotNull(component);
        assertFalse(InvocationRecorder.businessMethods().contains("initializerMethod"));
    }

    @Test
    @DisplayName("18.1 - Container invocation of producer, disposer and observer methods are business method invocations")
    void shouldTreatProducerDisposerAndObserverInvocationsAsBusinessMethodInvocations() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                InvocationRecorder.class,
                TrackedAroundInvokeInterceptor.class,
                TrackedLifecycleInterceptor.class,
                LifecycleAwareComponent.class,
                ProducedStringConsumer.class,
                BusinessEvent.class
        );
        BeanManager beanManager = syringe.getBeanManager();

        ProducedStringConsumer consumer = syringe.inject(ProducedStringConsumer.class);
        assertEquals("produced-value", consumer.value);

        Bean<String> producedBean = resolveQualifiedBean(beanManager, String.class, TrackedProductLiteral.INSTANCE);
        jakarta.enterprise.context.spi.CreationalContext<String> context = beanManager.createCreationalContext(producedBean);
        String value = (String) beanManager.getReference(producedBean, String.class, context);
        producedBean.destroy(value, context);

        beanManager.getEvent().select(BusinessEvent.class).fire(new BusinessEvent("evt"));

        assertTrue(InvocationRecorder.businessMethods().contains("produceTrackedValue"));
        assertTrue(InvocationRecorder.businessMethods().contains("disposeTrackedValue"));
        assertTrue(InvocationRecorder.businessMethods().contains("observeTrackedEvent"));
    }

    @Test
    @DisplayName("18.1 - Lifecycle callbacks are not business method invocations but are intercepted by lifecycle interceptors")
    void shouldTreatLifecycleCallbacksAsNonBusinessAndLifecycleIntercepted() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                InvocationRecorder.class,
                TrackedAroundInvokeInterceptor.class,
                TrackedLifecycleInterceptor.class,
                LifecycleAwareComponent.class
        );

        LifecycleAwareComponent component = syringe.inject(LifecycleAwareComponent.class);
        assertNotNull(component);
        assertTrue(InvocationRecorder.lifecycleCallbacks().contains("interceptor-post-construct"));
        assertTrue(InvocationRecorder.lifecycleCallbacks().contains("bean-post-construct"));
        assertFalse(InvocationRecorder.businessMethods().contains("postConstructCallback"));
    }

    @Test
    @DisplayName("18.1 - Invocations of methods declared by java.lang.Object are not business method invocations")
    void shouldNotTreatObjectMethodInvocationsAsBusinessMethodInvocations() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                InvocationRecorder.class,
                TrackedAroundInvokeInterceptor.class,
                TrackedLifecycleInterceptor.class,
                LifecycleAwareComponent.class
        );

        LifecycleAwareComponent component = syringe.inject(LifecycleAwareComponent.class);
        InvocationRecorder.resetBusiness();
        component.hashCode();
        component.toString();

        assertTrue(InvocationRecorder.businessMethods().isEmpty());
    }

    @Test
    @DisplayName("18.1 - A business method invocation passes through decorators and method interceptors")
    void shouldPassBusinessMethodInvocationThroughDecoratorsAndMethodInterceptors() {
        InvocationRecorder.reset();
        Syringe syringe = newSyringe(
                InvocationRecorder.class,
                TrackedAroundInvokeInterceptor.class,
                TrackedLifecycleInterceptor.class,
                DecoratedBusinessService.class,
                TrackingBusinessDecorator.class
        );
        BeanManager beanManager = syringe.getBeanManager();
        Bean<DecoratedBusinessService> bean = resolveBean(beanManager, DecoratedBusinessService.class);

        DecoratedContract service = (DecoratedContract) beanManager.getReference(
                bean,
                DecoratedContract.class,
                beanManager.createCreationalContext(bean)
        );

        assertEquals("decorated:bean", service.businessMethod());
        assertTrue(InvocationRecorder.decoratorInvocations().contains("decorator-invoked"));
        assertTrue(InvocationRecorder.businessMethods().contains("businessMethod"));
    }

    @Test
    @DisplayName("18.1 - Non-business invocations are treated as plain Java calls and are not intercepted")
    void shouldNotInterceptPlainJavaInvocation() {
        InvocationRecorder.reset();
        LifecycleAwareComponent direct = new LifecycleAwareComponent();
        assertEquals("business-ok", direct.businessMethod());
        assertTrue(InvocationRecorder.businessMethods().isEmpty());
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> beanType) {
        Set<Bean<?>> beans = beanManager.getBeans(beanType);
        return (Bean<T>) beanManager.resolve((Set) beans);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> Bean<T> resolveQualifiedBean(BeanManager beanManager, Class<T> beanType, java.lang.annotation.Annotation qualifier) {
        Set<Bean<?>> beans = beanManager.getBeans(beanType, qualifier);
        return (Bean<T>) beanManager.resolve((Set) beans);
    }

    private <T> T produceNonContextualInstance(BeanManager beanManager, Class<T> beanType) {
        AnnotatedType<T> annotatedType = beanManager.createAnnotatedType(beanType);
        InjectionTargetFactory<T> factory = beanManager.getInjectionTargetFactory(annotatedType);
        InjectionTarget<T> injectionTarget = factory.createInjectionTarget(null);
        jakarta.enterprise.context.spi.CreationalContext<T> context = beanManager.createCreationalContext(null);
        T instance = injectionTarget.produce(context);
        injectionTarget.inject(instance, context);
        injectionTarget.postConstruct(instance);
        return instance;
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface TrackedInvocation {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    public @interface TrackedProduct {
    }

    public static final class TrackedProductLiteral extends AnnotationLiteral<TrackedProduct> implements TrackedProduct {
        static final TrackedProductLiteral INSTANCE = new TrackedProductLiteral();
    }

    public static final class TrackedInvocationLiteral extends AnnotationLiteral<TrackedInvocation> implements TrackedInvocation {
        static final TrackedInvocationLiteral INSTANCE = new TrackedInvocationLiteral();
    }

    @Dependent
    public static class InvocationRecorder {
        private static final List<String> BUSINESS_METHODS = new ArrayList<String>();
        private static final List<String> LIFECYCLE_CALLBACKS = new ArrayList<String>();
        private static final List<String> DECORATOR_INVOCATIONS = new ArrayList<String>();

        static synchronized void reset() {
            BUSINESS_METHODS.clear();
            LIFECYCLE_CALLBACKS.clear();
            DECORATOR_INVOCATIONS.clear();
        }

        static synchronized void resetBusiness() {
            BUSINESS_METHODS.clear();
        }

        static synchronized void recordBusiness(String methodName) {
            BUSINESS_METHODS.add(methodName);
        }

        static synchronized void recordLifecycle(String callbackName) {
            LIFECYCLE_CALLBACKS.add(callbackName);
        }

        static synchronized void recordDecorator(String marker) {
            DECORATOR_INVOCATIONS.add(marker);
        }

        static synchronized List<String> businessMethods() {
            return new ArrayList<String>(BUSINESS_METHODS);
        }

        static synchronized List<String> lifecycleCallbacks() {
            return new ArrayList<String>(LIFECYCLE_CALLBACKS);
        }

        static synchronized List<String> decoratorInvocations() {
            return new ArrayList<String>(DECORATOR_INVOCATIONS);
        }
    }

    @TrackedInvocation
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 10)
    public static class TrackedAroundInvokeInterceptor {
        @AroundInvoke
        public Object aroundInvoke(InvocationContext context) throws Exception {
            if (context.getMethod() != null) {
                InvocationRecorder.recordBusiness(context.getMethod().getName());
            }
            return context.proceed();
        }
    }

    @TrackedInvocation
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 20)
    public static class TrackedLifecycleInterceptor {
        @PostConstruct
        public Object postConstruct(InvocationContext context) throws Exception {
            InvocationRecorder.recordLifecycle("interceptor-post-construct");
            return context.proceed();
        }

        @PreDestroy
        public Object preDestroy(InvocationContext context) throws Exception {
            InvocationRecorder.recordLifecycle("interceptor-pre-destroy");
            return context.proceed();
        }
    }

    @TrackedInvocation
    @Dependent
    public static class LifecycleAwareComponent {
        @Inject
        void initializerMethod() {
            // Business-method recorder should never capture initializer invocations.
        }

        @PostConstruct
        void postConstructCallback() {
            InvocationRecorder.recordLifecycle("bean-post-construct");
        }

        public String businessMethod() {
            return "business-ok";
        }

        @Produces
        @TrackedProduct
        String produceTrackedValue() {
            return "produced-value";
        }

        void disposeTrackedValue(@Disposes @TrackedProduct String value) {
            // Recorder is updated by interceptor.
        }

        void observeTrackedEvent(@Observes BusinessEvent event) {
            // Recorder is updated by interceptor.
        }
    }

    @Dependent
    public static class ProducedStringConsumer {
        @Inject
        @TrackedProduct
        String value;
    }

    @Dependent
    public static class PlainBusinessComponent {
        public String businessMethod() {
            return "plain-business";
        }
    }

    public interface DecoratedContract {
        String businessMethod();
    }

    @TrackedInvocation
    @Dependent
    public static class DecoratedBusinessService implements DecoratedContract {
        @Override
        public String businessMethod() {
            return "bean";
        }
    }

    @Decorator
    @Priority(10)
    public static class TrackingBusinessDecorator implements DecoratedContract {
        @Inject
        @Delegate
        DecoratedContract delegate;

        @Override
        public String businessMethod() {
            InvocationRecorder.recordDecorator("decorator-invoked");
            return "decorated:" + delegate.businessMethod();
        }
    }

    public static class BusinessEvent {
        private final String value;

        public BusinessEvent(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
