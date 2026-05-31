package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.nonportableinterceptor;

import jakarta.enterprise.inject.Alternative;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@Alternative
@AlternativeInterceptorBinding
public class AlternativeInterceptor {

    @AroundInvoke
    Object aroundInvoke(InvocationContext invocationContext) throws Exception {
        return invocationContext.proceed();
    }
}
