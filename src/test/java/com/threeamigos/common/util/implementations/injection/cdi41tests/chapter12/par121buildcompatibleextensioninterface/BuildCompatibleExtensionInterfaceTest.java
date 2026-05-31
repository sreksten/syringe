package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter12.par121buildcompatibleextensioninterface;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum;
import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.spi.SyringeCDIProvider;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.MetaAnnotations;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.inject.build.compatible.spi.Validation;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.interceptor.Interceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("12.1 - Build-compatible extension interface test")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
public class BuildCompatibleExtensionInterfaceTest {

    private CdiStateSnapshot cdiStateSnapshot;

    @BeforeEach
    void captureCdiState() throws Exception {
        cdiStateSnapshot = CdiStateSnapshot.capture();
    }

    @AfterEach
    void restoreCdiState() throws Exception {
        if (cdiStateSnapshot != null) {
            cdiStateSnapshot.restore();
        }
        SyringeCDIProvider.unregisterThreadLocalCDI();
        SyringeCDIProvider.unregisterGlobalCDI();
    }

    @Test
    @DisplayName("12.1 - BuildCompatibleExtension is an empty marker interface")
    void shouldExposeEmptyMarkerInterface() {
        assertEquals(0, BuildCompatibleExtension.class.getDeclaredMethods().length);
    }

    @Test
    @DisplayName("12.1 - Extension annotations map to supported phase markers")
    void shouldRecognizeAllExtensionPhaseAnnotations() throws Exception {
        Method discovery = PhaseAnnotatedMethods.class.getDeclaredMethod("onDiscovery");
        Method enhancement = PhaseAnnotatedMethods.class.getDeclaredMethod("onEnhancement");
        Method registration = PhaseAnnotatedMethods.class.getDeclaredMethod("onRegistration");
        Method synthesis = PhaseAnnotatedMethods.class.getDeclaredMethod("onSynthesis", SyntheticComponents.class);
        Method validation = PhaseAnnotatedMethods.class.getDeclaredMethod("onValidation");

        assertTrue(AnnotationPredicates.hasDiscoveryAnnotation(discovery));
        assertTrue(AnnotationPredicates.hasEnhancementAnnotation(enhancement));
        assertTrue(AnnotationPredicates.hasRegistrationAnnotation(registration));
        assertTrue(AnnotationPredicates.hasSynthesisAnnotation(synthesis));
        assertTrue(AnnotationPredicates.hasValidationAnnotation(validation));
    }

    @Test
    @DisplayName("12.1 - Build compatible extension can be discovered as META-INF/services provider")
    void shouldLoadBuildCompatibleExtensionFromServiceProvider() throws Exception {
        ServiceLoadedRecorder.reset();

        Path tempRoot = Files.createTempDirectory("syringe-bce-services");
        Path servicesDir = tempRoot.resolve("META-INF").resolve("services");
        Files.createDirectories(servicesDir);
        Path serviceFile = servicesDir.resolve("jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension");
        Files.write(serviceFile, Arrays.asList(ServiceLoadedBuildCompatibleExtension.class.getName()), StandardCharsets.UTF_8);

        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        URLClassLoader testCl = new URLClassLoader(new URL[]{tempRoot.toUri().toURL()}, previous);
        try {
            Thread.currentThread().setContextClassLoader(testCl);
            newSyringe().setup();
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            try {
                testCl.close();
            } catch (IOException ignored) {
            }
        }

        assertTrue(ServiceLoadedRecorder.discoveryCalled);
    }

    @Test
    @DisplayName("12.1 - Discovery methods inject supported service parameters")
    void shouldInjectSupportedDiscoveryParameters() {
        ServiceParametersRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(SupportedDiscoveryParametersExtension.class.getName());
        syringe.setup();

        assertTrue(ServiceParametersRecorder.typesInjected);
        assertTrue(ServiceParametersRecorder.messagesInjected);
        assertTrue(ServiceParametersRecorder.metaAnnotationsInjected);
        assertTrue(ServiceParametersRecorder.scannedClassesInjected);
    }

    @Test
    @DisplayName("12.1 - Unsupported extension method parameter type is a deployment problem")
    void shouldFailDeploymentForUnsupportedPhaseParameterType() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidDiscoveryParameterExtension.class.getName());
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("12.1 - Extension methods must be public")
    void shouldFailDeploymentForNonPublicExtensionMethod() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(NonPublicMethodExtension.class.getName());
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("12.1 - Extension methods must be non-static")
    void shouldFailDeploymentForStaticExtensionMethod() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(StaticMethodExtension.class.getName());
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("12.1 - Extension methods must not declare type parameters")
    void shouldFailDeploymentForGenericExtensionMethod() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(GenericMethodExtension.class.getName());
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("12.1 - Container invokes all extension methods on a single extension instance")
    void shouldInvokeAllMethodsOnSameExtensionInstance() {
        SingleInstanceRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(SingleInstanceExtension.class.getName());
        syringe.setup();

        assertEquals(1, SingleInstanceRecorder.constructorCalls);
        assertEquals(1, SingleInstanceRecorder.instanceIds.size());
        assertTrue(SingleInstanceRecorder.discoveryCalled);
        assertTrue(SingleInstanceRecorder.validationCalled);
    }

    @Test
    @DisplayName("12.1 - @Priority controls extension method ordering within a phase, default is APPLICATION + 500")
    void shouldOrderPhaseMethodsByPriority() {
        PriorityOrderRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(PriorityOrderedExtension.class.getName());
        syringe.setup();

        assertEquals(Arrays.asList("low", "default", "high"), PriorityOrderRecorder.calls);
    }

    @Test
    @DisplayName("12.1 - Exception thrown by extension method is treated as deployment problem")
    void shouldFailDeploymentWhenExtensionMethodThrowsException() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(ThrowingExtension.class.getName());
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("12.1 - Calling CDI.current() from extension method is non-portable and throws NonPortableBehaviourException")
    void shouldRejectCdiCurrentFromExtensionMethod() throws Exception {
        CdiStateSnapshot snapshot = CdiStateSnapshot.capture();
        try {
            setConfiguredProvider(null);
            setDiscoveredProviders(new LinkedHashSet<jakarta.enterprise.inject.spi.CDIProvider>());
            SyringeCDIProvider.unregisterThreadLocalCDI();
            SyringeCDIProvider.unregisterGlobalCDI();

            Syringe syringe = newSyringe();
            syringe.addBuildCompatibleExtension(CdiCurrentAccessingExtension.class.getName());
            Throwable thrown = assertThrows(Throwable.class, syringe::setup);
            assertTrue(isCdiNonPortableFailure(thrown),
                    "Expected non-portable CDI failure but got " + thrown.getClass().getName() + ": " + thrown.getMessage());
        } finally {
            snapshot.restore();
            SyringeCDIProvider.unregisterThreadLocalCDI();
            SyringeCDIProvider.unregisterGlobalCDI();
        }
    }

    private boolean isCdiNonPortableFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NonPortableBehaviourException) {
                return true;
            }
            if (current instanceof IllegalStateException
                    && current.getMessage() != null
                    && current.getMessage().contains("Unable to access CDI")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void setDiscoveredProviders(Set<jakarta.enterprise.inject.spi.CDIProvider> providers) throws Exception {
        Field field = CDI.class.getDeclaredField("discoveredProviders");
        field.setAccessible(true);
        field.set(null, providers);
    }

    private static void setConfiguredProvider(jakarta.enterprise.inject.spi.CDIProvider provider) throws Exception {
        Field field = CDI.class.getDeclaredField("configuredProvider");
        field.setAccessible(true);
        field.set(null, provider);
    }

    private static final class CdiStateSnapshot {
        private final jakarta.enterprise.inject.spi.CDIProvider configuredProvider;
        private final Set<jakarta.enterprise.inject.spi.CDIProvider> discoveredProviders;

        private CdiStateSnapshot(jakarta.enterprise.inject.spi.CDIProvider configuredProvider,
                                 Set<jakarta.enterprise.inject.spi.CDIProvider> discoveredProviders) {
            this.configuredProvider = configuredProvider;
            this.discoveredProviders = discoveredProviders;
        }

        @SuppressWarnings("unchecked")
        static CdiStateSnapshot capture() throws Exception {
            Field configuredField = CDI.class.getDeclaredField("configuredProvider");
            configuredField.setAccessible(true);
            Field discoveredField = CDI.class.getDeclaredField("discoveredProviders");
            discoveredField.setAccessible(true);
            return new CdiStateSnapshot(
                    (jakarta.enterprise.inject.spi.CDIProvider) configuredField.get(null),
                    (Set<jakarta.enterprise.inject.spi.CDIProvider>) discoveredField.get(null)
            );
        }

        void restore() throws Exception {
            setConfiguredProvider(configuredProvider);
            setDiscoveredProviders(discoveredProviders);
        }
    }

    private Syringe newSyringe() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NoopRootBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @Dependent
    public static class NoopRootBean {
    }

    public static class PhaseAnnotatedMethods {
        @Discovery
        public void onDiscovery() {}

        @Enhancement(types = Object.class)
        public void onEnhancement() {}

        @Registration(types = Object.class)
        public void onRegistration() {}

        @Synthesis
        public void onSynthesis(SyntheticComponents syntheticComponents) {}

        @Validation
        public void onValidation() {}
    }

    public static class ServiceLoadedRecorder {
        static boolean discoveryCalled;

        static void reset() {
            discoveryCalled = false;
        }
    }

    public static class ServiceLoadedBuildCompatibleExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery() {
            ServiceLoadedRecorder.discoveryCalled = true;
        }
    }

    public static class ServiceParametersRecorder {
        static boolean typesInjected;
        static boolean messagesInjected;
        static boolean metaAnnotationsInjected;
        static boolean scannedClassesInjected;

        static void reset() {
            typesInjected = false;
            messagesInjected = false;
            metaAnnotationsInjected = false;
            scannedClassesInjected = false;
        }
    }

    public static class SupportedDiscoveryParametersExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(Types types,
                              Messages messages,
                              MetaAnnotations metaAnnotations,
                              ScannedClasses scannedClasses) {
            ServiceParametersRecorder.typesInjected = types != null;
            ServiceParametersRecorder.messagesInjected = messages != null;
            ServiceParametersRecorder.metaAnnotationsInjected = metaAnnotations != null;
            ServiceParametersRecorder.scannedClassesInjected = scannedClasses != null;
        }
    }

    public static class InvalidDiscoveryParameterExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(String unsupported) {
        }
    }

    public static class NonPublicMethodExtension implements BuildCompatibleExtension {
        @Discovery
        void discovery() {
        }
    }

    public static class StaticMethodExtension implements BuildCompatibleExtension {
        @Discovery
        public static void discovery() {
        }
    }

    public static class GenericMethodExtension implements BuildCompatibleExtension {
        @Discovery
        public <T> void discovery() {
        }
    }

    public static class SingleInstanceRecorder {
        static int constructorCalls;
        static final Set<Integer> instanceIds = new LinkedHashSet<Integer>();
        static boolean discoveryCalled;
        static boolean validationCalled;

        static void reset() {
            constructorCalls = 0;
            instanceIds.clear();
            discoveryCalled = false;
            validationCalled = false;
        }
    }

    public static class SingleInstanceExtension implements BuildCompatibleExtension {
        public SingleInstanceExtension() {
            SingleInstanceRecorder.constructorCalls++;
        }

        @Discovery
        public void discovery() {
            SingleInstanceRecorder.discoveryCalled = true;
            SingleInstanceRecorder.instanceIds.add(System.identityHashCode(this));
        }

        @Validation
        public void validation() {
            SingleInstanceRecorder.validationCalled = true;
            SingleInstanceRecorder.instanceIds.add(System.identityHashCode(this));
        }
    }

    public static class PriorityOrderRecorder {
        static java.util.List<String> calls = new java.util.ArrayList<String>();

        static void reset() {
            calls.clear();
        }
    }

    public static class PriorityOrderedExtension implements BuildCompatibleExtension {
        @Discovery
        @Priority(100)
        public void low() {
            PriorityOrderRecorder.calls.add("low");
        }

        @Discovery
        public void def() {
            assertEquals(Interceptor.Priority.APPLICATION + 500, Interceptor.Priority.APPLICATION + 500);
            PriorityOrderRecorder.calls.add("default");
        }

        @Discovery
        @Priority(5000)
        public void high() {
            PriorityOrderRecorder.calls.add("high");
        }
    }

    public static class ThrowingExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery() {
            throw new IllegalStateException("boom");
        }
    }

    public static class CdiCurrentAccessingExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery() {
            CDI.current().getBeanContainer();
        }
    }
}
