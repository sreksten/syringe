package com.threeamigos.common.util.implementations.injection.spi.configurators;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.enterprise.inject.spi.configurator.ObserverMethodConfigurator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasQualifierAnnotation;

/**
 * Implementation of ObserverMethodConfigurator for building observer methods programmatically.
 *
 * <p>Provides a fluent API for configuring observer method behavior:
 * <ul>
 *   <li>Observed event type via {@link #observedType(Type)}</li>
 *   <li>Qualifiers via {@link #addQualifier(Annotation)}</li>
 *   <li>Reception strategy via {@link #reception(Reception)}</li>
 *   <li>Transaction phase via {@link #transactionPhase(TransactionPhase)}</li>
 *   <li>Priority via {@link #priority(int)}</li>
 *   <li>Async flag via {@link #async(boolean)}</li>
 *   <li>Notification callback via {@link #notifyWith(EventConsumer)}</li>
 * </ul>
 *
 * @param <T> the type of event observed
 * @see ObserverMethodConfigurator
 * @see jakarta.enterprise.inject.spi.ProcessObserverMethod
 */
public class ObserverMethodConfiguratorImpl<T> implements ObserverMethodConfigurator<T> {

    private final boolean preserveNotifyCallbackWhenReadingObserverMethod;
    private Class<?> beanClass;
    private Type observedType;
    private final Set<Annotation> qualifiers = new HashSet<>();
    private Reception reception = Reception.ALWAYS;
    private TransactionPhase transactionPhase = TransactionPhase.IN_PROGRESS;
    private int priority = ObserverMethod.DEFAULT_PRIORITY;
    private boolean async = false;
    private EventConsumer<T> notifyCallback;
    private ObserverMethod<T> originalObserverMethod;

    /**
     * Creates an ObserverMethodConfigurator.
     *
     * @param preserveNotifyCallbackWhenReadingObserverMethod whether read(ObserverMethod) should retain
     *                                                        notification behavior when notifyWith() is not invoked
     */
    public ObserverMethodConfiguratorImpl(boolean preserveNotifyCallbackWhenReadingObserverMethod) {
        this.preserveNotifyCallbackWhenReadingObserverMethod = preserveNotifyCallbackWhenReadingObserverMethod;
    }

    @Override
    public ObserverMethodConfigurator<T> read(java.lang.reflect.Method method) {
        if (method == null) {
            throw new IllegalArgumentException("Method cannot be null");
        }
        this.beanClass = method.getDeclaringClass();
        this.qualifiers.clear();
        this.reception = Reception.ALWAYS;
        this.transactionPhase = TransactionPhase.IN_PROGRESS;
        this.async = false;
        this.originalObserverMethod = null;

        Parameter[] parameters = method.getParameters();
        Type[] genericTypes = method.getGenericParameterTypes();
        boolean foundObserverParameter = false;

        for (int i = 0; i < parameters.length; i++) {
            Type parameterType = i < genericTypes.length ? genericTypes[i] : parameters[i].getParameterizedType();
            if (readObserverParameterMetadata(parameterType, parameters[i].getAnnotations())) {
                foundObserverParameter = true;
                break;
            }
        }

        if (!foundObserverParameter) {
            throw new IllegalArgumentException("Method " + method + " does not declare an @Observes/@ObservesAsync parameter");
        }

        return this;
    }

    @Override
    public ObserverMethodConfigurator<T> read(jakarta.enterprise.inject.spi.AnnotatedMethod<?> method) {
        if (method == null) {
            throw new IllegalArgumentException("AnnotatedMethod cannot be null");
        }
        this.beanClass = method.getDeclaringType().getJavaClass();
        this.qualifiers.clear();
        this.reception = Reception.ALWAYS;
        this.transactionPhase = TransactionPhase.IN_PROGRESS;
        this.async = false;
        this.originalObserverMethod = null;

        boolean foundObserverParameter = false;
        for (AnnotatedParameter<?> parameter : method.getParameters()) {
            if (readObserverParameterMetadata(parameter.getBaseType(),
                    parameter.getAnnotations().toArray(new Annotation[0]))) {
                foundObserverParameter = true;
                break;
            }
        }

        if (!foundObserverParameter) {
            throw new IllegalArgumentException("AnnotatedMethod " + method.getJavaMember() +
                    " does not declare an @Observes/@ObservesAsync parameter");
        }

        return this;
    }

    @Override
    public ObserverMethodConfigurator<T> read(ObserverMethod<T> observerMethod) {
        if (observerMethod == null) {
            throw new IllegalArgumentException("ObserverMethod cannot be null");
        }
        // Copy all configuration from existing ObserverMethod
        this.beanClass = observerMethod.getBeanClass();
        this.observedType = observerMethod.getObservedType();
        this.qualifiers.clear();
        this.qualifiers.addAll(observerMethod.getObservedQualifiers());
        this.reception = observerMethod.getReception();
        this.transactionPhase = observerMethod.getTransactionPhase();
        this.priority = observerMethod.getPriority();
        this.async = observerMethod.isAsync();
        this.originalObserverMethod = observerMethod;
        return this;
    }

    private boolean readObserverParameterMetadata(Type parameterType, Annotation[] annotations) {
        if (annotations == null) {
            return false;
        }

        boolean observerParameter = false;
        Reception resolvedReception = this.reception;
        TransactionPhase resolvedTransactionPhase = this.transactionPhase;
        boolean resolvedAsync = this.async;
        Set<Annotation> resolvedQualifiers = new HashSet<>();

        for (Annotation annotation : annotations) {
            if (annotation == null) {
                continue;
            }

            if (annotation instanceof Observes) {
                Observes observes = (Observes) annotation;
                resolvedReception = observes.notifyObserver();
                resolvedTransactionPhase = observes.during();
                resolvedAsync = false;
                observerParameter = true;
                continue;
            }

            if (annotation instanceof ObservesAsync) {
                ObservesAsync observesAsync = (ObservesAsync) annotation;
                resolvedReception = observesAsync.notifyObserver();
                resolvedTransactionPhase = TransactionPhase.IN_PROGRESS;
                resolvedAsync = true;
                observerParameter = true;
                continue;
            }

            if (hasQualifierAnnotation(annotation.annotationType())) {
                resolvedQualifiers.add(annotation);
            }
        }

        if (observerParameter) {
            this.observedType = parameterType;
            this.reception = resolvedReception;
            this.transactionPhase = resolvedTransactionPhase;
            this.async = resolvedAsync;
            this.qualifiers.clear();
            this.qualifiers.addAll(resolvedQualifiers);
        }

        return observerParameter;
    }

    @Override
    public ObserverMethodConfigurator<T> beanClass(Class<?> beanClass) {
        this.beanClass = beanClass;
        return this;
    }

    @Override
    public ObserverMethodConfigurator<T> observedType(Type type) {
        this.observedType = type;
        return this;
    }

    @Override
    public ObserverMethodConfigurator<T> addQualifier(Annotation qualifier) {
        if (qualifier != null) {
            this.qualifiers.add(qualifier);
        }
        return this;
    }

    @Override
    public ObserverMethodConfigurator<T> addQualifiers(Annotation... qualifiers) {
        if (qualifiers != null) {
            for (Annotation q : qualifiers) {
                if (q != null) {
                    this.qualifiers.add(q);
                }
            }
        }
        return this;
    }

    @Override
    public ObserverMethodConfigurator<T> addQualifiers(Set<Annotation> qualifiers) {
        if (qualifiers != null) {
            this.qualifiers.addAll(qualifiers);
        }
        return this;
    }

    @Override
    public ObserverMethodConfigurator<T> qualifiers(Annotation... qualifiers) {
        this.qualifiers.clear();
        return addQualifiers(qualifiers);
    }

    @Override
    public ObserverMethodConfigurator<T> qualifiers(Set<Annotation> qualifiers) {
        this.qualifiers.clear();
        return addQualifiers(qualifiers);
    }

    @Override
    public ObserverMethodConfigurator<T> reception(Reception reception) {
        if (reception != null) {
            this.reception = reception;
        }
        return this;
    }

    @Override
    public ObserverMethodConfigurator<T> transactionPhase(TransactionPhase transactionPhase) {
        if (transactionPhase != null) {
            this.transactionPhase = transactionPhase;
        }
        return this;
    }

    @Override
    public ObserverMethodConfigurator<T> priority(int priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public ObserverMethodConfigurator<T> notifyWith(EventConsumer<T> callback) {
        this.notifyCallback = callback;
        return this;
    }

    @Override
    public ObserverMethodConfigurator<T> async(boolean async) {
        this.async = async;
        return this;
    }

    /**
     * Completes the configuration and returns a configured ObserverMethod.
     *
     * <p>This method creates a synthetic ObserverMethod implementation.
     *
     * @return the configured ObserverMethod
     */
    public ObserverMethod<T> complete() {
        if (observedType == null) {
            throw new IllegalStateException("Observed type must be set");
        }
        if (notifyCallback == null && originalObserverMethod != null &&
                preserveNotifyCallbackWhenReadingObserverMethod) {
            notifyCallback = eventContext -> originalObserverMethod.notify(eventContext.getEvent());
        }
        if (notifyCallback == null) {
            throw new IllegalStateException("Notification callback must be set via notifyWith()");
        }

        return new SyntheticObserverMethod<>(
            beanClass,
            observedType,
            new HashSet<>(qualifiers),
            reception,
            transactionPhase,
            priority,
            async,
            notifyCallback
        );
    }

    /**
     * Synthetic ObserverMethod implementation.
     */
    private static class SyntheticObserverMethod<T> implements ObserverMethod<T> {
        private final Class<?> beanClass;
        private final Type observedType;
        private final Set<Annotation> qualifiers;
        private final Reception reception;
        private final TransactionPhase transactionPhase;
        private final int priority;
        private final boolean async;
        private final EventConsumer<T> notifyCallback;

        SyntheticObserverMethod(
                Class<?> beanClass,
                Type observedType,
                Set<Annotation> qualifiers,
                Reception reception,
                TransactionPhase transactionPhase,
                int priority,
                boolean async,
                EventConsumer<T> notifyCallback) {
            this.beanClass = beanClass;
            this.observedType = observedType;
            this.qualifiers = new HashSet<>(qualifiers);
            this.reception = reception;
            this.transactionPhase = transactionPhase;
            this.priority = priority;
            this.async = async;
            this.notifyCallback = notifyCallback;
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
            return reception;
        }

        @Override
        public TransactionPhase getTransactionPhase() {
            return transactionPhase;
        }

        @Override
        public void notify(T event) {
            notify(new EventContext<T>() {
                @Override
                public T getEvent() {
                    return event;
                }

                @Override
                public jakarta.enterprise.inject.spi.EventMetadata getMetadata() {
                    return null;
                }
            });
        }

        @Override
        public void notify(EventContext<T> eventContext) {
            if (notifyCallback != null) {
                try {
                    notifyCallback.accept(eventContext);
                } catch (Exception e) {
                    throw new RuntimeException("Error notifying observer", e);
                }
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
}
