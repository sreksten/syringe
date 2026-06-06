package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet8;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@UnproxyableInterceptorBinding
@Interceptor
@Priority(100)
public class UnproxyableFinalClassInterceptor {

    @AroundInvoke
    Object aroundInvoke(InvocationContext invocationContext) throws Exception {
        return invocationContext.proceed();
    }
}
