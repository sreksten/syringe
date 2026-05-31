package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.inject.Qualifier;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("3.3 - Producer fields TCK parity")
class ProducerFieldOnInterceptorTckParityTest {

    @Test
    @DisplayName("3.3 / ProducerFieldOnInterceptorTest - producer field declared on interceptor is definition error")
    void shouldMatchProducerFieldOnInterceptorTest() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), SimpleInterceptor_Broken.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @InterceptorBinding
    public @interface Secure {
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    public @interface Number {
    }

    @Interceptor
    @Secure
    @Priority(1)
    public static class SimpleInterceptor_Broken {
        @AroundInvoke
        public Object intercept(InvocationContext ctx) throws Exception {
            return ctx.proceed();
        }

        @Produces
        @Number
        private Integer zero = Integer.valueOf(0);
    }
}
