package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par65contextualinstancescontextualreferences;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum;
import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("6.5 - Contextual Instances and Contextual References Test")
@Execution(ExecutionMode.SAME_THREAD)
public class ContextualInstancesContextualReferencesTest {

    @Test
    @DisplayName("6.5.1 - Container throws ContextNotActiveException when no active context object exists for scope type")
    void shouldThrowContextNotActiveExceptionWhenNoActiveContextExists() {
        Syringe syringe = newSyringe(RequestScopedService.class, RequestScopedConsumer.class);
        RequestScopedConsumer consumer = syringe.inject(RequestScopedConsumer.class);

        assertThrows(ContextNotActiveException.class, consumer::invoke);
    }

    @Test
    @DisplayName("6.5.1 - Scope is active when exactly one active context object exists for the scope type")
    void shouldUseContextWhenExactlyOneActiveContextExists() {
        Syringe syringe = newSyringe(RequestScopedService.class, RequestScopedConsumer.class);
        BeanManager beanManager = syringe.getBeanManager();
        BeanManagerImpl beanManagerImpl = (BeanManagerImpl) beanManager;
        RequestScopedConsumer consumer = syringe.inject(RequestScopedConsumer.class);

        beanManagerImpl.getContextManager().activateRequest();
        try {
            assertEquals("request-ok", consumer.invoke());
        } finally {
            beanManagerImpl.getContextManager().deactivateRequest();
        }
    }

    @Test
    @DisplayName("6.5.1 - Container throws IllegalStateException when more than one active context object exists for scope type")
    void shouldThrowIllegalStateExceptionWhenMultipleActiveContextsExist() {
        AmbiguousContext ambiguousContext = new AmbiguousContext();
        Syringe syringe = newSyringeWithCustomContext(
                AmbiguousScopedService.class,
                AmbiguousScope.class,
                ambiguousContext
        );
        BeanManager beanManager = syringe.getBeanManager();
        Bean<AmbiguousScopedService> bean = resolveBean(beanManager, AmbiguousScopedService.class);
        CreationalContext<AmbiguousScopedService> creationalContext = beanManager.createCreationalContext(bean);
        Context context = beanManager.getContext(AmbiguousScope.class);

        assertThrows(IllegalStateException.class, () -> context.get(bean, creationalContext));
    }

    @Test
    @DisplayName("6.5.2 - Container provides built-in RequestContextController bean for programmatic request context management")
    void shouldProvideBuiltInRequestContextControllerBean() {
        Syringe syringe = newSyringe(RequestContextControllerConsumer.class);
        RequestContextControllerConsumer consumer = syringe.inject(RequestContextControllerConsumer.class);

        assertNotNull(consumer.newController());
    }

    @Test
    @DisplayName("6.5.2 - RequestContextController.activate() returns true on first activation and false when already active on same thread")
    void shouldReturnTrueThenFalseWhenActivatingRequestContextRepeatedly() {
        Syringe syringe = newSyringe(RequestContextControllerConsumer.class, RequestScopedIdentityBean.class);
        RequestContextControllerConsumer consumer = syringe.inject(RequestContextControllerConsumer.class);
        RequestContextController controller = consumer.newController();

        boolean first = controller.activate();
        boolean second = controller.activate();
        try {
            assertTrue(first);
            assertFalse(second);
            assertNotNull(consumer.currentRequestId());
        } finally {
            controller.deactivate();
        }
    }

    @Test
    @DisplayName("6.5.2 - RequestContextController.deactivate() throws ContextNotActiveException when no request context is active")
    void shouldThrowContextNotActiveExceptionWhenDeactivatingWithoutActiveRequestContext() {
        Syringe syringe = newSyringe(RequestContextControllerConsumer.class);
        RequestContextControllerConsumer consumer = syringe.inject(RequestContextControllerConsumer.class);
        RequestContextController controller = consumer.newController();

        assertThrows(ContextNotActiveException.class, controller::deactivate);
    }

    @Test
    @DisplayName("6.5.2 - Repeated activate/deactivate cycles produce new request context instances")
    void shouldCreateNewInstancesAcrossRequestContextActivationCycles() {
        Syringe syringe = newSyringe(RequestContextControllerConsumer.class, RequestScopedIdentityBean.class);
        RequestContextControllerConsumer consumer = syringe.inject(RequestContextControllerConsumer.class);
        RequestContextController controller = consumer.newController();

        controller.activate();
        String firstId;
        try {
            firstId = consumer.currentRequestId();
        } finally {
            controller.deactivate();
        }

        controller.activate();
        String secondId;
        try {
            secondId = consumer.currentRequestId();
        } finally {
            controller.deactivate();
        }

        assertNotEquals(firstId, secondId);
    }

    @Test
    @DisplayName("6.5.2 - @ActivateRequestContext interceptor activates request context around method invocation and deactivates afterwards")
    void shouldActivateAndDeactivateRequestContextViaInterceptorBinding() throws Exception {
        Method activatedMethod = ActivateRequestContextService.class
                .getDeclaredMethod("invokeInActivatedRequestContext");
        assertTrue(AnnotationPredicates.hasActivateRequestContextAnnotation(activatedMethod));

        Syringe syringe = newSyringe(
                ActivateRequestContextInvoker.class,
                ActivateRequestContextService.class,
                RequestScopedIdentityBean.class
        );
        ActivateRequestContextInvoker invoker = syringe.inject(ActivateRequestContextInvoker.class);

        String first = assertDoesNotThrow(invoker::invoke);
        String second = assertDoesNotThrow(invoker::invoke);
        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("6.5.3 - Container obtains contextual instance by calling Context.get(bean, CreationalContext) on active bean scope context")
    void shouldObtainContextualInstanceViaContextGetWithCreationalContext() {
        Syringe syringe = newSyringe(RequestScopedIdentityBean.class);
        BeanManager beanManager = syringe.getBeanManager();
        BeanManagerImpl beanManagerImpl = (BeanManagerImpl) beanManager;
        Context requestContext = beanManager.getContext(RequestScoped.class);
        Bean<RequestScopedIdentityBean> bean = resolveBean(beanManager, RequestScopedIdentityBean.class);

        beanManagerImpl.getContextManager().activateRequest();
        try {
            CreationalContext<RequestScopedIdentityBean> creationalContext =
                    beanManager.createCreationalContext(bean);
            RequestScopedIdentityBean created = requestContext.get(bean, creationalContext);
            RequestScopedIdentityBean existing = requestContext.get(bean);

            assertNotNull(created);
            assertNotNull(existing);
            assertSame(created, existing);
        } finally {
            beanManagerImpl.getContextManager().deactivateRequest();
        }
    }

    @Test
    @DisplayName("6.5.3 - Container obtains existing contextual instance by calling Context.get(bean) without CreationalContext when scope is active")
    void shouldObtainExistingContextualInstanceWithoutCreatingNewOne() {
        Syringe syringe = newSyringe(RequestScopedIdentityBean.class);
        BeanManager beanManager = syringe.getBeanManager();
        BeanManagerImpl beanManagerImpl = (BeanManagerImpl) beanManager;
        Context requestContext = beanManager.getContext(RequestScoped.class);
        Bean<RequestScopedIdentityBean> bean = resolveBean(beanManager, RequestScopedIdentityBean.class);

        beanManagerImpl.getContextManager().activateRequest();
        try {
            assertNull(requestContext.get(bean));
            RequestScopedIdentityBean created = requestContext.get(bean, beanManager.createCreationalContext(bean));
            RequestScopedIdentityBean existing = requestContext.get(bean);

            assertNotNull(created);
            assertSame(created, existing);
        } finally {
            beanManagerImpl.getContextManager().deactivateRequest();
        }
    }

    @Test
    @DisplayName("6.5.3 - If bean scope is not active, there is no already-existing contextual instance")
    void shouldTreatInactiveScopeAsNoExistingContextualInstance() {
        Syringe syringe = newSyringe(RequestScopedIdentityBean.class);
        BeanManager beanManager = syringe.getBeanManager();
        Context requestContext = beanManager.getContext(RequestScoped.class);
        Bean<RequestScopedIdentityBean> bean = resolveBean(beanManager, RequestScopedIdentityBean.class);

        RequestScopedIdentityBean existing = requestContext.isActive() ? requestContext.get(bean) : null;
        assertFalse(requestContext.isActive());
        assertNull(existing);
    }

    @Test
    @DisplayName("6.5.3 - Contextual instances of built-in beans are transformed to objects implementing application bean types")
    void shouldReturnBuiltInContextualInstancesAsExpectedBeanTypes() {
        Syringe syringe = newSyringe(RequestContextControllerConsumer.class);
        BeanManager beanManager = syringe.getBeanManager();

        Bean<BeanManager> beanManagerBean = resolveBean(beanManager, BeanManager.class);
        Bean<RequestContextController> controllerBean = resolveBean(beanManager, RequestContextController.class);

        BeanManager beanManagerReference = (BeanManager) beanManager.getReference(
                beanManagerBean,
                BeanManager.class,
                beanManager.createCreationalContext(beanManagerBean)
        );
        RequestContextController controllerReference = (RequestContextController) beanManager.getReference(
                controllerBean,
                RequestContextController.class,
                beanManager.createCreationalContext(controllerBean)
        );

        assertNotNull(beanManagerReference);
        assertNotNull(controllerReference);
        assertTrue(beanManagerReference instanceof BeanManager);
        assertTrue(controllerReference instanceof RequestContextController);
    }

    @Test
    @DisplayName("6.5.4 - Contextual reference for normal scoped bean is a client proxy implementing requested type and bean interface types")
    void shouldReturnContextualReferenceImplementingRequestedAndInterfaceTypesForNormalScope() {
        Syringe syringe = newSyringe(MultiInterfaceNormalScopedBean.class);
        BeanManager beanManager = syringe.getBeanManager();

        Bean<MultiInterfaceNormalScopedBean> bean = resolveBean(beanManager, MultiInterfaceNormalScopedBean.class);
        MultiService requestedTypeRef = (MultiService) beanManager.getReference(
                bean,
                MultiService.class,
                beanManager.createCreationalContext(bean)
        );

        assertNotNull(requestedTypeRef);
        assertTrue(requestedTypeRef instanceof MultiService);
        assertTrue(requestedTypeRef instanceof SecondaryContract);
        assertEquals("multi", requestedTypeRef.ping());
    }

    @Test
    @DisplayName("6.5.4 - Contextual reference for pseudo-scoped @Dependent bean is obtained as contextual instance associated with CreationalContext")
    void shouldObtainDependentContextualReferenceAndAllowLifecycleCleanupViaCreationalContext() {
        DestroyTrackedDependentBean.reset();
        Syringe syringe = newSyringe(DestroyTrackedDependentBean.class);
        BeanManager beanManager = syringe.getBeanManager();
        Bean<DestroyTrackedDependentBean> bean = resolveBean(beanManager, DestroyTrackedDependentBean.class);

        CreationalContext<DestroyTrackedDependentBean> firstContext = beanManager.createCreationalContext(bean);
        CreationalContext<DestroyTrackedDependentBean> secondContext = beanManager.createCreationalContext(bean);
        DestroyTrackedDependentBean first = (DestroyTrackedDependentBean) beanManager.getReference(
                bean,
                DestroyTrackedDependentBean.class,
                firstContext
        );
        DestroyTrackedDependentBean second = (DestroyTrackedDependentBean) beanManager.getReference(
                bean,
                DestroyTrackedDependentBean.class,
                secondContext
        );

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first.id(), second.id());

        bean.destroy(first, firstContext);
        bean.destroy(second, secondContext);
        assertEquals(2, DestroyTrackedDependentBean.preDestroyCount());
    }

    @Test
    @DisplayName("6.5.4 - Dependent object InjectionPoint @Default receives metadata for the injection point into which the dependent object is injected")
    void shouldProvideInjectionPointMetadataToDependentObjectDuringContextualReferenceResolution() {
        Syringe syringe = newSyringe(InjectionPointNameProducer.class, InjectionPointNameConsumer.class);
        BeanManager beanManager = syringe.getBeanManager();
        Bean<InjectionPointNameConsumer> ownerBean = resolveBean(beanManager, InjectionPointNameConsumer.class);
        InjectionPointNameConsumer owner = (InjectionPointNameConsumer) beanManager.getReference(
                ownerBean,
                InjectionPointNameConsumer.class,
                beanManager.createCreationalContext(ownerBean)
        );

        assertEquals("injectedValue", owner.injectionPointMemberName());
    }

    @Test
    @DisplayName("6.5.5 - Normal-scoped contextual reference is valid while retained but throws ContextNotActiveException when invoked with inactive context")
    void shouldThrowContextNotActiveExceptionWhenNormalScopedReferenceInvokedWithInactiveContext() {
        Syringe syringe = newSyringe(RequestScopedService.class);
        BeanManager beanManager = syringe.getBeanManager();
        Bean<RequestScopedService> bean = resolveBean(beanManager, RequestScopedService.class);

        RequestScopedService reference = (RequestScopedService) beanManager.getReference(
                bean,
                RequestScopedService.class,
                beanManager.createCreationalContext(bean)
        );
        assertNotNull(reference);
        assertThrows(ContextNotActiveException.class, reference::ping);
    }

    @Test
    @DisplayName("6.5.5 - Normal-scoped contextual reference becomes invalid after container shutdown and invocation throws IllegalStateException")
    void shouldInvalidateNormalScopedReferenceAfterShutdown() {
        Syringe syringe = newSyringe(ApplicationScopedPingBean.class);
        ApplicationScopedPingBean reference = syringe.inject(ApplicationScopedPingBean.class);
        assertEquals("app-ok", reference.ping());

        syringe.shutdown();

        assertThrows(IllegalStateException.class, reference::ping);
    }

    @Test
    @DisplayName("6.5.5 - Pseudo-scoped contextual reference remains invokable even when normal contexts are inactive")
    void shouldAllowPseudoScopedReferenceInvocationIndependentlyOfNormalContextActivity() {
        Syringe syringe = newSyringe(DependentPingBean.class);
        BeanManager beanManager = syringe.getBeanManager();
        Bean<DependentPingBean> bean = resolveBean(beanManager, DependentPingBean.class);

        DependentPingBean reference = (DependentPingBean) beanManager.getReference(
                bean,
                DependentPingBean.class,
                beanManager.createCreationalContext(bean)
        );

        assertNotNull(reference);
        assertEquals("dep-ok", reference.ping());
    }

    @Test
    @DisplayName("6.5.6 - Container identifies bean via typesafe resolution and injects contextual reference for the injection point type")
    void shouldInjectResolvedBeanAsInjectableReference() {
        Syringe syringe = newSyringe(
                FastPaymentService.class,
                SlowPaymentService.class,
                QualifiedPaymentConsumer.class
        );
        QualifiedPaymentConsumer consumer = syringe.inject(QualifiedPaymentConsumer.class);

        assertEquals("fast", consumer.process());
    }

    @Test
    @DisplayName("6.5.6 - Injectable reference optimization is allowed but resulting injected reference must remain valid")
    void shouldProvideValidInjectableReferenceRegardlessOfOptimizationStrategy() {
        Syringe syringe = newSyringe(DependentOptimizedLeaf.class, DependentOptimizedOwner.class);
        DependentOptimizedOwner owner = syringe.inject(DependentOptimizedOwner.class);

        assertEquals("opt-leaf", owner.invokeLeaf());
        assertNotNull(owner.leafImplementationClassName());
    }

    @Test
    @DisplayName("6.5.7 - Reference injected into a field remains valid until the receiving object is destroyed")
    void shouldKeepFieldInjectedReferenceValidUntilReceivingObjectIsDestroyed() {
        InjectableReferenceLifecycleRecorder.reset();
        Syringe syringe = newSyringe(FieldInjectedOwnerBean.class, LifecycleTrackedDependentReference.class);
        BeanManager beanManager = syringe.getBeanManager();
        Bean<FieldInjectedOwnerBean> ownerBean = resolveBean(beanManager, FieldInjectedOwnerBean.class);
        CreationalContext<FieldInjectedOwnerBean> ownerContext = beanManager.createCreationalContext(ownerBean);
        FieldInjectedOwnerBean owner = (FieldInjectedOwnerBean) beanManager.getReference(
                ownerBean,
                FieldInjectedOwnerBean.class,
                ownerContext
        );

        String dependentIdUsedBeforeDestroy = owner.useInjectedReference();
        assertNotNull(dependentIdUsedBeforeDestroy);
        assertTrue(InjectableReferenceLifecycleRecorder.destroyedDependentIds().isEmpty());

        ownerBean.destroy(owner, ownerContext);
        assertTrue(InjectableReferenceLifecycleRecorder.destroyedDependentIds().contains(dependentIdUsedBeforeDestroy));
    }

    @Test
    @DisplayName("6.5.7 - Reference injected into a producer method is only valid until the produced bean instance is destroyed")
    void shouldKeepProducerMethodInjectedReferenceValidUntilProducedInstanceIsDestroyed() {
        InjectableReferenceLifecycleRecorder.reset();
        Syringe syringe = newSyringe(
                ProducerMethodOwnerBean.class,
                ProducedWithInjectedReference.class,
                LifecycleTrackedDependentReference.class
        );
        BeanManager beanManager = syringe.getBeanManager();
        Bean<ProducedWithInjectedReference> producedBean = resolveBean(beanManager, ProducedWithInjectedReference.class);
        CreationalContext<ProducedWithInjectedReference> producedContext =
                beanManager.createCreationalContext(producedBean);
        ProducedWithInjectedReference produced = (ProducedWithInjectedReference) beanManager.getReference(
                producedBean,
                ProducedWithInjectedReference.class,
                producedContext
        );

        String producerMethodParameterDependentId = produced.producerParameterDependentId();
        assertNotNull(producerMethodParameterDependentId);
        assertFalse(InjectableReferenceLifecycleRecorder.producerMethodParameterDestroyed());

        producedBean.destroy(produced, producedContext);
        assertTrue(InjectableReferenceLifecycleRecorder.destroyedDependentIds().contains(producerMethodParameterDependentId));
        assertTrue(InjectableReferenceLifecycleRecorder.producerMethodParameterDestroyed());
        assertEquals(producerMethodParameterDependentId, InjectableReferenceLifecycleRecorder.lastDisposedProducedId());
    }

    @Test
    @DisplayName("6.5.7 - Reference injected into an observer method is valid only until observer invocation completes")
    void shouldDestroyObserverInjectedReferenceWhenObserverInvocationCompletes() {
        InjectableReferenceLifecycleRecorder.reset();
        Syringe syringe = newSyringe(
                ObserverOwnerBean.class,
                LifecycleTrackedDependentReference.class,
                ObserverPayloadEvent.class
        );
        BeanManager beanManager = syringe.getBeanManager();

        beanManager.getEvent().select(ObserverPayloadEvent.class).fire(new ObserverPayloadEvent("evt-6-5-7"));

        assertNotNull(InjectableReferenceLifecycleRecorder.lastObservedDependentId());
        assertTrue(InjectableReferenceLifecycleRecorder.destroyedDependentIds().contains(
                InjectableReferenceLifecycleRecorder.lastObservedDependentId()
        ));
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    private Syringe newSyringeWithCustomContext(
            Class<?> beanClass,
            Class<? extends java.lang.annotation.Annotation> scope,
            Context context
    ) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClass);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.registerCustomContext(scope, context);
        syringe.setup();
        return syringe;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> beanType) {
        Set<Bean<?>> beans = beanManager.getBeans(beanType);
        return (Bean<T>) beanManager.resolve((Set) beans);
    }

    @RequestScoped
    public static class RequestScopedService {
        public String ping() {
            return "request-ok";
        }
    }

    @ApplicationScoped
    public static class ApplicationScopedPingBean {
        public String ping() {
            return "app-ok";
        }
    }

    @Dependent
    public static class DependentPingBean {
        public String ping() {
            return "dep-ok";
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface Fast {
    }

    public interface PaymentService {
        String process();
    }

    @ApplicationScoped
    @Fast
    public static class FastPaymentService implements PaymentService {
        @Override
        public String process() {
            return "fast";
        }
    }

    @ApplicationScoped
    public static class SlowPaymentService implements PaymentService {
        @Override
        public String process() {
            return "slow";
        }
    }

    @Dependent
    public static class QualifiedPaymentConsumer {
        @Inject
        @Fast
        PaymentService paymentService;

        String process() {
            return paymentService.process();
        }
    }

    @Dependent
    public static class DependentOptimizedLeaf {
        String ping() {
            return "opt-leaf";
        }
    }

    @Dependent
    public static class DependentOptimizedOwner {
        @Inject
        DependentOptimizedLeaf leaf;

        String invokeLeaf() {
            return leaf.ping();
        }

        String leafImplementationClassName() {
            return leaf.getClass().getName();
        }
    }

    @Dependent
    public static class LifecycleTrackedDependentReference {
        private final String id = java.util.UUID.randomUUID().toString();

        String id() {
            return id;
        }

        @PreDestroy
        void preDestroy() {
            InjectableReferenceLifecycleRecorder.recordDestroyed(id);
        }
    }

    @Dependent
    public static class FieldInjectedOwnerBean {
        @Inject
        LifecycleTrackedDependentReference dependency;

        String useInjectedReference() {
            return dependency.id();
        }
    }

    @Dependent
    public static class ProducerMethodOwnerBean {
        @Produces
        ProducedWithInjectedReference produce(LifecycleTrackedDependentReference dependency) {
            InjectableReferenceLifecycleRecorder.recordProducerMethodParameterId(dependency.id());
            return new ProducedWithInjectedReference(dependency.id());
        }

        void dispose(@Disposes ProducedWithInjectedReference produced) {
            InjectableReferenceLifecycleRecorder.recordProducedDisposed(produced.producerParameterDependentId());
        }
    }

    public static class ProducedWithInjectedReference {
        private final String producerParameterDependentId;

        public ProducedWithInjectedReference(String producerParameterDependentId) {
            this.producerParameterDependentId = producerParameterDependentId;
        }

        public String producerParameterDependentId() {
            return producerParameterDependentId;
        }
    }

    public static class ObserverPayloadEvent {
        private final String payload;

        public ObserverPayloadEvent(String payload) {
            this.payload = payload;
        }

        public String payload() {
            return payload;
        }
    }

    @Dependent
    public static class ObserverOwnerBean {
        void onEvent(@Observes ObserverPayloadEvent event, LifecycleTrackedDependentReference dependency) {
            InjectableReferenceLifecycleRecorder.recordObserverParameterId(dependency.id());
        }
    }

    public static class InjectableReferenceLifecycleRecorder {
        private static final List<String> DESTROYED_DEPENDENT_IDS = new ArrayList<String>();
        private static String producerMethodParameterId;
        private static String lastObservedDependentId;
        private static String lastDisposedProducedId;

        static synchronized void reset() {
            DESTROYED_DEPENDENT_IDS.clear();
            producerMethodParameterId = null;
            lastObservedDependentId = null;
            lastDisposedProducedId = null;
        }

        static synchronized void recordDestroyed(String id) {
            DESTROYED_DEPENDENT_IDS.add(id);
        }

        static synchronized List<String> destroyedDependentIds() {
            return new ArrayList<String>(DESTROYED_DEPENDENT_IDS);
        }

        static synchronized void recordProducerMethodParameterId(String id) {
            producerMethodParameterId = id;
        }

        static synchronized void recordObserverParameterId(String id) {
            lastObservedDependentId = id;
        }

        static synchronized String lastObservedDependentId() {
            return lastObservedDependentId;
        }

        static synchronized void recordProducedDisposed(String id) {
            lastDisposedProducedId = id;
        }

        static synchronized String lastDisposedProducedId() {
            return lastDisposedProducedId;
        }

        static synchronized boolean producerMethodParameterDestroyed() {
            return producerMethodParameterId != null && DESTROYED_DEPENDENT_IDS.contains(producerMethodParameterId);
        }
    }

    public static class RequestScopedConsumer {
        @Inject
        RequestScopedService service;

        public String invoke() {
            return service.ping();
        }
    }

    @RequestScoped
    public static class RequestScopedIdentityBean {
        private final String id = java.util.UUID.randomUUID().toString();

        public String id() {
            return id;
        }
    }

    public static class RequestContextControllerConsumer {
        @Inject
        Instance<RequestContextController> controllers;

        @Inject
        RequestScopedIdentityBean requestScopedIdentityBean;

        public RequestContextController newController() {
            return controllers.get();
        }

        public String currentRequestId() {
            return requestScopedIdentityBean.id();
        }
    }

    public static class ActivateRequestContextInvoker {
        @Inject
        ActivateRequestContextService service;

        public String invoke() {
            return service.invokeInActivatedRequestContext();
        }
    }

    public static class ActivateRequestContextService {
        @Inject
        RequestScopedIdentityBean requestScopedIdentityBean;

        @ActivateRequestContext
        public String invokeInActivatedRequestContext() {
            return requestScopedIdentityBean.id();
        }
    }

    public interface MultiService {
        String ping();
    }

    public interface SecondaryContract {
        String extra();
    }

    @ApplicationScoped
    public static class MultiInterfaceNormalScopedBean implements MultiService, SecondaryContract {
        @Override
        public String ping() {
            return "multi";
        }

        @Override
        public String extra() {
            return "secondary";
        }
    }

    @Dependent
    public static class DestroyTrackedDependentBean {
        private static final AtomicInteger PRE_DESTROY_COUNT = new AtomicInteger(0);
        private final String id = java.util.UUID.randomUUID().toString();

        static void reset() {
            PRE_DESTROY_COUNT.set(0);
        }

        static int preDestroyCount() {
            return PRE_DESTROY_COUNT.get();
        }

        String id() {
            return id;
        }

        @PreDestroy
        void preDestroy() {
            PRE_DESTROY_COUNT.incrementAndGet();
        }
    }

    @Dependent
    public static class InjectionPointNameProducer {
        @jakarta.enterprise.inject.Produces
        InjectionPointName produce(InjectionPoint injectionPoint) {
            String member = injectionPoint == null ? null : injectionPoint.getMember().getName();
            return new InjectionPointName(member);
        }
    }

    public static class InjectionPointName {
        private final String memberName;

        public InjectionPointName(String memberName) {
            this.memberName = memberName;
        }

        public String memberName() {
            return memberName;
        }
    }

    @Dependent
    public static class InjectionPointNameConsumer {
        @Inject
        InjectionPointName injectedValue;

        String injectionPointMemberName() {
            return injectedValue.memberName();
        }
    }

    @NormalScope
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    public @interface AmbiguousScope {
    }

    @AmbiguousScope
    public static class AmbiguousScopedService {
        public String ping() {
            return "ambiguous-ok";
        }
    }

    /**
     * Simulates the CDI rule branch where more than one active context object
     * exists for the same scope and context resolution must fail.
     */
    public static class AmbiguousContext implements Context {

        private final Map<Contextual<?>, Object> instances = new ConcurrentHashMap<Contextual<?>, Object>();

        @Override
        public Class<? extends java.lang.annotation.Annotation> getScope() {
            return AmbiguousScope.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Contextual<T> contextual) {
            throw new IllegalStateException("More than one active context object exists for scope @" +
                    AmbiguousScope.class.getSimpleName());
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
            throw new IllegalStateException("More than one active context object exists for scope @" +
                    AmbiguousScope.class.getSimpleName());
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }

}
