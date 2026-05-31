package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter24;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.AfterTypeDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.enterprise.inject.spi.BeforeShutdown;
import jakarta.enterprise.inject.spi.ProcessBeanAttributes;
import jakarta.enterprise.inject.spi.ProcessObserverMethod;
import jakarta.enterprise.inject.spi.ProcessProducer;
import jakarta.enterprise.inject.spi.ProcessProducerField;
import jakarta.enterprise.inject.spi.ProcessProducerMethod;
import jakarta.enterprise.inject.spi.ProcessSyntheticAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.ProcessInjectionTarget;
import jakarta.enterprise.inject.Disposes;
import jakarta.inject.Inject;
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
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("24 - Packaging and Deployment in CDI full")
@Execution(ExecutionMode.SAME_THREAD)
public class PackagingAndDeploymentInCDIFullTest {

    @Test
    @DisplayName("24 - Container performs discovery and lifecycle events during application startup")
    void shouldPerformDiscoveryAndLifecycleDuringStartup() {
        Chapter24Recorder.reset();
        Syringe syringe = newIsolatedSyringe(Chapter24LifecycleBean.class);
        syringe.addExtension(Chapter24LifecycleExtension.class.getName());

        syringe.setup();

        assertTrue(Chapter24Recorder.events.contains("BeforeBeanDiscovery"));
        assertTrue(Chapter24Recorder.events.contains("ProcessAnnotatedType"));
        assertTrue(Chapter24Recorder.events.contains("AfterTypeDiscovery"));
        assertTrue(Chapter24Recorder.events.contains("AfterBeanDiscovery"));
        assertTrue(Chapter24Recorder.events.contains("AfterDeploymentValidation"));
        assertNotNull(Chapter24Recorder.beanManager);

        int bbd = Chapter24Recorder.events.indexOf("BeforeBeanDiscovery");
        int pat = Chapter24Recorder.events.indexOf("ProcessAnnotatedType");
        int atd = Chapter24Recorder.events.indexOf("AfterTypeDiscovery");
        int abd = Chapter24Recorder.events.indexOf("AfterBeanDiscovery");
        int adv = Chapter24Recorder.events.indexOf("AfterDeploymentValidation");
        assertTrue(bbd >= 0 && pat > bbd && atd > pat && abd > atd && adv > abd);
    }

    @Test
    @DisplayName("24 - Definition errors detected during startup abort deployment")
    void shouldAbortStartupWhenDefinitionErrorIsRegistered() {
        Syringe syringe = newIsolatedSyringe(Chapter24LifecycleBean.class);
        syringe.addExtension(Chapter24DefinitionErrorExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("24 - Deployment problems detected during startup abort deployment")
    void shouldAbortStartupWhenDeploymentProblemIsRegistered() {
        Syringe syringe = newIsolatedSyringe(Chapter24LifecycleBean.class);
        syringe.addExtension(Chapter24DeploymentProblemExtension.class.getName());

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("24 - AfterTypeDiscovery exposes enabled alternatives, interceptors and decorators in ascending priority")
    void shouldExposeEnabledComponentsAndOrderingDuringAfterTypeDiscovery() {
        Chapter24OrderingRecorder.reset();
        Syringe syringe = newIsolatedSyringe(
                Chapter24AlternativeLow.class,
                Chapter24AlternativeHigh.class,
                Chapter24InterceptorLow.class,
                Chapter24InterceptorHigh.class,
                Chapter24DecoratorLow.class,
                Chapter24DecoratorHigh.class,
                Chapter24DecoratedServiceProducer.class
        );
        syringe.addExtension(Chapter24OrderingCaptureExtension.class.getName());
        syringe.setup();

        assertTrue(Chapter24OrderingRecorder.alternatives.contains(Chapter24AlternativeLow.class));
        assertTrue(Chapter24OrderingRecorder.alternatives.contains(Chapter24AlternativeHigh.class));
        assertTrue(Chapter24OrderingRecorder.interceptors.contains(Chapter24InterceptorLow.class));
        assertTrue(Chapter24OrderingRecorder.interceptors.contains(Chapter24InterceptorHigh.class));
        assertTrue(Chapter24OrderingRecorder.decorators.contains(Chapter24DecoratorLow.class));
        assertTrue(Chapter24OrderingRecorder.decorators.contains(Chapter24DecoratorHigh.class));

        assertTrue(
                Chapter24OrderingRecorder.alternatives.indexOf(Chapter24AlternativeLow.class) <
                        Chapter24OrderingRecorder.alternatives.indexOf(Chapter24AlternativeHigh.class)
        );
        assertTrue(
                Chapter24OrderingRecorder.interceptors.indexOf(Chapter24InterceptorLow.class) <
                        Chapter24OrderingRecorder.interceptors.indexOf(Chapter24InterceptorHigh.class)
        );
        assertTrue(
                Chapter24OrderingRecorder.decorators.indexOf(Chapter24DecoratorLow.class) <
                        Chapter24OrderingRecorder.decorators.indexOf(Chapter24DecoratorHigh.class)
        );
    }

    @Test
    @DisplayName("24 - Portable extension may register additional beans programmatically after discovery")
    void shouldAllowPortableExtensionToRegisterAdditionalBeansAfterDiscovery() {
        Syringe syringe = newIsolatedSyringe(Chapter24ProgrammaticConsumer.class);
        syringe.addExtension(Chapter24ProgrammaticBeanExtension.class.getName());
        syringe.setup();

        Chapter24ProgrammaticConsumer consumer = syringe.inject(Chapter24ProgrammaticConsumer.class);
        assertEquals("programmatic", consumer.value());
    }

    @Test
    @DisplayName("24.1 - Explicit bean archive mode deploys bean classes from the archive")
    void shouldDeployBeanClassesInExplicitBeanArchiveMode() {
        Syringe syringe = newIsolatedSyringeWithMode(BeanArchiveMode.EXPLICIT, Chapter24_1PlainClass.class);
        syringe.setup();

        assertTrue(hasBean(syringe, Chapter24_1PlainClass.class));
    }

    @Test
    @DisplayName("24.1 - Implicit bean archive mode deploys only classes with bean-defining annotations")
    void shouldDeployOnlyBeanDefiningClassesInImplicitBeanArchiveMode() {
        Syringe syringe = newIsolatedSyringeWithMode(
                BeanArchiveMode.IMPLICIT,
                Chapter24_1BeanDefiningClass.class,
                Chapter24_1PlainClass.class
        );
        syringe.setup();

        assertTrue(hasBean(syringe, Chapter24_1BeanDefiningClass.class));
        assertFalse(hasBean(syringe, Chapter24_1PlainClass.class));
    }

    @Test
    @DisplayName("24.1 - Bean discovery mode none makes the archive non-bean-archive for bean deployment")
    void shouldNotDeployBeansWhenDiscoveryModeIsNone() {
        Syringe syringe = newIsolatedSyringeWithMode(BeanArchiveMode.NONE, Chapter24_1BeanDefiningClass.class);
        syringe.setup();

        assertFalse(hasBean(syringe, Chapter24_1BeanDefiningClass.class));
    }

    @Test
    @DisplayName("24.1 - Extension may be deployed in archive that is not a bean archive")
    void shouldAllowExtensionInNonBeanArchive() {
        Chapter24_1ExtensionRecorder.reset();

        Syringe syringe = newIsolatedSyringeWithMode(BeanArchiveMode.NONE, Chapter24_1PlainClass.class);
        syringe.addExtension(Chapter24_1PortableExtension.class.getName());
        syringe.setup();

        assertTrue(Chapter24_1ExtensionRecorder.beforeBeanDiscoveryObserved);
        assertFalse(hasBean(syringe, Chapter24_1PlainClass.class));
    }

    @Test
    @DisplayName("24.1 - If a bean class is discovered again after already found, non-portable behavior is detected")
    void shouldFailWhenBeanClassIsDiscoveredTwice() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), Chapter24_1DuplicateProbe.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.initialize();
        syringe.addDiscoveredClass(Chapter24_1DuplicateProbe.class);

        assertThrows(NonPortableBehaviourException.class, () ->
                syringe.addDiscoveredClass(Chapter24_1DuplicateProbe.class));
    }

    @Test
    @DisplayName("24.2 - Container startup lifecycle executes in spec order and requests are served only after validation")
    void shouldExecuteStartupLifecycleInOrderAndServeRequestsAfterValidation() {
        Chapter24_2LifecycleRecorder.reset();

        Syringe syringe = newIsolatedSyringe(Chapter24_2StartupBean.class, Chapter24_2StartupConsumer.class);
        syringe.addExtension(Chapter24_2LifecycleExtension.class.getName());
        syringe.setup();

        assertTrue(Chapter24_2LifecycleRecorder.events.contains("BeforeBeanDiscovery"));
        assertTrue(Chapter24_2LifecycleRecorder.events.contains("ProcessAnnotatedType"));
        assertTrue(Chapter24_2LifecycleRecorder.events.contains("AfterTypeDiscovery"));
        assertTrue(Chapter24_2LifecycleRecorder.events.contains("ProcessBean"));
        assertTrue(Chapter24_2LifecycleRecorder.events.contains("AfterBeanDiscovery"));
        assertTrue(Chapter24_2LifecycleRecorder.events.contains("AfterDeploymentValidation"));

        assertTrue(indexOf("BeforeBeanDiscovery") < indexOf("ProcessAnnotatedType"));
        assertTrue(indexOf("ProcessAnnotatedType") < indexOf("AfterTypeDiscovery"));
        assertTrue(indexOf("AfterTypeDiscovery") < indexOf("ProcessBean"));
        assertTrue(indexOf("ProcessBean") < indexOf("AfterBeanDiscovery"));
        assertTrue(indexOf("AfterBeanDiscovery") < indexOf("AfterDeploymentValidation"));

        Chapter24_2StartupConsumer consumer = syringe.inject(Chapter24_2StartupConsumer.class);
        assertEquals("ready", consumer.value());
        assertTrue(Chapter24_2LifecycleRecorder.events.contains("AfterDeploymentValidation"));
    }

    @Test
    @DisplayName("24.2 - Container instantiates a single instance of each discovered Extension service provider")
    void shouldInstantiateSingleExtensionInstancePerServiceProvider() {
        Chapter24_2LifecycleRecorder.reset();

        Syringe syringe = newIsolatedSyringe(Chapter24_2StartupBean.class);
        syringe.addExtension(Chapter24_2LifecycleExtension.class.getName());
        syringe.setup();

        assertEquals(1, Chapter24_2LifecycleRecorder.extensionInstanceIds.size());
    }

    @Test
    @DisplayName("24.2 - Container aborts initialization when AfterBeanDiscovery observer registers definition error")
    void shouldAbortWhenAfterBeanDiscoveryRegistersDefinitionError() {
        Syringe syringe = newIsolatedSyringe(Chapter24_2StartupBean.class);
        syringe.addExtension(Chapter24_2DefinitionErrorExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("24.2 - Container aborts initialization when automatic dependency validation finds deployment problems")
    void shouldAbortWhenAutomaticValidationDetectsDeploymentProblems() {
        Syringe syringe = newIsolatedSyringe(Chapter24_2UnsatisfiedConsumer.class);
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("24.2 - Container aborts initialization when AfterDeploymentValidation observer registers deployment problem")
    void shouldAbortWhenAfterDeploymentValidationRegistersDeploymentProblem() {
        Syringe syringe = newIsolatedSyringe(Chapter24_2StartupBean.class);
        syringe.addExtension(Chapter24_2DeploymentProblemExtension.class.getName());

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("24.2 - On application stop, container destroys contexts before firing BeforeShutdown")
    void shouldDestroyContextsBeforeBeforeShutdownOnStop() {
        Chapter24_2ShutdownRecorder.reset();

        Syringe syringe = newIsolatedSyringe(Chapter24_2StartupBean.class);
        syringe.addExtension(Chapter24_2ShutdownExtension.class.getName());
        syringe.setup();
        syringe.shutdown();

        assertTrue(Chapter24_2ShutdownRecorder.events.contains("before-shutdown"));
        assertTrue(Chapter24_2ShutdownRecorder.contextsAlreadyDestroyedAtBeforeShutdown);
    }

    @Test
    @DisplayName("24.2 - BeforeShutdown event is fired when application is stopped")
    void shouldFireBeforeShutdownOnStop() {
        Chapter24_2ShutdownRecorder.reset();

        Syringe syringe = newIsolatedSyringe(Chapter24_2StartupBean.class);
        syringe.addExtension(Chapter24_2ShutdownExtension.class.getName());
        syringe.setup();
        syringe.shutdown();

        assertTrue(Chapter24_2ShutdownRecorder.events.contains("before-shutdown"));
    }

    @Test
    @DisplayName("24.4.1 - Explicit bean archive type discovery includes class, interface and enum, excluding annotation types")
    void shouldDiscoverClassInterfaceAndEnumInExplicitArchive() {
        Chapter24_4_1TypeDiscoveryRecorder.reset();

        Syringe syringe = newIsolatedSyringeWithMode(
                BeanArchiveMode.EXPLICIT,
                Chapter24_4_1ExplicitClass.class,
                Chapter24_4_1ExplicitInterface.class,
                Chapter24_4_1ExplicitEnum.class,
                Chapter24_4_1ExplicitAnnotation.class
        );
        syringe.addExtension(Chapter24_4_1TypeDiscoveryExtension.class.getName());
        syringe.setup();

        assertTrue(Chapter24_4_1TypeDiscoveryRecorder.discoveredTypes.contains(Chapter24_4_1ExplicitClass.class));
        assertTrue(Chapter24_4_1TypeDiscoveryRecorder.discoveredTypes.contains(Chapter24_4_1ExplicitInterface.class));
        assertTrue(Chapter24_4_1TypeDiscoveryRecorder.discoveredTypes.contains(Chapter24_4_1ExplicitEnum.class));
        assertFalse(Chapter24_4_1TypeDiscoveryRecorder.discoveredTypes.contains(Chapter24_4_1ExplicitAnnotation.class));
    }

    @Test
    @DisplayName("24.4.1 - Implicit bean archive type discovery includes only classes with bean defining annotations")
    void shouldDiscoverOnlyBeanDefiningClassesInImplicitArchive() {
        Chapter24_4_1TypeDiscoveryRecorder.reset();

        Syringe syringe = newIsolatedSyringeWithMode(
                BeanArchiveMode.IMPLICIT,
                Chapter24_4_1ImplicitBean.class,
                Chapter24_4_1ImplicitPlain.class
        );
        syringe.addExtension(Chapter24_4_1TypeDiscoveryExtension.class.getName());
        syringe.setup();

        assertTrue(Chapter24_4_1TypeDiscoveryRecorder.discoveredTypes.contains(Chapter24_4_1ImplicitBean.class));
        assertFalse(Chapter24_4_1TypeDiscoveryRecorder.discoveredTypes.contains(Chapter24_4_1ImplicitPlain.class));
    }

    @Test
    @DisplayName("24.4.1 - Exclude filter prevents discovered type from being part of type discovery")
    void shouldRespectExcludeFilterDuringTypeDiscovery() {
        Chapter24_4_1TypeDiscoveryRecorder.reset();

        Syringe syringe = newIsolatedSyringeWithMode(
                BeanArchiveMode.EXPLICIT,
                Chapter24_4_1ExcludedClass.class
        );
        syringe.exclude(Chapter24_4_1ExcludedClass.class);
        syringe.addExtension(Chapter24_4_1TypeDiscoveryExtension.class.getName());
        syringe.setup();

        assertFalse(Chapter24_4_1TypeDiscoveryRecorder.discoveredTypes.contains(Chapter24_4_1ExcludedClass.class));
    }

    @Test
    @DisplayName("24.4.1 - addAnnotatedType in BeforeBeanDiscovery and AfterTypeDiscovery contributes synthetic discovered types")
    void shouldFireProcessSyntheticAnnotatedTypeForAddedAnnotatedTypes() {
        Chapter24_4_1TypeDiscoveryRecorder.reset();

        Syringe syringe = newIsolatedSyringe(Chapter24_4_1ExplicitClass.class);
        syringe.addExtension(Chapter24_4_1SyntheticTypeExtension.class.getName());
        syringe.setup();

        assertTrue(Chapter24_4_1TypeDiscoveryRecorder.syntheticTypes.contains(Chapter24_4_1SyntheticFromBbd.class));
        assertTrue(Chapter24_4_1TypeDiscoveryRecorder.syntheticTypes.contains(Chapter24_4_1SyntheticFromAtd.class));
        assertTrue(Chapter24_4_1TypeDiscoveryRecorder.syntheticSourcesCaptured >= 2);
    }

    @Test
    @DisplayName("24.4.1 - Managed bean discovery scans for producer methods, producer fields, disposers and observer methods")
    void shouldDiscoverManagedBeanMembersForProductionDisposalAndObservation() {
        Chapter24_4_1ManagedDiscoveryRecorder.reset();

        Syringe syringe = newIsolatedSyringe(
                Chapter24_4_1ManagedBean.class,
                Chapter24_4_1ManagedConsumer.class
        );
        syringe.addExtension(Chapter24_4_1ManagedDiscoveryExtension.class.getName());
        syringe.setup();

        Chapter24_4_1ManagedConsumer consumer = syringe.inject(Chapter24_4_1ManagedConsumer.class);
        assertEquals("method", consumer.methodValue());
        assertEquals("field", consumer.fieldValue());

        syringe.getBeanManager().getEvent().select(Chapter24_4_1ObservedEvent.class).fire(new Chapter24_4_1ObservedEvent());
        assertTrue(Chapter24_4_1ManagedDiscoveryRecorder.observerNotified);
        syringe.shutdown();

        assertTrue(Chapter24_4_1ManagedDiscoveryRecorder.processProducerMethodSeen);
        assertTrue(Chapter24_4_1ManagedDiscoveryRecorder.processProducerFieldSeen);
        assertTrue(Chapter24_4_1ManagedDiscoveryRecorder.processObserverMethodSeen);
    }

    @Test
    @DisplayName("24.4.2 - Active exact-name exclude filter excludes the matching type from discovery")
    void shouldExcludeTypeByExactNameWhenFilterIsActive() {
        Syringe syringe = newIsolatedSyringeWithMode(BeanArchiveMode.EXPLICIT, Chapter24_4_2ExactExcluded.class);
        addScanExcludeBeansXml(syringe, "all",
                "<exclude name=\"" + Chapter24_4_2ExactExcluded.class.getName() + "\"/>");
        syringe.setup();

        assertTrue(isVetoed(syringe, Chapter24_4_2ExactExcluded.class));
    }

    @Test
    @DisplayName("24.4.2 - Active package .* exclude filter excludes types in the package")
    void shouldExcludeTypeByPackageSingleLevelPattern() {
        Syringe syringe = newIsolatedSyringeWithMode(BeanArchiveMode.EXPLICIT, Chapter24_4_2PkgExcluded.class);
        addScanExcludeBeansXml(syringe, "all",
                "<exclude name=\"" + getClass().getPackage().getName() + ".*\"/>");
        syringe.setup();

        assertTrue(isVetoed(syringe, Chapter24_4_2PkgExcluded.class));
    }

    @Test
    @DisplayName("24.4.2 - Active package .** exclude filter excludes types in package hierarchy")
    void shouldExcludeTypeByPackageRecursivePattern() {
        Syringe syringe = newIsolatedSyringeWithMode(BeanArchiveMode.EXPLICIT, Chapter24_4_2RecursiveExcluded.class);
        addScanExcludeBeansXml(syringe, "all",
                "<exclude name=\"" + getClass().getPackage().getName() + ".**\"/>");
        syringe.setup();

        assertTrue(isVetoed(syringe, Chapter24_4_2RecursiveExcluded.class));
    }

    @Test
    @DisplayName("24.4.2 - Filter with if-class-available is inactive when class is not available")
    void shouldDeactivateFilterWhenIfClassAvailableConditionFails() {
        Syringe syringe = newIsolatedSyringeWithMode(BeanArchiveMode.EXPLICIT, Chapter24_4_2Conditional.class);
        addScanExcludeBeansXml(syringe, "all",
                "<exclude name=\"" + Chapter24_4_2Conditional.class.getName() + "\">" +
                        "<if-class-available name=\"com.example.DoesNotExist\"/>" +
                        "</exclude>");
        syringe.setup();

        assertTrue(hasBean(syringe, Chapter24_4_2Conditional.class));
    }

    @Test
    @DisplayName("24.4.2 - Filter with if-class-not-available is inactive when class is available")
    void shouldDeactivateFilterWhenIfClassNotAvailableConditionFails() {
        Syringe syringe = newIsolatedSyringeWithMode(BeanArchiveMode.EXPLICIT, Chapter24_4_2Conditional.class);
        addScanExcludeBeansXml(syringe, "all",
                "<exclude name=\"" + Chapter24_4_2Conditional.class.getName() + "\">" +
                        "<if-class-not-available name=\"jakarta.enterprise.inject.Model\"/>" +
                        "</exclude>");
        syringe.setup();

        assertTrue(hasBean(syringe, Chapter24_4_2Conditional.class));
    }

    @Test
    @DisplayName("24.4.2 - Filter with if-system-property(name) is inactive when property is absent")
    void shouldDeactivateFilterWhenIfSystemPropertyNameIsMissing() {
        String key = "chapter24.exclude.missing";
        String previous = System.getProperty(key);
        System.clearProperty(key);
        try {
            Syringe syringe = newIsolatedSyringeWithMode(BeanArchiveMode.EXPLICIT, Chapter24_4_2Conditional.class);
            addScanExcludeBeansXml(syringe, "all",
                    "<exclude name=\"" + Chapter24_4_2Conditional.class.getName() + "\">" +
                            "<if-system-property name=\"" + key + "\"/>" +
                            "</exclude>");
            syringe.setup();

            assertTrue(hasBean(syringe, Chapter24_4_2Conditional.class));
        } finally {
            restoreProperty(key, previous);
        }
    }

    @Test
    @DisplayName("24.4.2 - Filter with if-system-property(name,value) is inactive when value does not match")
    void shouldDeactivateFilterWhenIfSystemPropertyValueMismatches() {
        String key = "chapter24.exclude.value";
        String previous = System.getProperty(key);
        System.setProperty(key, "high");
        try {
            Syringe syringe = newIsolatedSyringeWithMode(BeanArchiveMode.EXPLICIT, Chapter24_4_2Conditional.class);
            addScanExcludeBeansXml(syringe, "all",
                    "<exclude name=\"" + Chapter24_4_2Conditional.class.getName() + "\">" +
                            "<if-system-property name=\"" + key + "\" value=\"low\"/>" +
                            "</exclude>");
            syringe.setup();

            assertTrue(hasBean(syringe, Chapter24_4_2Conditional.class));
        } finally {
            restoreProperty(key, previous);
        }
    }

    @Test
    @DisplayName("24.4.2 - Filter with if-system-property(name) is active when property exists with any value")
    void shouldActivateFilterWhenIfSystemPropertyNameExists() {
        String key = "chapter24.exclude.any";
        String previous = System.getProperty(key);
        System.setProperty(key, "present");
        try {
            Syringe syringe = newIsolatedSyringeWithMode(BeanArchiveMode.EXPLICIT, Chapter24_4_2Conditional.class);
            addScanExcludeBeansXml(syringe, "all",
                    "<exclude name=\"" + Chapter24_4_2Conditional.class.getName() + "\">" +
                            "<if-system-property name=\"" + key + "\"/>" +
                            "</exclude>");
            syringe.setup();

            assertTrue(isVetoed(syringe, Chapter24_4_2Conditional.class));
        } finally {
            restoreProperty(key, previous);
        }
    }

    @Test
    @DisplayName("24.4.2 - Filter with multiple conditions is active only when all conditions are satisfied")
    void shouldApplyExcludeOnlyWhenAllConditionsAreSatisfied() {
        String key = "chapter24.exclude.multi";
        String previous = System.getProperty(key);
        System.setProperty(key, "on");
        try {
            Syringe syringe = newIsolatedSyringeWithMode(BeanArchiveMode.EXPLICIT, Chapter24_4_2Conditional.class);
            addScanExcludeBeansXml(syringe, "all",
                    "<exclude name=\"" + Chapter24_4_2Conditional.class.getName() + "\">" +
                            "<if-class-available name=\"jakarta.enterprise.inject.Model\"/>" +
                            "<if-system-property name=\"" + key + "\"/>" +
                            "</exclude>");
            syringe.setup();

            assertTrue(isVetoed(syringe, Chapter24_4_2Conditional.class));
        } finally {
            restoreProperty(key, previous);
        }
    }

    @Test
    @DisplayName("24.4.3 - Trimmed explicit archive removes types without bean-defining annotation or scope")
    void shouldRemoveNonBeanDefiningTypesInTrimmedExplicitArchive() {
        Chapter24_4_3TypeDiscoveryRecorder.reset();

        Syringe syringe = newIsolatedSyringeWithMode(
                BeanArchiveMode.TRIMMED,
                Chapter24_4_3ScopedBean.class,
                Chapter24_4_3DependentBean.class,
                Chapter24_4_3PlainType.class
        );
        syringe.addExtension(Chapter24_4_3TypeDiscoveryExtension.class.getName());
        syringe.setup();

        assertTrue(Chapter24_4_3TypeDiscoveryRecorder.discoveredTypes.contains(Chapter24_4_3ScopedBean.class));
        assertTrue(Chapter24_4_3TypeDiscoveryRecorder.discoveredTypes.contains(Chapter24_4_3DependentBean.class));
        assertFalse(Chapter24_4_3TypeDiscoveryRecorder.discoveredTypes.contains(Chapter24_4_3PlainType.class));
        assertTrue(hasBean(syringe, Chapter24_4_3ScopedBean.class));
        assertTrue(hasBean(syringe, Chapter24_4_3DependentBean.class));
        assertFalse(hasBean(syringe, Chapter24_4_3PlainType.class));
    }

    @Test
    @DisplayName("24.4.3 - Non-trimmed explicit archive keeps plain discovered types")
    void shouldKeepPlainTypesInNonTrimmedExplicitArchive() {
        Chapter24_4_3TypeDiscoveryRecorder.reset();

        Syringe syringe = newIsolatedSyringeWithMode(
                BeanArchiveMode.EXPLICIT,
                Chapter24_4_3PlainType.class
        );
        syringe.addExtension(Chapter24_4_3TypeDiscoveryExtension.class.getName());
        syringe.setup();

        assertTrue(Chapter24_4_3TypeDiscoveryRecorder.discoveredTypes.contains(Chapter24_4_3PlainType.class));
        assertTrue(hasBean(syringe, Chapter24_4_3PlainType.class));
    }

    @Test
    @DisplayName("24.4.4 - Container processes discovered type metadata and member lifecycle events in deployment sequence")
    void shouldProcessDiscoveredTypeAndMembersThroughLifecycleSequence() {
        Chapter24_4_4LifecycleRecorder.reset();

        Syringe syringe = newIsolatedSyringeWithMode(
                BeanArchiveMode.EXPLICIT,
                Chapter24_4_4LifecycleBean.class,
                Chapter24_4_4Dependency.class,
                Chapter24_4_4Produced.class
        );
        syringe.addExtension(Chapter24_4_4LifecycleExtension.class.getName());
        syringe.setup();

        assertTrue(containsRecord("PIP:Chapter24_4_4LifecycleBean#dependency"));
        assertTrue(containsRecord("PIP:Chapter24_4_4LifecycleBean#produce"));
        assertTrue(containsRecord("PIP:Chapter24_4_4LifecycleBean#dispose"));
        assertTrue(containsRecord("PIP:Chapter24_4_4LifecycleBean#observe"));

        assertTrue(containsRecord("PIT:Chapter24_4_4LifecycleBean"));
        assertTrue(containsRecord("PBA_TYPE:Chapter24_4_4LifecycleBean"));
        assertTrue(containsRecord("PBA_METHOD:Chapter24_4_4LifecycleBean#produce"));
        assertTrue(containsRecord("PB:ProcessManagedBean:Chapter24_4_4LifecycleBean"));
        assertTrue(containsRecord("PB:ProcessProducerMethod:Chapter24_4_4LifecycleBean"));
        assertTrue(containsRecord("PP:Chapter24_4_4LifecycleBean#produce"));
        assertTrue(containsRecord("POM:Chapter24_4_4LifecycleBean#observe"));

        assertTrue(indexOfRecord("PIP:Chapter24_4_4LifecycleBean#dependency")
                < indexOfRecord("PIT:Chapter24_4_4LifecycleBean"));
        assertTrue(indexOfRecord("PIT:Chapter24_4_4LifecycleBean")
                < indexOfRecord("PBA_TYPE:Chapter24_4_4LifecycleBean"));
        assertTrue(indexOfRecord("PBA_TYPE:Chapter24_4_4LifecycleBean")
                < indexOfRecord("PB:ProcessManagedBean:Chapter24_4_4LifecycleBean"));

        assertTrue(indexOfRecord("PIP:Chapter24_4_4LifecycleBean#produce")
                < indexOfRecord("PP:Chapter24_4_4LifecycleBean#produce"));

        assertTrue(indexOfRecord("PIP:Chapter24_4_4LifecycleBean#observe")
                < indexOfRecord("POM:Chapter24_4_4LifecycleBean#observe"));

        assertTrue(hasBean(syringe, Chapter24_4_4LifecycleBean.class));
        assertTrue(hasBean(syringe, Chapter24_4_4Produced.class));
        assertTrue(
                syringe.getKnowledgeBase().getObserverMethodInfos().stream()
                        .anyMatch(info -> info.getObserverMethod() != null
                                && info.getObserverMethod().getDeclaringClass().equals(Chapter24_4_4LifecycleBean.class)
                                && "observe".equals(info.getObserverMethod().getName()))
        );
    }

    @Test
    @DisplayName("24.4.4 - Bean vetoed during ProcessBeanAttributes does not receive ProcessBean and is not registered")
    void shouldNotRegisterBeanWhenVetoedInProcessBeanAttributes() {
        Chapter24_4_4VetoRecorder.reset();

        Syringe syringe = newIsolatedSyringeWithMode(
                BeanArchiveMode.EXPLICIT,
                Chapter24_4_4VetoedBean.class
        );
        syringe.addExtension(Chapter24_4_4VetoExtension.class.getName());
        syringe.setup();

        assertTrue(Chapter24_4_4VetoRecorder.vetoCalled);
        assertFalse(Chapter24_4_4VetoRecorder.processBeanSeenForVetoedType);
        assertFalse(hasBean(syringe, Chapter24_4_4VetoedBean.class));
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        return newSyringeWithMode(BeanArchiveMode.EXPLICIT, beanClasses);
    }

    private Syringe newSyringeWithMode(BeanArchiveMode mode, Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(mode);
        return syringe;
    }

    private Syringe newIsolatedSyringe(Class<?>... includedFixtures) {
        return newIsolatedSyringeWithMode(BeanArchiveMode.EXPLICIT, includedFixtures);
    }

    private Syringe newIsolatedSyringeWithMode(BeanArchiveMode mode, Class<?>... includedFixtures) {
        Class<?>[] effectiveSeed = includedFixtures != null && includedFixtures.length > 0
                ? includedFixtures
                : new Class<?>[]{Chapter24LifecycleBean.class};
        Syringe syringe = newSyringeWithMode(mode, effectiveSeed);
        excludeChapter24FixturesNotExplicitlyIncluded(syringe, effectiveSeed);
        return syringe;
    }

    private void excludeChapter24FixturesNotExplicitlyIncluded(Syringe syringe, Class<?>... includedFixtures) {
        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(includedFixtures));
        for (Class<?> fixture : allChapter24Fixtures()) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
    }

    private Class<?>[] allChapter24Fixtures() {
        return new Class<?>[]{
                Chapter24LifecycleBean.class,
                Chapter24ProgrammaticServiceImpl.class,
                Chapter24ProgrammaticConsumer.class,
                Chapter24AlternativeLow.class,
                Chapter24AlternativeHigh.class,
                Chapter24InterceptorLow.class,
                Chapter24InterceptorHigh.class,
                Chapter24DecoratorLow.class,
                Chapter24DecoratorHigh.class,
                Chapter24DecoratedServiceImpl.class,
                Chapter24DecoratedServiceProducer.class,
                Chapter24_1PlainClass.class,
                Chapter24_1BeanDefiningClass.class,
                Chapter24_1DuplicateProbe.class,
                Chapter24_2StartupBean.class,
                Chapter24_2StartupConsumer.class,
                Chapter24_2UnsatisfiedConsumer.class,
                Chapter24_4_1ExplicitClass.class,
                Chapter24_4_1ExplicitInterface.class,
                Chapter24_4_1ExplicitEnum.class,
                Chapter24_4_1ExplicitAnnotation.class,
                Chapter24_4_1ImplicitBean.class,
                Chapter24_4_1ImplicitPlain.class,
                Chapter24_4_1ExcludedClass.class,
                Chapter24_4_1SyntheticFromBbd.class,
                Chapter24_4_1SyntheticFromAtd.class,
                Chapter24_4_1ManagedBean.class,
                Chapter24_4_1ManagedConsumer.class,
                Chapter24_4_2ExactExcluded.class,
                Chapter24_4_2PkgExcluded.class,
                Chapter24_4_2RecursiveExcluded.class,
                Chapter24_4_2Conditional.class,
                Chapter24_4_3ScopedBean.class,
                Chapter24_4_3DependentBean.class,
                Chapter24_4_3PlainType.class,
                Chapter24_4_4LifecycleBean.class,
                Chapter24_4_4Dependency.class,
                Chapter24_4_4Produced.class,
                Chapter24_4_4VetoedBean.class
        };
    }

    private static void addScanExcludeBeansXml(Syringe syringe, String discoveryMode, String excludesXml) {
        String mode = discoveryMode != null ? discoveryMode : "all";
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd\" " +
                "bean-discovery-mode=\"" + mode + "\" version=\"3.0\">" +
                "<scan>" + excludesXml + "</scan>" +
                "</beans>";
        BeansXml beansXml = new BeansXmlParser()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        syringe.getKnowledgeBase().addBeansXml(beansXml);
    }

    private static void restoreProperty(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    private static boolean hasBean(Syringe syringe, Class<?> beanClass) {
        return syringe.getKnowledgeBase().getBeans().stream()
                .anyMatch(bean -> beanClass.equals(bean.getBeanClass()));
    }

    private static boolean isVetoed(Syringe syringe, Class<?> clazz) {
        return syringe.getKnowledgeBase().getVetoedTypes().contains(clazz);
    }

    private static boolean containsRecord(String expected) {
        return Chapter24_4_4LifecycleRecorder.events.contains(expected);
    }

    private static int indexOfRecord(String expected) {
        return Chapter24_4_4LifecycleRecorder.events.indexOf(expected);
    }

    public static class Chapter24Recorder {
        static final List<String> events = new ArrayList<String>();
        static BeanManager beanManager;
        static boolean patRecorded;

        static void reset() {
            events.clear();
            beanManager = null;
            patRecorded = false;
        }
    }

    public static class Chapter24OrderingRecorder {
        static final List<Class<?>> alternatives = new ArrayList<Class<?>>();
        static final List<Class<?>> interceptors = new ArrayList<Class<?>>();
        static final List<Class<?>> decorators = new ArrayList<Class<?>>();

        static void reset() {
            alternatives.clear();
            interceptors.clear();
            decorators.clear();
        }
    }

    public static class Chapter24LifecycleExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery event, BeanManager beanManager) {
            Chapter24Recorder.events.add("BeforeBeanDiscovery");
            Chapter24Recorder.beanManager = beanManager;
        }

        public <T> void pat(@Observes ProcessAnnotatedType<T> event) {
            if (!Chapter24Recorder.patRecorded) {
                Chapter24Recorder.events.add("ProcessAnnotatedType");
                Chapter24Recorder.patRecorded = true;
            }
        }

        public void afterType(@Observes AfterTypeDiscovery event) {
            Chapter24Recorder.events.add("AfterTypeDiscovery");
        }

        public void afterBean(@Observes AfterBeanDiscovery event) {
            Chapter24Recorder.events.add("AfterBeanDiscovery");
        }

        public void afterDeployment(@Observes AfterDeploymentValidation event) {
            Chapter24Recorder.events.add("AfterDeploymentValidation");
        }
    }

    public static class Chapter24DefinitionErrorExtension implements Extension {
        public void afterBean(@Observes AfterBeanDiscovery event) {
            event.addDefinitionError(new IllegalStateException("24 definition error"));
        }
    }

    public static class Chapter24DeploymentProblemExtension implements Extension {
        public void afterDeployment(@Observes AfterDeploymentValidation event) {
            event.addDeploymentProblem(new IllegalStateException("24 deployment problem"));
        }
    }

    public static class Chapter24OrderingCaptureExtension implements Extension {
        public void afterType(@Observes AfterTypeDiscovery event) {
            Chapter24OrderingRecorder.alternatives.addAll(event.getAlternatives());
            Chapter24OrderingRecorder.interceptors.addAll(event.getInterceptors());
            Chapter24OrderingRecorder.decorators.addAll(event.getDecorators());
        }
    }

    public interface Chapter24ProgrammaticService {
        String value();
    }

    public static class Chapter24ProgrammaticServiceImpl implements Chapter24ProgrammaticService {
        @Override
        public String value() {
            return "programmatic";
        }
    }

    public static class Chapter24ProgrammaticBeanExtension implements Extension {
        public void afterBean(@Observes AfterBeanDiscovery event) {
            event.addBean()
                    .beanClass(Chapter24ProgrammaticServiceImpl.class)
                    .addTransitiveTypeClosure(Chapter24ProgrammaticServiceImpl.class)
                    .scope(Dependent.class)
                    .createWith(ctx -> new Chapter24ProgrammaticServiceImpl());
        }
    }

    @Dependent
    public static class Chapter24ProgrammaticConsumer {
        @Inject
        Chapter24ProgrammaticService service;

        String value() {
            return service.value();
        }
    }

    @Dependent
    public static class Chapter24LifecycleBean {
    }

    public interface Chapter24AlternativeContract {
        String id();
    }

    @Alternative
    @Priority(10)
    @Dependent
    public static class Chapter24AlternativeLow implements Chapter24AlternativeContract {
        @Override
        public String id() {
            return "low";
        }
    }

    @Alternative
    @Priority(50)
    @Dependent
    public static class Chapter24AlternativeHigh implements Chapter24AlternativeContract {
        @Override
        public String id() {
            return "high";
        }
    }

    @InterceptorBinding
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Chapter24Binding {
    }

    @Interceptor
    @Chapter24Binding
    @Priority(100)
    public static class Chapter24InterceptorLow {
        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }
    }

    @Interceptor
    @Chapter24Binding
    @Priority(200)
    public static class Chapter24InterceptorHigh {
        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }
    }

    public interface Chapter24DecoratedService {
        String ping();
    }

    @Dependent
    @Vetoed
    public static class Chapter24DecoratedServiceImpl implements Chapter24DecoratedService {
        @Override
        public String ping() {
            return "ok";
        }
    }

    @Decorator
    @Priority(300)
    public static class Chapter24DecoratorLow implements Chapter24DecoratedService {
        @Inject
        @Delegate
        @Default
        Chapter24DecoratedService delegate;

        @Override
        public String ping() {
            return delegate.ping();
        }
    }

    @Decorator
    @Priority(400)
    public static class Chapter24DecoratorHigh implements Chapter24DecoratedService {
        @Inject
        @Delegate
        @Default
        Chapter24DecoratedService delegate;

        @Override
        public String ping() {
            return delegate.ping();
        }
    }

    @Dependent
    public static class Chapter24DecoratedServiceProducer {
        @Produces
        Chapter24DecoratedService produce() {
            return new Chapter24DecoratedServiceImpl();
        }
    }

    public static class Chapter24_1PlainClass {
        String value() {
            return "plain";
        }
    }

    @Dependent
    public static class Chapter24_1BeanDefiningClass {
        String value() {
            return "bda";
        }
    }

    @Dependent
    public static class Chapter24_1DuplicateProbe {
    }

    public static class Chapter24_1ExtensionRecorder {
        static boolean beforeBeanDiscoveryObserved;

        static void reset() {
            beforeBeanDiscoveryObserved = false;
        }
    }

    public static class Chapter24_1PortableExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery event) {
            Chapter24_1ExtensionRecorder.beforeBeanDiscoveryObserved = true;
        }
    }

    public static class Chapter24_2LifecycleRecorder {
        static final List<String> events = new ArrayList<String>();
        static final Set<Integer> extensionInstanceIds = new HashSet<Integer>();

        static void reset() {
            events.clear();
            extensionInstanceIds.clear();
        }
    }

    public static class Chapter24_2LifecycleExtension implements Extension {
        public Chapter24_2LifecycleExtension() {
            Chapter24_2LifecycleRecorder.extensionInstanceIds.add(System.identityHashCode(this));
        }

        public void before(@Observes BeforeBeanDiscovery event) {
            Chapter24_2LifecycleRecorder.events.add("BeforeBeanDiscovery");
        }

        public <T> void pat(@Observes ProcessAnnotatedType<T> event) {
            if (!Chapter24_2LifecycleRecorder.events.contains("ProcessAnnotatedType")) {
                Chapter24_2LifecycleRecorder.events.add("ProcessAnnotatedType");
            }
        }

        public void afterType(@Observes AfterTypeDiscovery event) {
            Chapter24_2LifecycleRecorder.events.add("AfterTypeDiscovery");
        }

        public void processBean(@Observes ProcessBean<?> event) {
            if (!Chapter24_2LifecycleRecorder.events.contains("ProcessBean")) {
                Chapter24_2LifecycleRecorder.events.add("ProcessBean");
            }
        }

        public void afterBean(@Observes AfterBeanDiscovery event) {
            Chapter24_2LifecycleRecorder.events.add("AfterBeanDiscovery");
        }

        public void afterDeployment(@Observes AfterDeploymentValidation event) {
            Chapter24_2LifecycleRecorder.events.add("AfterDeploymentValidation");
        }
    }

    public static class Chapter24_2DefinitionErrorExtension implements Extension {
        public void afterBean(@Observes AfterBeanDiscovery event) {
            event.addDefinitionError(new IllegalStateException("24.2 definition error"));
        }
    }

    public static class Chapter24_2DeploymentProblemExtension implements Extension {
        public void afterDeployment(@Observes AfterDeploymentValidation event) {
            event.addDeploymentProblem(new IllegalStateException("24.2 deployment problem"));
        }
    }

    @Dependent
    public static class Chapter24_2StartupBean {
        String value() {
            return "ready";
        }
    }

    @Dependent
    public static class Chapter24_2StartupConsumer {
        @Inject
        Chapter24_2StartupBean bean;

        String value() {
            return bean.value();
        }
    }

    @Dependent
    public static class Chapter24_2UnsatisfiedConsumer {
        @Inject
        Chapter24_2MissingDependency missingDependency;
    }

    public interface Chapter24_2MissingDependency {
    }

    public static class Chapter24_2ShutdownRecorder {
        static final List<String> events = new ArrayList<String>();
        static boolean contextsAlreadyDestroyedAtBeforeShutdown;

        static void reset() {
            events.clear();
            contextsAlreadyDestroyedAtBeforeShutdown = false;
        }
    }

    public static class Chapter24_2ShutdownExtension implements Extension {
        public void beforeShutdown(@Observes BeforeShutdown event, BeanManager beanManager) {
            try {
                beanManager.getContext(ApplicationScoped.class);
            } catch (RuntimeException e) {
                Chapter24_2ShutdownRecorder.contextsAlreadyDestroyedAtBeforeShutdown = true;
            }
            Chapter24_2ShutdownRecorder.events.add("before-shutdown");
        }
    }

    public static class Chapter24_4_1TypeDiscoveryRecorder {
        static final Set<Class<?>> discoveredTypes = new HashSet<Class<?>>();
        static final Set<Class<?>> syntheticTypes = new HashSet<Class<?>>();
        static int syntheticSourcesCaptured;

        static void reset() {
            discoveredTypes.clear();
            syntheticTypes.clear();
            syntheticSourcesCaptured = 0;
        }
    }

    public static class Chapter24_4_1TypeDiscoveryExtension implements Extension {
        public <T> void onPat(@Observes ProcessAnnotatedType<T> event) {
            Chapter24_4_1TypeDiscoveryRecorder.discoveredTypes.add(event.getAnnotatedType().getJavaClass());
        }
    }

    public static class Chapter24_4_1SyntheticTypeExtension implements Extension {
        public void before(@Observes BeforeBeanDiscovery event, BeanManager beanManager) {
            event.addAnnotatedType(
                    beanManager.createAnnotatedType(Chapter24_4_1SyntheticFromBbd.class),
                    "chapter24-4-1-bbd"
            );
        }

        public void afterType(@Observes AfterTypeDiscovery event, BeanManager beanManager) {
            event.addAnnotatedType(
                    beanManager.createAnnotatedType(Chapter24_4_1SyntheticFromAtd.class),
                    "chapter24-4-1-atd"
            );
        }

        public <T> void onSynthetic(@Observes ProcessSyntheticAnnotatedType<T> event) {
            Chapter24_4_1TypeDiscoveryRecorder.syntheticTypes.add(event.getAnnotatedType().getJavaClass());
            if (event.getSource() != null) {
                Chapter24_4_1TypeDiscoveryRecorder.syntheticSourcesCaptured++;
            }
        }
    }

    public static class Chapter24_4_1ManagedDiscoveryRecorder {
        static boolean processProducerMethodSeen;
        static boolean processProducerFieldSeen;
        static boolean processObserverMethodSeen;
        static boolean observerNotified;

        static void reset() {
            processProducerMethodSeen = false;
            processProducerFieldSeen = false;
            processObserverMethodSeen = false;
            observerNotified = false;
        }
    }

    public static class Chapter24_4_1ManagedDiscoveryExtension implements Extension {
        public void onProducerMethod(@Observes ProcessProducerMethod<?, ?> event) {
            if (event.getBean().getBeanClass().equals(Chapter24_4_1ManagedBean.class)) {
                Chapter24_4_1ManagedDiscoveryRecorder.processProducerMethodSeen = true;
            }
        }

        public void onProducerField(@Observes ProcessProducerField<?, ?> event) {
            if (event.getBean().getBeanClass().equals(Chapter24_4_1ManagedBean.class)) {
                Chapter24_4_1ManagedDiscoveryRecorder.processProducerFieldSeen = true;
            }
        }

        public void onObserverMethod(@Observes ProcessObserverMethod<Chapter24_4_1ObservedEvent, ?> event) {
            if (event.getObserverMethod().getBeanClass().equals(Chapter24_4_1ManagedBean.class)) {
                Chapter24_4_1ManagedDiscoveryRecorder.processObserverMethodSeen = true;
            }
        }
    }

    public static class Chapter24_4_1ExplicitClass {
    }

    public interface Chapter24_4_1ExplicitInterface {
    }

    public enum Chapter24_4_1ExplicitEnum {
        VALUE
    }

    public @interface Chapter24_4_1ExplicitAnnotation {
    }

    @Dependent
    public static class Chapter24_4_1ImplicitBean {
    }

    public static class Chapter24_4_1ImplicitPlain {
    }

    public static class Chapter24_4_1ExcludedClass {
    }

    public static class Chapter24_4_1SyntheticFromBbd {
    }

    public static class Chapter24_4_1SyntheticFromAtd {
    }

    public static class Chapter24_4_1ProducedByMethod {
        private final String value;

        Chapter24_4_1ProducedByMethod(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }

    public static class Chapter24_4_1ProducedByField {
        private final String value;

        Chapter24_4_1ProducedByField(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }

    public static class Chapter24_4_1ObservedEvent {
    }

    @Dependent
    public static class Chapter24_4_1ManagedBean {
        @Produces
        Chapter24_4_1ProducedByMethod produceMethod() {
            return new Chapter24_4_1ProducedByMethod("method");
        }

        void disposeMethod(@Disposes Chapter24_4_1ProducedByMethod ignored) {
        }

        @Produces
        Chapter24_4_1ProducedByField producedField = new Chapter24_4_1ProducedByField("field");

        void observe(@Observes Chapter24_4_1ObservedEvent event) {
            Chapter24_4_1ManagedDiscoveryRecorder.observerNotified = true;
        }
    }

    @Dependent
    public static class Chapter24_4_1ManagedConsumer {
        @Inject
        Chapter24_4_1ProducedByMethod producedByMethod;
        @Inject
        Chapter24_4_1ProducedByField producedByField;

        String methodValue() {
            return producedByMethod.value();
        }

        String fieldValue() {
            return producedByField.value();
        }
    }

    public static class Chapter24_4_2ExactExcluded {
    }

    public static class Chapter24_4_2PkgExcluded {
    }

    public static class Chapter24_4_2RecursiveExcluded {
    }

    public static class Chapter24_4_2Conditional {
    }

    public static class Chapter24_4_3TypeDiscoveryRecorder {
        static final Set<Class<?>> discoveredTypes = new HashSet<Class<?>>();

        static void reset() {
            discoveredTypes.clear();
        }
    }

    public static class Chapter24_4_3TypeDiscoveryExtension implements Extension {
        public <T> void onPat(@Observes ProcessAnnotatedType<T> event) {
            Chapter24_4_3TypeDiscoveryRecorder.discoveredTypes.add(event.getAnnotatedType().getJavaClass());
        }
    }

    @ApplicationScoped
    public static class Chapter24_4_3ScopedBean {
    }

    @Dependent
    public static class Chapter24_4_3DependentBean {
    }

    public static class Chapter24_4_3PlainType {
    }

    public static class Chapter24_4_4LifecycleRecorder {
        static final List<String> events = new ArrayList<String>();

        static void reset() {
            events.clear();
        }
    }

    public static class Chapter24_4_4LifecycleExtension implements Extension {
        public void onPip(@Observes ProcessInjectionPoint<?, ?> event) {
            if (event.getInjectionPoint() != null && event.getInjectionPoint().getMember() != null) {
                Chapter24_4_4LifecycleRecorder.events.add(
                        "PIP:" + event.getInjectionPoint().getMember().getDeclaringClass().getSimpleName()
                                + "#" + event.getInjectionPoint().getMember().getName());
            }
        }

        public void onPit(@Observes ProcessInjectionTarget<?> event) {
            if (event.getAnnotatedType() != null && event.getAnnotatedType().getJavaClass() != null) {
                Chapter24_4_4LifecycleRecorder.events.add(
                        "PIT:" + event.getAnnotatedType().getJavaClass().getSimpleName());
            }
        }

        public void onPba(@Observes ProcessBeanAttributes<?> event) {
            if (event.getAnnotated() instanceof jakarta.enterprise.inject.spi.AnnotatedType<?>) {
                jakarta.enterprise.inject.spi.AnnotatedType<?> type =
                        (jakarta.enterprise.inject.spi.AnnotatedType<?>) event.getAnnotated();
                Chapter24_4_4LifecycleRecorder.events.add("PBA_TYPE:" + type.getJavaClass().getSimpleName());
            } else if (event.getAnnotated() instanceof jakarta.enterprise.inject.spi.AnnotatedMethod<?>) {
                jakarta.enterprise.inject.spi.AnnotatedMethod<?> method =
                        (jakarta.enterprise.inject.spi.AnnotatedMethod<?>) event.getAnnotated();
                Chapter24_4_4LifecycleRecorder.events.add(
                        "PBA_METHOD:" + method.getJavaMember().getDeclaringClass().getSimpleName()
                                + "#" + method.getJavaMember().getName());
            } else if (event.getAnnotated() instanceof jakarta.enterprise.inject.spi.AnnotatedField<?>) {
                jakarta.enterprise.inject.spi.AnnotatedField<?> field =
                        (jakarta.enterprise.inject.spi.AnnotatedField<?>) event.getAnnotated();
                Chapter24_4_4LifecycleRecorder.events.add(
                        "PBA_FIELD:" + field.getJavaMember().getDeclaringClass().getSimpleName()
                                + "#" + field.getJavaMember().getName());
            }
        }

        public void onPb(@Observes ProcessBean<?> event) {
            String eventType;
            if (event instanceof jakarta.enterprise.inject.spi.ProcessManagedBean<?>) {
                eventType = "ProcessManagedBean";
            } else if (event instanceof jakarta.enterprise.inject.spi.ProcessProducerMethod<?, ?>) {
                eventType = "ProcessProducerMethod";
            } else if (event instanceof jakarta.enterprise.inject.spi.ProcessProducerField<?, ?>) {
                eventType = "ProcessProducerField";
            } else if (event instanceof jakarta.enterprise.inject.spi.ProcessSyntheticBean<?>) {
                eventType = "ProcessSyntheticBean";
            } else {
                eventType = event.getClass().getSimpleName();
            }
            if (event.getBean() != null && event.getBean().getBeanClass() != null) {
                Chapter24_4_4LifecycleRecorder.events.add(
                        "PB:" + eventType + ":" + event.getBean().getBeanClass().getSimpleName());
            }
        }

        public void onPp(@Observes ProcessProducer<?, ?> event) {
            if (event.getAnnotatedMember() != null && event.getAnnotatedMember().getJavaMember() != null) {
                Chapter24_4_4LifecycleRecorder.events.add(
                        "PP:" + event.getAnnotatedMember().getJavaMember().getDeclaringClass().getSimpleName()
                                + "#" + event.getAnnotatedMember().getJavaMember().getName());
            }
        }

        public void onPom(@Observes ProcessObserverMethod<?, ?> event) {
            if (event.getAnnotatedMethod() != null && event.getAnnotatedMethod().getJavaMember() != null) {
                Chapter24_4_4LifecycleRecorder.events.add(
                        "POM:" + event.getAnnotatedMethod().getJavaMember().getDeclaringClass().getSimpleName()
                                + "#" + event.getAnnotatedMethod().getJavaMember().getName());
            }
        }
    }

    @Dependent
    public static class Chapter24_4_4Dependency {
    }

    public static class Chapter24_4_4Produced {
    }

    @Dependent
    public static class Chapter24_4_4LifecycleBean {
        @Inject
        Chapter24_4_4Dependency dependency;

        @Produces
        Chapter24_4_4Produced produce(Chapter24_4_4Dependency ignored) {
            return new Chapter24_4_4Produced();
        }

        void dispose(@Disposes Chapter24_4_4Produced ignored, Chapter24_4_4Dependency dependency) {
        }

        void observe(@Observes Chapter24_4_1ObservedEvent event, Chapter24_4_4Dependency dependency) {
        }
    }

    public static class Chapter24_4_4VetoRecorder {
        static boolean vetoCalled;
        static boolean processBeanSeenForVetoedType;

        static void reset() {
            vetoCalled = false;
            processBeanSeenForVetoedType = false;
        }
    }

    public static class Chapter24_4_4VetoExtension implements Extension {
        public void veto(@Observes ProcessBeanAttributes<?> event) {
            if (event.getAnnotated() instanceof jakarta.enterprise.inject.spi.AnnotatedType<?>) {
                jakarta.enterprise.inject.spi.AnnotatedType<?> type =
                        (jakarta.enterprise.inject.spi.AnnotatedType<?>) event.getAnnotated();
                if (type.getJavaClass().equals(Chapter24_4_4VetoedBean.class)) {
                    event.veto();
                    Chapter24_4_4VetoRecorder.vetoCalled = true;
                }
            }
        }

        public void processBean(@Observes ProcessBean<?> event) {
            if (event.getBean() != null && Chapter24_4_4VetoedBean.class.equals(event.getBean().getBeanClass())) {
                Chapter24_4_4VetoRecorder.processBeanSeenForVetoedType = true;
            }
        }
    }

    @Dependent
    public static class Chapter24_4_4VetoedBean {
    }

    private int indexOf(String eventName) {
        return Chapter24_2LifecycleRecorder.events.indexOf(eventName);
    }
}
