package com.threeamigos.common.util.implementations.injection.testpackages.scopes;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ApplicationScopedClass {
    private final String id;

    public ApplicationScopedClass() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }
}
