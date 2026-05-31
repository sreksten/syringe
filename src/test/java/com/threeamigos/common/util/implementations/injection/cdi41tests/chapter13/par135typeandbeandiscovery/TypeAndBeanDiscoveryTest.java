package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter13.par135typeandbeandiscovery;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.InterceptorInfo;
import jakarta.enterprise.inject.build.compatible.spi.ObserverInfo;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserver;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.annotation.Priority;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("13.5 - Type and bean discovery tests")
@Execution(ExecutionMode.SAME_THREAD)
public class TypeAndBeanDiscoveryTest {

    @Test
    @DisplayName("13.5 - Container automatically discovers managed beans in bean archives")
    public void shouldAutomaticallyDiscoverManagedBeans() {
        Syringe syringe = newSyringe(RootBean.class);
        syringe.setup();

        DiscoveredManagedBean bean = syringe.inject(DiscoveredManagedBean.class);
        assertEquals("managed", bean.value());
    }

    @Test
    @DisplayName("13.5 - Container discovers producer methods and producer fields on bean classes")
    public void shouldDiscoverProducerMethodsAndFields() {
        Syringe syringe = newSyringe(ProducerConsumer.class);
        syringe.setup();

        ProducerConsumer consumer = syringe.inject(ProducerConsumer.class);
        assertEquals("from-producer-method", consumer.textValue);
        assertEquals(Integer.valueOf(42), consumer.numberValue);
    }

    @Test
    @DisplayName("13.5 - Container searches disposer methods during discovery and validates invalid declarations")
    public void shouldValidateDisposerMethodsDuringDiscovery() {
        Syringe syringe = newErrorSyringe(InvalidDisposerOwner.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("13.5 - Container discovers observer methods on bean classes")
    public void shouldDiscoverObserverMethods() {
        DiscoveryRecorder.reset();
        Syringe syringe = newSyringe(RootBean.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        beanManager.getEvent().select(String.class).fire("hello");

        assertTrue(DiscoveryRecorder.observed);
        assertEquals("hello", DiscoveryRecorder.lastObservedPayload);
    }

    @Test
    @DisplayName("13.5.1 - Container discovers each class with bean-defining annotation in implicit bean archives")
    public void shouldDiscoverBeanDefiningAnnotatedTypesInImplicitArchive() {
        Syringe syringe = newImplicitSyringe(ImplicitAnnotatedBean.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        assertTrue(!beanManager.getBeans(ImplicitAnnotatedBean.class).isEmpty());
        assertTrue(beanManager.getBeans(ImplicitPlainClass.class).isEmpty());
    }

    @Test
    @DisplayName("13.5.1 - @Discovery adds scanned classes and @Enhancement alters discovered type metadata afterwards")
    public void shouldApplyDiscoveryThenEnhancementToAddedTypes() {
        DiscoveryRecorder.reset();
        Syringe syringe = newImplicitSyringe(RootBean.class);
        syringe.addBuildCompatibleExtension(DiscoveryAndEnhancementExtension.class.getName());

        syringe.setup();

        assertEquals("discovery", DiscoveryRecorder.phaseOrder[0]);
        assertEquals("enhancement", DiscoveryRecorder.phaseOrder[1]);
        assertTrue(DiscoveryRecorder.discoveredAddedClass);
        assertTrue(DiscoveryRecorder.enhancementMutatedMetadata);
    }

    @Test
    @DisplayName("13.5.2 - Container validates discovered type metadata and aborts when a discovered type has definition errors")
    public void shouldValidateDiscoveredTypeMetadataAndFailOnDefinitionError() {
        Syringe syringe = newErrorSyringe(InvalidDisposerOwner.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("13.5.2 - Container determines enabled alternatives and interceptors during bean discovery")
    public void shouldDetermineEnabledAlternativesAndInterceptors() {
        DiscoveryRecorder.reset();
        Syringe syringe = newSyringe(AlternativeConsumer.class);
        syringe.setup();

        AlternativeConsumer consumer = syringe.inject(AlternativeConsumer.class);
        assertEquals("alternative", consumer.serviceId());

        InterceptedTarget target = syringe.inject(InterceptedTarget.class);
        assertEquals("ok", target.call());
        assertTrue(DiscoveryRecorder.interceptorInvocations > 0);
    }

    @Test
    @DisplayName("13.5.2 - Container registers Bean/Interceptor/Observer and runs @Registration before and after @Synthesis for synthetic components")
    public void shouldRunRegistrationForDiscoveredAndSyntheticComponents() {
        DiscoveryRecorder.reset();
        Syringe syringe = newImplicitSyringe(RootBean.class);
        syringe.addBuildCompatibleExtension(RegistrationAndSynthesisExtension.class.getName());
        syringe.setup();

        assertTrue(DiscoveryRecorder.registrationBeanCalls > 0);
        assertTrue(DiscoveryRecorder.registrationObserverCalls > 0);

        BeanManager beanManager = syringe.getBeanManager();
        SyntheticContractImpl synthetic = syringe.inject(SyntheticContractImpl.class);
        assertEquals("synthetic", synthetic.id());
        beanManager.getEvent().select(SyntheticEvent.class).fire(new SyntheticEvent("evt"));
        assertTrue(DiscoveryRecorder.syntheticObserverTriggered);

        assertTrue(DiscoveryRecorder.syntheticBeanSeenInRegistration);
        assertTrue(DiscoveryRecorder.syntheticObserverSeenInRegistration);
    }

    private Syringe newSyringe(Class<?> rootClass) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), rootClass);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(InvalidDisposerOwner.class);
        return syringe;
    }

    private Syringe newErrorSyringe(Class<?> rootClass) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), rootClass);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    private Syringe newImplicitSyringe(Class<?> rootClass) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), rootClass);
        syringe.forceBeanArchiveMode(BeanArchiveMode.IMPLICIT);
        syringe.exclude(InvalidDisposerOwner.class);
        return syringe;
    }

    @Dependent
    public static class RootBean {
    }

    @Dependent
    public static class DiscoveredManagedBean {
        String value() {
            return "managed";
        }
    }

    @Dependent
    public static class ProducerOwner {
        @Produces
        public String produceText() {
            return "from-producer-method";
        }

        @Produces
        public Integer producedNumber = Integer.valueOf(42);

        @Produces
        @ApplicationScoped
        public DisposableResource produceDisposable() {
            return new DisposableResource("res");
        }

        public void dispose(@Disposes DisposableResource resource) {
        }
    }

    @Dependent
    public static class InvalidDisposerOwner {
        @Produces
        public DisposableResource produceDisposable() {
            return new DisposableResource("bad");
        }

        public void invalidDispose(
                @Disposes DisposableResource first,
                @Disposes DisposableResource second) {
        }
    }

    @Dependent
    public static class ProducerConsumer {
        @Inject
        String textValue;

        @Inject
        Integer numberValue;
    }

    public static class DisposableResource {
        private final String id;

        DisposableResource(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    @Dependent
    public static class EventObserverBean {
        public void onString(@Observes String payload) {
            DiscoveryRecorder.observed = true;
            DiscoveryRecorder.lastObservedPayload = payload;
        }
    }

    @Dependent
    public static class ImplicitAnnotatedBean {
        String value() {
            return "implicit";
        }
    }

    public static class ImplicitPlainClass {
        String value() {
            return "plain";
        }
    }

    public static class AddedByDiscoveryBean {
        String value() {
            return "added-by-bce";
        }
    }

    public static class DiscoveryAndEnhancementExtension implements BuildCompatibleExtension {
        @Discovery
        public void discover(ScannedClasses scannedClasses) {
            DiscoveryRecorder.phaseOrder[0] = "discovery";
            scannedClasses.add(AddedByDiscoveryBean.class.getName());
            DiscoveryRecorder.discoveredAddedClass = true;
        }

        @Enhancement(types = AddedByDiscoveryBean.class, withSubtypes = false)
        public void enhance(ClassConfig classConfig) {
            DiscoveryRecorder.phaseOrder[1] = "enhancement";
            classConfig.addAnnotation(Dependent.class);
            DiscoveryRecorder.enhancementMutatedMetadata = true;
        }
    }

    public interface ServiceContract {
        String id();
    }

    @Dependent
    public static class DefaultService implements ServiceContract {
        @Override
        public String id() {
            return "default";
        }
    }

    @Alternative
    @Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION + 10)
    @Dependent
    public static class AlternativeService implements ServiceContract {
        @Override
        public String id() {
            return "alternative";
        }
    }

    @Dependent
    public static class AlternativeConsumer {
        @Inject
        ServiceContract service;

        String serviceId() {
            return service.id();
        }
    }

    @InterceptorBinding
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public @interface Tracked {
    }

    @Tracked
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION + 1)
    public static class TrackingInterceptor {
        @AroundInvoke
        public Object around(InvocationContext context) throws Exception {
            DiscoveryRecorder.interceptorInvocations++;
            return context.proceed();
        }
    }

    @Tracked
    @Dependent
    public static class InterceptedTarget {
        public String call() {
            return "ok";
        }
    }

    public static class StandardEvent {
        final String value;

        public StandardEvent(String value) {
            this.value = value;
        }
    }

    @Dependent
    public static class StandardObserver {
        void observe(@Observes StandardEvent event) {
            DiscoveryRecorder.standardObserverTriggered = true;
        }
    }

    public interface SyntheticContract {
        String id();
    }

    public static class SyntheticContractImpl implements SyntheticContract {
        @Override
        public String id() {
            return "synthetic";
        }
    }

    @jakarta.inject.Qualifier
    @Retention(RUNTIME)
    @Target({TYPE, METHOD, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.PARAMETER})
    public @interface SyntheticMarker {
    }

    public static final class SyntheticMarkerLiteral extends AnnotationLiteral<SyntheticMarker> implements SyntheticMarker {
        static final SyntheticMarkerLiteral INSTANCE = new SyntheticMarkerLiteral();
    }

    public static class SyntheticEvent {
        final String payload;

        public SyntheticEvent(String payload) {
            this.payload = payload;
        }
    }

    public static class SyntheticContractCreator implements SyntheticBeanCreator<SyntheticContractImpl> {
        @Override
        public SyntheticContractImpl create(jakarta.enterprise.inject.Instance<Object> lookup, Parameters params) {
            return new SyntheticContractImpl();
        }
    }

    public static class SyntheticEventObserver implements SyntheticObserver<SyntheticEvent> {
        @Override
        public void observe(EventContext<SyntheticEvent> event, Parameters params) {
            DiscoveryRecorder.syntheticObserverTriggered = true;
        }
    }

    public static class RegistrationAndSynthesisExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void onBean(BeanInfo beanInfo) {
            if (beanInfo == null) {
                return;
            }
            DiscoveryRecorder.registrationBeanCalls++;
        }

        @Registration(types = Object.class)
        public void onInterceptor(InterceptorInfo interceptorInfo) {
            if (interceptorInfo != null) {
                DiscoveryRecorder.registrationInterceptorCalls++;
            }
        }

        @Registration(types = Object.class)
        public void onObserver(ObserverInfo observerInfo) {
            if (observerInfo == null) {
                return;
            }
            DiscoveryRecorder.registrationObserverCalls++;
        }

        @Registration(types = SyntheticContractImpl.class)
        public void onSyntheticBean(BeanInfo beanInfo) {
            if (beanInfo != null && SyntheticContractImpl.class.getName().equals(beanInfo.declaringClass().name())) {
                DiscoveryRecorder.syntheticBeanSeenInRegistration = true;
            }
        }

        @Registration(types = SyntheticEvent.class)
        public void onSyntheticObserver(ObserverInfo observerInfo) {
            if (observerInfo != null) {
                DiscoveryRecorder.syntheticObserverSeenInRegistration = true;
            }
        }

        @Synthesis
        public void synthesis(SyntheticComponents syntheticComponents) {
            syntheticComponents.addBean(SyntheticContractImpl.class)
                    .type(SyntheticContract.class)
                    .type(SyntheticContractImpl.class)
                    .scope(Dependent.class)
                    .createWith(SyntheticContractCreator.class);
            syntheticComponents.addObserver(SyntheticEvent.class)
                    .observeWith(SyntheticEventObserver.class);
        }
    }

    static class DiscoveryRecorder {
        static boolean observed;
        static String lastObservedPayload;
        static final String[] phaseOrder = new String[2];
        static boolean discoveredAddedClass;
        static boolean enhancementMutatedMetadata;
        static int interceptorInvocations;
        static boolean standardObserverTriggered;
        static boolean syntheticObserverTriggered;
        static int registrationBeanCalls;
        static int registrationInterceptorCalls;
        static int registrationObserverCalls;
        static boolean syntheticBeanSeenInRegistration;
        static boolean syntheticObserverSeenInRegistration;

        static void reset() {
            observed = false;
            lastObservedPayload = null;
            phaseOrder[0] = null;
            phaseOrder[1] = null;
            discoveredAddedClass = false;
            enhancementMutatedMetadata = false;
            interceptorInvocations = 0;
            standardObserverTriggered = false;
            syntheticObserverTriggered = false;
            registrationBeanCalls = 0;
            registrationInterceptorCalls = 0;
            registrationObserverCalls = 0;
            syntheticBeanSeenInRegistration = false;
            syntheticObserverSeenInRegistration = false;
        }
    }
}
