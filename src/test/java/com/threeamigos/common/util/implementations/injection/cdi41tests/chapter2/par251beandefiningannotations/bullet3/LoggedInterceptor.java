package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par251beandefiningannotations.bullet3;

import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@Logged
public class LoggedInterceptor {

    @AroundInvoke
    Object aroundInvoke(InvocationContext invocationContext) throws Exception {
        return invocationContext.proceed();
    }
}
