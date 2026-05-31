package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter12.par123enhancementphase;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.FieldConfig;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName( "12.3 - Enhancement phase test")
@Execution(ExecutionMode.SAME_THREAD)
public class EnhancementPhaseTest {

    @Test
    @DisplayName("12.3 - ClassConfig enhancement runs for each matching discovered class and can navigate members")
    public void shouldInvokeClassConfigAndAllowNavigation() {
        Recorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(ClassConfigExtension.class.getName());

        syringe.setup();

        assertEquals(1, Recorder.classConfigCalls);
        assertTrue(Recorder.classConfigConstructorsSeen > 0);
        assertTrue(Recorder.classConfigMethodsSeen > 0);
        assertTrue(Recorder.classConfigFieldsSeen > 0);
        assertTrue(Recorder.classConfigParameterNavigationWorked);
    }

    @Test
    @DisplayName("12.3 - MethodInfo enhancement runs for each method and constructor of matching classes")
    public void shouldInvokeMethodInfoForMethodsAndConstructors() {
        Recorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(MethodInfoExtension.class.getName());

        syringe.setup();

        assertEquals(EnhancementTarget.class.getDeclaredMethods().length +
                EnhancementTarget.class.getDeclaredConstructors().length,
            Recorder.methodInfoCalls);
    }

    @Test
    @DisplayName("12.3 - FieldInfo enhancement runs for each field of matching classes")
    public void shouldInvokeFieldInfoForEachField() {
        Recorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(FieldInfoExtension.class.getName());

        syringe.setup();

        assertEquals(EnhancementTarget.class.getDeclaredFields().length, Recorder.fieldInfoCalls);
    }

    @Test
    @DisplayName("12.3 - Enhancement supports optional Types and Messages parameters")
    public void shouldInjectTypesAndMessagesInEnhancement() {
        Recorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(ModelWithServicesExtension.class.getName());

        syringe.setup();

        assertTrue(Recorder.typesInjected);
        assertTrue(Recorder.messagesInjected);
        assertTrue(Recorder.typesFactoryWorked);
    }

    @Test
    @DisplayName("12.3 - Enhancement method must declare exactly one model parameter")
    public void shouldRejectEnhancementWithoutModelParameter() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidNoModelEnhancementExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("12.3 - Enhancement method with multiple model parameters is a deployment problem")
    public void shouldRejectEnhancementWithMultipleModelParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidMultipleModelsEnhancementExtension.class.getName());

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

    public static class EnhancementTarget {
        private String f1;
        private int f2;

        public EnhancementTarget() {
        }

        public EnhancementTarget(String ignored) {
        }

        public void m1() {
        }

        public String m2(int a, long b) {
            return String.valueOf(a + b);
        }
    }

    static class Recorder {
        static int classConfigCalls;
        static int classConfigConstructorsSeen;
        static int classConfigMethodsSeen;
        static int classConfigFieldsSeen;
        static boolean classConfigParameterNavigationWorked;

        static int methodInfoCalls;
        static int fieldInfoCalls;

        static boolean typesInjected;
        static boolean messagesInjected;
        static boolean typesFactoryWorked;

        static void reset() {
            classConfigCalls = 0;
            classConfigConstructorsSeen = 0;
            classConfigMethodsSeen = 0;
            classConfigFieldsSeen = 0;
            classConfigParameterNavigationWorked = false;
            methodInfoCalls = 0;
            fieldInfoCalls = 0;
            typesInjected = false;
            messagesInjected = false;
            typesFactoryWorked = false;
        }
    }

    public static class ClassConfigExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTarget.class, withSubtypes = false)
        public void enhance(ClassConfig classConfig) {
            Recorder.classConfigCalls++;
            Recorder.classConfigConstructorsSeen += classConfig.constructors().size();
            Recorder.classConfigMethodsSeen += classConfig.methods().size();
            Recorder.classConfigFieldsSeen += classConfig.fields().size();
            for (MethodConfig methodConfig : classConfig.methods()) {
                if (!methodConfig.parameters().isEmpty()) {
                    methodConfig.parameters().get(0).addAnnotation(Deprecated.class);
                    Recorder.classConfigParameterNavigationWorked = true;
                    break;
                }
            }
        }
    }

    public static class MethodInfoExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTarget.class, withSubtypes = false)
        public void enhance(MethodInfo methodInfo) {
            if (methodInfo != null) {
                Recorder.methodInfoCalls++;
            }
        }
    }

    public static class FieldInfoExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTarget.class, withSubtypes = false)
        public void enhance(FieldInfo fieldInfo) {
            if (fieldInfo != null) {
                Recorder.fieldInfoCalls++;
            }
        }
    }

    public static class ModelWithServicesExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTarget.class, withSubtypes = false)
        public void enhance(ClassInfo classInfo, Types types, Messages messages) {
            Recorder.typesInjected = classInfo != null && types != null;
            Recorder.messagesInjected = classInfo != null && messages != null;
            Recorder.typesFactoryWorked = types.ofVoid() != null &&
                types.ofPrimitive(jakarta.enterprise.lang.model.types.PrimitiveType.PrimitiveKind.INT) != null &&
                types.of(String.class) != null &&
                types.ofClass(EnhancementTarget.class.getName()) != null &&
                types.ofArray(types.of(String.class), 1) != null &&
                types.parameterized(java.util.List.class, String.class) != null &&
                types.wildcardUnbounded() != null;
            messages.info("enhancement-phase-types-ok");
        }
    }

    public static class InvalidNoModelEnhancementExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTarget.class, withSubtypes = false)
        public void enhance(Types types, Messages messages) {
        }
    }

    public static class InvalidMultipleModelsEnhancementExtension implements BuildCompatibleExtension {
        @Enhancement(types = EnhancementTarget.class, withSubtypes = false)
        public void enhance(ClassInfo classInfo, MethodInfo methodInfo) {
        }
    }
}
