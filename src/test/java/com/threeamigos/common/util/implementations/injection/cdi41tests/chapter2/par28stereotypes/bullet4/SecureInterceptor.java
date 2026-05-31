package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet4;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Secure
@Interceptor
@Priority(100)
public class SecureInterceptor {

    @AroundInvoke
    Object aroundInvoke(InvocationContext invocationContext) throws Exception {
        return invocationContext.proceed();
    }
}
