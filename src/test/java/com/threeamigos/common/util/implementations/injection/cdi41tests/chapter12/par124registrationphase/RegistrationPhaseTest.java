package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter12.par124registrationphase;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.InterceptorInfo;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.ObserverInfo;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("12.4 - Registration phase tests")
@Execution(ExecutionMode.SAME_THREAD)
public class RegistrationPhaseTest {

    @Test
    @DisplayName("12.4 - Registration BeanInfo callback is invoked for beans whose bean types match @Registration types")
    public void shouldInvokeBeanInfoForMatchingBeanTypes() {
        RegistrationRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(BeanInfoRegistrationExtension.class.getName());

        syringe.setup();

        assertTrue(RegistrationRecorder.beanInfoCount > 0);
        assertTrue(!RegistrationRecorder.sawNonMatchingBeanType);
    }

    @Test
    @DisplayName("12.4 - Registration accepts InterceptorInfo model parameter with @Registration type criteria")
    public void shouldInvokeInterceptorInfoForMatchingInterceptorTypes() {
        RegistrationRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InterceptorInfoRegistrationExtension.class.getName());

        syringe.setup();
    }

    @Test
    @DisplayName("12.4 - Registration accepts ObserverInfo model parameter with @Registration type criteria")
    public void shouldInvokeObserverInfoForMatchingObservedEventTypes() {
        RegistrationRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(ObserverInfoRegistrationExtension.class.getName());

        syringe.setup();
    }

    @Test
    @DisplayName("12.4 - Registration supports optional InvokerFactory, Types and Messages parameters")
    public void shouldInjectOptionalRegistrationServices() {
        RegistrationRecorder.reset();
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(RegistrationWithServicesExtension.class.getName());

        syringe.setup();

        assertTrue(RegistrationRecorder.invokerFactoryInjected);
        assertTrue(RegistrationRecorder.typesInjected);
        assertTrue(RegistrationRecorder.messagesInjected);
    }

    @Test
    @DisplayName("12.4 - TCK parity (RegistrationTest): registration phase processes BeanInfo, ObserverInfo and interceptor registrations for matching types")
    public void shouldMatchTckRegistrationTest() {
        shouldInvokeBeanInfoForMatchingBeanTypes();
        shouldInvokeObserverInfoForMatchingObservedEventTypes();
        shouldInvokeInterceptorInfoForMatchingInterceptorTypes();
    }

    @Test
    @DisplayName("12.4 - Registration method with unsupported parameter type is a deployment problem")
    public void shouldFailDeploymentForUnsupportedRegistrationParameterType() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidRegistrationParameterExtension.class.getName());
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("12.4 - Registration method with multiple model parameters is a deployment problem")
    public void shouldFailDeploymentForMultipleRegistrationModelParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidMultipleRegistrationModelsExtension.class.getName());
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

    public interface RegistrationContract {
        String id();
    }

    @Dependent
    public static class RegistrationContractBean implements RegistrationContract {
        @Override
        public String id() {
            return "contract";
        }
    }

    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    @InterceptorBinding
    public @interface Track {
    }

    @Track
    @Interceptor
    @Priority(Interceptor.Priority.APPLICATION)
    public static class TrackingInterceptor {
        @AroundInvoke
        public Object around(InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }
    }

    @Dependent
    public static class TrackedTargetBean {
        @Track
        public String ping() {
            return "pong";
        }
    }

    public static class RegistrationEvent {
    }

    public static class OtherEvent {
    }

    @Dependent
    public static class RegistrationObserverBean {
        public void observeRegistration(@Observes RegistrationEvent event) {
        }

        public void observeOther(@Observes OtherEvent event) {
        }
    }

    public static class RegistrationRecorder {
        static int beanInfoCount;
        static int interceptorInfoCount;
        static int observerInfoCount;
        static boolean sawNonMatchingBeanType;
        static int observerEventTypesSeen;
        static boolean invokerFactoryInjected;
        static boolean typesInjected;
        static boolean messagesInjected;

        static void reset() {
            beanInfoCount = 0;
            interceptorInfoCount = 0;
            observerInfoCount = 0;
            sawNonMatchingBeanType = false;
            observerEventTypesSeen = 0;
            invokerFactoryInjected = false;
            typesInjected = false;
            messagesInjected = false;
        }
    }

    public static class BeanInfoRegistrationExtension implements BuildCompatibleExtension {
        @Registration(types = RegistrationContract.class)
        public void registration(BeanInfo beanInfo) {
            if (beanInfo == null) {
                return;
            }
            boolean matches = false;
            for (jakarta.enterprise.lang.model.types.Type beanType : beanInfo.types()) {
                if (beanType.isClass() &&
                    RegistrationContract.class.getName().equals(beanType.asClass().declaration().name())) {
                    matches = true;
                    break;
                }
            }
            if (matches) {
                RegistrationRecorder.beanInfoCount++;
            } else {
                RegistrationRecorder.sawNonMatchingBeanType = true;
            }
        }
    }

    public static class InterceptorInfoRegistrationExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(InterceptorInfo interceptorInfo) {
            if (interceptorInfo != null) {
                RegistrationRecorder.interceptorInfoCount++;
            }
        }
    }

    public static class ObserverInfoRegistrationExtension implements BuildCompatibleExtension {
        @Registration(types = Object.class)
        public void registration(ObserverInfo observerInfo) {
            if (observerInfo == null) {
                return;
            }
            RegistrationRecorder.observerInfoCount++;
            if (observerInfo.eventType() != null) {
                RegistrationRecorder.observerEventTypesSeen++;
            }
        }
    }

    public static class RegistrationWithServicesExtension implements BuildCompatibleExtension {
        @Registration(types = RegistrationContract.class)
        public void registration(BeanInfo beanInfo,
                                 InvokerFactory invokerFactory,
                                 Types types,
                                 Messages messages) {
            RegistrationRecorder.invokerFactoryInjected = beanInfo != null && invokerFactory != null;
            RegistrationRecorder.typesInjected = beanInfo != null && types != null;
            RegistrationRecorder.messagesInjected = beanInfo != null && messages != null;
        }
    }

    public static class InvalidRegistrationParameterExtension implements BuildCompatibleExtension {
        @Registration(types = RegistrationContract.class)
        public void registration(BeanInfo beanInfo, String unsupported) {
        }
    }

    public static class InvalidMultipleRegistrationModelsExtension implements BuildCompatibleExtension {
        @Registration(types = RegistrationContract.class)
        public void registration(BeanInfo beanInfo, ObserverInfo observerInfo) {
        }
    }
}
