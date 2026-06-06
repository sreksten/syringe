package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter7.par73lifecycleofcontextualinstances;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.IllegalProductException;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("7.3 - Lifecycle of contextual instances")
@Execution(ExecutionMode.SAME_THREAD)
public class LifecycleOfContextualInstancesTest {

    @Test
    @DisplayName("7.3.1 - Bean.create() for managed bean calls bean constructor and performs field and initializer injection")
    void shouldCreateManagedBeanWithConstructorFieldAndInitializerInjection() {
        Syringe syringe = newSyringe(
                ManagedBeanUnderTest.class,
                ConstructorDependency.class,
                FieldDependency.class,
                InitializerDependency.class,
                ExtraDependency.class
        );
        BeanManager beanManager = syringe.getBeanManager();
        Bean<ManagedBeanUnderTest> bean = resolveBean(beanManager, ManagedBeanUnderTest.class);
        CreationalContext<ManagedBeanUnderTest> creationalContext = beanManager.createCreationalContext(bean);

        ManagedBeanUnderTest created = bean.create(creationalContext);

        assertNotNull(created);
        assertNotNull(created.constructorDependency);
        assertNotNull(created.fieldDependency);
        assertNotNull(created.initializerDependency);
        assertNotNull(created.extraDependencyFromInitializer);
        assertTrue(created.initializerCalled);
        assertNotSame(created.constructorDependency, created.fieldDependency);
        assertNotSame(created.fieldDependency, created.initializerDependency);
        assertNotSame(created.constructorDependency, created.initializerDependency);
    }

    @Test
    @DisplayName("7.3.1 - Bean.destroy() destroys managed bean instance and its dependent objects")
    void shouldDestroyManagedBeanAndDependentObjectsWhenBeanDestroyIsCalled() {
        ManagedBeanUnderTest.resetLifecycleCounters();
        Syringe syringe = newSyringe(
                ManagedBeanUnderTest.class,
                ConstructorDependency.class,
                FieldDependency.class,
                InitializerDependency.class,
                ExtraDependency.class
        );
        BeanManager beanManager = syringe.getBeanManager();
        Bean<ManagedBeanUnderTest> bean = resolveBean(beanManager, ManagedBeanUnderTest.class);
        CreationalContext<ManagedBeanUnderTest> creationalContext = beanManager.createCreationalContext(bean);
        ManagedBeanUnderTest created = bean.create(creationalContext);
        assertNotNull(created);

        ConstructorDependency constructorDep = created.constructorDependency;
        FieldDependency fieldDep = created.fieldDependency;
        InitializerDependency initializerDep = created.initializerDependency;
        ExtraDependency extraDep = created.extraDependencyFromInitializer;

        bean.destroy(created, creationalContext);

        assertTrue(ManagedBeanUnderTest.beanPreDestroyCount.get() >= 1);
        assertTrue(ConstructorDependency.preDestroyCount.get() >= 1);
        assertTrue(FieldDependency.preDestroyCount.get() >= 1);
        assertTrue(InitializerDependency.preDestroyCount.get() >= 1);
        assertTrue(ExtraDependency.preDestroyCount.get() >= 1);
        assertSame(constructorDep, created.constructorDependency);
        assertSame(fieldDep, created.fieldDependency);
        assertSame(initializerDep, created.initializerDependency);
        assertSame(extraDep, created.extraDependencyFromInitializer);
    }

    @Test
    @DisplayName("7.3.2 - Bean.create() for producer method invokes producer and returns produced contextual instance")
    void shouldCreateContextualInstanceFromProducerMethodBeanCreate() {
        ProducerMethodLifecycleRecorder.reset();
        Syringe syringe = newSyringe(
                ProducerMethodOwner.class,
                ProducerMethodInputDependency.class,
                ProducerMethodDisposerDependency.class
        );
        BeanManager beanManager = syringe.getBeanManager();
        Bean<ProducedPayload> bean = resolveQualifiedBean(beanManager, ProducedPayload.class, ProducedPayloadLiteral.INSTANCE);
        CreationalContext<ProducedPayload> creationalContext = beanManager.createCreationalContext(bean);

        ProducedPayload created = bean.create(creationalContext);

        assertNotNull(created);
        assertSame(created, ProducerMethodLifecycleRecorder.lastProducedPayload);
        assertNotNull(ProducerMethodLifecycleRecorder.lastProducerInputDependency);
        assertTrue(ProducerMethodLifecycleRecorder.produceInvocationCount.get() >= 1);
    }

    @Test
    @DisplayName("7.3.2 - If @Dependent producer method returns null, Bean.create() returns null")
    void shouldReturnNullWhenDependentProducerReturnsNull() {
        Syringe syringe = newSyringe(ProducerMethodOwner.class);
        BeanManager beanManager = syringe.getBeanManager();
        Bean<String> bean = resolveQualifiedBean(beanManager, String.class, DependentNullProductLiteral.INSTANCE);
        CreationalContext<String> creationalContext = beanManager.createCreationalContext(bean);

        String created = bean.create(creationalContext);

        assertNull(created);
    }

    @Test
    @DisplayName("7.3.2 - If non-@Dependent producer method returns null, Bean.create() throws IllegalProductException")
    void shouldThrowIllegalProductExceptionWhenNonDependentProducerReturnsNull() {
        Syringe syringe = newSyringe(ProducerMethodOwner.class);
        BeanManager beanManager = syringe.getBeanManager();
        Bean<String> bean = resolveQualifiedBean(beanManager, String.class, NonDependentNullProductLiteral.INSTANCE);
        CreationalContext<String> creationalContext = beanManager.createCreationalContext(bean);

        assertThrows(IllegalProductException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                bean.create(creationalContext);
            }
        });
    }

    @Test
    @DisplayName("7.3.2 - Bean.destroy() for producer method invokes disposer with destroyed instance and destroys dependent objects")
    void shouldInvokeDisposerAndDestroyDependentObjectsOnProducerBeanDestroy() {
        ProducerMethodLifecycleRecorder.reset();
        Syringe syringe = newSyringe(
                ProducerMethodOwner.class,
                ProducerMethodInputDependency.class,
                ProducerMethodDisposerDependency.class
        );
        BeanManager beanManager = syringe.getBeanManager();
        Bean<ProducedPayload> bean = resolveQualifiedBean(beanManager, ProducedPayload.class, ProducedPayloadLiteral.INSTANCE);
        CreationalContext<ProducedPayload> creationalContext = beanManager.createCreationalContext(bean);
        ProducedPayload created = bean.create(creationalContext);

        bean.destroy(created, creationalContext);

        assertSame(created, ProducerMethodLifecycleRecorder.lastDisposedPayload);
        assertNotNull(ProducerMethodLifecycleRecorder.lastDisposerDependency);
        assertTrue(ProducerMethodLifecycleRecorder.disposerInvocationCount.get() >= 1);
        assertTrue(ProducerMethodInputDependency.preDestroyCount.get() >= 1);
        assertTrue(ProducerMethodDisposerDependency.preDestroyCount.get() >= 1);
    }

    @Test
    @DisplayName("7.3.3 - Bean.create() for producer field accesses field value and returns produced contextual instance")
    void shouldCreateContextualInstanceFromProducerFieldBeanCreate() {
        ProducerFieldLifecycleRecorder.reset();
        Syringe syringe = newSyringe(ProducerFieldOwner.class);
        BeanManager beanManager = syringe.getBeanManager();
        Bean<ProducedFieldPayload> bean =
                resolveQualifiedBean(beanManager, ProducedFieldPayload.class, ProducedFieldPayloadLiteral.INSTANCE);
        CreationalContext<ProducedFieldPayload> creationalContext = beanManager.createCreationalContext(bean);

        ProducedFieldPayload created = bean.create(creationalContext);

        assertNotNull(created);
        assertSame(created, ProducerFieldOwner.payloadField);
    }

    @Test
    @DisplayName("7.3.3 - If @Dependent producer field contains null, Bean.create() returns null")
    void shouldReturnNullWhenDependentProducerFieldContainsNull() {
        ProducerFieldOwner.dependentNullField = null;
        Syringe syringe = newSyringe(ProducerFieldOwner.class);
        BeanManager beanManager = syringe.getBeanManager();
        Bean<String> bean = resolveQualifiedBean(beanManager, String.class, DependentNullFieldLiteral.INSTANCE);
        CreationalContext<String> creationalContext = beanManager.createCreationalContext(bean);

        String created = bean.create(creationalContext);

        assertNull(created);
    }

    @Test
    @DisplayName("7.3.3 - If non-@Dependent producer field contains null, Bean.create() throws IllegalProductException")
    void shouldThrowIllegalProductExceptionWhenNonDependentProducerFieldContainsNull() {
        ProducerFieldOwner.applicationNullField = null;
        Syringe syringe = newSyringe(ProducerFieldOwner.class);
        BeanManager beanManager = syringe.getBeanManager();
        Bean<String> bean = resolveQualifiedBean(beanManager, String.class, NonDependentNullFieldLiteral.INSTANCE);
        CreationalContext<String> creationalContext = beanManager.createCreationalContext(bean);

        assertThrows(IllegalProductException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                bean.create(creationalContext);
            }
        });
    }

    @Test
    @DisplayName("7.3.3 - Bean.destroy() for producer field with disposer invokes disposer and passes destroyed instance")
    void shouldInvokeDisposerForProducerFieldBeanDestroy() {
        ProducerFieldLifecycleRecorder.reset();
        Syringe syringe = newSyringe(ProducerFieldOwner.class);
        BeanManager beanManager = syringe.getBeanManager();
        Bean<ProducedFieldPayload> bean =
                resolveQualifiedBean(beanManager, ProducedFieldPayload.class, ProducedFieldPayloadLiteral.INSTANCE);
        CreationalContext<ProducedFieldPayload> creationalContext = beanManager.createCreationalContext(bean);
        ProducedFieldPayload created = bean.create(creationalContext);

        bean.destroy(created, creationalContext);

        assertTrue(ProducerFieldLifecycleRecorder.disposerInvocationCount.get() >= 1);
        assertSame(created, ProducerFieldLifecycleRecorder.lastDisposedPayload);
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

    @Dependent
    public static class ManagedBeanUnderTest {
        static final AtomicInteger beanPreDestroyCount = new AtomicInteger(0);

        final ConstructorDependency constructorDependency;

        @Inject
        FieldDependency fieldDependency;

        InitializerDependency initializerDependency;
        ExtraDependency extraDependencyFromInitializer;
        boolean initializerCalled;

        @Inject
        ManagedBeanUnderTest(ConstructorDependency constructorDependency) {
            this.constructorDependency = constructorDependency;
        }

        @Inject
        void init(InitializerDependency initializerDependency, ExtraDependency extraDependency) {
            this.initializerDependency = initializerDependency;
            this.extraDependencyFromInitializer = extraDependency;
            this.initializerCalled = true;
        }

        @PreDestroy
        void preDestroy() {
            beanPreDestroyCount.incrementAndGet();
        }

        static void resetLifecycleCounters() {
            beanPreDestroyCount.set(0);
            ConstructorDependency.preDestroyCount.set(0);
            FieldDependency.preDestroyCount.set(0);
            InitializerDependency.preDestroyCount.set(0);
            ExtraDependency.preDestroyCount.set(0);
        }
    }

    @Dependent
    public static class ConstructorDependency {
        static final AtomicInteger preDestroyCount = new AtomicInteger(0);

        @PreDestroy
        void preDestroy() {
            preDestroyCount.incrementAndGet();
        }
    }

    @Dependent
    public static class FieldDependency {
        static final AtomicInteger preDestroyCount = new AtomicInteger(0);

        @PreDestroy
        void preDestroy() {
            preDestroyCount.incrementAndGet();
        }
    }

    @Dependent
    public static class InitializerDependency {
        static final AtomicInteger preDestroyCount = new AtomicInteger(0);

        @PreDestroy
        void preDestroy() {
            preDestroyCount.incrementAndGet();
        }
    }

    @Dependent
    public static class ExtraDependency {
        static final AtomicInteger preDestroyCount = new AtomicInteger(0);

        @PreDestroy
        void preDestroy() {
            preDestroyCount.incrementAndGet();
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    public @interface ProducedPayloadQualifier {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    public @interface DependentNullProduct {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    public @interface NonDependentNullProduct {
    }

    public static final class ProducedPayloadLiteral extends AnnotationLiteral<ProducedPayloadQualifier>
            implements ProducedPayloadQualifier {
        static final ProducedPayloadLiteral INSTANCE = new ProducedPayloadLiteral();
    }

    public static final class DependentNullProductLiteral extends AnnotationLiteral<DependentNullProduct>
            implements DependentNullProduct {
        static final DependentNullProductLiteral INSTANCE = new DependentNullProductLiteral();
    }

    public static final class NonDependentNullProductLiteral extends AnnotationLiteral<NonDependentNullProduct>
            implements NonDependentNullProduct {
        static final NonDependentNullProductLiteral INSTANCE = new NonDependentNullProductLiteral();
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    public @interface ProducedFieldPayloadQualifier {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    public @interface DependentNullField {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
    public @interface NonDependentNullField {
    }

    public static final class ProducedFieldPayloadLiteral extends AnnotationLiteral<ProducedFieldPayloadQualifier>
            implements ProducedFieldPayloadQualifier {
        static final ProducedFieldPayloadLiteral INSTANCE = new ProducedFieldPayloadLiteral();
    }

    public static final class DependentNullFieldLiteral extends AnnotationLiteral<DependentNullField>
            implements DependentNullField {
        static final DependentNullFieldLiteral INSTANCE = new DependentNullFieldLiteral();
    }

    public static final class NonDependentNullFieldLiteral extends AnnotationLiteral<NonDependentNullField>
            implements NonDependentNullField {
        static final NonDependentNullFieldLiteral INSTANCE = new NonDependentNullFieldLiteral();
    }

    public static class ProducedPayload {
        private final String value;

        public ProducedPayload(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static class ProducedFieldPayload {
        private final String value;

        public ProducedFieldPayload(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    @Dependent
    public static class ProducerMethodInputDependency {
        static final AtomicInteger preDestroyCount = new AtomicInteger(0);

        final String id = java.util.UUID.randomUUID().toString();

        @PreDestroy
        void preDestroy() {
            preDestroyCount.incrementAndGet();
        }
    }

    @Dependent
    public static class ProducerMethodDisposerDependency {
        static final AtomicInteger preDestroyCount = new AtomicInteger(0);

        final String id = java.util.UUID.randomUUID().toString();

        @PreDestroy
        void preDestroy() {
            preDestroyCount.incrementAndGet();
        }
    }

    public static class ProducerMethodLifecycleRecorder {
        static final AtomicInteger produceInvocationCount = new AtomicInteger(0);
        static final AtomicInteger disposerInvocationCount = new AtomicInteger(0);
        static volatile ProducedPayload lastProducedPayload;
        static volatile ProducedPayload lastDisposedPayload;
        static volatile ProducerMethodInputDependency lastProducerInputDependency;
        static volatile ProducerMethodDisposerDependency lastDisposerDependency;

        static synchronized void reset() {
            produceInvocationCount.set(0);
            disposerInvocationCount.set(0);
            lastProducedPayload = null;
            lastDisposedPayload = null;
            lastProducerInputDependency = null;
            lastDisposerDependency = null;
            ProducerMethodInputDependency.preDestroyCount.set(0);
            ProducerMethodDisposerDependency.preDestroyCount.set(0);
        }
    }

    public static class ProducerFieldLifecycleRecorder {
        static final AtomicInteger disposerInvocationCount = new AtomicInteger(0);
        static volatile ProducedFieldPayload lastDisposedPayload;

        static synchronized void reset() {
            disposerInvocationCount.set(0);
            lastDisposedPayload = null;
            ProducerFieldOwner.payloadField = new ProducedFieldPayload("field-value");
            ProducerFieldOwner.dependentNullField = null;
            ProducerFieldOwner.applicationNullField = null;
        }
    }

    @Dependent
    public static class ProducerMethodOwner {
        @Produces
        @ProducedPayloadQualifier
        ProducedPayload producePayload(ProducerMethodInputDependency dependency) {
            ProducerMethodLifecycleRecorder.produceInvocationCount.incrementAndGet();
            ProducerMethodLifecycleRecorder.lastProducerInputDependency = dependency;
            ProducedPayload payload = new ProducedPayload(dependency.id);
            ProducerMethodLifecycleRecorder.lastProducedPayload = payload;
            return payload;
        }

        void disposePayload(@jakarta.enterprise.inject.Disposes @ProducedPayloadQualifier ProducedPayload payload,
                            ProducerMethodDisposerDependency dependency) {
            ProducerMethodLifecycleRecorder.disposerInvocationCount.incrementAndGet();
            ProducerMethodLifecycleRecorder.lastDisposedPayload = payload;
            ProducerMethodLifecycleRecorder.lastDisposerDependency = dependency;
        }

        @Produces
        @Dependent
        @DependentNullProduct
        String produceNullDependent() {
            return null;
        }

        @Produces
        @ApplicationScoped
        @NonDependentNullProduct
        String produceNullApplicationScoped() {
            return null;
        }
    }

    @Dependent
    public static class ProducerFieldOwner {
        @Produces
        @ProducedFieldPayloadQualifier
        static ProducedFieldPayload payloadField = new ProducedFieldPayload("field-value");

        @Produces
        @Dependent
        @DependentNullField
        static String dependentNullField = null;

        @Produces
        @ApplicationScoped
        @NonDependentNullField
        static String applicationNullField = null;

        void disposeProducedFieldPayload(@jakarta.enterprise.inject.Disposes @ProducedFieldPayloadQualifier ProducedFieldPayload payload) {
            ProducerFieldLifecycleRecorder.disposerInvocationCount.incrementAndGet();
            ProducerFieldLifecycleRecorder.lastDisposedPayload = payload;
        }
    }
}
