package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet11;

import jakarta.enterprise.inject.Produces;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@IllegalProducerInterceptorBinding
public class InvalidInterceptorProducerMethod {

    @AroundInvoke
    Object intercept(InvocationContext context) throws Exception {
        return context.proceed();
    }

    @Produces
    String produceInvalidType() {
        return null;
    }
}
