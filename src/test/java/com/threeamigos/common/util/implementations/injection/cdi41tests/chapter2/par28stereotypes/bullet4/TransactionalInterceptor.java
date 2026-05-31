package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet4;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Transactional
@Interceptor
@Priority(200)
public class TransactionalInterceptor {

    @AroundInvoke
    Object aroundInvoke(InvocationContext invocationContext) throws Exception {
        return invocationContext.proceed();
    }
}
