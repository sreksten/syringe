package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par26nonportableinterceptor;

import jakarta.inject.Named;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@Named("namedNonPortableInterceptor")
@NamedInterceptorBinding
public class NamedNonPortableInterceptor {

    @AroundInvoke
    Object aroundInvoke(InvocationContext invocationContext) throws Exception {
        return invocationContext.proceed();
    }
}
