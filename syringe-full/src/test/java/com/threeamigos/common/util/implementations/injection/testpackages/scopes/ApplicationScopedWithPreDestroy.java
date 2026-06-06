package com.threeamigos.common.util.implementations.injection.testpackages.scopes;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ApplicationScopedWithPreDestroy {
    private boolean destroyed = false;

    @PreDestroy
    public void destroy() {
        this.destroyed = true;
    }

    public boolean isDestroyed() {
        return destroyed;
    }
}
