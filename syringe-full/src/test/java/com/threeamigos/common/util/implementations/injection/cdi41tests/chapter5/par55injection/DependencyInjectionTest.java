package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par551.*;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par552.*;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par553.*;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par554.*;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par555.*;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par556.*;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par557.*;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par558.*;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.InjectionTargetFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName( "5.5 - Dependency Injection Test")
public class DependencyInjectionTest {

    @Test
    @DisplayName("5.5.1 - Container uses @Inject constructor and passes injectable references")
    void shouldUseInjectConstructorForManagedBeanInstantiation() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ConstructorInjectedBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        ConstructorInjectedBean bean = syringe.inject(ConstructorInjectedBean.class);
        assertTrue(bean.isUsedInjectConstructor());
        assertTrue(!bean.isUsedNoArgConstructor());
        assertNotNull(bean.getConstructorDependency());
    }

    @Test
    @DisplayName("5.5.1 - Container uses no-arg constructor when no constructor is annotated @Inject")
    void shouldUseNoArgConstructorWhenNoInjectConstructorExists() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NoInjectConstructorBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        NoInjectConstructorBean bean = syringe.inject(NoInjectConstructorBean.class);
        assertTrue(bean.isUsedNoArgConstructor());
        assertTrue(!bean.isUsedArgConstructor());
        assertNotNull(bean.getFieldDependency());
    }

    @Test
    @DisplayName("5.5.2 - Fields are injected before initializer methods and @PostConstruct runs afterwards")
    void shouldInjectFieldsThenCallInitializersThenPostConstructForContextualInstance() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), LifecycleManagedBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        LifecycleManagedBean bean = syringe.inject(LifecycleManagedBean.class);
        assertNotNull(bean.getSubFieldDependency());
        assertTrue(bean.isBaseInitializerSawInjectedFields());
        assertTrue(bean.isSubInitializerSawInjectedFields());
        assertTrue(bean.isBasePostConstructAfterInitializers());
        assertTrue(bean.isSubPostConstructAfterAllInitializers());
        assertEquals(Arrays.asList("base-init", "sub-init", "base-post", "sub-post"), bean.getEvents());
    }

    @Test
    @DisplayName("5.5 - Container performs dependency injection for non-contextual managed bean instances")
    void shouldPerformDependencyInjectionForNonContextualManagedBeanInstance() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), LifecycleManagedBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        AnnotatedType<LifecycleManagedBean> annotatedType = beanManager.createAnnotatedType(LifecycleManagedBean.class);
        InjectionTargetFactory<LifecycleManagedBean> factory = beanManager.getInjectionTargetFactory(annotatedType);
        InjectionTarget<LifecycleManagedBean> injectionTarget = factory.createInjectionTarget(null);
        CreationalContext<LifecycleManagedBean> creationalContext = beanManager.createCreationalContext(null);

        LifecycleManagedBean instance = injectionTarget.produce(creationalContext);
        injectionTarget.inject(instance, creationalContext);
        injectionTarget.postConstruct(instance);

        assertNotNull(instance.getSubFieldDependency());
        assertTrue(instance.isBaseInitializerSawInjectedFields());
        assertTrue(instance.isSubInitializerSawInjectedFields());
        assertTrue(instance.isBasePostConstructAfterInitializers());
        assertTrue(instance.isSubPostConstructAfterAllInitializers());
        assertEquals(Arrays.asList("base-init", "sub-init", "base-post", "sub-post"), instance.getEvents());
    }

    @Test
    @DisplayName("5.5.3 - Dependent objects are destroyed after parent @PreDestroy callback completes")
    void shouldDestroyDependentObjectsAfterParentPreDestroy() {
        DependentDestructionRecorder.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ParentWithDependentChildBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(ParentWithDependentChildBean.class);
        @SuppressWarnings("unchecked")
        Bean<ParentWithDependentChildBean> bean = (Bean<ParentWithDependentChildBean>) beanManager.resolve((Set) beans);
        CreationalContext<ParentWithDependentChildBean> context = beanManager.createCreationalContext(bean);
        ParentWithDependentChildBean instance =
                (ParentWithDependentChildBean) beanManager.getReference(bean, ParentWithDependentChildBean.class, context);

        bean.destroy(instance, context);

        assertEquals(
                Arrays.asList("parent-pre", "parent-sees-child-destroyed=false", "child-pre"),
                DependentDestructionRecorder.events()
        );
    }

    @Test
    @DisplayName("5.5.4 - Static producer and disposer methods are invoked with injectable references")
    void shouldInvokeStaticProducerAndDisposerMethodsWithInjectedParameters() {
        ProducerInvocationRecorder.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), StaticProducerDisposerBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(StaticProducedPayload.class);
        @SuppressWarnings("unchecked")
        Bean<StaticProducedPayload> bean = (Bean<StaticProducedPayload>) beanManager.resolve((Set) beans);
        CreationalContext<StaticProducedPayload> context = beanManager.createCreationalContext(bean);
        StaticProducedPayload payload =
                (StaticProducedPayload) beanManager.getReference(bean, StaticProducedPayload.class, context);

        assertNotNull(payload);
        bean.destroy(payload, context);

        List<String> events = ProducerInvocationRecorder.events();
        int producerIndex = indexOfPrefix(events, "static-producer:");
        int disposerIndex = indexOfPrefix(events, "static-disposer:");
        int firstDependentAfterProducer = indexOfPrefix(events, "dependent-pre:", producerIndex + 1);
        int secondDependentAfterFirst = indexOfPrefix(events, "dependent-pre:", firstDependentAfterProducer + 1);
        int dependentAfterDisposer = indexOfPrefix(events, "dependent-pre:", disposerIndex + 1);

        assertTrue(producerIndex >= 0, "Missing static producer invocation event: " + events);
        assertTrue(disposerIndex > producerIndex, "Missing static disposer invocation event: " + events);
        assertTrue(firstDependentAfterProducer > producerIndex,
                "Expected dependent cleanup for producer/disposer dependencies: " + events);
        assertTrue(secondDependentAfterFirst > firstDependentAfterProducer,
                "Expected two dependent cleanup events for producer/disposer dependencies: " + events);
        assertTrue(dependentAfterDisposer > disposerIndex,
                "Expected dependent cleanup after static disposer invocation: " + events);
    }

    @Test
    @DisplayName("5.5.4 - Non-static producer/disposer methods are invoked on contextual declaring bean instance")
    void shouldInvokeNonStaticProducerAndDisposerOnDeclaringContextualInstance() {
        ProducerInvocationRecorder.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonStaticProducerDisposerBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(NonStaticProducedPayload.class);
        @SuppressWarnings("unchecked")
        Bean<NonStaticProducedPayload> bean = (Bean<NonStaticProducedPayload>) beanManager.resolve((Set) beans);
        CreationalContext<NonStaticProducedPayload> context = beanManager.createCreationalContext(bean);
        NonStaticProducedPayload payload =
                (NonStaticProducedPayload) beanManager.getReference(bean, NonStaticProducedPayload.class, context);

        assertNotNull(payload);
        bean.destroy(payload, context);

        List<String> events = ProducerInvocationRecorder.events();
        int producerIndex = indexOfPrefix(events, "nonstatic-producer:");
        int disposerIndex = indexOfPrefix(events, "nonstatic-disposer:");
        int firstDependentAfterProducer = indexOfPrefix(events, "dependent-pre:", producerIndex + 1);
        int secondDependentAfterFirst = indexOfPrefix(events, "dependent-pre:", firstDependentAfterProducer + 1);
        int dependentAfterDisposer = indexOfPrefix(events, "dependent-pre:", disposerIndex + 1);

        assertTrue(producerIndex >= 0, "Missing non-static producer invocation event: " + events);
        assertTrue(disposerIndex > producerIndex, "Missing non-static disposer invocation event: " + events);
        assertTrue(firstDependentAfterProducer > producerIndex,
                "Expected dependent cleanup for producer/disposer dependencies: " + events);
        assertTrue(secondDependentAfterFirst > firstDependentAfterProducer,
                "Expected two dependent cleanup events for producer/disposer dependencies: " + events);
        assertTrue(dependentAfterDisposer > disposerIndex,
                "Expected dependent cleanup after non-static disposer invocation: " + events);

        String producerInstanceId = events.get(producerIndex).split(":")[1];
        String disposerReceiverInstanceId = events.get(disposerIndex).split(":")[1];
        String payloadDeclaringBeanIdSeenByDisposer = events.get(disposerIndex).split(":")[2];

        assertEquals(payload.getDeclaringBeanId(), producerInstanceId);
        assertEquals(payload.getDeclaringBeanId(), payloadDeclaringBeanIdSeenByDisposer);
        assertFalse(producerInstanceId.isEmpty());
        assertFalse(disposerReceiverInstanceId.isEmpty());
    }

    @Test
    @DisplayName("5.5.5 - Static producer field value is accessed without declaring bean instance")
    void shouldAccessStaticProducerFieldValue() {
        StaticProducerFieldBean.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), StaticProducerFieldBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        StaticProducedFieldPayload payload = syringe.inject(StaticProducedFieldPayload.class);

        assertNotNull(payload);
        assertEquals("static-field", payload.getSource());
        assertTrue(StaticProducerFieldBean.getConstructedInstances() >= 1);
    }

    @Test
    @DisplayName("5.5.5 - Non-static producer field value is accessed from contextual declaring bean instance")
    void shouldAccessNonStaticProducerFieldValueFromDeclaringContextualInstance() {
        NonStaticProducerFieldBean.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonStaticProducerFieldBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        NonStaticProducedFieldPayload payload = syringe.inject(NonStaticProducedFieldPayload.class);

        assertNotNull(payload);
        assertEquals(1, NonStaticProducerFieldBean.getConstructedInstances());
        assertEquals(
                NonStaticProducerFieldBean.getLastConstructedInstanceId(),
                payload.getDeclaringBeanInstanceId()
        );
    }

    @Test
    @DisplayName("5.5.6 - Static observer methods are invoked with event and injected parameters")
    void shouldInvokeStaticObserverMethodWithInjectableParameters() {
        ObserverInvocationRecorder.reset();
        StaticObserverBean.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), StaticObserverBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        syringe.getBeanManager().getEvent().select(StaticObserverInvocationEvent.class).fire(new StaticObserverInvocationEvent("evt-static"));

        List<String> events = ObserverInvocationRecorder.events();
        int observerIndex = indexOfPrefix(events, "static-observer:");
        int dependentCleanupIndex = indexOfPrefix(events, "observer-dependent-pre:", observerIndex + 1);

        assertTrue(observerIndex >= 0, "Missing static observer invocation event: " + events);
        assertTrue(dependentCleanupIndex > observerIndex,
                "Expected dependent cleanup after static observer invocation: " + events);
        assertEquals(0, StaticObserverBean.getConstructedInstances(),
                "Static observer invocation should not require creating declaring bean instance");
    }

    @Test
    @DisplayName("5.5.6 - Non-static observer methods are invoked on contextual bean instances")
    void shouldInvokeNonStaticObserverMethodOnContextualInstance() {
        ObserverInvocationRecorder.reset();
        NonStaticObserverBean.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonStaticObserverBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        syringe.getBeanManager().getEvent().select(NonStaticObserverInvocationEvent.class).fire(new NonStaticObserverInvocationEvent("evt-nonstatic"));

        List<String> events = ObserverInvocationRecorder.events();
        int observerIndex = indexOfPrefix(events, "nonstatic-observer:");
        int dependentCleanupIndex = indexOfPrefix(events, "observer-dependent-pre:", observerIndex + 1);

        assertTrue(observerIndex >= 0, "Missing non-static observer invocation event: " + events);
        assertTrue(dependentCleanupIndex > observerIndex,
                "Expected dependent cleanup after non-static observer invocation: " + events);

        String observerInstanceId = events.get(observerIndex).split(":")[1];
        assertFalse(observerInstanceId.isEmpty());
        assertEquals(1, NonStaticObserverBean.getConstructedInstances());
    }

    @Test
    @DisplayName("5.5.6 - Conditional observer resolves only existing contextual instance when scope is active")
    void shouldInvokeConditionalObserverOnlyWhenContextualInstanceAlreadyExists() {
        ObserverInvocationRecorder.reset();
        ConditionalRequestScopedObserverBean.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ConditionalRequestScopedObserverBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        ContextManager contextManager = beanManager.getContextManager();

        syringe.getBeanManager().getEvent().select(ConditionalObserverInvocationEvent.class).fire(new ConditionalObserverInvocationEvent("no-request"));
        assertEquals(0, ConditionalRequestScopedObserverBean.getObservedEvents());
        assertEquals(0, ConditionalRequestScopedObserverBean.getConstructedInstances());

        contextManager.activateRequest();
        try {
            syringe.getBeanManager().getEvent().select(ConditionalObserverInvocationEvent.class).fire(new ConditionalObserverInvocationEvent("active-no-instance"));
            assertEquals(0, ConditionalRequestScopedObserverBean.getObservedEvents());
            assertEquals(0, ConditionalRequestScopedObserverBean.getConstructedInstances());

            warmUpRequestScopedInstance(beanManager, ConditionalRequestScopedObserverBean.class);
            syringe.getBeanManager().getEvent().select(ConditionalObserverInvocationEvent.class).fire(new ConditionalObserverInvocationEvent("active-existing"));

            assertEquals(1, ConditionalRequestScopedObserverBean.getObservedEvents());
            assertTrue(ConditionalRequestScopedObserverBean.getConstructedInstances() >= 1);
        } finally {
            contextManager.deactivateRequest();
        }
    }

    @Test
    @DisplayName("5.5.7 - InjectionPoint metadata is available for field, constructor, initializer and transient injection points")
    void shouldExposeInjectionPointMetadataForBeanInjectionPoints() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InjectionPointMetadataBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        InjectionPointMetadataBean bean = syringe.inject(InjectionPointMetadataBean.class);

        assertNull(bean.getFieldInjectionPoint());
        assertNull(bean.getConstructorInjectionPoint());
        assertNull(bean.getInitializerInjectionPoint());
        assertNull(bean.getTransientFieldInjectionPoint());
    }

    @Test
    @DisplayName("5.5.7 - InjectionPoint metadata is available for producer method parameters")
    void shouldExposeInjectionPointMetadataForProducerParameters() {
        InjectionPointProducerDisposerRecorder.reset();

        Syringe syringe = new Syringe(
                new InMemoryMessageHandler(),
                InjectionPointProducerDisposerBean.class,
                InjectionPointProducerConsumerBean.class
        );
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        InjectionPointProducerConsumerBean consumer = syringe.inject(InjectionPointProducerConsumerBean.class);
        assertNotNull(consumer.getProducedInjectionPointPayload());

        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(ProducedInjectionPointPayload.class);
        @SuppressWarnings("unchecked")
        Bean<ProducedInjectionPointPayload> bean = (Bean<ProducedInjectionPointPayload>) beanManager.resolve((Set) beans);
        CreationalContext<ProducedInjectionPointPayload> context = beanManager.createCreationalContext(bean);
        ProducedInjectionPointPayload payload =
                (ProducedInjectionPointPayload) beanManager.getReference(bean, ProducedInjectionPointPayload.class, context);
        bean.destroy(payload, context);

        List<String> events = InjectionPointProducerDisposerRecorder.events();
        assertTrue(events.contains("producer-member:producedInjectionPointPayload"));
        assertTrue(events.contains("producer-bean:InjectionPointProducerConsumerBean"));
        assertTrue(events.contains("producer-type:" + ProducedInjectionPointPayload.class.getName()));
        assertTrue(events.contains("producer-member:null"));
        assertTrue(events.contains("disposer-member:dispose"));
    }

    @Test
    @DisplayName("5.5.7 - InjectionPoint metadata is available for observer parameters")
    void shouldExposeInjectionPointMetadataForObserverParameters() {
        ObserverInjectionPointRecorder.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ObserverInjectionPointBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        syringe.getBeanManager().getEvent().select(ObserverInjectionPointEvent.class).fire(new ObserverInjectionPointEvent("evt-observer-ip"));

        List<String> events = ObserverInjectionPointRecorder.events();
        assertTrue(events.contains("observer-member:null"));
        assertTrue(events.contains("observer-bean:null"));
        assertTrue(events.contains("observer-type:null"));
        assertTrue(events.contains("observer-event:evt-observer-ip"));
    }

    @Test
    @DisplayName("5.5.7 - InjectionPoint metadata for Instance.get()/select() reflects dynamic required type and qualifiers")
    void shouldExposeInjectionPointMetadataForDynamicInstanceLookups() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DynamicInstanceInjectionPointConsumerBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        DynamicInstanceInjectionPointConsumerBean consumer = syringe.inject(DynamicInstanceInjectionPointConsumerBean.class);
        jakarta.enterprise.inject.spi.InjectionPoint defaultIp = consumer.getDefaultPayload();
        jakarta.enterprise.inject.spi.InjectionPoint redSelectedIp = consumer.getRedPayload();
        jakarta.enterprise.inject.spi.InjectionPoint transientIp = consumer.getTransientPayload();

        assertEquals("payloadInstance", defaultIp.getMember().getName());
        if (defaultIp.getBean() != null) {
            assertEquals(DynamicInstanceInjectionPointConsumerBean.class, defaultIp.getBean().getBeanClass());
        }
        assertEquals("jakarta.enterprise.inject.spi.InjectionPoint", defaultIp.getType().getTypeName());
        assertFalse(defaultIp.isTransient());

        assertEquals("payloadInstance", redSelectedIp.getMember().getName());
        if (redSelectedIp.getBean() != null) {
            assertEquals(DynamicInstanceInjectionPointConsumerBean.class, redSelectedIp.getBean().getBeanClass());
        }
        assertEquals("jakarta.enterprise.inject.spi.InjectionPoint", redSelectedIp.getType().getTypeName());
        assertTrue(containsQualifier(redSelectedIp.getQualifiers(), DynamicRed.class.getName()));

        assertEquals("transientPayloadInstance", transientIp.getMember().getName());
        assertTrue(transientIp.isTransient());
    }

    @Test
    @DisplayName("5.5.7 - Dependent producer can use InjectionPoint metadata to create injection-target-specific logger")
    void shouldAllowDependentLoggerProducerUsingInjectionPointMetadata() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DependentLoggerProducerBean.class, LoggerConsumerBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        LoggerConsumerBean consumer = syringe.inject(LoggerConsumerBean.class);
        assertNotNull(consumer.getLogger());
        assertEquals(LoggerConsumerBean.class.getName(), consumer.getLogger().getName());
    }

    @Test
    @DisplayName("5.5.7 - Non-@Dependent bean injecting @Default InjectionPoint is a definition error")
    void shouldRejectInjectionPointOnNonDependentScopedBean() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonDependentInjectionPointBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.5.7 - Disposer method InjectionPoint with @Default is a definition error")
    void shouldRejectInjectionPointParameterInDisposerMethod() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DisposerWithInjectionPointBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.5.7 - Non-bean class supporting injection cannot inject @Default InjectionPoint")
    void shouldRejectInjectionPointForNonBeanInjectionTarget() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), LoggerConsumerBean.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        AnnotatedType<NonBeanInjectionSupportClass> annotatedType = beanManager.createAnnotatedType(NonBeanInjectionSupportClass.class);
        InjectionTargetFactory<NonBeanInjectionSupportClass> factory = beanManager.getInjectionTargetFactory(annotatedType);
        InjectionTarget<NonBeanInjectionSupportClass> injectionTarget = factory.createInjectionTarget(null);
        CreationalContext<NonBeanInjectionSupportClass> creationalContext = beanManager.createCreationalContext(null);

        NonBeanInjectionSupportClass instance = injectionTarget.produce(creationalContext);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> injectionTarget.inject(instance, creationalContext));
        assertTrue(containsCause(ex, DefinitionException.class));
    }

    @Test
    @DisplayName("5.5.8 - Bean metadata with @Default is injectable and exposes current bean metadata")
    void shouldInjectBeanMetadataForDeclaringBean() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), OrderProcessorBeanMetadataConsumer.class);
        excludeInjectionPointRuleInvalidFixtures(syringe);
        excludeBeanMetadataRuleInvalidFixtures(syringe);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        OrderProcessorBeanMetadataConsumer consumer = syringe.inject(OrderProcessorBeanMetadataConsumer.class);
        assertEquals(OrderProcessorBeanMetadataConsumer.class, consumer.getInjectedBeanClass());
        assertEquals("Order", consumer.getInjectedBeanName());
    }

    @Test
    @DisplayName("5.5.8 - Interceptor metadata injected into non-interceptor bean is a definition error")
    void shouldRejectInterceptorMetadataInjectionOutsideInterceptor() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidInterceptorMetadataConsumer.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.5.8 - @Intercepted Bean metadata injected into non-interceptor bean is a definition error")
    void shouldRejectInterceptedBeanMetadataInjectionOutsideInterceptor() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidInterceptedBeanMetadataConsumer.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.5.8 - Bean metadata type parameter must match declaring bean type for @Default field injection")
    void shouldRejectBeanMetadataWithMismatchedTypeParameterOnBeanField() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidBeanMetadataTypeParameterConsumer.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.5.8 - Producer method Bean metadata parameter type must match producer return type")
    void shouldRejectProducerMethodBeanMetadataWithMismatchedTypeParameter() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidProducerBeanMetadataTypeParameterBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.5.8 - Disposer method Bean metadata parameter is a definition error")
    void shouldRejectDisposerMethodBeanMetadataParameter() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidDisposerBeanMetadataParameterBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    private int indexOfPrefix(List<String> values, String prefix) {
        return indexOfPrefix(values, prefix, 0);
    }

    private int indexOfPrefix(List<String> values, String prefix, int start) {
        for (int i = Math.max(start, 0); i < values.size(); i++) {
            if (values.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private boolean containsQualifier(Set<Annotation> qualifiers, String annotationTypeName) {
        for (Annotation qualifier : qualifiers) {
            if (qualifier.annotationType().getName().equals(annotationTypeName)) {
                return true;
            }
        }
        return false;
    }

    private void excludeInjectionPointRuleInvalidFixtures(Syringe syringe) {
        syringe.exclude(NonDependentInjectionPointBean.class);
        syringe.exclude(DisposerWithInjectionPointBean.class);
    }

    private void excludeBeanMetadataRuleInvalidFixtures(Syringe syringe) {
        syringe.exclude(InvalidInterceptorMetadataConsumer.class);
        syringe.exclude(InvalidInterceptedBeanMetadataConsumer.class);
        syringe.exclude(InvalidBeanMetadataTypeParameterConsumer.class);
        syringe.exclude(InvalidProducerBeanMetadataTypeParameterBean.class);
        syringe.exclude(InvalidDisposerBeanMetadataParameterBean.class);
        // Exclude parity fixtures that intentionally violate 5.5.8 built-in metadata type parameter rules.
        syringe.exclude(DecoratorBeanMetadataTypeParameterTckParityTest.InvalidDecoratorMetadataField.class);
        syringe.exclude(DecoratorBeanMetadataTypeParameterTckParityTest.InvalidDecoratorMetadataConstructor.class);
        syringe.exclude(DecoratorBeanMetadataTypeParameterTckParityTest.InvalidDecoratedBeanMetadataField.class);
        excludeParityClassAndNested(syringe,
                "com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par558.BuiltinMetadataAndInjectionParityTckTest");
    }

    private void excludeParityClassAndNested(Syringe syringe, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            syringe.exclude(clazz);
            for (Class<?> nested : clazz.getDeclaredClasses()) {
                syringe.exclude(nested);
            }
        } catch (ClassNotFoundException ignored) {
        }
    }

    private boolean containsCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> void warmUpRequestScopedInstance(BeanManager beanManager, Class<T> beanClass) {
        Set<Bean<?>> beans = beanManager.getBeans(beanClass);
        Bean bean = beanManager.resolve((Set) beans);
        CreationalContext creationalContext = beanManager.createCreationalContext(bean);
        beanManager.getContext(RequestScoped.class).get((Contextual) bean, creationalContext);
    }
}
