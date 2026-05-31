package com.threeamigos.common.util.implementations.injection.testpackages.scopes;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ConversationScoped;

import java.io.Serializable;

@ConversationScoped
public class ConversationScopedWithPreDestroy implements Serializable {
    private boolean preDestroyCalled = false;

    @PreDestroy
    public void destroy() {
        preDestroyCalled = true;
    }

    public boolean isPreDestroyCalled() {
        return preDestroyCalled;
    }
}
