package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter12.par122discoveryphase;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.MetaAnnotations;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("12.2 - Discovery phase test")
@Execution(ExecutionMode.SAME_THREAD)
public class DiscoveryPhaseTest {

    @Test
    @DisplayName("12.2 - @Discovery can inject ScannedClasses, MetaAnnotations and Messages")
    public void shouldInjectSupportedDiscoveryParameters() {
        DiscoveryRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(DiscoveryParametersExtension.class.getName());

        syringe.setup();

        assertTrue(DiscoveryRecorder.scannedClassesInjected);
        assertTrue(DiscoveryRecorder.metaAnnotationsInjected);
        assertTrue(DiscoveryRecorder.messagesInjected);
        assertTrue(DiscoveryRecorder.classConfigFluentWorks);
    }

    @Test
    @DisplayName("12.2 - Discovery ScannedClasses.add registers additional class that becomes injectable")
    public void shouldRegisterAdditionalScannedClassInDiscovery() {
        DiscoveryRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(DiscoveryParametersExtension.class.getName());

        syringe.setup();

        assertTrue(syringe.inject(DiscoveryAddedBean.class) != null);
    }

    @Test
    @DisplayName("12.2 - Discovery MetaAnnotations can register qualifier/interceptor binding/stereotype")
    public void shouldRegisterMetaAnnotationsInDiscovery() {
        DiscoveryRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(DiscoveryParametersExtension.class.getName());

        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        assertTrue(beanManager.isQualifier(DynamicQualifier.class));
        assertTrue(beanManager.isInterceptorBinding(DynamicInterceptorBinding.class));
        assertTrue(beanManager.isStereotype(DynamicStereotype.class));
        assertTrue(syringe.inject(DynamicQualifierConsumer.class).hasInjectedService());
    }

    @Test
    @DisplayName("12.2 - Discovery method with unsupported parameter type is a deployment problem")
    public void shouldFailDeploymentForUnsupportedDiscoveryParameterType() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidDiscoveryParameterExtension.class.getName());
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("12.2 - ScannedClasses.add with unknown class name is a deployment problem")
    public void shouldFailDeploymentForUnknownScannedClassName() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidScannedClassNameExtension.class.getName());
        assertThrows(DefinitionException.class, syringe::setup);
    }

    private Syringe newSyringe() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), RootBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @Dependent
    public static class RootBean {
    }

    @Retention(RUNTIME)
    @Target({TYPE, FIELD, METHOD, PARAMETER})
    public @interface DynamicQualifier {
    }

    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public @interface DynamicInterceptorBinding {
    }

    @Retention(RUNTIME)
    @Target(TYPE)
    public @interface DynamicStereotype {
    }

    @DynamicQualifier
    public static class DynamicQualifiedService {
    }

    public static class DynamicQualifierConsumer {
        @Inject
        @DynamicQualifier
        DynamicQualifiedService service;

        public boolean hasInjectedService() {
            return service != null;
        }
    }

    public static class DiscoveryAddedBean {
    }

    public static class DiscoveryRecorder {
        static boolean scannedClassesInjected;
        static boolean metaAnnotationsInjected;
        static boolean messagesInjected;
        static boolean classConfigFluentWorks;

        static void reset() {
            scannedClassesInjected = false;
            metaAnnotationsInjected = false;
            messagesInjected = false;
            classConfigFluentWorks = false;
        }
    }

    public static class DiscoveryParametersExtension implements BuildCompatibleExtension {
        @Discovery
        public void discover(ScannedClasses scannedClasses,
                             MetaAnnotations metaAnnotations,
                             Messages messages) {
            DiscoveryRecorder.scannedClassesInjected = scannedClasses != null;
            DiscoveryRecorder.metaAnnotationsInjected = metaAnnotations != null;
            DiscoveryRecorder.messagesInjected = messages != null;

            scannedClasses.add(DiscoveryAddedBean.class.getName());

            ClassConfig qualifierConfig = metaAnnotations.addQualifier(DynamicQualifier.class);
            ClassConfig interceptorConfig = metaAnnotations.addInterceptorBinding(DynamicInterceptorBinding.class);
            ClassConfig stereotypeConfig = metaAnnotations.addStereotype(DynamicStereotype.class);

            DiscoveryRecorder.classConfigFluentWorks =
                qualifierConfig != null &&
                    interceptorConfig != null &&
                    stereotypeConfig != null &&
                    qualifierConfig.addAnnotation(Deprecated.class) != null &&
                    qualifierConfig.removeAllAnnotations() != null &&
                    qualifierConfig.constructors() != null &&
                    qualifierConfig.methods() != null &&
                    qualifierConfig.fields() != null;

            messages.info("discovery-info");
            messages.warn("discovery-warn");
        }
    }

    public static class InvalidDiscoveryParameterExtension implements BuildCompatibleExtension {
        @Discovery
        public void invalid(String unsupported) {
        }
    }

    public static class InvalidScannedClassNameExtension implements BuildCompatibleExtension {
        @Discovery
        public void invalid(ScannedClasses scannedClasses) {
            scannedClasses.add("com.threeamigos.missing.DoesNotExist");
        }
    }
}
