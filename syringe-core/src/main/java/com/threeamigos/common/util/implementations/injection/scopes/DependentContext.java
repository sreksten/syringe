package com.threeamigos.common.util.implementations.injection.scopes;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;

/**
 * Implementation of Dependent pseudo-scope.
 * Always creates a new instance - no caching.
 * Instances are destroyed when their owning bean is destroyed.
 *
 * @author Stefano Reksten
 */
public class DependentContext implements ScopeContext {

    @Override
    public <T> T get(Bean<T> bean, CreationalContext<T> creationalContext) {
        // Dependent scope always creates a new instance
        return bean.create(creationalContext);
    }

    @Override
    public <T> T getIfExists(Bean<T> bean) {
        // Dependent scope never has "existing" instances
        return null;
    }

    @Override
    public void destroy() {
        // Dependent instances are destroyed by their owning beans, not by the context
        // No-op here
    }

    @Override
    public boolean isActive() {
        // Dependent scope is always active
        return true;
    }

    @Override
    public boolean isPassivationCapable() {
        // Dependent is a pseudo-scope with no independent lifecycle
        // Passivation is determined by the bean that owns the dependent instance
        return false;
    }
}
