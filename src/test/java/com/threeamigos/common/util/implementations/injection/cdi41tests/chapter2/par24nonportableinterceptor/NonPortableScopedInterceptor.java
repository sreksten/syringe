package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par24nonportableinterceptor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@ApplicationScoped
@NonPortableInterceptorBinding
public class NonPortableScopedInterceptor {

    @AroundInvoke
    Object aroundInvoke(InvocationContext invocationContext) throws Exception {
        return invocationContext.proceed();
    }
}
