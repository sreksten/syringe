package com.threeamigos.common.util.implementations.injection.testpackages.scopes;

import jakarta.enterprise.context.SessionScoped;
import java.io.Serializable;

@SessionScoped
public class SessionScopedClass implements Serializable {
    private final String id;

    public SessionScopedClass() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }
}
