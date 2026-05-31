package com.threeamigos.common.util.implementations.injection.interceptors;

import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvocationContextImplTest {

    @Test
    void proceedRestoresPositionWhenNextInterceptorThrows() throws Exception {
        RetryFixture.reset();
        RetryFixture fixture = new RetryFixture();

        Method interceptor3 = RetryFixture.class.getDeclaredMethod("interceptor3", InvocationContext.class);
        Method interceptor4 = RetryFixture.class.getDeclaredMethod("interceptor4", InvocationContext.class);
        Method target = RetryFixture.class.getDeclaredMethod("target");

        InterceptorChain chain = InterceptorChain.builder()
                .addInterceptor(fixture, interceptor3)
                .addInterceptor(fixture, interceptor4)
                .build();

        Object result = chain.invoke(fixture, target, new Object[0]);

        assertTrue((Boolean) result);
        assertEquals(3, RetryFixture.count);
    }

    static class RetryFixture {
        static int count;

        static void reset() {
            count = 0;
        }

        public Object interceptor3(InvocationContext ctx) throws Exception {
            try {
                failFirstTwoInvocations();
            } catch (RuntimeException ignored) {
                // expected first failure
            }
            try {
                ctx.proceed();
            } catch (RuntimeException ignored) {
                // expected second failure
            }
            return ctx.proceed();
        }

        public Object interceptor4(InvocationContext ctx) throws Exception {
            failFirstTwoInvocations();
            return !(Boolean) ctx.proceed();
        }

        public boolean target() {
            return false;
        }

        static void failFirstTwoInvocations() {
            count++;
            if (count <= 2) {
                throw new RuntimeException("boom-" + count);
            }
        }
    }
}
