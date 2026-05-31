package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter10.par103usinginvokerbuilder;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.BuildServices;
import jakarta.enterprise.inject.build.compatible.spi.BuildServicesResolver;
import jakarta.enterprise.inject.build.compatible.spi.DisposerInfo;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.MetaAnnotations;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;
import jakarta.enterprise.inject.build.compatible.spi.ScopeInfo;
import jakarta.enterprise.inject.build.compatible.spi.StereotypeInfo;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserver;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.Validation;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Extension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.lang.reflect.Method;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("10.3 - Build Compatible Extension lifecycle integration")
@Tag("bce-conformance")
@Execution(ExecutionMode.SAME_THREAD)
public class BuildCompatibleExtensionLifecycleTest {

    @AfterEach
    public void cleanup() {
        PhaseRecorder.reset();
        ServiceMatrixRecorder.reset();
        OrderingRecorder.reset();
        DuplicateRegistrationRecorder.reset();
        DiagnosticsRecorder.reset();
        PortableDuplicateRecorder.reset();
        EnhancementModelRecorder.reset();
        ScannedClassesRecorder.reset();
        SubtypeEnhancementRecorder.reset();
        EnhancementServiceModelRecorder.reset();
        EnhancementNoMatchRecorder.reset();
        RegistrationValidationModelRecorder.reset();
        InterceptorModelRecorder.reset();
        InjectionPointModelRecorder.reset();
        DisposerModelRecorder.reset();
        ScopeModelRecorder.reset();
        StereotypeModelRecorder.reset();
        SyntheticObserverParityRecorder.reset();
        EnhancementConfigGraphRecorder.reset();
        EnhancementAnnotationMutationRecorder.reset();
        EnhancementCrossMethodPersistenceRecorder.reset();
        EnhancementCrossViewPersistenceRecorder.reset();
        EnhancementFieldCrossViewRecorder.reset();
        EnhancementMethodReverseCrossViewRecorder.reset();
        EnhancementRemoveAllCrossViewRecorder.reset();
        EnhancementRemovePredicateCrossViewRecorder.reset();
        EnhancementParameterCrossViewRecorder.reset();
        LanguageModelEdgeCaseRecorder.reset();
    }

    @Test
    @DisplayName("10.3 - Explicitly registered build compatible extension runs supported phases in deployment order")
    public void shouldRunBuildCompatibleExtensionPhasesInOrder() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(TrackingBuildCompatibleExtension.class.getName());
        syringe.setup();

        // CDI 4.1 §13.5.2: registration callbacks run before and after synthesis
        // (the second registration pass processes synthetic components).
        assertEquals(
            Arrays.asList("DISCOVERY", "ENHANCEMENT", "REGISTRATION", "SYNTHESIS", "REGISTRATION", "VALIDATION"),
            PhaseRecorder.snapshot()
        );
    }

    @Test
    @DisplayName("10.3 - Portable extensions and build compatible extensions are registered independently")
    public void shouldKeepPortableAndBuildCompatibleExtensionsSeparate() {
        Syringe syringe = newSyringe();
        syringe.addExtension(HybridPortableAndBuildCompatibleExtension.class.getName());
        syringe.setup();

        assertEquals(Collections.emptyList(), PhaseRecorder.snapshot());
    }

    @Test
    @DisplayName("10.3 - Unsupported build compatible extension method signatures are deployment problems")
    public void shouldRejectUnsupportedBuildCompatiblePhaseMethodSignature() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidSignatureBuildCompatibleExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - BCE method with multiple phase annotations is a deployment problem")
    public void shouldRejectMethodWithMultiplePhaseAnnotations() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(MultiPhaseAnnotatedExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - BCE phase methods must declare void return type")
    public void shouldRejectNonVoidPhaseMethod() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(NonVoidPhaseMethodExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Registration phase can receive InvokerFactory and discovered metadata context")
    public void shouldInjectInvokerFactoryAndRegistrationContext() {
        RegistrationContextRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(RegistrationContextExtension.class.getName());

        syringe.setup();

        assertTrue(RegistrationContextRecorder.invokerFactoryInjected);
        assertTrue(RegistrationContextRecorder.contextInjected);
        assertTrue(!RegistrationContextRecorder.discoveredBeanClassNames.isEmpty());
    }

    @Test
    @DisplayName("10.3 - Portable and BCE lifecycles run in deterministic order and once per channel")
    public void shouldRunPortableAndBceLifecyclesInOrderWithoutDuplicates() {
        MixedLifecycleRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(HybridPortableAndBuildCompatibleExtension.class.getName());
        syringe.addBuildCompatibleExtension(HybridPortableAndBuildCompatibleExtension.class.getName());

        syringe.setup();

        assertEquals(1, MixedLifecycleRecorder.portableAfterBeanDiscoveryCalls);
        assertEquals(1, MixedLifecycleRecorder.bceDiscoveryCalls);
        assertTrue(MixedLifecycleRecorder.events.indexOf("bce-discovery") <
            MixedLifecycleRecorder.events.indexOf("portable-afterBeanDiscovery"));
    }

    @Test
    @DisplayName("10.3 - BuildServicesResolver is available during BCE phase callbacks")
    public void shouldExposeBuildServicesResolverDuringBcePhases() {
        BuildServicesRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(BuildServicesResolverExtension.class.getName());

        syringe.setup();

        assertTrue(BuildServicesRecorder.discoveryResolverAvailable);
        assertTrue(BuildServicesRecorder.registrationResolverAvailable);
        assertTrue(BuildServicesRecorder.synthesisResolverAvailable);
        assertTrue(BuildServicesRecorder.validationResolverAvailable);
        assertTrue(BuildServicesRecorder.annotationBuilderWorked);
    }

    @Test
    @DisplayName("10.3 - Allowed BCE service parameters are injected for all supported phases")
    public void shouldInjectAllowedServiceParametersForSupportedPhases() {
        ServiceMatrixRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(AllowedServiceParametersExtension.class.getName());

        syringe.setup();

        assertTrue(ServiceMatrixRecorder.discoveryTypesInjected);
        assertTrue(ServiceMatrixRecorder.discoveryMessagesInjected);
        assertTrue(ServiceMatrixRecorder.discoveryMetaAnnotationsInjected);
        assertTrue(ServiceMatrixRecorder.discoveryScannedClassesInjected);
        assertTrue(ServiceMatrixRecorder.enhancementTypesInjected);
        assertTrue(ServiceMatrixRecorder.enhancementMessagesInjected);
        assertTrue(ServiceMatrixRecorder.registrationTypesInjected);
        assertTrue(ServiceMatrixRecorder.registrationMessagesInjected);
        assertTrue(ServiceMatrixRecorder.synthesisTypesInjected);
        assertTrue(ServiceMatrixRecorder.synthesisMessagesInjected);
        assertTrue(ServiceMatrixRecorder.validationTypesInjected);
        assertTrue(ServiceMatrixRecorder.validationMessagesInjected);
    }

    @Test
    @DisplayName("10.3 - Discovery methods reject duplicate BCE service parameters")
    public void shouldRejectDuplicateDiscoveryServiceParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidDiscoveryDuplicateTypesExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Enhancement methods reject unsupported service parameter types")
    public void shouldRejectUnsupportedEnhancementServiceParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidEnhancementMetaAnnotationsExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Registration methods reject unsupported ScannedClasses parameter")
    public void shouldRejectUnsupportedRegistrationScannedClassesParameter() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidRegistrationScannedClassesExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Synthesis methods may omit SyntheticComponents")
    public void shouldAllowSynthesisMethodWithoutSyntheticComponents() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(SynthesisWithoutSyntheticComponentsExtension.class.getName());

        assertDoesNotThrow(syringe::setup);
    }

    @Test
    @DisplayName("10.3 - BCE Messages.error in phase method produces deployment problem")
    public void shouldFailDeploymentWhenMessagesErrorIsReported() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(MessagesErrorExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - BCE Messages.info and warn do not fail deployment")
    public void shouldAllowMessagesInfoAndWarnWithoutDeploymentFailure() {
        DiagnosticsRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(MessagesInfoWarnExtension.class.getName());

        syringe.setup();

        assertTrue(DiagnosticsRecorder.discoveryRan);
        assertTrue(DiagnosticsRecorder.validationRan);
    }

    @Test
    @DisplayName("10.3 - BCE Messages.error(Exception) produces deployment problem")
    public void shouldFailDeploymentWhenMessagesErrorExceptionIsReported() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(MessagesErrorExceptionExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - BCE phase method ordering is deterministic across multiple extensions")
    public void shouldRunBcePhaseMethodsInDeterministicOrderAcrossExtensions() {
        OrderingRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(BetaOrderedExtension.class.getName());
        syringe.addBuildCompatibleExtension(AlphaOrderedExtension.class.getName());

        syringe.setup();

        assertEquals(
            Arrays.asList("alpha-first", "alpha-second", "beta-only"),
            OrderingRecorder.snapshot()
        );
    }

    @Test
    @DisplayName("10.3 - Duplicate BCE extension registration in same channel is deduplicated")
    public void shouldDeduplicateDuplicateBceExtensionRegistrations() {
        DuplicateRegistrationRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(DuplicateBceExtension.class.getName());
        syringe.addBuildCompatibleExtension(DuplicateBceExtension.class.getName());
        syringe.addBuildCompatibleExtension(DuplicateBceExtension.class.getName());

        syringe.setup();

        assertEquals(1, DuplicateRegistrationRecorder.discoveryCalls);
    }

    @Test
    @DisplayName("10.3 - Duplicate portable extension registration in same channel is deduplicated")
    public void shouldDeduplicateDuplicatePortableExtensionRegistrations() {
        PortableDuplicateRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addExtension(PortableDuplicateExtension.class.getName());
        syringe.addExtension(PortableDuplicateExtension.class.getName());
        syringe.addExtension(PortableDuplicateExtension.class.getName());

        syringe.setup();

        assertEquals(1, PortableDuplicateRecorder.afterBeanDiscoveryCalls);
    }

    @Test
    @DisplayName("10.3 - Synthesis methods reject duplicate SyntheticComponents parameters")
    public void shouldRejectDuplicateSyntheticComponentsParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidSynthesisDuplicateSyntheticComponentsExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Registration methods reject duplicate Messages parameters")
    public void shouldRejectDuplicateRegistrationMessagesParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidRegistrationDuplicateMessagesExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Synthesis methods reject unsupported MetaAnnotations parameter")
    public void shouldRejectUnsupportedSynthesisMetaAnnotationsParameter() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidSynthesisMetaAnnotationsExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Registration methods reject BuildServices parameter")
    public void shouldRejectRegistrationBuildServicesParameter() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidRegistrationBuildServicesExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Discovery methods reject BuildServices parameter")
    public void shouldRejectDiscoveryBuildServicesParameter() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidDiscoveryBuildServicesExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Synthesis methods reject BuildServices parameter")
    public void shouldRejectSynthesisBuildServicesParameter() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidSynthesisBuildServicesExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Validation methods reject BuildServices parameter")
    public void shouldRejectValidationBuildServicesParameter() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidValidationBuildServicesExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Enhancement can receive ClassInfo/ClassConfig and runs per matching class")
    public void shouldInvokeEnhancementPerMatchingClassForClassModel() {
        EnhancementModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementClassModelExtension.class.getName());

        syringe.setup();

        assertEquals(1, EnhancementModelRecorder.classInfoInvocations);
        assertEquals(1, EnhancementModelRecorder.classConfigInvocations);
    }

    @Test
    @DisplayName("10.3 - Enhancement can receive MethodInfo and runs per matching annotated method")
    public void shouldInvokeEnhancementPerMatchingMethodForMethodModel() {
        EnhancementModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementMethodModelExtension.class.getName());

        syringe.setup();

        assertEquals(1, EnhancementModelRecorder.methodInfoInvocations);
    }

    @Test
    @DisplayName("10.3 - Enhancement can receive MethodConfig and runs per matching annotated method")
    public void shouldInvokeEnhancementPerMatchingMethodForMethodConfigModel() {
        EnhancementModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementMethodConfigModelExtension.class.getName());

        syringe.setup();

        assertEquals(1, EnhancementModelRecorder.methodConfigInvocations);
    }

    @Test
    @DisplayName("10.3 - Enhancement can receive FieldInfo/FieldConfig and runs per matching annotated field")
    public void shouldInvokeEnhancementPerMatchingFieldForFieldModels() {
        EnhancementModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementFieldModelExtension.class.getName());

        syringe.setup();

        assertEquals(1, EnhancementModelRecorder.fieldInfoInvocations);
        assertEquals(1, EnhancementModelRecorder.fieldConfigInvocations);
    }

    @Test
    @DisplayName("10.3 - ClassConfig exposes constructors, methods, fields and nested parameter configs")
    public void shouldExposeClassConfigMemberGraphs() {
        EnhancementConfigGraphRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementClassConfigGraphExtension.class.getName());

        syringe.setup();

        assertTrue(EnhancementConfigGraphRecorder.classConfigSeen > 0);
        assertTrue(EnhancementConfigGraphRecorder.constructorsCount > 0);
        assertTrue(EnhancementConfigGraphRecorder.methodsCount > 0);
        assertTrue(EnhancementConfigGraphRecorder.fieldsCount > 0);
        assertTrue(EnhancementConfigGraphRecorder.parameterConfigsCount > 0);
    }

    @Test
    @DisplayName("10.3 - Enhancement annotation mutations are reflected in info views for class/method/field/parameter configs")
    public void shouldMaterializeEnhancementAnnotationMutationsInConfigInfoViews() {
        EnhancementAnnotationMutationRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementAnnotationMutationExtension.class.getName());

        syringe.setup();

        assertTrue(EnhancementAnnotationMutationRecorder.classAddedVisible);
        assertTrue(EnhancementAnnotationMutationRecorder.classRemovedVisible);
        assertTrue(EnhancementAnnotationMutationRecorder.methodAddedVisible);
        assertTrue(EnhancementAnnotationMutationRecorder.methodRemovedVisible);
        assertTrue(EnhancementAnnotationMutationRecorder.fieldAddedVisible);
        assertTrue(EnhancementAnnotationMutationRecorder.fieldRemovedVisible);
        assertTrue(EnhancementAnnotationMutationRecorder.parameterAddedVisible);
        assertTrue(EnhancementAnnotationMutationRecorder.parameterRemovedVisible);
    }

    @Test
    @DisplayName("10.3 - Enhancement config mutations persist across enhancement methods for the same target")
    public void shouldPersistEnhancementConfigMutationsAcrossMethods() {
        EnhancementCrossMethodPersistenceRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementCrossMethodPersistenceExtension.class.getName());

        syringe.setup();

        assertTrue(EnhancementCrossMethodPersistenceRecorder.addMethodExecuted);
        assertTrue(EnhancementCrossMethodPersistenceRecorder.verifyMethodSawMutation);
    }

    @Test
    @DisplayName("10.3 - MethodConfig mutations are visible through ClassConfig method views in same enhancement phase")
    public void shouldShareMethodConfigMutationsAcrossClassAndMethodViews() {
        EnhancementCrossViewPersistenceRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementCrossViewPersistenceExtension.class.getName());

        syringe.setup();

        assertTrue(EnhancementCrossViewPersistenceRecorder.methodMutationExecuted);
        assertTrue(EnhancementCrossViewPersistenceRecorder.classViewSawMethodMutation);
    }

    @Test
    @DisplayName("10.3 - FieldConfig mutations stay coherent across ClassConfig and FieldInfo enhancement views")
    public void shouldShareFieldConfigMutationsAcrossClassAndFieldViews() {
        EnhancementFieldCrossViewRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementFieldCrossViewExtension.class.getName());

        syringe.setup();

        assertTrue(EnhancementFieldCrossViewRecorder.fieldConfigMutationExecuted);
        assertTrue(EnhancementFieldCrossViewRecorder.classViewSawFieldMutation);
        assertTrue(EnhancementFieldCrossViewRecorder.classMutationExecuted);
        assertTrue(EnhancementFieldCrossViewRecorder.fieldInfoSawClassMutation);
    }

    @Test
    @DisplayName("10.3 - ClassConfig method mutations are visible through later MethodInfo enhancement views")
    public void shouldShareMethodMutationsFromClassViewToMethodInfoView() {
        EnhancementMethodReverseCrossViewRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementMethodReverseCrossViewExtension.class.getName());

        syringe.setup();

        assertTrue(EnhancementMethodReverseCrossViewRecorder.classMutationExecuted);
        assertTrue(EnhancementMethodReverseCrossViewRecorder.methodInfoSawClassMutation);
    }

    @Test
    @DisplayName("10.3 - removeAllAnnotations stays coherent across MethodConfig/FieldConfig and later info views")
    public void shouldPropagateRemoveAllAnnotationsAcrossEnhancementViews() {
        EnhancementRemoveAllCrossViewRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementRemoveAllCrossViewExtension.class.getName());

        syringe.setup();

        assertTrue(EnhancementRemoveAllCrossViewRecorder.methodRemoveAllExecuted);
        assertTrue(EnhancementRemoveAllCrossViewRecorder.classViewSawMethodRemoved);
        assertTrue(EnhancementRemoveAllCrossViewRecorder.methodInfoSawRemoved);
        assertTrue(EnhancementRemoveAllCrossViewRecorder.fieldRemoveAllExecuted);
        assertTrue(EnhancementRemoveAllCrossViewRecorder.classViewSawFieldRemoved);
        assertTrue(EnhancementRemoveAllCrossViewRecorder.fieldInfoSawRemoved);
    }

    @Test
    @DisplayName("10.3 - removeAnnotation(predicate) stays coherent across method/field config and later info views")
    public void shouldPropagateRemoveAnnotationPredicateAcrossEnhancementViews() {
        EnhancementRemovePredicateCrossViewRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementRemovePredicateCrossViewExtension.class.getName());

        syringe.setup();

        assertTrue(EnhancementRemovePredicateCrossViewRecorder.methodRemoveExecuted);
        assertTrue(EnhancementRemovePredicateCrossViewRecorder.methodClassViewRemoved);
        assertTrue(EnhancementRemovePredicateCrossViewRecorder.methodInfoRemoved);
        assertTrue(EnhancementRemovePredicateCrossViewRecorder.fieldRemoveExecuted);
        assertTrue(EnhancementRemovePredicateCrossViewRecorder.fieldClassViewRemoved);
        assertTrue(EnhancementRemovePredicateCrossViewRecorder.fieldInfoRemoved);
    }

    @Test
    @DisplayName("10.3 - ParameterConfig mutations are visible through later MethodInfo parameter views")
    public void shouldPropagateParameterMutationsToMethodInfoViews() {
        EnhancementParameterCrossViewRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementParameterCrossViewExtension.class.getName());

        syringe.setup();

        assertTrue(EnhancementParameterCrossViewRecorder.parameterAddExecuted);
        assertTrue(EnhancementParameterCrossViewRecorder.methodInfoSawParameterAdded);
        assertTrue(EnhancementParameterCrossViewRecorder.parameterRemoveExecuted);
        assertTrue(EnhancementParameterCrossViewRecorder.methodInfoSawParameterRemoved);
    }

    @Test
    @DisplayName("10.3 - Language model repeatableAnnotation resolves repeated annotations from container")
    public void shouldResolveRepeatableAnnotationsFromLanguageModel() {
        LanguageModelEdgeCaseRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(LanguageModelRepeatableExtension.class.getName());

        syringe.setup();

        assertEquals(2, LanguageModelEdgeCaseRecorder.repeatableCountOnClassInfo);
        assertEquals(2, LanguageModelEdgeCaseRecorder.repeatableCountOnClassConfigInfo);
    }

    @Test
    @DisplayName("10.3 - Enhancement rejects multiple model/config parameters")
    public void shouldRejectEnhancementWithMultipleModelParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidEnhancementMultipleModelsExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Discovery ScannedClasses can add classes that become injectable")
    public void shouldAllowScannedClassesToAddInjectableClass() {
        ScannedClassesRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(ScannedClassesAddsBeanExtension.class.getName());

        syringe.setup();

        ScannedAddedBean bean = syringe.inject(ScannedAddedBean.class);
        assertTrue(bean != null);
        assertTrue(ScannedClassesRecorder.discoveryRan);
    }

    @Test
    @DisplayName("10.3 - Discovery ScannedClasses rejects unknown class names")
    public void shouldRejectUnknownScannedClassName() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidScannedClassesExtension.class.getName());

        assertThrows(RuntimeException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Enhancement withSubtypes=true matches subclasses")
    public void shouldMatchSubtypesWhenEnhancementWithSubtypesIsTrue() {
        SubtypeEnhancementRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementWithSubtypesTrueExtension.class.getName());

        syringe.setup();

        assertEquals(2, SubtypeEnhancementRecorder.classInfoHits);
    }

    @Test
    @DisplayName("10.3 - Enhancement withSubtypes=false excludes subclasses")
    public void shouldExcludeSubtypesWhenEnhancementWithSubtypesIsFalse() {
        SubtypeEnhancementRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementWithSubtypesFalseExtension.class.getName());

        syringe.setup();

        assertEquals(1, SubtypeEnhancementRecorder.classInfoHits);
    }

    @Test
    @DisplayName("10.3 - Enhancement allows model plus Types/Messages")
    public void shouldAllowEnhancementModelWithSupportedServices() {
        EnhancementServiceModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementModelWithSupportedServicesExtension.class.getName());

        syringe.setup();

        assertTrue(EnhancementServiceModelRecorder.modelInjected);
        assertTrue(EnhancementServiceModelRecorder.typesInjected);
        assertTrue(EnhancementServiceModelRecorder.messagesInjected);
    }

    @Test
    @DisplayName("10.3 - Enhancement rejects duplicate Messages when model parameter is present")
    public void shouldRejectEnhancementDuplicateMessagesWithModel() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidEnhancementDuplicateMessagesWithModelExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Enhancement class model is not invoked when class annotation filter does not match")
    public void shouldNotInvokeEnhancementClassModelWhenAnnotationFilterDoesNotMatch() {
        EnhancementNoMatchRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementClassNoMatchExtension.class.getName());

        syringe.setup();

        assertEquals(0, EnhancementNoMatchRecorder.classInfoHits);
    }

    @Test
    @DisplayName("10.3 - Enhancement method model is not invoked when method annotation filter does not match")
    public void shouldNotInvokeEnhancementMethodModelWhenAnnotationFilterDoesNotMatch() {
        EnhancementNoMatchRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(EnhancementMethodNoMatchExtension.class.getName());

        syringe.setup();

        assertEquals(0, EnhancementNoMatchRecorder.methodInfoHits);
    }

    @Test
    @DisplayName("10.3 - Discovery methods reject duplicate MetaAnnotations parameters")
    public void shouldRejectDuplicateDiscoveryMetaAnnotationsParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidDiscoveryDuplicateMetaAnnotationsExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Discovery methods reject duplicate ScannedClasses parameters")
    public void shouldRejectDuplicateDiscoveryScannedClassesParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidDiscoveryDuplicateScannedClassesExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Enhancement methods reject BuildServices parameter")
    public void shouldRejectEnhancementBuildServicesParameter() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidEnhancementBuildServicesExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Validation methods reject duplicate Messages parameters")
    public void shouldRejectDuplicateValidationMessagesParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidValidationDuplicateMessagesExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Registration can receive BeanInfo and ObserverInfo model parameters")
    public void shouldInjectRegistrationBeanInfoAndObserverInfo() {
        RegistrationValidationModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(RegistrationModelParametersExtension.class.getName());

        syringe.setup();

        assertTrue(RegistrationValidationModelRecorder.registrationBeanInfoCount > 0);
    }

    @Test
    @DisplayName("10.3 - Validation can receive BeanInfo and ObserverInfo model parameters")
    public void shouldInjectValidationBeanInfoAndObserverInfo() {
        RegistrationValidationModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(ValidationModelParametersExtension.class.getName());

        syringe.setup();

        assertTrue(RegistrationValidationModelRecorder.validationBeanInfoCount > 0);
        assertTrue(RegistrationValidationModelRecorder.validationObserverInfoCount > 0);
    }

    @Test
    @DisplayName("10.3 - Registration rejects multiple model parameters")
    public void shouldRejectRegistrationWithMultipleModelParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidRegistrationMultipleModelsExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Validation rejects multiple model parameters")
    public void shouldRejectValidationWithMultipleModelParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidValidationMultipleModelsExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Registration can receive InterceptorInfo model parameter")
    public void shouldInjectRegistrationInterceptorInfo() {
        InterceptorModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(RegistrationInterceptorModelExtension.class.getName());

        syringe.setup();
    }

    @Test
    @DisplayName("10.3 - Validation can receive InterceptorInfo model parameter")
    public void shouldInjectValidationInterceptorInfo() {
        InterceptorModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(ValidationInterceptorModelExtension.class.getName());

        syringe.setup();
    }

    @Test
    @DisplayName("10.3 - Registration rejects BeanInfo plus InterceptorInfo model parameters")
    public void shouldRejectRegistrationBeanAndInterceptorModelParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidRegistrationBeanAndInterceptorModelsExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Registration can receive InjectionPointInfo model parameter")
    public void shouldInjectRegistrationInjectionPointInfo() {
        InjectionPointModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(RegistrationInjectionPointModelExtension.class.getName());

        syringe.setup();

        assertTrue(InjectionPointModelRecorder.registrationInjectionPointInfoCount > 0);
    }

    @Test
    @DisplayName("10.3 - Validation can receive InjectionPointInfo model parameter")
    public void shouldInjectValidationInjectionPointInfo() {
        InjectionPointModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(ValidationInjectionPointModelExtension.class.getName());

        syringe.setup();

        assertTrue(InjectionPointModelRecorder.validationInjectionPointInfoCount > 0);
    }

    @Test
    @DisplayName("10.3 - Validation rejects BeanInfo plus InjectionPointInfo model parameters")
    public void shouldRejectValidationBeanAndInjectionPointModelParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidValidationBeanAndInjectionPointModelsExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Registration can receive DisposerInfo model parameter")
    public void shouldInjectRegistrationDisposerInfo() {
        DisposerModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(RegistrationDisposerModelExtension.class.getName());

        syringe.setup();

        assertTrue(DisposerModelRecorder.registrationDisposerInfoCount > 0);
    }

    @Test
    @DisplayName("10.3 - Validation can receive DisposerInfo model parameter")
    public void shouldInjectValidationDisposerInfo() {
        DisposerModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(ValidationDisposerModelExtension.class.getName());

        syringe.setup();

        assertTrue(DisposerModelRecorder.validationDisposerInfoCount > 0);
    }

    @Test
    @DisplayName("10.3 - Registration rejects BeanInfo plus DisposerInfo model parameters")
    public void shouldRejectRegistrationBeanAndDisposerModelParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidRegistrationBeanAndDisposerModelsExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Registration can receive ScopeInfo model parameter")
    public void shouldInjectRegistrationScopeInfo() {
        ScopeModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(RegistrationScopeModelExtension.class.getName());

        syringe.setup();

        assertTrue(ScopeModelRecorder.registrationScopeInfoCount > 0);
    }

    @Test
    @DisplayName("10.3 - Validation can receive ScopeInfo model parameter")
    public void shouldInjectValidationScopeInfo() {
        ScopeModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(ValidationScopeModelExtension.class.getName());

        syringe.setup();

        assertTrue(ScopeModelRecorder.validationScopeInfoCount > 0);
    }

    @Test
    @DisplayName("10.3 - Validation rejects BeanInfo plus ScopeInfo model parameters")
    public void shouldRejectValidationBeanAndScopeModelParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidValidationBeanAndScopeModelsExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Registration can receive StereotypeInfo model parameter")
    public void shouldInjectRegistrationStereotypeInfo() {
        StereotypeModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(RegistrationStereotypeModelExtension.class.getName());

        syringe.setup();

        assertTrue(StereotypeModelRecorder.registrationStereotypeInfoCount > 0);
    }

    @Test
    @DisplayName("10.3 - Validation can receive StereotypeInfo model parameter")
    public void shouldInjectValidationStereotypeInfo() {
        StereotypeModelRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(ValidationStereotypeModelExtension.class.getName());

        syringe.setup();

        assertTrue(StereotypeModelRecorder.validationStereotypeInfoCount > 0);
    }

    @Test
    @DisplayName("10.3 - Registration rejects BeanInfo plus StereotypeInfo model parameters")
    public void shouldRejectRegistrationBeanAndStereotypeModelParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidRegistrationBeanAndStereotypeModelsExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("10.3 - Validation ObserverInfo model includes synthetic observers and supports diagnostics logging")
    public void shouldExposeSyntheticObserversThroughObserverInfoModelInValidation() {
        SyntheticObserverParityRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(SyntheticObserverParityExtension.class.getName());

        syringe.setup();

        assertTrue(SyntheticObserverParityRecorder.syntheticObserverSeen > 0);
        assertTrue(SyntheticObserverParityRecorder.messagesObserverCalls > 0);
        assertTrue(SyntheticObserverParityRecorder.syntheticObserverMethodAccessible > 0);
        assertTrue(SyntheticObserverParityRecorder.syntheticEventParameterAccessible > 0);
        assertTrue(SyntheticObserverParityRecorder.syntheticBeanAccessible > 0);
    }

    private Syringe newSyringe() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(),
            MinimalBean.class,
            TrackedBean.class,
            EnhancementTargetBean.class,
            NonEnhancedTargetBean.class,
            BaseEnhancementType.class,
            SubEnhancementType.class,
            ObserverFixtureBean.class,
            InterceptorFixtureBean.class,
            TestInterceptor.class,
            InjectionPointFixtureBean.class,
            DisposerFixtureProducerBean.class,
            StereotypeFixtureBean.class,
            EnhancementConfigGraphTarget.class,
            RepeatableTargetBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    static class PhaseRecorder {
        private static final List<String> PHASES = new ArrayList<String>();

        static synchronized void add(String phase) {
            PHASES.add(phase);
        }

        static synchronized List<String> snapshot() {
            return new ArrayList<String>(PHASES);
        }

        static synchronized void reset() {
            PHASES.clear();
        }
    }

    public static class TrackingBuildCompatibleExtension implements BuildCompatibleExtension {
        @Discovery
        public void onDiscovery() {
            PhaseRecorder.add("DISCOVERY");
        }

        @Enhancement(types = TrackedBean.class, withSubtypes = false)
        public void onEnhancement(jakarta.enterprise.lang.model.declarations.ClassInfo classInfo) {
            PhaseRecorder.add("ENHANCEMENT");
        }

        @Registration(types = Object.class)
        public void onRegistration() {
            PhaseRecorder.add("REGISTRATION");
        }

        @Synthesis
        public void onSynthesis(SyntheticComponents ignored) {
            PhaseRecorder.add("SYNTHESIS");
        }

        @Validation
        public void onValidation() {
            PhaseRecorder.add("VALIDATION");
        }
    }

    public static class HybridPortableAndBuildCompatibleExtension implements Extension, BuildCompatibleExtension {
        public void portable(@Observes AfterBeanDiscovery ignored) {
            MixedLifecycleRecorder.portableAfterBeanDiscoveryCalls++;
            MixedLifecycleRecorder.events.add("portable-afterBeanDiscovery");
        }

        @Discovery
        public void onDiscovery() {
            MixedLifecycleRecorder.bceDiscoveryCalls++;
            MixedLifecycleRecorder.events.add("bce-discovery");
        }
    }

    public static class InvalidSignatureBuildCompatibleExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void invalidRegistrationSignature(String notSupportedYet) {
            // intentionally invalid for step-2 BCE support
        }
    }

    public static class MultiPhaseAnnotatedExtension implements BuildCompatibleExtension {
        @Discovery
        @Validation
        public void invalidMultiPhaseMethod() {
        }
    }

    public static class NonVoidPhaseMethodExtension implements BuildCompatibleExtension {
        @Discovery
        String invalidReturnType() {
            return "x";
        }
    }

    public static class RegistrationContextExtension implements BuildCompatibleExtension {
        @Registration(types = {TrackedBean.class})
        public void registration(InvokerFactory invokerFactory, com.threeamigos.common.util.implementations.injection.bce.BceRegistrationContext context) {
            RegistrationContextRecorder.invokerFactoryInjected = invokerFactory != null;
            RegistrationContextRecorder.contextInjected = context != null;
            if (context != null) {
                for (jakarta.enterprise.inject.build.compatible.spi.BeanInfo beanInfo : context.beans()) {
                    RegistrationContextRecorder.discoveredBeanClassNames.add(
                        beanInfo.declaringClass().name());
                }
            }
        }
    }

    public static class BuildServicesResolverExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(Messages messages) {
            BuildServices buildServices = resolveBuildServices();
            BuildServicesRecorder.discoveryResolverAvailable = messages != null && buildServices != null;
            jakarta.enterprise.lang.model.AnnotationInfo built =
                buildServices.annotationBuilderFactory().create(SampleQualifier.class).build();
            BuildServicesRecorder.annotationBuilderWorked =
                built != null && SampleQualifier.class.getName().equals(built.declaration().name());
        }

        @Registration(types = TrackedBean.class)
        public void registration(jakarta.enterprise.inject.build.compatible.spi.BeanInfo beanInfo) {
            BuildServicesRecorder.registrationResolverAvailable =
                beanInfo != null && resolveBuildServices() != null;
        }

        @Synthesis
        public void synthesis(SyntheticComponents components) {
            BuildServicesRecorder.synthesisResolverAvailable =
                components != null && resolveBuildServices() != null;
        }

        @Validation
        public void validation(Messages messages) {
            BuildServicesRecorder.validationResolverAvailable =
                messages != null && resolveBuildServices() != null;
        }

        private BuildServices resolveBuildServices() {
            try {
                Method getMethod = BuildServicesResolver.class.getDeclaredMethod("get");
                getMethod.setAccessible(true);
                Object resolved = getMethod.invoke(null);
                return resolved instanceof BuildServices ? (BuildServices) resolved : null;
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static class RegistrationContextRecorder {
        private static boolean invokerFactoryInjected;
        private static boolean contextInjected;
        private static final java.util.Set<String> discoveredBeanClassNames = new java.util.HashSet<String>();

        static synchronized void reset() {
            invokerFactoryInjected = false;
            contextInjected = false;
            discoveredBeanClassNames.clear();
        }
    }

    public static class AllowedServiceParametersExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(Types types, Messages messages, MetaAnnotations metaAnnotations, ScannedClasses scannedClasses) {
            ServiceMatrixRecorder.discoveryTypesInjected = types != null;
            ServiceMatrixRecorder.discoveryMessagesInjected = messages != null;
            ServiceMatrixRecorder.discoveryMetaAnnotationsInjected = metaAnnotations != null;
            ServiceMatrixRecorder.discoveryScannedClassesInjected = scannedClasses != null;
        }

        @Enhancement(types = TrackedBean.class, withSubtypes = false)
        public void enhancement(jakarta.enterprise.lang.model.declarations.ClassInfo classInfo,
                                Types types,
                                Messages messages) {
            ServiceMatrixRecorder.enhancementTypesInjected = classInfo != null && types != null;
            ServiceMatrixRecorder.enhancementMessagesInjected = classInfo != null && messages != null;
        }

        @Registration(types = Object.class)
        public void registration(Types types, Messages messages) {
            ServiceMatrixRecorder.registrationTypesInjected = types != null;
            ServiceMatrixRecorder.registrationMessagesInjected = messages != null;
        }

        @Synthesis
        public void synthesis(SyntheticComponents syntheticComponents, Types types, Messages messages) {
            ServiceMatrixRecorder.synthesisTypesInjected = syntheticComponents != null && types != null;
            ServiceMatrixRecorder.synthesisMessagesInjected = syntheticComponents != null && messages != null;
        }

        @Validation
        public void validation(Types types, Messages messages) {
            ServiceMatrixRecorder.validationTypesInjected = types != null;
            ServiceMatrixRecorder.validationMessagesInjected = messages != null;
        }
    }

    public static class InvalidDiscoveryDuplicateTypesExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(Types one, Types two) {
            // invalid by contract: duplicate Types parameter
        }
    }

    public static class InvalidEnhancementMetaAnnotationsExtension implements BuildCompatibleExtension {
        @Enhancement(types = Object.class)
        public void enhancement(MetaAnnotations metaAnnotations) {
            // invalid by contract: MetaAnnotations not allowed in Enhancement
        }
    }

    public static class InvalidRegistrationScannedClassesExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(ScannedClasses scannedClasses) {
            // invalid by contract: ScannedClasses not allowed in Registration
        }
    }

    public static class SynthesisWithoutSyntheticComponentsExtension implements BuildCompatibleExtension {
        @Synthesis
        public void synthesis(Types types, Messages messages) {
            // valid by contract: SyntheticComponents is optional in synthesis.
        }
    }

    public static class InvalidSynthesisDuplicateSyntheticComponentsExtension implements BuildCompatibleExtension {
        @Synthesis
        public void synthesis(SyntheticComponents one, SyntheticComponents two) {
            // invalid by contract: duplicate SyntheticComponents
        }
    }

    public static class InvalidRegistrationDuplicateMessagesExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(Messages one, Messages two) {
            // invalid by contract: duplicate Messages parameter
        }
    }

    public static class InvalidSynthesisMetaAnnotationsExtension implements BuildCompatibleExtension {
        @Synthesis
        public void synthesis(SyntheticComponents syntheticComponents, MetaAnnotations metaAnnotations) {
            // invalid by contract: MetaAnnotations not allowed in Synthesis
        }
    }

    public static class InvalidRegistrationBuildServicesExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(BuildServices buildServices) {
            // invalid by contract: BuildServices parameter not supported in phase callbacks
        }
    }

    public static class InvalidDiscoveryBuildServicesExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(BuildServices buildServices) {
            // invalid by contract: BuildServices parameter not supported in phase callbacks
        }
    }

    public static class InvalidSynthesisBuildServicesExtension implements BuildCompatibleExtension {
        @Synthesis
        public void synthesis(BuildServices buildServices) {
            // invalid by contract: BuildServices parameter not supported in phase callbacks
        }
    }

    public static class InvalidValidationBuildServicesExtension implements BuildCompatibleExtension {
        @Validation
        public void validation(BuildServices buildServices) {
            // invalid by contract: BuildServices parameter not supported in phase callbacks
        }
    }

    public static class EnhancementClassModelExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void enhanceClass(jakarta.enterprise.lang.model.declarations.ClassInfo classInfo, Messages messages) {
            if (EnhancementTargetBean.class.getName().equals(classInfo.name())) {
                EnhancementModelRecorder.classInfoInvocations++;
                messages.info("class-info");
            }
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void enhanceClassConfig(ClassConfig classConfig) {
            if (EnhancementTargetBean.class.getName().equals(classConfig.info().name())) {
                EnhancementModelRecorder.classConfigInvocations++;
            }
        }
    }

    public static class EnhancementMethodModelExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void enhanceMethod(jakarta.enterprise.lang.model.declarations.MethodInfo methodInfo) {
            if ("markedMethod".equals(methodInfo.name())) {
                EnhancementModelRecorder.methodInfoInvocations++;
            }
        }
    }

    public static class EnhancementMethodConfigModelExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void enhanceMethodConfig(jakarta.enterprise.inject.build.compatible.spi.MethodConfig methodConfig) {
            if ("markedMethod".equals(methodConfig.info().name())) {
                EnhancementModelRecorder.methodConfigInvocations++;
            }
        }
    }

    public static class EnhancementFieldModelExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void enhanceField(jakarta.enterprise.lang.model.declarations.FieldInfo fieldInfo) {
            if ("markedField".equals(fieldInfo.name())) {
                EnhancementModelRecorder.fieldInfoInvocations++;
            }
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void enhanceFieldConfig(jakarta.enterprise.inject.build.compatible.spi.FieldConfig fieldConfig) {
            if ("markedField".equals(fieldConfig.info().name())) {
                EnhancementModelRecorder.fieldConfigInvocations++;
            }
        }
    }

    public static class EnhancementClassConfigGraphExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementConfigGraphTarget.class, withSubtypes = false)
        public void enhance(jakarta.enterprise.inject.build.compatible.spi.ClassConfig classConfig) {
            if (!EnhancementConfigGraphTarget.class.getName().equals(classConfig.info().name())) {
                return;
            }
            EnhancementConfigGraphRecorder.classConfigSeen++;
            EnhancementConfigGraphRecorder.constructorsCount += classConfig.constructors().size();
            EnhancementConfigGraphRecorder.methodsCount += classConfig.methods().size();
            EnhancementConfigGraphRecorder.fieldsCount += classConfig.fields().size();
            for (jakarta.enterprise.inject.build.compatible.spi.MethodConfig methodConfig : classConfig.methods()) {
                EnhancementConfigGraphRecorder.parameterConfigsCount += methodConfig.parameters().size();
            }
            for (jakarta.enterprise.inject.build.compatible.spi.MethodConfig constructorConfig : classConfig.constructors()) {
                EnhancementConfigGraphRecorder.parameterConfigsCount += constructorConfig.parameters().size();
            }
        }
    }

    public static class EnhancementAnnotationMutationExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementConfigGraphTarget.class, withSubtypes = false)
        public void enhance(ClassConfig classConfig) {
            if (!EnhancementConfigGraphTarget.class.getName().equals(classConfig.info().name())) {
                return;
            }

            classConfig.addAnnotation(MutableAdded.class);
            EnhancementAnnotationMutationRecorder.classAddedVisible = classConfig.info().hasAnnotation(MutableAdded.class);
            classConfig.removeAnnotation(a -> MutableAdded.class.getName().equals(a.declaration().name()));
            EnhancementAnnotationMutationRecorder.classRemovedVisible = !classConfig.info().hasAnnotation(MutableAdded.class);

            for (jakarta.enterprise.inject.build.compatible.spi.MethodConfig methodConfig : classConfig.methods()) {
                if (!"update".equals(methodConfig.info().name())) {
                    continue;
                }
                methodConfig.addAnnotation(MutableAdded.class);
                EnhancementAnnotationMutationRecorder.methodAddedVisible =
                    methodConfig.info().hasAnnotation(MutableAdded.class);
                methodConfig.removeAnnotation(a -> MutableAdded.class.getName().equals(a.declaration().name()));
                EnhancementAnnotationMutationRecorder.methodRemovedVisible =
                    !methodConfig.info().hasAnnotation(MutableAdded.class);

                if (!methodConfig.parameters().isEmpty()) {
                    jakarta.enterprise.inject.build.compatible.spi.ParameterConfig parameterConfig =
                        methodConfig.parameters().get(0);
                    parameterConfig.addAnnotation(MutableAdded.class);
                    EnhancementAnnotationMutationRecorder.parameterAddedVisible =
                        parameterConfig.info().hasAnnotation(MutableAdded.class);
                    parameterConfig.removeAnnotation(a -> MutableAdded.class.getName().equals(a.declaration().name()));
                    EnhancementAnnotationMutationRecorder.parameterRemovedVisible =
                        !parameterConfig.info().hasAnnotation(MutableAdded.class);
                }
            }

            for (jakarta.enterprise.inject.build.compatible.spi.FieldConfig fieldConfig : classConfig.fields()) {
                if (!"data".equals(fieldConfig.info().name())) {
                    continue;
                }
                fieldConfig.addAnnotation(MutableAdded.class);
                EnhancementAnnotationMutationRecorder.fieldAddedVisible =
                    fieldConfig.info().hasAnnotation(MutableAdded.class);
                fieldConfig.removeAnnotation(a -> MutableAdded.class.getName().equals(a.declaration().name()));
                EnhancementAnnotationMutationRecorder.fieldRemovedVisible =
                    !fieldConfig.info().hasAnnotation(MutableAdded.class);
            }
        }
    }

    public static class EnhancementCrossMethodPersistenceExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementConfigGraphTarget.class, withSubtypes = false)
        public void aAddMarker(ClassConfig classConfig) {
            if (!EnhancementConfigGraphTarget.class.getName().equals(classConfig.info().name())) {
                return;
            }
            classConfig.addAnnotation(MutableAdded.class);
            EnhancementCrossMethodPersistenceRecorder.addMethodExecuted = true;
        }

        @Enhancement(types = EnhancementConfigGraphTarget.class, withSubtypes = false)
        public void zVerifyMarker(ClassConfig classConfig) {
            if (!EnhancementConfigGraphTarget.class.getName().equals(classConfig.info().name())) {
                return;
            }
            EnhancementCrossMethodPersistenceRecorder.verifyMethodSawMutation =
                classConfig.info().hasAnnotation(MutableAdded.class);
        }
    }

    public static class EnhancementCrossViewPersistenceExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void aMutateMethodConfig(jakarta.enterprise.inject.build.compatible.spi.MethodConfig methodConfig) {
            if (!"markedMethod".equals(methodConfig.info().name())) {
                return;
            }
            methodConfig.addAnnotation(MutableAdded.class);
            EnhancementCrossViewPersistenceRecorder.methodMutationExecuted = true;
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void zVerifyViaClassConfig(ClassConfig classConfig) {
            if (!EnhancementTargetBean.class.getName().equals(classConfig.info().name())) {
                return;
            }
            for (jakarta.enterprise.inject.build.compatible.spi.MethodConfig methodConfig : classConfig.methods()) {
                if ("markedMethod".equals(methodConfig.info().name())) {
                    EnhancementCrossViewPersistenceRecorder.classViewSawMethodMutation =
                        methodConfig.info().hasAnnotation(MutableAdded.class);
                }
            }
        }
    }

    public static class EnhancementFieldCrossViewExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void aMutateFieldConfig(jakarta.enterprise.inject.build.compatible.spi.FieldConfig fieldConfig) {
            if (!"markedField".equals(fieldConfig.info().name())) {
                return;
            }
            fieldConfig.addAnnotation(MutableAdded.class);
            EnhancementFieldCrossViewRecorder.fieldConfigMutationExecuted = true;
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void bVerifyClassSeesFieldMutation(ClassConfig classConfig) {
            if (!EnhancementTargetBean.class.getName().equals(classConfig.info().name())) {
                return;
            }
            for (jakarta.enterprise.inject.build.compatible.spi.FieldConfig fieldConfig : classConfig.fields()) {
                if ("markedField".equals(fieldConfig.info().name())) {
                    EnhancementFieldCrossViewRecorder.classViewSawFieldMutation =
                        fieldConfig.info().hasAnnotation(MutableAdded.class);
                }
            }
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void cMutateClassFieldView(ClassConfig classConfig) {
            if (!EnhancementTargetBean.class.getName().equals(classConfig.info().name())) {
                return;
            }
            for (jakarta.enterprise.inject.build.compatible.spi.FieldConfig fieldConfig : classConfig.fields()) {
                if ("markedField".equals(fieldConfig.info().name())) {
                    fieldConfig.addAnnotation(MutableClassAdded.class);
                    EnhancementFieldCrossViewRecorder.classMutationExecuted = true;
                }
            }
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void zVerifyFieldInfoSeesClassMutation(jakarta.enterprise.lang.model.declarations.FieldInfo fieldInfo) {
            if (!"markedField".equals(fieldInfo.name())) {
                return;
            }
            EnhancementFieldCrossViewRecorder.fieldInfoSawClassMutation =
                fieldInfo.hasAnnotation(MutableClassAdded.class);
        }
    }

    public static class EnhancementMethodReverseCrossViewExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void aMutateClassMethodView(ClassConfig classConfig) {
            if (!EnhancementTargetBean.class.getName().equals(classConfig.info().name())) {
                return;
            }
            for (jakarta.enterprise.inject.build.compatible.spi.MethodConfig methodConfig : classConfig.methods()) {
                if ("markedMethod".equals(methodConfig.info().name())) {
                    methodConfig.addAnnotation(MutableClassAdded.class);
                    EnhancementMethodReverseCrossViewRecorder.classMutationExecuted = true;
                }
            }
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void zVerifyMethodInfoSeesClassMutation(jakarta.enterprise.lang.model.declarations.MethodInfo methodInfo) {
            if (!"markedMethod".equals(methodInfo.name())) {
                return;
            }
            EnhancementMethodReverseCrossViewRecorder.methodInfoSawClassMutation =
                methodInfo.hasAnnotation(MutableClassAdded.class);
        }
    }

    public static class EnhancementRemoveAllCrossViewExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void aRemoveMethodAnnotations(jakarta.enterprise.inject.build.compatible.spi.MethodConfig methodConfig) {
            if (!"markedMethod".equals(methodConfig.info().name())) {
                return;
            }
            methodConfig.removeAllAnnotations();
            EnhancementRemoveAllCrossViewRecorder.methodRemoveAllExecuted = true;
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void bVerifyMethodViaClassConfig(ClassConfig classConfig) {
            if (!EnhancementTargetBean.class.getName().equals(classConfig.info().name())) {
                return;
            }
            for (jakarta.enterprise.inject.build.compatible.spi.MethodConfig methodConfig : classConfig.methods()) {
                if ("markedMethod".equals(methodConfig.info().name())) {
                    EnhancementRemoveAllCrossViewRecorder.classViewSawMethodRemoved =
                        !methodConfig.info().hasAnnotation(EnhancedMarker.class);
                }
            }
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void cVerifyMethodViaMethodInfo(jakarta.enterprise.lang.model.declarations.MethodInfo methodInfo) {
            if (!"markedMethod".equals(methodInfo.name())) {
                return;
            }
            EnhancementRemoveAllCrossViewRecorder.methodInfoSawRemoved =
                !methodInfo.hasAnnotation(EnhancedMarker.class);
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void dRemoveFieldAnnotations(jakarta.enterprise.inject.build.compatible.spi.FieldConfig fieldConfig) {
            if (!"markedField".equals(fieldConfig.info().name())) {
                return;
            }
            fieldConfig.removeAllAnnotations();
            EnhancementRemoveAllCrossViewRecorder.fieldRemoveAllExecuted = true;
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void eVerifyFieldViaClassConfig(ClassConfig classConfig) {
            if (!EnhancementTargetBean.class.getName().equals(classConfig.info().name())) {
                return;
            }
            for (jakarta.enterprise.inject.build.compatible.spi.FieldConfig fieldConfig : classConfig.fields()) {
                if ("markedField".equals(fieldConfig.info().name())) {
                    EnhancementRemoveAllCrossViewRecorder.classViewSawFieldRemoved =
                        !fieldConfig.info().hasAnnotation(EnhancedMarker.class);
                }
            }
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void fVerifyFieldViaFieldInfo(jakarta.enterprise.lang.model.declarations.FieldInfo fieldInfo) {
            if (!"markedField".equals(fieldInfo.name())) {
                return;
            }
            EnhancementRemoveAllCrossViewRecorder.fieldInfoSawRemoved =
                !fieldInfo.hasAnnotation(EnhancedMarker.class);
        }
    }

    public static class EnhancementRemovePredicateCrossViewExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void aRemoveMethodEnhanced(jakarta.enterprise.inject.build.compatible.spi.MethodConfig methodConfig) {
            if (!"markedMethod".equals(methodConfig.info().name())) {
                return;
            }
            methodConfig.removeAnnotation(a -> EnhancedMarker.class.getName().equals(a.declaration().name()));
            EnhancementRemovePredicateCrossViewRecorder.methodRemoveExecuted = true;
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void bVerifyMethodViaClassConfig(ClassConfig classConfig) {
            if (!EnhancementTargetBean.class.getName().equals(classConfig.info().name())) {
                return;
            }
            for (jakarta.enterprise.inject.build.compatible.spi.MethodConfig methodConfig : classConfig.methods()) {
                if ("markedMethod".equals(methodConfig.info().name())) {
                    EnhancementRemovePredicateCrossViewRecorder.methodClassViewRemoved =
                        !methodConfig.info().hasAnnotation(EnhancedMarker.class);
                }
            }
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void cVerifyMethodViaMethodInfo(jakarta.enterprise.lang.model.declarations.MethodInfo methodInfo) {
            if (!"markedMethod".equals(methodInfo.name())) {
                return;
            }
            EnhancementRemovePredicateCrossViewRecorder.methodInfoRemoved =
                !methodInfo.hasAnnotation(EnhancedMarker.class);
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void dRemoveFieldEnhanced(jakarta.enterprise.inject.build.compatible.spi.FieldConfig fieldConfig) {
            if (!"markedField".equals(fieldConfig.info().name())) {
                return;
            }
            fieldConfig.removeAnnotation(a -> EnhancedMarker.class.getName().equals(a.declaration().name()));
            EnhancementRemovePredicateCrossViewRecorder.fieldRemoveExecuted = true;
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void eVerifyFieldViaClassConfig(ClassConfig classConfig) {
            if (!EnhancementTargetBean.class.getName().equals(classConfig.info().name())) {
                return;
            }
            for (jakarta.enterprise.inject.build.compatible.spi.FieldConfig fieldConfig : classConfig.fields()) {
                if ("markedField".equals(fieldConfig.info().name())) {
                    EnhancementRemovePredicateCrossViewRecorder.fieldClassViewRemoved =
                        !fieldConfig.info().hasAnnotation(EnhancedMarker.class);
                }
            }
        }

        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void fVerifyFieldViaFieldInfo(jakarta.enterprise.lang.model.declarations.FieldInfo fieldInfo) {
            if (!"markedField".equals(fieldInfo.name())) {
                return;
            }
            EnhancementRemovePredicateCrossViewRecorder.fieldInfoRemoved =
                !fieldInfo.hasAnnotation(EnhancedMarker.class);
        }
    }

    public static class EnhancementParameterCrossViewExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementConfigGraphTarget.class, withSubtypes = false)
        public void aAddParameterAnnotation(jakarta.enterprise.inject.build.compatible.spi.MethodConfig methodConfig) {
            if (!"update".equals(methodConfig.info().name()) || methodConfig.parameters().isEmpty()) {
                return;
            }
            methodConfig.parameters().get(0).addAnnotation(MutableAdded.class);
            EnhancementParameterCrossViewRecorder.parameterAddExecuted = true;
        }

        @Enhancement(types = EnhancementConfigGraphTarget.class, withSubtypes = false)
        public void bVerifyParameterAdded(jakarta.enterprise.lang.model.declarations.MethodInfo methodInfo) {
            if (!"update".equals(methodInfo.name()) || methodInfo.parameters().isEmpty()) {
                return;
            }
            EnhancementParameterCrossViewRecorder.methodInfoSawParameterAdded =
                methodInfo.parameters().get(0).hasAnnotation(MutableAdded.class);
        }

        @Enhancement(types = EnhancementConfigGraphTarget.class, withSubtypes = false)
        public void cRemoveParameterAnnotation(jakarta.enterprise.inject.build.compatible.spi.MethodConfig methodConfig) {
            if (!"update".equals(methodConfig.info().name()) || methodConfig.parameters().isEmpty()) {
                return;
            }
            methodConfig.parameters().get(0)
                .removeAnnotation(a -> MutableAdded.class.getName().equals(a.declaration().name()));
            EnhancementParameterCrossViewRecorder.parameterRemoveExecuted = true;
        }

        @Enhancement(types = EnhancementConfigGraphTarget.class, withSubtypes = false)
        public void dVerifyParameterRemoved(jakarta.enterprise.lang.model.declarations.MethodInfo methodInfo) {
            if (!"update".equals(methodInfo.name()) || methodInfo.parameters().isEmpty()) {
                return;
            }
            EnhancementParameterCrossViewRecorder.methodInfoSawParameterRemoved =
                !methodInfo.parameters().get(0).hasAnnotation(MutableAdded.class);
        }
    }

    public static class LanguageModelRepeatableExtension implements BuildCompatibleExtension {
        @Enhancement(types = RepeatableTargetBean.class, withSubtypes = false)
        public void inspect(jakarta.enterprise.lang.model.declarations.ClassInfo classInfo) {
            if (!RepeatableTargetBean.class.getName().equals(classInfo.name())) {
                return;
            }
            LanguageModelEdgeCaseRecorder.repeatableCountOnClassInfo =
                classInfo.repeatableAnnotation(RepeatableEdge.class).size();
        }

        @Enhancement(types = RepeatableTargetBean.class, withSubtypes = false)
        public void inspectConfig(ClassConfig classConfig) {
            if (!RepeatableTargetBean.class.getName().equals(classConfig.info().name())) {
                return;
            }
            LanguageModelEdgeCaseRecorder.repeatableCountOnClassConfigInfo =
                classConfig.info().repeatableAnnotation(RepeatableEdge.class).size();
        }
    }

    public static class InvalidEnhancementMultipleModelsExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTargetBean.class)
        public void enhance(jakarta.enterprise.lang.model.declarations.ClassInfo classInfo,
                     jakarta.enterprise.lang.model.declarations.MethodInfo methodInfo) {
            // invalid by contract: only one enhancement model/config parameter allowed
        }
    }

    public static class ScannedClassesAddsBeanExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(ScannedClasses scannedClasses) {
            scannedClasses.add(ScannedAddedBean.class.getName());
            ScannedClassesRecorder.discoveryRan = true;
        }
    }

    public static class InvalidScannedClassesExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(ScannedClasses scannedClasses) {
            scannedClasses.add("missing.DoesNotExist");
        }
    }

    public static class EnhancementWithSubtypesTrueExtension implements BuildCompatibleExtension {
        @Enhancement(types = BaseEnhancementType.class, withSubtypes = true)
        public void enhance(jakarta.enterprise.lang.model.declarations.ClassInfo classInfo) {
            if (BaseEnhancementType.class.getName().equals(classInfo.name()) ||
                SubEnhancementType.class.getName().equals(classInfo.name())) {
                SubtypeEnhancementRecorder.classInfoHits++;
            }
        }
    }

    public static class EnhancementWithSubtypesFalseExtension implements BuildCompatibleExtension {
        @Enhancement(types = BaseEnhancementType.class, withSubtypes = false)
        public void enhance(jakarta.enterprise.lang.model.declarations.ClassInfo classInfo) {
            if (BaseEnhancementType.class.getName().equals(classInfo.name())) {
                SubtypeEnhancementRecorder.classInfoHits++;
            }
        }
    }

    public static class EnhancementModelWithSupportedServicesExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = EnhancedMarker.class)
        public void enhance(jakarta.enterprise.lang.model.declarations.MethodInfo methodInfo,
                     Types types,
                     Messages messages) {
            EnhancementServiceModelRecorder.modelInjected = methodInfo != null;
            EnhancementServiceModelRecorder.typesInjected = types != null;
            EnhancementServiceModelRecorder.messagesInjected = messages != null;
        }
    }

    public static class InvalidEnhancementDuplicateMessagesWithModelExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTargetBean.class)
        public void enhance(jakarta.enterprise.lang.model.declarations.ClassInfo classInfo,
                     Messages one,
                     Messages two) {
            // invalid by contract: duplicate Messages
        }
    }

    public static class EnhancementClassNoMatchExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = NonMatchingMarker.class)
        public void enhance(jakarta.enterprise.lang.model.declarations.ClassInfo classInfo) {
            EnhancementNoMatchRecorder.classInfoHits++;
        }
    }

    public static class EnhancementMethodNoMatchExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTargetBean.class, withSubtypes = false, withAnnotations = NonMatchingMarker.class)
        public void enhance(jakarta.enterprise.lang.model.declarations.MethodInfo methodInfo) {
            EnhancementNoMatchRecorder.methodInfoHits++;
        }
    }

    public static class InvalidDiscoveryDuplicateMetaAnnotationsExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(MetaAnnotations one, MetaAnnotations two) {
            // invalid by contract: duplicate MetaAnnotations
        }
    }

    public static class InvalidDiscoveryDuplicateScannedClassesExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(ScannedClasses one, ScannedClasses two) {
            // invalid by contract: duplicate ScannedClasses
        }
    }

    public static class InvalidEnhancementBuildServicesExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTargetBean.class)
        public void enhance(BuildServices buildServices) {
            // invalid by contract: BuildServices parameter not supported in phase callbacks
        }
    }

    public static class InvalidValidationDuplicateMessagesExtension implements BuildCompatibleExtension {
        @Validation
        public void validate(Messages one, Messages two) {
            // invalid by contract: duplicate Messages
        }
    }

    public static class RegistrationModelParametersExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(jakarta.enterprise.inject.build.compatible.spi.BeanInfo beanInfo) {
            if (beanInfo != null) {
                RegistrationValidationModelRecorder.registrationBeanInfoCount++;
            }
        }
    }

    public static class ValidationModelParametersExtension implements BuildCompatibleExtension {
        @Validation
        public void validation(jakarta.enterprise.inject.build.compatible.spi.BeanInfo beanInfo) {
            if (beanInfo != null) {
                RegistrationValidationModelRecorder.validationBeanInfoCount++;
            }
        }

        @Validation
        public void validationObserver(jakarta.enterprise.inject.build.compatible.spi.ObserverInfo observerInfo) {
            if (observerInfo != null) {
                RegistrationValidationModelRecorder.validationObserverInfoCount++;
            }
        }
    }

    public static class InvalidRegistrationMultipleModelsExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(jakarta.enterprise.inject.build.compatible.spi.BeanInfo beanInfo,
                          jakarta.enterprise.inject.build.compatible.spi.ObserverInfo observerInfo) {
            // invalid by contract: more than one model parameter
        }
    }

    public static class InvalidValidationMultipleModelsExtension implements BuildCompatibleExtension {
        @Validation
        public void validation(jakarta.enterprise.inject.build.compatible.spi.BeanInfo beanInfo,
                        jakarta.enterprise.inject.build.compatible.spi.ObserverInfo observerInfo) {
            // invalid by contract: more than one model parameter
        }
    }

    public static class RegistrationInterceptorModelExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(jakarta.enterprise.inject.build.compatible.spi.InterceptorInfo interceptorInfo) {
            if (interceptorInfo != null) {
                InterceptorModelRecorder.registrationInterceptorInfoCount++;
            }
        }
    }

    public static class ValidationInterceptorModelExtension implements BuildCompatibleExtension {
        @Validation
        public void validation(jakarta.enterprise.inject.build.compatible.spi.InterceptorInfo interceptorInfo) {
            if (interceptorInfo != null) {
                InterceptorModelRecorder.validationInterceptorInfoCount++;
            }
        }
    }

    public static class InvalidRegistrationBeanAndInterceptorModelsExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(jakarta.enterprise.inject.build.compatible.spi.BeanInfo beanInfo,
                          jakarta.enterprise.inject.build.compatible.spi.InterceptorInfo interceptorInfo) {
            // invalid by contract: more than one model parameter
        }
    }

    public static class RegistrationInjectionPointModelExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(jakarta.enterprise.inject.build.compatible.spi.InjectionPointInfo injectionPointInfo) {
            if (injectionPointInfo != null) {
                InjectionPointModelRecorder.registrationInjectionPointInfoCount++;
            }
        }
    }

    public static class ValidationInjectionPointModelExtension implements BuildCompatibleExtension {
        @Validation
        public void validation(jakarta.enterprise.inject.build.compatible.spi.InjectionPointInfo injectionPointInfo) {
            if (injectionPointInfo != null) {
                InjectionPointModelRecorder.validationInjectionPointInfoCount++;
            }
        }
    }

    public static class InvalidValidationBeanAndInjectionPointModelsExtension implements BuildCompatibleExtension {
        @Validation
        public void validation(jakarta.enterprise.inject.build.compatible.spi.BeanInfo beanInfo,
                        jakarta.enterprise.inject.build.compatible.spi.InjectionPointInfo injectionPointInfo) {
            // invalid by contract: more than one model parameter
        }
    }

    public static class RegistrationDisposerModelExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(DisposerInfo disposerInfo) {
            if (disposerInfo != null) {
                DisposerModelRecorder.registrationDisposerInfoCount++;
            }
        }
    }

    public static class ValidationDisposerModelExtension implements BuildCompatibleExtension {
        @Validation
        public void validation(DisposerInfo disposerInfo) {
            if (disposerInfo != null) {
                DisposerModelRecorder.validationDisposerInfoCount++;
            }
        }
    }

    public static class InvalidRegistrationBeanAndDisposerModelsExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(jakarta.enterprise.inject.build.compatible.spi.BeanInfo beanInfo,
                          DisposerInfo disposerInfo) {
            // invalid by contract: more than one model parameter
        }
    }

    public static class RegistrationScopeModelExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(ScopeInfo scopeInfo) {
            if (scopeInfo != null) {
                ScopeModelRecorder.registrationScopeInfoCount++;
            }
        }
    }

    public static class ValidationScopeModelExtension implements BuildCompatibleExtension {
        @Validation
        public void validation(ScopeInfo scopeInfo) {
            if (scopeInfo != null) {
                ScopeModelRecorder.validationScopeInfoCount++;
            }
        }
    }

    public static class InvalidValidationBeanAndScopeModelsExtension implements BuildCompatibleExtension {
        @Validation
        public void validation(jakarta.enterprise.inject.build.compatible.spi.BeanInfo beanInfo,
                        ScopeInfo scopeInfo) {
            // invalid by contract: more than one model parameter
        }
    }

    public static class RegistrationStereotypeModelExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(StereotypeInfo stereotypeInfo) {
            if (stereotypeInfo != null) {
                StereotypeModelRecorder.registrationStereotypeInfoCount++;
            }
        }
    }

    public static class ValidationStereotypeModelExtension implements BuildCompatibleExtension {
        @Validation
        public void validation(StereotypeInfo stereotypeInfo) {
            if (stereotypeInfo != null) {
                StereotypeModelRecorder.validationStereotypeInfoCount++;
            }
        }
    }

    public static class InvalidRegistrationBeanAndStereotypeModelsExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(jakarta.enterprise.inject.build.compatible.spi.BeanInfo beanInfo,
                          StereotypeInfo stereotypeInfo) {
            // invalid by contract: more than one model parameter
        }
    }

    public static class SyntheticObserverParityExtension implements BuildCompatibleExtension {
        @Synthesis
        public void synthesis(SyntheticComponents syntheticComponents) {
            syntheticComponents.addObserver(FixtureEvent.class)
                .declaringClass(StereotypeFixtureBean.class)
                .observeWith(FixtureSyntheticObserver.class);
        }

        @Validation
        public void validation(jakarta.enterprise.inject.build.compatible.spi.ObserverInfo observerInfo, Messages messages) {
            if (observerInfo != null && observerInfo.isSynthetic()) {
                SyntheticObserverParityRecorder.syntheticObserverSeen++;
                if (observerInfo.observerMethod() != null) {
                    SyntheticObserverParityRecorder.syntheticObserverMethodAccessible++;
                }
                if (observerInfo.eventParameter() != null) {
                    SyntheticObserverParityRecorder.syntheticEventParameterAccessible++;
                }
                if (observerInfo.bean() != null) {
                    SyntheticObserverParityRecorder.syntheticBeanAccessible++;
                }
                messages.info("synthetic observer info", observerInfo);
                SyntheticObserverParityRecorder.messagesObserverCalls++;
            }
        }
    }

    public static class AlphaOrderedExtension implements BuildCompatibleExtension {
        @Discovery
        public void second() {
            OrderingRecorder.record("alpha-second");
        }

        @Discovery
        public void first() {
            OrderingRecorder.record("alpha-first");
        }
    }

    public static class BetaOrderedExtension implements BuildCompatibleExtension {
        @Discovery
        public void only() {
            OrderingRecorder.record("beta-only");
        }
    }

    public static class DuplicateBceExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery() {
            DuplicateRegistrationRecorder.discoveryCalls++;
        }
    }

    public static class MessagesErrorExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(Messages messages) {
            messages.error("forced diagnostics deployment error");
        }
    }

    public static class MessagesErrorExceptionExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(Messages messages) {
            messages.error(new IllegalStateException("forced diagnostics exception"));
        }
    }

    public static class MessagesInfoWarnExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(Messages messages) {
            messages.info("bce diagnostics info");
            messages.warn("bce diagnostics warn");
            DiagnosticsRecorder.discoveryRan = true;
        }

        @Validation
        public void validation(Messages messages) {
            messages.info("bce diagnostics validation");
            DiagnosticsRecorder.validationRan = true;
        }
    }

    public static class PortableDuplicateExtension implements Extension {
        public void observeAfterBeanDiscovery(@Observes AfterBeanDiscovery ignored) {
            PortableDuplicateRecorder.afterBeanDiscoveryCalls++;
        }
    }

    public static class MixedLifecycleRecorder {
        private static int portableAfterBeanDiscoveryCalls;
        private static int bceDiscoveryCalls;
        private static final java.util.List<String> events = new java.util.ArrayList<String>();

        static synchronized void reset() {
            portableAfterBeanDiscoveryCalls = 0;
            bceDiscoveryCalls = 0;
            events.clear();
        }
    }

    public static class BuildServicesRecorder {
        private static boolean discoveryResolverAvailable;
        private static boolean registrationResolverAvailable;
        private static boolean synthesisResolverAvailable;
        private static boolean validationResolverAvailable;
        private static boolean annotationBuilderWorked;

        static synchronized void reset() {
            discoveryResolverAvailable = false;
            registrationResolverAvailable = false;
            synthesisResolverAvailable = false;
            validationResolverAvailable = false;
            annotationBuilderWorked = false;
        }
    }

    public static class ServiceMatrixRecorder {
        private static boolean discoveryTypesInjected;
        private static boolean discoveryMessagesInjected;
        private static boolean discoveryMetaAnnotationsInjected;
        private static boolean discoveryScannedClassesInjected;
        private static boolean enhancementTypesInjected;
        private static boolean enhancementMessagesInjected;
        private static boolean registrationTypesInjected;
        private static boolean registrationMessagesInjected;
        private static boolean synthesisTypesInjected;
        private static boolean synthesisMessagesInjected;
        private static boolean validationTypesInjected;
        private static boolean validationMessagesInjected;

        static synchronized void reset() {
            discoveryTypesInjected = false;
            discoveryMessagesInjected = false;
            discoveryMetaAnnotationsInjected = false;
            discoveryScannedClassesInjected = false;
            enhancementTypesInjected = false;
            enhancementMessagesInjected = false;
            registrationTypesInjected = false;
            registrationMessagesInjected = false;
            synthesisTypesInjected = false;
            synthesisMessagesInjected = false;
            validationTypesInjected = false;
            validationMessagesInjected = false;
        }
    }

    public static class OrderingRecorder {
        private static final java.util.List<String> EVENTS = new java.util.ArrayList<String>();

        static synchronized void record(String event) {
            EVENTS.add(event);
        }

        static synchronized java.util.List<String> snapshot() {
            return new java.util.ArrayList<String>(EVENTS);
        }

        static synchronized void reset() {
            EVENTS.clear();
        }
    }

    public static class DuplicateRegistrationRecorder {
        private static int discoveryCalls;

        static synchronized void reset() {
            discoveryCalls = 0;
        }
    }

    public static class DiagnosticsRecorder {
        private static boolean discoveryRan;
        private static boolean validationRan;

        static synchronized void reset() {
            discoveryRan = false;
            validationRan = false;
        }
    }

    public static class PortableDuplicateRecorder {
        private static int afterBeanDiscoveryCalls;

        static synchronized void reset() {
            afterBeanDiscoveryCalls = 0;
        }
    }

    public static class EnhancementModelRecorder {
        private static int classInfoInvocations;
        private static int classConfigInvocations;
        private static int methodInfoInvocations;
        private static int methodConfigInvocations;
        private static int fieldInfoInvocations;
        private static int fieldConfigInvocations;

        static synchronized void reset() {
            classInfoInvocations = 0;
            classConfigInvocations = 0;
            methodInfoInvocations = 0;
            methodConfigInvocations = 0;
            fieldInfoInvocations = 0;
            fieldConfigInvocations = 0;
        }
    }

    public static class ScannedClassesRecorder {
        private static boolean discoveryRan;

        static synchronized void reset() {
            discoveryRan = false;
        }
    }

    public static class SubtypeEnhancementRecorder {
        private static int classInfoHits;

        static synchronized void reset() {
            classInfoHits = 0;
        }
    }

    public static class EnhancementServiceModelRecorder {
        private static boolean modelInjected;
        private static boolean typesInjected;
        private static boolean messagesInjected;

        static synchronized void reset() {
            modelInjected = false;
            typesInjected = false;
            messagesInjected = false;
        }
    }

    public static class EnhancementNoMatchRecorder {
        private static int classInfoHits;
        private static int methodInfoHits;

        static synchronized void reset() {
            classInfoHits = 0;
            methodInfoHits = 0;
        }
    }

    public static class RegistrationValidationModelRecorder {
        private static int registrationBeanInfoCount;
        private static int validationBeanInfoCount;
        private static int validationObserverInfoCount;

        static synchronized void reset() {
            registrationBeanInfoCount = 0;
            validationBeanInfoCount = 0;
            validationObserverInfoCount = 0;
        }
    }

    public static class InterceptorModelRecorder {
        private static int registrationInterceptorInfoCount;
        private static int validationInterceptorInfoCount;

        static synchronized void reset() {
            registrationInterceptorInfoCount = 0;
            validationInterceptorInfoCount = 0;
        }
    }

    public static class InjectionPointModelRecorder {
        private static int registrationInjectionPointInfoCount;
        private static int validationInjectionPointInfoCount;

        static synchronized void reset() {
            registrationInjectionPointInfoCount = 0;
            validationInjectionPointInfoCount = 0;
        }
    }

    public static class DisposerModelRecorder {
        private static int registrationDisposerInfoCount;
        private static int validationDisposerInfoCount;

        static synchronized void reset() {
            registrationDisposerInfoCount = 0;
            validationDisposerInfoCount = 0;
        }
    }

    public static class ScopeModelRecorder {
        private static int registrationScopeInfoCount;
        private static int validationScopeInfoCount;

        static synchronized void reset() {
            registrationScopeInfoCount = 0;
            validationScopeInfoCount = 0;
        }
    }

    public static class StereotypeModelRecorder {
        private static int registrationStereotypeInfoCount;
        private static int validationStereotypeInfoCount;

        static synchronized void reset() {
            registrationStereotypeInfoCount = 0;
            validationStereotypeInfoCount = 0;
        }
    }

    public static class SyntheticObserverParityRecorder {
        private static int syntheticObserverSeen;
        private static int messagesObserverCalls;
        private static int syntheticObserverMethodAccessible;
        private static int syntheticEventParameterAccessible;
        private static int syntheticBeanAccessible;

        static synchronized void reset() {
            syntheticObserverSeen = 0;
            messagesObserverCalls = 0;
            syntheticObserverMethodAccessible = 0;
            syntheticEventParameterAccessible = 0;
            syntheticBeanAccessible = 0;
        }
    }

    public static class EnhancementConfigGraphRecorder {
        private static int classConfigSeen;
        private static int constructorsCount;
        private static int methodsCount;
        private static int fieldsCount;
        private static int parameterConfigsCount;

        static synchronized void reset() {
            classConfigSeen = 0;
            constructorsCount = 0;
            methodsCount = 0;
            fieldsCount = 0;
            parameterConfigsCount = 0;
        }
    }

    public static class EnhancementAnnotationMutationRecorder {
        private static boolean classAddedVisible;
        private static boolean classRemovedVisible;
        private static boolean methodAddedVisible;
        private static boolean methodRemovedVisible;
        private static boolean fieldAddedVisible;
        private static boolean fieldRemovedVisible;
        private static boolean parameterAddedVisible;
        private static boolean parameterRemovedVisible;

        static synchronized void reset() {
            classAddedVisible = false;
            classRemovedVisible = false;
            methodAddedVisible = false;
            methodRemovedVisible = false;
            fieldAddedVisible = false;
            fieldRemovedVisible = false;
            parameterAddedVisible = false;
            parameterRemovedVisible = false;
        }
    }

    public static class EnhancementCrossMethodPersistenceRecorder {
        private static boolean addMethodExecuted;
        private static boolean verifyMethodSawMutation;

        static synchronized void reset() {
            addMethodExecuted = false;
            verifyMethodSawMutation = false;
        }
    }

    public static class EnhancementCrossViewPersistenceRecorder {
        private static boolean methodMutationExecuted;
        private static boolean classViewSawMethodMutation;

        static synchronized void reset() {
            methodMutationExecuted = false;
            classViewSawMethodMutation = false;
        }
    }

    public static class EnhancementFieldCrossViewRecorder {
        private static boolean fieldConfigMutationExecuted;
        private static boolean classViewSawFieldMutation;
        private static boolean classMutationExecuted;
        private static boolean fieldInfoSawClassMutation;

        static synchronized void reset() {
            fieldConfigMutationExecuted = false;
            classViewSawFieldMutation = false;
            classMutationExecuted = false;
            fieldInfoSawClassMutation = false;
        }
    }

    public static class EnhancementMethodReverseCrossViewRecorder {
        private static boolean classMutationExecuted;
        private static boolean methodInfoSawClassMutation;

        static synchronized void reset() {
            classMutationExecuted = false;
            methodInfoSawClassMutation = false;
        }
    }

    public static class EnhancementRemoveAllCrossViewRecorder {
        private static boolean methodRemoveAllExecuted;
        private static boolean classViewSawMethodRemoved;
        private static boolean methodInfoSawRemoved;
        private static boolean fieldRemoveAllExecuted;
        private static boolean classViewSawFieldRemoved;
        private static boolean fieldInfoSawRemoved;

        static synchronized void reset() {
            methodRemoveAllExecuted = false;
            classViewSawMethodRemoved = false;
            methodInfoSawRemoved = false;
            fieldRemoveAllExecuted = false;
            classViewSawFieldRemoved = false;
            fieldInfoSawRemoved = false;
        }
    }

    public static class EnhancementRemovePredicateCrossViewRecorder {
        private static boolean methodRemoveExecuted;
        private static boolean methodClassViewRemoved;
        private static boolean methodInfoRemoved;
        private static boolean fieldRemoveExecuted;
        private static boolean fieldClassViewRemoved;
        private static boolean fieldInfoRemoved;

        static synchronized void reset() {
            methodRemoveExecuted = false;
            methodClassViewRemoved = false;
            methodInfoRemoved = false;
            fieldRemoveExecuted = false;
            fieldClassViewRemoved = false;
            fieldInfoRemoved = false;
        }
    }

    public static class EnhancementParameterCrossViewRecorder {
        private static boolean parameterAddExecuted;
        private static boolean methodInfoSawParameterAdded;
        private static boolean parameterRemoveExecuted;
        private static boolean methodInfoSawParameterRemoved;

        static synchronized void reset() {
            parameterAddExecuted = false;
            methodInfoSawParameterAdded = false;
            parameterRemoveExecuted = false;
            methodInfoSawParameterRemoved = false;
        }
    }

    public static class LanguageModelEdgeCaseRecorder {
        private static int repeatableCountOnClassInfo;
        private static int repeatableCountOnClassConfigInfo;

        static synchronized void reset() {
            repeatableCountOnClassInfo = 0;
            repeatableCountOnClassConfigInfo = 0;
        }
    }

    @Dependent
    public static class TrackedBean {
    }

    @jakarta.inject.Qualifier
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.FIELD,
        java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.METHOD})
    public @interface SampleQualifier {
    }

    public static class MinimalBean {
    }

    @EnhancedMarker
    public static class EnhancementTargetBean {
        @EnhancedMarker
        String markedField;

        @EnhancedMarker
        public void markedMethod() {
        }

        public void plainMethod() {
        }
    }

    public static class EnhancementConfigGraphTarget {
        String data;

        public EnhancementConfigGraphTarget() {
        }

        public EnhancementConfigGraphTarget(String data) {
            this.data = data;
        }

        public void update(String value, int version) {
            this.data = value + version;
        }
    }

    @RepeatableEdge("one")
    @RepeatableEdge("two")
    public static class RepeatableTargetBean {
    }

    @Target({TYPE, METHOD, FIELD, java.lang.annotation.ElementType.PARAMETER})
    @Retention(RUNTIME)
    public @interface MutableAdded {
    }

    @Target({TYPE, METHOD, FIELD, java.lang.annotation.ElementType.PARAMETER})
    @Retention(RUNTIME)
    public @interface MutableClassAdded {
    }

    @java.lang.annotation.Repeatable(RepeatableEdgeList.class)
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface RepeatableEdge {
        String value();
    }

    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface RepeatableEdgeList {
        RepeatableEdge[] value();
    }

    public static class NonEnhancedTargetBean {
        public void plainMethod() {
        }
    }

    public static class ScannedAddedBean {
    }

    public static class BaseEnhancementType {
    }

    public static class SubEnhancementType extends BaseEnhancementType {
    }

    public static class ObserverFixtureBean {
        public void onFixtureEvent(@Observes FixtureEvent event) {
            // test fixture observer
        }
    }

    public static class FixtureEvent {
    }

    @TestInterceptorBinding
    public static class InterceptorFixtureBean {
        public String ping() {
            return "pong";
        }
    }

    public static class InjectionPointFixtureBean {
        @jakarta.inject.Inject
        MinimalBean field;
    }

    public static class DisposerFixtureProducerBean {
        @Produces
        String producedText() {
            return "fixture";
        }

        public void disposeText(@Disposes String value) {
            // disposer fixture
        }
    }

    @jakarta.enterprise.inject.Stereotype
    @Target(TYPE)
    @Retention(RUNTIME)
    public @interface FixtureStereotype {
    }

    @FixtureStereotype
    public static class StereotypeFixtureBean {
    }

    public static class FixtureSyntheticObserver implements SyntheticObserver<FixtureEvent> {
        @Override
        public void observe(jakarta.enterprise.inject.spi.EventContext<FixtureEvent> eventContext,
                            jakarta.enterprise.inject.build.compatible.spi.Parameters parameters) {
            // synthetic observer fixture for validation model coverage
        }
    }

    @jakarta.annotation.Priority(1)
    @jakarta.interceptor.Interceptor
    @TestInterceptorBinding
    public static class TestInterceptor {
        @jakarta.interceptor.AroundInvoke
        Object around(jakarta.interceptor.InvocationContext invocationContext) throws Exception {
            return invocationContext.proceed();
        }
    }

    @jakarta.interceptor.InterceptorBinding
    @Target({TYPE, METHOD})
    @Retention(RUNTIME)
    public @interface TestInterceptorBinding {
    }

    @Target({TYPE, METHOD, FIELD})
    @Retention(RUNTIME)
    public @interface EnhancedMarker {
    }

    @Target({TYPE, METHOD, FIELD})
    @Retention(RUNTIME)
    public @interface NonMatchingMarker {
    }
}
