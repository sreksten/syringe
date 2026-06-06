package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter23.par232producerandinjectiontargetinterfaces;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.InjectionTargetFactory;
import jakarta.enterprise.inject.spi.Producer;
import jakarta.enterprise.inject.spi.ProducerFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("23.2 - Producer and InjectionTarget interfaces")
public class ProducerAndInjectionTargetInterfacesTest {

    @Test
    @DisplayName("23.2 - InjectionTarget for a class uses @Inject constructor, injects all discovered injection points, and runs lifecycle callbacks")
    void shouldApplyClassInjectionTargetContract() {
        Syringe syringe = newSyringe(
                ConstructorDependency.class,
                FieldDependency.class,
                InitializerDependency.class,
                ManagedClassProduct.class
        );
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<ManagedClassProduct> annotatedType = beanManager.createAnnotatedType(ManagedClassProduct.class);
        InjectionTargetFactory<ManagedClassProduct> factory = beanManager.getInjectionTargetFactory(annotatedType);
        InjectionTarget<ManagedClassProduct> injectionTarget = factory.createInjectionTarget(null);
        CreationalContext<ManagedClassProduct> context = beanManager.createCreationalContext(null);

        Set<InjectionPoint> injectionPoints = injectionTarget.getInjectionPoints();
        assertEquals(3, injectionPoints.size());
        Set<Type> injectionPointTypes = new HashSet<Type>();
        for (InjectionPoint injectionPoint : injectionPoints) {
            injectionPointTypes.add(injectionPoint.getType());
        }
        assertTrue(injectionPointTypes.contains(ConstructorDependency.class));
        assertTrue(injectionPointTypes.contains(FieldDependency.class));
        assertTrue(injectionPointTypes.contains(InitializerDependency.class));

        ManagedClassProduct instance = injectionTarget.produce(context);
        assertTrue(instance.usedInjectConstructor);
        assertFalse(instance.usedNoArgConstructor);
        assertNotNull(instance.constructorDependency);

        injectionTarget.inject(instance, context);
        assertNotNull(instance.fieldDependency);
        assertNotNull(instance.initializerDependency);
        assertTrue(instance.initializerCalled);

        injectionTarget.postConstruct(instance);
        assertTrue(instance.postConstructCalled);

        injectionTarget.preDestroy(instance);
        assertTrue(instance.preDestroyCalled);

        boolean preDestroyAfterPreDestroy = instance.preDestroyCalled;
        injectionTarget.dispose(instance);
        assertEquals(preDestroyAfterPreDestroy, instance.preDestroyCalled, "dispose() should be a no-op for class producers");
    }

    @Test
    @DisplayName("23.2 - InjectionTarget.produce uses no-arg constructor when no constructor is annotated @Inject")
    void shouldUseNoArgConstructorWhenNoInjectConstructorExists() {
        Syringe syringe = newSyringe(NoInjectConstructorProduct.class, ConstructorDependency.class);
        BeanManager beanManager = syringe.getBeanManager();

        AnnotatedType<NoInjectConstructorProduct> annotatedType = beanManager.createAnnotatedType(NoInjectConstructorProduct.class);
        InjectionTargetFactory<NoInjectConstructorProduct> factory = beanManager.getInjectionTargetFactory(annotatedType);
        InjectionTarget<NoInjectConstructorProduct> injectionTarget = factory.createInjectionTarget(null);
        CreationalContext<NoInjectConstructorProduct> context = beanManager.createCreationalContext(null);

        NoInjectConstructorProduct instance = injectionTarget.produce(context);
        assertTrue(instance.usedNoArgConstructor);
        assertFalse(instance.usedArgConstructor);
    }

    @Test
    @DisplayName("23.2 - Producer for producer method invokes declaring contextual instance, injects parameters, reports method-parameter injection points, and disposes via disposer method")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldApplyProducerMethodContract() {
        ProducerMethodRecorder.reset();
        Syringe syringe = newSyringe(ProducerMethodDeclaringBean.class, ProducerMethodParam.class);
        BeanManager beanManager = syringe.getBeanManager();

        Bean<ProducerMethodDeclaringBean> declaringBean = resolveBean(beanManager, ProducerMethodDeclaringBean.class);
        AnnotatedMethod<?> producerMethod = findProducesMethod(beanManager.createAnnotatedType(ProducerMethodDeclaringBean.class));
        ProducerFactory<ProducedByMethod> producerFactory = (ProducerFactory<ProducedByMethod>) beanManager.getProducerFactory(
                (AnnotatedMethod) producerMethod,
                (Bean) declaringBean
        );
        Producer<ProducedByMethod> producer = (Producer<ProducedByMethod>) producerFactory.createProducer((Bean) declaringBean);

        Set<InjectionPoint> injectionPoints = producer.getInjectionPoints();
        assertEquals(1, injectionPoints.size());
        assertEquals(ProducerMethodParam.class, injectionPoints.iterator().next().getType());

        CreationalContext<ProducedByMethod> context = beanManager.createCreationalContext(null);
        ProducedByMethod produced = producer.produce(context);
        assertNotNull(produced);
        assertNotNull(produced.declaringBeanInstanceId);
        assertNotNull(produced.methodParamId);
        assertTrue(ProducerMethodRecorder.events().stream().anyMatch(e -> e.startsWith("produce:")));

        producer.dispose(produced);
        assertTrue(
                ProducerMethodRecorder.events().stream().anyMatch(e -> e.startsWith("dispose:")),
                "dispose() should invoke disposer method for producer method products"
        );
    }

    @Test
    @DisplayName("23.2 - Producer for producer field reads from declaring contextual instance and disposes via disposer method")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldApplyProducerFieldContract() {
        ProducerFieldRecorder.reset();
        Syringe syringe = newSyringe(ProducerFieldDeclaringBean.class);
        BeanManager beanManager = syringe.getBeanManager();

        Bean<ProducerFieldDeclaringBean> declaringBean = resolveBean(beanManager, ProducerFieldDeclaringBean.class);
        AnnotatedField<?> producerField = findProducesField(beanManager.createAnnotatedType(ProducerFieldDeclaringBean.class));
        ProducerFactory<ProducedByField> producerFactory = (ProducerFactory<ProducedByField>) beanManager.getProducerFactory(
                (AnnotatedField) producerField,
                (Bean) declaringBean
        );
        Producer<ProducedByField> producer = (Producer<ProducedByField>) producerFactory.createProducer((Bean) declaringBean);

        assertEquals(0, producer.getInjectionPoints().size());

        CreationalContext<ProducedByField> context = beanManager.createCreationalContext(null);
        ProducedByField produced = producer.produce(context);
        assertNotNull(produced);
        assertNotNull(produced.declaringBeanInstanceId);
        assertTrue(ProducerFieldRecorder.events().stream().anyMatch(e -> e.startsWith("produce-field:")));

        producer.dispose(produced);
        assertTrue(
                ProducerFieldRecorder.events().stream().anyMatch(e -> e.startsWith("dispose-field:")),
                "dispose() should invoke disposer method for producer field products"
        );
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

    private AnnotatedMethod<?> findProducesMethod(AnnotatedType<?> annotatedType) {
        for (AnnotatedMethod<?> method : annotatedType.getMethods()) {
            if (method.isAnnotationPresent(Produces.class)) {
                return method;
            }
        }
        throw new AssertionError("Expected a @Produces method on " + annotatedType.getJavaClass().getName());
    }

    private AnnotatedField<?> findProducesField(AnnotatedType<?> annotatedType) {
        for (AnnotatedField<?> field : annotatedType.getFields()) {
            if (field.isAnnotationPresent(Produces.class)) {
                return field;
            }
        }
        throw new AssertionError("Expected a @Produces field on " + annotatedType.getJavaClass().getName());
    }

    @Dependent
    public static class ConstructorDependency {
        final String id = UUID.randomUUID().toString();
    }

    @Dependent
    public static class FieldDependency {
        final String id = UUID.randomUUID().toString();
    }

    @Dependent
    public static class InitializerDependency {
        final String id = UUID.randomUUID().toString();
    }

    @Dependent
    public static class ManagedClassProduct {
        boolean usedInjectConstructor;
        boolean usedNoArgConstructor;
        ConstructorDependency constructorDependency;
        @Inject
        FieldDependency fieldDependency;
        InitializerDependency initializerDependency;
        boolean initializerCalled;
        boolean postConstructCalled;
        boolean preDestroyCalled;

        ManagedClassProduct() {
            this.usedNoArgConstructor = true;
        }

        @Inject
        ManagedClassProduct(ConstructorDependency constructorDependency) {
            this.usedInjectConstructor = true;
            this.constructorDependency = constructorDependency;
        }

        @Inject
        void init(InitializerDependency initializerDependency) {
            this.initializerCalled = true;
            this.initializerDependency = initializerDependency;
        }

        @PostConstruct
        void onPostConstruct() {
            this.postConstructCalled = true;
        }

        @PreDestroy
        void onPreDestroy() {
            this.preDestroyCalled = true;
        }
    }

    @Dependent
    public static class NoInjectConstructorProduct {
        boolean usedNoArgConstructor;
        boolean usedArgConstructor;

        NoInjectConstructorProduct() {
            this.usedNoArgConstructor = true;
        }

        NoInjectConstructorProduct(ConstructorDependency ignored) {
            this.usedArgConstructor = true;
        }
    }

    @Dependent
    public static class ProducerMethodParam {
        final String id = UUID.randomUUID().toString();
    }

    @Dependent
    public static class ProducerMethodDeclaringBean {
        final String instanceId = UUID.randomUUID().toString();

        @Produces
        ProducedByMethod produce(ProducerMethodParam param) {
            ProducerMethodRecorder.record("produce:" + instanceId + ":" + param.id);
            return new ProducedByMethod(instanceId, param.id);
        }

        void dispose(@Disposes ProducedByMethod produced) {
            ProducerMethodRecorder.record("dispose:" + instanceId + ":" + produced.declaringBeanInstanceId);
        }
    }

    public static class ProducedByMethod {
        final String declaringBeanInstanceId;
        final String methodParamId;

        ProducedByMethod(String declaringBeanInstanceId, String methodParamId) {
            this.declaringBeanInstanceId = declaringBeanInstanceId;
            this.methodParamId = methodParamId;
        }
    }

    @Dependent
    public static class ProducerFieldDeclaringBean {
        final String instanceId = UUID.randomUUID().toString();

        @Produces
        ProducedByField fieldProduct = produceForRecording();

        ProducedByField produceForRecording() {
            ProducerFieldRecorder.record("produce-field:" + instanceId);
            return new ProducedByField(instanceId);
        }

        void dispose(@Disposes ProducedByField produced) {
            ProducerFieldRecorder.record("dispose-field:" + instanceId + ":" + produced.declaringBeanInstanceId);
        }
    }

    public static class ProducedByField {
        final String declaringBeanInstanceId;

        ProducedByField(String declaringBeanInstanceId) {
            this.declaringBeanInstanceId = declaringBeanInstanceId;
        }
    }

    @Dependent
    public static class ProducerMethodRecorder {
        private static final java.util.List<String> EVENTS = new java.util.ArrayList<String>();

        static synchronized void reset() {
            EVENTS.clear();
        }

        static synchronized void record(String event) {
            EVENTS.add(event);
        }

        static synchronized java.util.List<String> events() {
            return new java.util.ArrayList<String>(EVENTS);
        }
    }

    @Dependent
    public static class ProducerFieldRecorder {
        private static final java.util.List<String> EVENTS = new java.util.ArrayList<String>();

        static synchronized void reset() {
            EVENTS.clear();
        }

        static synchronized void record(String event) {
            EVENTS.add(event);
        }

        static synchronized java.util.List<String> events() {
            return new java.util.ArrayList<String>(EVENTS);
        }
    }
}
