package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet9;

import jakarta.enterprise.inject.Disposes;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@IllegalDisposerInterceptorBinding
public class InvalidInterceptorDisposerMethod {

    void disposeInvalid(@Disposes String value) {
    }

    @AroundInvoke
    Object intercept(InvocationContext context) throws Exception {
        return context.proceed();
    }
}
