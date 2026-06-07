package com.threeamigos.common.util.implementations.injection.scopes;

import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of ApplicationScoped context.
 * Maintains a single instance per bean for the entire application lifecycle.
 *
 * <p><b>PHASE 2 - Interceptor Support:</b> This context automatically wraps beans that
 * have interceptors with interceptor-aware proxies. This ensures that interceptor chains
 * are executed before business methods are called.
 *
 * @author Stefano Reksten
 */
public class ApplicationScopedContext implements ScopeContext {

    private final Map<Bean<?>, Object> instances = new ConcurrentHashMap<>();
    private final Map<Bean<?>, CreationalContext<?>> creationalContexts = new ConcurrentHashMap<>();
    private volatile boolean active = true;

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Bean<T> bean, CreationalContext<T> creationalContext) {
        if (!active) {
            throw new ContextNotActiveException("ApplicationScoped context is not active");
        }

        Object existing = instances.get(bean);
        if (existing != null) {
            return (T) existing;
        }

        synchronized (this) {
            existing = instances.get(bean);
            if (existing != null) {
                return (T) existing;
            }

            // Create the contextual instance; explicit locking avoids nested computeIfAbsent
            // recursion when bean creation triggers other ApplicationScoped bean lookups.
            T instance = bean.create(creationalContext);

            // PHASE 2 - Wrap with interceptor-aware proxy if bean has interceptors.
            if (bean instanceof BeanImpl) {
                BeanImpl<T> beanImpl = (BeanImpl<T>) bean;
                if (beanImpl.hasInterceptors()) {
                    instance = beanImpl.createInterceptorAwareProxy(instance);
                }
            }

            if (creationalContext != null) {
                creationalContexts.put(bean, creationalContext);
            }
            instances.put(bean, instance);
            return instance;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getIfExists(Bean<T> bean) {
        return (T) instances.get(bean);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void destroy() {
        for (Map.Entry<Bean<?>, Object> entry : instances.entrySet()) {
            Bean<Object> bean = (Bean<Object>) entry.getKey();
            Object instance = entry.getValue();
            CreationalContext<Object> ctx = (CreationalContext<Object>) creationalContexts.get(bean);

            try {
                bean.destroy(instance, ctx);
            } catch (Exception e) {
                // Log error but continue destroying other beans
                System.err.println("Error destroying bean " + bean.getBeanClass().getName() + ": " + e.getMessage());
            }
        }

        instances.clear();
        creationalContexts.clear();
        active = false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void destroy(Contextual<?> contextual) {
        if (!active) {
            throw new ContextNotActiveException("ApplicationScoped context is not active");
        }
        if (!(contextual instanceof Bean)) {
            return;
        }
        Bean<Object> bean = (Bean<Object>) contextual;
        Object instance = instances.remove(bean);
        CreationalContext<Object> ctx = (CreationalContext<Object>) creationalContexts.remove(bean);
        if (instance != null) {
            bean.destroy(instance, ctx);
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isPassivationCapable() {
        // ApplicationScoped beans live for the entire application lifetime
        // They are never passivated, so no serialization requirement
        return false;
    }
}
