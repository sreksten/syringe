package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par67contextmanagementforcustomscopes;

import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.BeanManager;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MyTestCustomContext implements Context {

    private final Map<Contextual<?>, Entry<?>> instances = new ConcurrentHashMap<Contextual<?>, Entry<?>>();
    private volatile boolean active;
    private volatile BeanManager beanManager;

    public void setBeanManager(BeanManager beanManager) {
        this.beanManager = beanManager;
    }

    public void activate(Object payload) {
        if (active) {
            return;
        }
        active = true;
        fireLifecycleEvent(payload, initializedLiteral());
    }

    public void deactivate(Object payload) {
        if (!active) {
            return;
        }
        fireLifecycleEvent(payload, beforeDestroyedLiteral());
        destroyAllInstances();
        active = false;
        fireLifecycleEvent(payload, destroyedLiteral());
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return MyTestCustomScoped.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Contextual<T> contextual) {
        ensureActive();
        Entry<?> entry = instances.get(contextual);
        return entry == null ? null : (T) entry.instance;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        ensureActive();
        Entry<?> existing = instances.get(contextual);
        if (existing != null) {
            return (T) existing.instance;
        }
        T created = contextual.create(creationalContext);
        if (created == null) {
            return null;
        }
        instances.put(contextual, new Entry<T>(created, contextual, creationalContext));
        return created;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void destroyAllInstances() {
        for (Entry<?> entry : instances.values()) {
            Entry raw = entry;
            raw.contextual.destroy(raw.instance, raw.creationalContext);
        }
        instances.clear();
    }

    private void ensureActive() {
        if (!active) {
            throw new ContextNotActiveException("Custom context @" + getScope().getSimpleName() + " is not active");
        }
    }

    private void fireLifecycleEvent(Object payload, Annotation qualifier) {
        BeanManager manager = beanManager;
        if (manager == null) {
            return;
        }
        Object eventPayload = payload == null ? getScope().getSimpleName() : payload;
        manager.getEvent().select(Object.class, qualifier).fire(eventPayload);
    }

    private Annotation initializedLiteral() {
        return Initialized.Literal.of(MyTestCustomScoped.class);
    }

    private Annotation beforeDestroyedLiteral() {
        return BeforeDestroyed.Literal.of(MyTestCustomScoped.class);
    }

    private Annotation destroyedLiteral() {
        return Destroyed.Literal.of(MyTestCustomScoped.class);
    }

    private static class Entry<T> {
        private final T instance;
        private final Contextual<T> contextual;
        private final CreationalContext<T> creationalContext;

        private Entry(T instance, Contextual<T> contextual, CreationalContext<T> creationalContext) {
            this.instance = instance;
            this.contextual = contextual;
            this.creationalContext = creationalContext;
        }
    }
}
