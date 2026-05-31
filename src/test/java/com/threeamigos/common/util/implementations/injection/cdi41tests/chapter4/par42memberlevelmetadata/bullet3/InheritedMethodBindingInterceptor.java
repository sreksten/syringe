package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet3;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@Priority(120)
@InheritedMethodBinding
public class InheritedMethodBindingInterceptor {

    public static int aroundInvokeCalls;

    @AroundInvoke
    Object aroundInvoke(InvocationContext invocationContext) throws Exception {
        aroundInvokeCalls++;
        return invocationContext.proceed();
    }

    public static void reset() {
        aroundInvokeCalls = 0;
    }
}
