package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter12.par126validationphase;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.inject.build.compatible.spi.Validation;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("12.6 - Validation phase tests")
@Execution(ExecutionMode.SAME_THREAD)
public class ValidationPhaseTest {

    @Test
    @DisplayName("12.6 - @Validation methods support Types and Messages parameters")
    public void shouldInjectTypesAndMessagesInValidation() {
        ValidationRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(TypesAndMessagesValidationExtension.class.getName());

        syringe.setup();

        assertTrue(ValidationRecorder.typesInjected);
        assertTrue(ValidationRecorder.messagesInjected);
        assertTrue(ValidationRecorder.typesFactoryWorked);
    }

    @Test
    @DisplayName("12.6 - Calling Messages.error(String) in @Validation registers deployment problem")
    public void shouldFailDeploymentWhenValidationCallsMessagesErrorString() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(MessagesErrorStringValidationExtension.class.getName());
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("12.6 - Calling Messages.error(Exception) in @Validation registers deployment problem")
    public void shouldFailDeploymentWhenValidationCallsMessagesErrorException() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(MessagesErrorExceptionValidationExtension.class.getName());
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("12.6 - Unsupported @Validation parameter type is a deployment problem")
    public void shouldRejectUnsupportedValidationParameterType() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidValidationParameterExtension.class.getName());
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

    static class ValidationRecorder {
        static boolean typesInjected;
        static boolean messagesInjected;
        static boolean typesFactoryWorked;

        static void reset() {
            typesInjected = false;
            messagesInjected = false;
            typesFactoryWorked = false;
        }
    }

    public static class TypesAndMessagesValidationExtension implements BuildCompatibleExtension {
        @Validation
        public void validate(Types types, Messages messages) {
            ValidationRecorder.typesInjected = types != null;
            ValidationRecorder.messagesInjected = messages != null;
            ValidationRecorder.typesFactoryWorked =
                types != null &&
                    types.ofVoid() != null &&
                    types.ofPrimitive(jakarta.enterprise.lang.model.types.PrimitiveType.PrimitiveKind.BOOLEAN) != null &&
                    types.of(String.class) != null &&
                    types.ofClass(String.class.getName()) != null &&
                    types.ofArray(types.of(String.class), 1) != null &&
                    types.parameterized(java.util.List.class, String.class) != null &&
                    types.wildcardUnbounded() != null;
            messages.info("validation-info");
            messages.warn("validation-warn");
        }
    }

    public static class MessagesErrorStringValidationExtension implements BuildCompatibleExtension {
        @Validation
        public void validate(Messages messages) {
            messages.error("validation-error");
        }
    }

    public static class MessagesErrorExceptionValidationExtension implements BuildCompatibleExtension {
        @Validation
        public void validate(Messages messages) {
            messages.error(new IllegalStateException("validation-ex"));
        }
    }

    public static class InvalidValidationParameterExtension implements BuildCompatibleExtension {
        @Validation
        public void validate(String unsupported) {
        }
    }
}
