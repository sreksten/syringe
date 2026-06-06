package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet9;

import jakarta.enterprise.inject.Produces;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@IllegalProducerFieldInterceptorBinding
public class InvalidInterceptorProducerField {

    @Produces
    String invalidProduct = "invalid";

    @AroundInvoke
    Object intercept(InvocationContext context) throws Exception {
        return context.proceed();
    }
}
