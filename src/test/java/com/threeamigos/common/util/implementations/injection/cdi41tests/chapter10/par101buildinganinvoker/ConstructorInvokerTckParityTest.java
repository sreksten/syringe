package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter10.par101buildinganinvoker;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("10.1 - TCK parity for invalid constructor target in InvokerFactory")
@Execution(ExecutionMode.SAME_THREAD)
class ConstructorInvokerTckParityTest {

    @AfterEach
    void cleanup() {
        ConstructorInvokerRegistrationRecorder.reset();
    }

    @Test
    @DisplayName("ConstructorInvokerTest - building invoker for constructor must be deployment problem")
    void shouldFailDeploymentWhenInvokerFactoryTargetsConstructor() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), MyService.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addBuildCompatibleExtension(ConstructorTargetingExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
        assertTrue(ConstructorInvokerRegistrationRecorder.registrationInvoked);
        assertTrue(ConstructorInvokerRegistrationRecorder.constructorCount > 0);
    }

    @Test
    @DisplayName("Diagnostic - current Syringe behavior for constructor-targeting invoker extension")
    void shouldExposeCurrentBehaviorForDiagnostics() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), MyService.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addBuildCompatibleExtension(ConstructorTargetingExtension.class.getName());

        try {
            syringe.setup();
        } catch (RuntimeException expected) {
            // expected diagnostic path for TCK-parity behavior
        }

        assertTrue(ConstructorInvokerRegistrationRecorder.registrationInvoked);
        assertEquals(1, ConstructorInvokerRegistrationRecorder.constructorCount);
        assertTrue(
                ConstructorInvokerRegistrationRecorder.invokerBuildAttempted,
                "Extension reached invokerFactory.createInvoker(...).build() for constructor target"
        );
    }

    @ApplicationScoped
    public static class MyService {
        public MyService() {
        }
    }

    public static class ConstructorTargetingExtension implements BuildCompatibleExtension {
        @Registration(types = MyService.class)
        public void register(BeanInfo bean, InvokerFactory invokers) {
            ConstructorInvokerRegistrationRecorder.registrationInvoked = true;
            for (MethodInfo ctor : bean.declaringClass().constructors()) {
                ConstructorInvokerRegistrationRecorder.constructorCount++;
                ConstructorInvokerRegistrationRecorder.invokerBuildAttempted = true;
                invokers.createInvoker(bean, ctor).build();
            }
        }
    }

    public static class ConstructorInvokerRegistrationRecorder {
        static volatile boolean registrationInvoked;
        static volatile boolean invokerBuildAttempted;
        static volatile int constructorCount;

        static void reset() {
            registrationInvoked = false;
            invokerBuildAttempted = false;
            constructorCount = 0;
        }
    }
}
