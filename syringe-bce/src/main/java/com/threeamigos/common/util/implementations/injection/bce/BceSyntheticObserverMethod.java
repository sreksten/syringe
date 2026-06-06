package com.threeamigos.common.util.implementations.injection.bce;

import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserver;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.ObserverMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class BceSyntheticObserverMethod<T> implements ObserverMethod<T> {

    private final Class<?> beanClass;
    private final Type observedType;
    private final Set<Annotation> qualifiers;
    private final int priority;
    private final boolean async;
    private final TransactionPhase transactionPhase;
    private final Class<? extends SyntheticObserver<T>> observerClass;
    private final Map<String, Object> params;
    private final BceInvokerRegistry invokerRegistry;

    BceSyntheticObserverMethod(Class<?> beanClass,
                               Type observedType,
                               Set<Annotation> qualifiers,
                               int priority,
                               boolean async,
                               TransactionPhase transactionPhase,
                               Class<? extends SyntheticObserver<T>> observerClass,
                               Map<String, Object> params,
                               BceInvokerRegistry invokerRegistry) {
        this.beanClass = beanClass;
        this.observedType = observedType;
        this.qualifiers = qualifiers != null
            ? Collections.unmodifiableSet(new HashSet<>(qualifiers))
            : Collections.emptySet();
        this.priority = priority;
        this.async = async;
        this.transactionPhase = transactionPhase != null ? transactionPhase : TransactionPhase.IN_PROGRESS;
        this.observerClass = observerClass;
        this.params = params;
        this.invokerRegistry = invokerRegistry;
    }

    @Override
    public Class<?> getBeanClass() {
        return beanClass;
    }

    @Override
    public Type getObservedType() {
        return observedType;
    }

    @Override
    public Set<Annotation> getObservedQualifiers() {
        return qualifiers;
    }

    @Override
    public Reception getReception() {
        return Reception.ALWAYS;
    }

    @Override
    public TransactionPhase getTransactionPhase() {
        return transactionPhase;
    }

    @Override
    public void notify(final T event) {
        try {
            final SyntheticObserver<T> observer =
                observerClass.getDeclaredConstructor().newInstance();
            final BceParameters parameters = new BceParameters(params, invokerRegistry);
            observer.observe(new EventContext<T>() {
                @Override
                public T getEvent() {
                    return event;
                }

                @Override
                public EventMetadata getMetadata() {
                    return null;
                }
            }, parameters);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to jakarta.enterprise.invoke BCE synthetic observer " + observerClass.getName(), e);
        }
    }

    @Override
    public boolean isAsync() {
        return async;
    }

    @Override
    public int getPriority() {
        return priority;
    }
}
