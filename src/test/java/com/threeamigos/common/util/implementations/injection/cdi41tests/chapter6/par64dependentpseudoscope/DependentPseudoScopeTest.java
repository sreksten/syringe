package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par24nonportableinterceptor.NonPortableScopedInterceptor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par64.*;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par641.*;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par64dependentpseudoscope.par642.*;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("6.4 - Dependent pseudo-scope")
public class DependentPseudoScopeTest {

    @Test
    @DisplayName("6.4 - No injected dependent instance is shared between multiple injection points")
    void shouldNotShareDependentInstanceAcrossInjectionPoints() {
        DependentInvocationRecorder.reset();
        Syringe syringe = newSyringe(DependentTwoInjectionPointsBean.class, InvocationScopedDependentBean.class);

        DependentTwoInjectionPointsBean bean = syringe.inject(DependentTwoInjectionPointsBean.class);
        assertNotNull(bean);
        assertNotEquals(bean.firstId(), bean.secondId());
    }

    @Test
    @DisplayName("6.4 - Dependent instance injected into created object is bound to that object's lifecycle")
    void shouldBindDependentLifecycleToCreatedObject() {
        DependentInvocationRecorder.reset();
        Syringe syringe = newSyringe(DependentParentBean.class, InvocationScopedDependentBean.class);
        BeanManager beanManager = syringe.getBeanManager();

        Bean<DependentParentBean> parentBean = resolveBean(beanManager, DependentParentBean.class);
        CreationalContext<DependentParentBean> parentContext = beanManager.createCreationalContext(parentBean);
        DependentParentBean parent =
                (DependentParentBean) beanManager.getReference(parentBean, DependentParentBean.class, parentContext);
        String childId = parent.childId();

        parentBean.destroy(parent, parentContext);

        assertTrue(DependentInvocationRecorder.preDestroyedDependentIds().contains(childId));
    }

    @Test
    @DisplayName("6.4 - Dependent instances injected into producer/disposer parameters exist only for invocation")
    void shouldUseInvocationBoundDependentForProducerAndDisposerParameters() {
        DependentInvocationRecorder.reset();
        Syringe syringe = newSyringe(
                ProducerDisposerOwnerBean.class,
                ProducedPayload.class,
                InvocationScopedDependentBean.class
        );
        BeanManager beanManager = syringe.getBeanManager();

        Bean<ProducedPayload> producedBean = resolveBean(beanManager, ProducedPayload.class);
        CreationalContext<ProducedPayload> producedContext = beanManager.createCreationalContext(producedBean);
        ProducedPayload payload =
                (ProducedPayload) beanManager.getReference(producedBean, ProducedPayload.class, producedContext);
        assertNotNull(payload);
        producedBean.destroy(payload, producedContext);

        List<String> producerIds = DependentInvocationRecorder.producerParameterDependentIds();
        List<String> disposerIds = DependentInvocationRecorder.disposerParameterDependentIds();
        assertEquals(1, producerIds.size());
        assertEquals(1, disposerIds.size());
        assertNotEquals(producerIds.get(0), disposerIds.get(0));
        assertTrue(DependentInvocationRecorder.preDestroyedDependentIds().contains(producerIds.get(0)));
        assertTrue(DependentInvocationRecorder.preDestroyedDependentIds().contains(disposerIds.get(0)));
    }

    @Test
    @DisplayName("6.4 - Dependent instances injected into observer method parameters exist only for invocation")
    void shouldUseInvocationBoundDependentForObserverMethodParameters() {
        DependentInvocationRecorder.reset();
        Syringe syringe = newSyringe(
                ObserverOwnerBean.class,
                SimpleDependentEvent.class,
                InvocationScopedDependentBean.class
        );
        BeanManager beanManager = syringe.getBeanManager();

        beanManager.getEvent().fire(new SimpleDependentEvent("e1"));
        beanManager.getEvent().fire(new SimpleDependentEvent("e2"));

        List<String> observerIds = DependentInvocationRecorder.observerParameterDependentIds();
        assertEquals(2, observerIds.size());
        assertNotEquals(observerIds.get(0), observerIds.get(1));
        assertTrue(DependentInvocationRecorder.preDestroyedDependentIds().contains(observerIds.get(0)));
        assertTrue(DependentInvocationRecorder.preDestroyedDependentIds().contains(observerIds.get(1)));
    }

    @Test
    @DisplayName("6.4 - Dependent context get() with CreationalContext returns new instance every invocation")
    void shouldReturnNewInstanceForDependentContextGetWithCreationalContext() {
        DependentInvocationRecorder.reset();
        Syringe syringe = newSyringe(InvocationScopedDependentBean.class);
        BeanManager beanManager = syringe.getBeanManager();
        Context dependentContext = beanManager.getContext(Dependent.class);
        Bean<InvocationScopedDependentBean> bean = resolveBean(beanManager, InvocationScopedDependentBean.class);

        InvocationScopedDependentBean first = dependentContext.get(bean, beanManager.createCreationalContext(bean));
        InvocationScopedDependentBean second = dependentContext.get(bean, beanManager.createCreationalContext(bean));

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first.id(), second.id());
    }

    @Test
    @DisplayName("6.4 - Dependent context get() without CreationalContext returns null and scope is always active")
    void shouldReturnNullWithoutCreationalContextAndRemainAlwaysActive() {
        Syringe syringe = newSyringe(InvocationScopedDependentBean.class);
        BeanManager beanManager = syringe.getBeanManager();
        Context dependentContext = beanManager.getContext(Dependent.class);
        Bean<InvocationScopedDependentBean> bean = resolveBean(beanManager, InvocationScopedDependentBean.class);

        assertTrue(dependentContext.isActive());
        assertNull(dependentContext.get(bean));
    }

    @Test
    @DisplayName("6.4.1 - Dependent bean injected into field/constructor/initializer is dependent object of injected bean")
    void shouldDestroyFieldConstructorInitializerDependentsWithOwningBean() {
        DependentObjectsRecorder.reset();
        Syringe syringe = newSyringe(DependentLifecycleOwnerBean.class, TrackedDependentObject.class);
        BeanManager beanManager = syringe.getBeanManager();

        Bean<DependentLifecycleOwnerBean> ownerBean = resolveBean(beanManager, DependentLifecycleOwnerBean.class);
        CreationalContext<DependentLifecycleOwnerBean> ownerContext = beanManager.createCreationalContext(ownerBean);
        DependentLifecycleOwnerBean owner =
                (DependentLifecycleOwnerBean) beanManager.getReference(ownerBean, DependentLifecycleOwnerBean.class, ownerContext);

        String fieldId = owner.fieldId();
        String ctorId = owner.constructorId();
        String initId = owner.initializerId();
        assertNotEquals(fieldId, ctorId);
        assertNotEquals(fieldId, initId);
        assertNotEquals(ctorId, initId);

        ownerBean.destroy(owner, ownerContext);

        List<String> destroyed = DependentObjectsRecorder.preDestroyIds();
        assertTrue(destroyed.contains(fieldId));
        assertTrue(destroyed.contains(ctorId));
        assertTrue(destroyed.contains(initId));
    }

    @Test
    @DisplayName("6.4.1 - Dependent bean injected into producer method is dependent object of producer method bean instance")
    void shouldTreatProducerMethodDependentParameterAsDependentObject() {
        DependentObjectsRecorder.reset();
        Syringe syringe = newSyringe(
                ProducerMethodOwnerBean.class,
                ProducedDependentObject.class,
                TrackedDependentObject.class
        );
        BeanManager beanManager = syringe.getBeanManager();

        Bean<ProducedDependentObject> producedBean = resolveBean(beanManager, ProducedDependentObject.class);
        CreationalContext<ProducedDependentObject> producedContext = beanManager.createCreationalContext(producedBean);
        ProducedDependentObject produced =
                (ProducedDependentObject) beanManager.getReference(producedBean, ProducedDependentObject.class, producedContext);

        String producerParamId = produced.producerParamId();
        assertEquals(1, DependentObjectsRecorder.producerParamIds().size());
        assertEquals(producerParamId, DependentObjectsRecorder.producerParamIds().get(0));
        assertFalse(DependentObjectsRecorder.preDestroyIds().contains(producerParamId));

        producedBean.destroy(produced, producedContext);
        assertTrue(DependentObjectsRecorder.preDestroyIds().contains(producerParamId));
    }

    @Test
    @DisplayName("6.4.1 - Dependent bean obtained by Instance.get() is dependent object of that Instance")
    void shouldDestroyDependentObjectObtainedFromInstanceGet() {
        DependentObjectsRecorder.reset();
        Syringe syringe = newSyringe(InstanceLookupOwnerBean.class, TrackedDependentObject.class);

        InstanceLookupOwnerBean owner = syringe.inject(InstanceLookupOwnerBean.class);
        TrackedDependentObject dependentObject = owner.getFromInstance();
        String dependentId = dependentObject.id();

        owner.destroyFromSameInstance(dependentObject);

        assertTrue(DependentObjectsRecorder.preDestroyIds().contains(dependentId));
    }

    @Test
    @DisplayName("6.4.1 - Interceptor instances must be dependent objects (non-dependent interceptor scope is rejected)")
    void shouldRejectInterceptorWithNonDependentScope() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonPortableScopedInterceptor.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("6.4.2 - Dependent objects of contextual instance are destroyed when Contextual.destroy calls CreationalContext.release")
    void shouldDestroyDependentObjectsWhenContextualInstanceIsDestroyed() {
        DependentInvocationRecorder.reset();
        Syringe syringe = newSyringe(DependentParentBean.class, InvocationScopedDependentBean.class);
        BeanManager beanManager = syringe.getBeanManager();

        Bean<DependentParentBean> parentBean = resolveBean(beanManager, DependentParentBean.class);
        CreationalContext<DependentParentBean> parentContext = beanManager.createCreationalContext(parentBean);
        DependentParentBean parent =
                (DependentParentBean) beanManager.getReference(parentBean, DependentParentBean.class, parentContext);
        String childId = parent.childId();

        assertNotNull(parentBean);
        parentBean.destroy(parent, parentContext);
        assertTrue(DependentInvocationRecorder.preDestroyedDependentIds().contains(childId));
    }

    @Test
    @DisplayName("6.4.2 - Dependent objects of non-contextual bean instance are destroyed when instance is destroyed by container")
    void shouldDestroyDependentObjectsOfNonContextualInstance() {
        DependentInvocationRecorder.reset();
        Syringe syringe = newSyringe(NonContextualDependentOwner.class, InvocationScopedDependentBean.class);
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<NonContextualDependentOwner> annotatedType =
                beanManager.createAnnotatedType(NonContextualDependentOwner.class);
        InjectionTargetFactory<NonContextualDependentOwner> factory =
                beanManager.getInjectionTargetFactory(annotatedType);
        InjectionTarget<NonContextualDependentOwner> injectionTarget = factory.createInjectionTarget(null);
        CreationalContext<NonContextualDependentOwner> context = beanManager.createCreationalContext(null);

        NonContextualDependentOwner owner = injectionTarget.produce(context);
        injectionTarget.inject(owner, context);
        injectionTarget.postConstruct(owner);
        String dependentId = owner.dependentId();

        injectionTarget.preDestroy(owner);
        injectionTarget.dispose(owner);
        context.release();

        assertTrue(DependentInvocationRecorder.preDestroyedDependentIds().contains(dependentId));
    }

    @Test
    @DisplayName("6.4.2 - @Dependent parameters of disposer and observer methods are destroyed when invocation completes")
    void shouldDestroyDependentDisposerAndObserverParametersAfterInvocation() {
        DependentDestructionParamRecorder.reset();
        Syringe syringe = newSyringe(
                DependentDisposerReceiverBean.class,
                ProducedByDependentDisposerOwner.class,
                DependentObserverReceiverBean.class,
                DependentReceiverEvent.class,
                TransientReferenceDependentParam.class,
                InvocationScopedDependentBean.class
        );
        BeanManager beanManager = syringe.getBeanManager();

        Bean<ProducedByDependentDisposerOwner> producedBean =
                resolveBean(beanManager, ProducedByDependentDisposerOwner.class);
        assertNotNull(producedBean);
        CreationalContext<ProducedByDependentDisposerOwner> producedContext =
                beanManager.createCreationalContext(producedBean);
        ProducedByDependentDisposerOwner produced =
                (ProducedByDependentDisposerOwner) beanManager.getReference(
                        producedBean,
                        ProducedByDependentDisposerOwner.class,
                        producedContext
                );
        producedBean.destroy(produced, producedContext);

        beanManager.getEvent().fire(new DependentReceiverEvent("obs"));

        assertFalse(DependentDestructionParamRecorder.disposerParamIds().isEmpty());
        assertFalse(DependentDestructionParamRecorder.observerParamIds().isEmpty());
        assertTrue(DependentDestructionParamRecorder.preDestroyedIds().contains(
                DependentDestructionParamRecorder.disposerParamIds().get(0)
        ));
        assertTrue(DependentDestructionParamRecorder.preDestroyedIds().contains(
                DependentDestructionParamRecorder.observerParamIds().get(0)
        ));
    }

    @Test
    @DisplayName("6.4.2 - @Dependent bean instances created to receive producer/disposer/observer invocations are destroyed after invocation")
    void shouldDestroyDependentReceiversAfterProducerDisposerAndObserverInvocations() {
        ReceiverInvocationRecorder.reset();
        DependentInvocationRecorder.reset();
        Syringe syringe = newSyringe(
                DependentProducerMethodReceiverBean.class,
                ProducedByDependentProducerMethod.class,
                DependentProducerFieldReceiverBean.class,
                ProducedByDependentProducerField.class,
                DependentDisposerReceiverBean.class,
                ProducedByDependentDisposerOwner.class,
                DependentObserverReceiverBean.class,
                DependentReceiverEvent.class,
                TransientReferenceDependentParam.class,
                InvocationScopedDependentBean.class
        );
        BeanManager beanManager = syringe.getBeanManager();

        Bean<ProducedByDependentProducerMethod> methodProducedBean =
                resolveBean(beanManager, ProducedByDependentProducerMethod.class);
        assertNotNull(methodProducedBean);
        beanManager.getReference(
                methodProducedBean,
                ProducedByDependentProducerMethod.class,
                beanManager.createCreationalContext(methodProducedBean)
        );

        Bean<ProducedByDependentProducerField> fieldProducedBean =
                resolveBean(beanManager, ProducedByDependentProducerField.class);
        assertNotNull(fieldProducedBean);
        beanManager.getReference(
                fieldProducedBean,
                ProducedByDependentProducerField.class,
                beanManager.createCreationalContext(fieldProducedBean)
        );

        Bean<ProducedByDependentDisposerOwner> disposerProducedBean =
                resolveBean(beanManager, ProducedByDependentDisposerOwner.class);
        assertNotNull(disposerProducedBean);
        CreationalContext<ProducedByDependentDisposerOwner> disposerContext =
                beanManager.createCreationalContext(disposerProducedBean);
        ProducedByDependentDisposerOwner producedForDispose =
                (ProducedByDependentDisposerOwner) beanManager.getReference(
                        disposerProducedBean,
                        ProducedByDependentDisposerOwner.class,
                        disposerContext
                );
        disposerProducedBean.destroy(producedForDispose, disposerContext);

        beanManager.getEvent().fire(new DependentReceiverEvent("obs2"));

        assertTrue(ReceiverInvocationRecorder.producerMethodReceiverDestroyedCount() >= 1);
        assertTrue(ReceiverInvocationRecorder.producerFieldReceiverDestroyedCount() >= 1);
        assertTrue(ReceiverInvocationRecorder.disposerReceiverDestroyedCount() >= 1);
        assertTrue(ReceiverInvocationRecorder.observerReceiverDestroyedCount() >= 1);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> beanType) {
        Set<Bean<?>> beans = beanManager.getBeans(beanType);
        Bean<T> resolved = beanManager.resolve((Set) beans);
        if (resolved != null) {
            return resolved;
        }
        if (!beans.isEmpty()) {
            return (Bean<T>) beans.iterator().next();
        }
        return null;
    }

}
