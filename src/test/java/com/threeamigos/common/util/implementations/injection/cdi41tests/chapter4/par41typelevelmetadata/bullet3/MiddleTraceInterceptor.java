package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par41typelevelmetadata.bullet3;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@Priority(110)
@InheritedTraceBinding("intermediate")
public class MiddleTraceInterceptor {

    public static int invocations = 0;

    @AroundInvoke
    Object aroundInvoke(InvocationContext context) throws Exception {
        invocations++;
        return context.proceed();
    }

    public static void reset() {
        invocations = 0;
    }
}
