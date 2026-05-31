package com.threeamigos.common.util.implementations.injection.builtinbeans;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * Built-in CDI interceptor for @ActivateRequestContext.
 */
@Interceptor
@ActivateRequestContext
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 100)
public class ActivateRequestContextInterceptor {

    @Inject
    private RequestContextController requestContextController;

    @AroundInvoke
    Object aroundInvoke(InvocationContext invocationContext) throws Exception {
        boolean activated = requestContextController.activate();
        try {
            return invocationContext.proceed();
        } finally {
            if (activated) {
                requestContextController.deactivate();
            }
        }
    }
}
