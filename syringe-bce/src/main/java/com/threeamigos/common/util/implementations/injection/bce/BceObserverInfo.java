package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.events.ObserverMethodInfo;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.ObserverInfo;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.declarations.ParameterInfo;
import jakarta.enterprise.lang.model.types.Type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class BceObserverInfo implements ObserverInfo {

    private final ObserverMethodInfo delegate;

    private BceObserverInfo(ObserverMethodInfo delegate) {
        this.delegate = delegate;
    }

    static Collection<ObserverInfo> from(Collection<ObserverMethodInfo> observerMethodInfos) {
        if (observerMethodInfos == null || observerMethodInfos.isEmpty()) {
            return Collections.emptyList();
        }
        List<ObserverInfo> out = new ArrayList<>();
        for (ObserverMethodInfo info : observerMethodInfos) {
            out.add(new BceObserverInfo(info));
        }
        out.sort(Comparator
            .comparing((ObserverInfo i) -> i.declaringClass().name())
            .thenComparing(i -> {
                try {
                    return i.observerMethod().name();
                } catch (RuntimeException e) {
                    return "";
                }
            }));
        return Collections.unmodifiableList(out);
    }

    static Collection<ObserverInfo> fromSynthetic(Collection<ObserverMethod<?>> syntheticObserverMethods) {
        if (syntheticObserverMethods == null || syntheticObserverMethods.isEmpty()) {
            return Collections.emptyList();
        }
        List<ObserverInfo> out = new ArrayList<>();
        for (ObserverMethod<?> syntheticObserver : syntheticObserverMethods) {
            ObserverMethodInfo info = new ObserverMethodInfo(
                syntheticObserver.getObservedType(),
                syntheticObserver.getObservedQualifiers(),
                syntheticObserver.getReception(),
                syntheticObserver.getTransactionPhase(),
                syntheticObserver.getPriority(),
                syntheticObserver.isAsync(),
                null,
                syntheticObserver
            );
            out.add(new BceObserverInfo(info));
        }
        out.sort(Comparator
            .comparing((ObserverInfo i) -> i.declaringClass().name())
            .thenComparing(i -> {
                try {
                    return i.observerMethod().name();
                } catch (RuntimeException e) {
                    return "";
                }
            }));
        return Collections.unmodifiableList(out);
    }

    @Override
    public Type eventType() {
        return BceMetadata.type(delegate.getEventType());
    }

    @Override
    public Collection<AnnotationInfo> qualifiers() {
        List<AnnotationInfo> out = new ArrayList<>();
        for (Annotation annotation : delegate.getQualifiers()) {
            out.add(BceMetadata.annotationInfo(annotation));
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public ClassInfo declaringClass() {
        Method method = delegate.getObserverMethod();
        if (method != null) {
            return BceMetadata.classInfo(method.getDeclaringClass());
        }
        if (delegate.getSyntheticObserver() != null) {
            return BceMetadata.classInfo(delegate.getSyntheticObserver().getBeanClass());
        }
        throw new IllegalStateException("Cannot resolve declaring class for observer");
    }

    @Override
    public MethodInfo observerMethod() {
        Method method = delegate.getObserverMethod();
        if (method != null) {
            return BceMetadata.methodInfo(method);
        }
        return syntheticObserverMethodInfo();
    }

    @Override
    public ParameterInfo eventParameter() {
        if (!isSynthetic()) {
            MethodInfo methodInfo = observerMethod();
            List<ParameterInfo> parameters = methodInfo.parameters();
            if (parameters.isEmpty()) {
                throw new IllegalStateException("Observer method has no parameters");
            }
            return parameters.get(0);
        }
        List<ParameterInfo> parameters = observerMethod().parameters();
        if (parameters.isEmpty()) {
            throw new IllegalStateException("Synthetic observer placeholder has no parameters");
        }
        return parameters.get(0);
    }

    @Override
    public BeanInfo bean() {
        if (delegate.getDeclaringBean() != null) {
            return BceMetadata.beanInfo(delegate.getDeclaringBean().getBeanClass());
        }
        return BceMetadata.beanInfo(BceMetadata.unwrapClassInfo(declaringClass()));
    }

    @Override
    public boolean isSynthetic() {
        return delegate.isSynthetic();
    }

    @Override
    public int priority() {
        return delegate.getPriority();
    }

    @Override
    public boolean isAsync() {
        return delegate.isAsync();
    }

    @Override
    public jakarta.enterprise.event.Reception reception() {
        return delegate.getReception();
    }

    @Override
    public jakarta.enterprise.event.TransactionPhase transactionPhase() {
        return delegate.getTransactionPhase();
    }

    private MethodInfo syntheticObserverMethodInfo() {
        try {
            return BceMetadata.methodInfo(SyntheticObserverMethodPlaceholder.class
                .getDeclaredMethod("observe", Object.class));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Cannot resolve synthetic observer placeholder method", e);
        }
    }

    private static final class SyntheticObserverMethodPlaceholder {
        /**
         * Placeholder method for synthetic observers. The parameter is ignored as it is only used for invocation,
         * but still it is necessary. See {@link BceObserverInfo#syntheticObserverMethodInfo()}
         */
        void observe(Object ignoredEvent) {
        }
    }
}
