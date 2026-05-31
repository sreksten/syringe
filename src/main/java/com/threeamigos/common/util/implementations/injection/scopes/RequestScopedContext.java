package com.threeamigos.common.util.implementations.injection.scopes;

import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of RequestScoped context.
 * Maintains instances for the duration of a single request.
 *
 * <p><b>PHASE 2 - Interceptor Support:</b> This context automatically wraps beans that
 * have interceptors with interceptor-aware proxies. This ensures that interceptor chains
 * are executed before business methods are called.
 *
 * @author Stefano Reksten
 */
public class RequestScopedContext implements ScopeContext {

    private final ThreadLocal<Map<Bean<?>, Object>> requestInstances = ThreadLocal.withInitial(ConcurrentHashMap::new);
    private final ThreadLocal<Map<Bean<?>, CreationalContext<?>>> requestContexts = ThreadLocal.withInitial(ConcurrentHashMap::new);
    private final ThreadLocal<Boolean> requestActive = ThreadLocal.withInitial(() -> false);
    private volatile boolean active = true;

    /**
     * Activates the request scope for the current thread.
     */
    public void activateRequest() {
        requestActive.set(true);
        requestInstances.get().clear();
        requestContexts.get().clear();
    }

    /**
     * Deactivates and cleans up the request scope for the current thread.
     */
    public void deactivateRequest() {
        if (requestActive.get()) {
            destroyRequest();
            requestActive.set(false);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Bean<T> bean, CreationalContext<T> creationalContext) {
        if (!active) {
            throw new ContextNotActiveException("RequestScoped context is not active");
        }

        if (!requestActive.get()) {
            throw new ContextNotActiveException("No active request. Call activateRequest() first.");
        }

        Map<Bean<?>, Object> instances = requestInstances.get();
        Map<Bean<?>, CreationalContext<?>> contexts = requestContexts.get();

        return (T) instances.computeIfAbsent(bean, b -> {
            if (creationalContext != null) {
                contexts.put(bean, creationalContext);
            }

            // Step 1: Create the actual bean instance
            T instance = bean.create(creationalContext);

            // Step 2: PHASE 2 - Wrap with interceptor-aware proxy if bean has interceptors
            if (bean instanceof BeanImpl) {
                BeanImpl<T> beanImpl = (BeanImpl<T>) bean;
                if (beanImpl.hasInterceptors()) {
                    instance = beanImpl.createInterceptorAwareProxy(instance);
                }
            }

            return instance;
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getIfExists(Bean<T> bean) {
        if (!requestActive.get()) {
            return null;
        }
        return (T) requestInstances.get().get(bean);
    }

    @Override
    public void destroy() {
        destroyRequest();
        requestInstances.remove();
        requestContexts.remove();
        requestActive.remove();
        active = false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void destroy(Contextual<?> contextual) {
        if (!active) {
            throw new ContextNotActiveException("RequestScoped context is not active");
        }
        if (!requestActive.get()) {
            throw new ContextNotActiveException("No active request. Call activateRequest() first.");
        }
        if (!(contextual instanceof Bean)) {
            return;
        }

        Map<Bean<?>, Object> instances = requestInstances.get();
        Map<Bean<?>, CreationalContext<?>> contexts = requestContexts.get();
        Bean<Object> bean = (Bean<Object>) contextual;
        Object instance = instances.remove(bean);
        CreationalContext<Object> ctx = (CreationalContext<Object>) contexts.remove(bean);
        if (instance != null) {
            bean.destroy(instance, ctx);
        }
    }

    @Override
    public boolean isActive() {
        return active && requestActive.get();
    }

    @Override
    public boolean isPassivationCapable() {
        // RequestScoped beans are short-lived (destroyed at end of request)
        // They are never passivated, so no serialization requirement
        return false;
    }

    @SuppressWarnings("unchecked")
    private void destroyRequest() {
        Map<Bean<?>, Object> instances = requestInstances.get();
        Map<Bean<?>, CreationalContext<?>> contexts = requestContexts.get();

        for (Map.Entry<Bean<?>, Object> entry : instances.entrySet()) {
            Bean<Object> bean = (Bean<Object>) entry.getKey();
            Object instance = entry.getValue();
            CreationalContext<Object> ctx = (CreationalContext<Object>) contexts.get(bean);

            try {
                bean.destroy(instance, ctx);
            } catch (Exception e) {
                System.err.println("Error destroying bean " + bean.getBeanClass().getName() +
                                 " in request: " + e.getMessage());
            }
        }

        instances.clear();
        contexts.clear();
    }
}
