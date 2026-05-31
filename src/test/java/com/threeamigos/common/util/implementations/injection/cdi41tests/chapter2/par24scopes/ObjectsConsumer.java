package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par24scopes;

import jakarta.inject.Inject;

public class ObjectsConsumer {

    @Inject
    private ApplicationScopedObject applicationScopedObject;

    @Inject
    private SessionScopedObject sessionScopedObject;

    @Inject
    private RequestScopedObject requestScopedObject;

    @Inject
    private DependentObject dependentObject;

    public ApplicationScopedObject getApplicationScopedObject() {
        return applicationScopedObject;
    }

    public SessionScopedObject getSessionScopedObject() {
        return sessionScopedObject;
    }

    public RequestScopedObject getRequestScopedObject() {
        return requestScopedObject;
    }

    public DependentObject getDependentObject() {
        return dependentObject;
    }
}
