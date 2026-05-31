package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter10.par101buildinganinvoker;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.InterceptorInfo;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("10.1 - TCK parity for invalid interceptor target in InvokerFactory")
@Execution(ExecutionMode.SAME_THREAD)
class InterceptorInvokerTckParityTest {

    @AfterEach
    void cleanup() {
        InterceptorInvokerRecorder.reset();
    }

    @Test
    @DisplayName("InterceptorInvokerTest - building invoker for interceptor target must be deployment problem")
    void shouldFailDeploymentWhenInvokerFactoryTargetsInterceptor() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), MyInterceptor.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addBuildCompatibleExtension(InterceptorTargetingExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
        assertTrue(InterceptorInvokerRecorder.registrationInvoked);
        assertTrue(InterceptorInvokerRecorder.invokerBuildAttempted);
    }

    @MyInterceptorBinding
    @Interceptor
    @Priority(1)
    public static class MyInterceptor {
        String hello() {
            return "foobar";
        }

        @AroundInvoke
        Object intercept(InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }
    }

    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @InterceptorBinding
    public @interface MyInterceptorBinding {
    }

    public static class InterceptorTargetingExtension implements BuildCompatibleExtension {
        @Registration(types = MyInterceptor.class)
        public void register(InterceptorInfo bean, InvokerFactory invokers) {
            InterceptorInvokerRecorder.registrationInvoked = true;
            bean.declaringClass().methods().stream()
                .filter(method -> "hello".equals(method.name()))
                .forEach(method -> {
                    InterceptorInvokerRecorder.invokerBuildAttempted = true;
                    invokers.createInvoker(bean, method).build();
                });
        }
    }

    public static class InterceptorInvokerRecorder {
        static volatile boolean registrationInvoked;
        static volatile boolean invokerBuildAttempted;

        static void reset() {
            registrationInvoked = false;
            invokerBuildAttempted = false;
        }
    }
}
