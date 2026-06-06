package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet6;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@InitializerMethodBinding
@Interceptor
@Priority(100)
public class InitializerMethodInterceptor {

    public static int aroundInvokeCalls = 0;

    @AroundInvoke
    Object aroundInvoke(InvocationContext invocationContext) throws Exception {
        aroundInvokeCalls++;
        return invocationContext.proceed();
    }

    public static void reset() {
        aroundInvokeCalls = 0;
    }
}
