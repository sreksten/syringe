package com.threeamigos.common.util.implementations.injection.events;

import com.threeamigos.common.util.implementations.injection.resolution.BeanResolver;
import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import com.threeamigos.common.util.implementations.injection.scopes.ScopeContext;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.inject.spi.ObserverMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasObservesAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasObservesAsyncAnnotation;

/**
 * Adapts a reflective {@link ObserverMethodInfo} to the CDI {@link ObserverMethod} SPI,
 * allowing portable extensions to receive and replace it via {@code ProcessObserverMethod}.
 *
 * <p>Originally a private inner class of {@code Syringe}; extracted so that
 * {@code ObserverSupportImpl} can reference it directly.
 */
public class ReflectiveObserverMethodAdapter<T> implements ObserverMethod<T> {

    private final ObserverMethodInfo info;
    private final BeanResolver beanResolver;
    private final ContextManager contextManager;
    private final String identityKey;

    public ReflectiveObserverMethodAdapter(ObserverMethodInfo info,
                                           BeanResolver beanResolver,
                                           ContextManager contextManager) {
        this.info = info;
        this.beanResolver = beanResolver;
        this.contextManager = contextManager;
        this.identityKey = observerMethodIdentityKey(info);
    }

    @Override
    public Class<?> getBeanClass() {
        return info.getDeclaringBean() != null
                ? info.getDeclaringBean().getBeanClass()
                : info.getObserverMethod().getDeclaringClass();
    }

    @Override
    public Type getObservedType() {
        return info.getEventType();
    }

    @Override
    public Set<Annotation> getObservedQualifiers() {
        return info.getQualifiers();
    }

    @Override
    public Reception getReception() {
        return info.getReception();
    }

    @Override
    public jakarta.enterprise.event.TransactionPhase getTransactionPhase() {
        return info.getTransactionPhase();
    }

    @Override
    public void notify(T event) {
        try {
            if (info.getReception() == Reception.IF_EXISTS && info.getDeclaringBean() != null) {
                Class<? extends Annotation> scope = info.getDeclaringBean().getScope();
                try {
                    ScopeContext ctx = contextManager.getContext(scope);
                    Object existing = ctx.getIfExists(info.getDeclaringBean());
                    if (existing == null) {
                        return;
                    }
                } catch (IllegalArgumentException ignored) {
                    return;
                }
            }

            Method method = info.getObserverMethod();
            Object beanInstance = info.getDeclaringBean() != null
                    ? beanResolver.resolveDeclaringBeanInstance(info.getDeclaringBean().getBeanClass())
                    : beanResolver.resolveDeclaringBeanInstance(method.getDeclaringClass());

            Parameter[] params = method.getParameters();
            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                Parameter p = params[i];
                if (hasObservesAnnotation(p) || hasObservesAsyncAnnotation(p)) {
                    args[i] = event;
                } else {
                    args[i] = beanResolver.resolve(p.getParameterizedType(), p.getAnnotations());
                }
            }

            method.setAccessible(true);
            method.invoke(beanInstance, args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to notify observer " +
                    info.getObserverMethod().getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAsync() {
        return info.isAsync();
    }

    @Override
    public int getPriority() {
        return info.getPriority();
    }

    @Override
    public int hashCode() {
        return identityKey.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ObserverMethod<?>)) {
            return false;
        }
        return identityKey.equals(observerMethodIdentityKey((ObserverMethod<?>) obj));
    }

    // -------------------------------------------------------------------------
    // Identity-key helpers (used for deduplication across ProcessObserverMethod
    // SPI events)
    // -------------------------------------------------------------------------

    static String observerMethodIdentityKey(ObserverMethodInfo info) {
        if (info == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Method observerMethod = info.getObserverMethod();
        if (observerMethod != null) {
            sb.append(observerMethod.getDeclaringClass().getName())
                    .append('#').append(observerMethod.getName()).append('(');
            Class<?>[] parameterTypes = observerMethod.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(parameterTypes[i].getName());
            }
            sb.append(')');
        } else if (info.getSyntheticObserver() != null) {
            sb.append("synthetic:").append(info.getSyntheticObserver().getClass().getName());
        } else {
            sb.append("unknown");
        }
        sb.append('|').append(info.getEventType() != null ? info.getEventType().getTypeName() : "");
        sb.append('|').append(sortedQualifierIdentity(info.getQualifiers()));
        sb.append('|').append(info.getReception());
        sb.append('|').append(info.getTransactionPhase());
        sb.append('|').append(info.isAsync());
        sb.append('|').append(info.getPriority());
        return sb.toString();
    }

    static String observerMethodIdentityKey(ObserverMethod<?> observerMethod) {
        ObserverMethodInfo info = extractObserverMethodInfo(observerMethod);
        if (info != null) {
            return observerMethodIdentityKey(info);
        }
        StringBuilder sb = new StringBuilder();
        if (observerMethod.getBeanClass() != null) {
            sb.append(observerMethod.getBeanClass().getName());
        } else {
            sb.append("unknown");
        }
        sb.append('|').append(observerMethod.getObservedType() != null
                ? observerMethod.getObservedType().getTypeName() : "");
        sb.append('|').append(sortedQualifierIdentity(observerMethod.getObservedQualifiers()));
        sb.append('|').append(observerMethod.getReception());
        sb.append('|').append(observerMethod.getTransactionPhase());
        sb.append('|').append(observerMethod.isAsync());
        sb.append('|').append(observerMethod.getPriority());
        return sb.toString();
    }

    private static ObserverMethodInfo extractObserverMethodInfo(ObserverMethod<?> observerMethod) {
        if (observerMethod == null) {
            return null;
        }
        Class<?> current = observerMethod.getClass();
        while (current != null && !Object.class.equals(current)) {
            for (Field field : current.getDeclaredFields()) {
                if (!ObserverMethodInfo.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(observerMethod);
                    if (value instanceof ObserverMethodInfo) {
                        return (ObserverMethodInfo) value;
                    }
                } catch (IllegalAccessException ignored) {
                    // try next field/superclass
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static String sortedQualifierIdentity(Set<Annotation> qualifiers) {
        if (qualifiers == null || qualifiers.isEmpty()) {
            return "";
        }
        List<String> entries = new ArrayList<>();
        for (Annotation qualifier : qualifiers) {
            if (qualifier != null) {
                entries.add(qualifier.annotationType().getName() + ":" + qualifier);
            }
        }
        Collections.sort(entries);
        return String.join(",", entries);
    }
}
