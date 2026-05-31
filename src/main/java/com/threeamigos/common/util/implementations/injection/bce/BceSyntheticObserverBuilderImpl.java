package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserver;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserverBuilder;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.inject.spi.ObserverMethod;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

final class BceSyntheticObserverBuilderImpl<T> extends BceSyntheticAbstractBuilder implements SyntheticObserverBuilder<T> {

    private final KnowledgeBase knowledgeBase;
    private final BceInvokerRegistry invokerRegistry;
    private final Class<T> observedClass;

    private Class<?> declaringClass = Object.class;
    private int priority = ObserverMethod.DEFAULT_PRIORITY;
    private boolean async = false;
    private TransactionPhase transactionPhase = TransactionPhase.IN_PROGRESS;
    private Class<? extends SyntheticObserver<T>> observerClass;

    BceSyntheticObserverBuilderImpl(KnowledgeBase knowledgeBase,
                                    BceInvokerRegistry invokerRegistry,
                                    Class<T> observedClass) {
        this.knowledgeBase = knowledgeBase;
        this.invokerRegistry = invokerRegistry;
        this.observedClass = observedClass;
    }

    @Override
    public SyntheticObserverBuilder<T> declaringClass(Class<?> declaringClass) {
        if (declaringClass != null) {
            this.declaringClass = declaringClass;
        }
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> declaringClass(ClassInfo declaringClass) {
        if (declaringClass != null) {
            this.declaringClass = BceMetadata.unwrapClassInfo(declaringClass);
        }
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> qualifier(Class<? extends Annotation> qualifier) {
        qualifierImpl(qualifier);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> qualifier(AnnotationInfo qualifier) {
        qualifierImpl(qualifier);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> qualifier(Annotation qualifier) {
        qualifierImpl(qualifier);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> priority(int priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> async(boolean async) {
        this.async = async;
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> transactionPhase(TransactionPhase transactionPhase) {
        if (transactionPhase != null) {
            this.transactionPhase = transactionPhase;
        }
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, boolean value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, boolean[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, int value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, int[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, long value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, long[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, double value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, double[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, String value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, String[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, Enum<?> value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, Enum<?>[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, Class<?> value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, ClassInfo value) {
        withParamInternal(name, value != null ? BceMetadata.unwrapClassInfo(value) : null);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, Class<?>[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, ClassInfo[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, AnnotationInfo value) {
        withParamInternal(name, value != null ? BceMetadata.unwrapAnnotationInfo(value) : null);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, Annotation value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, AnnotationInfo[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, Annotation[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, InvokerInfo value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String name, InvokerInfo[] value) {
        withParamInternal(name, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> observeWith(Class<? extends SyntheticObserver<T>> observerClass) {
        this.observerClass = observerClass;
        return this;
    }

    void complete() {
        if (observerClass == null) {
            throw new IllegalStateException("Synthetic observer implementation is required via observeWith()");
        }
        Set<Annotation> effectiveQualifiers = new LinkedHashSet<>(qualifiers);
        BceSyntheticObserverMethod<T> observerMethod = new BceSyntheticObserverMethod<>(
                declaringClass,
                observedClass,
                effectiveQualifiers,
                priority,
                async,
                transactionPhase,
                observerClass,
                Collections.unmodifiableMap(new LinkedHashMap<>(params)),
                invokerRegistry
        );
        knowledgeBase.addSyntheticObserverMethod(observerMethod);
    }
}
