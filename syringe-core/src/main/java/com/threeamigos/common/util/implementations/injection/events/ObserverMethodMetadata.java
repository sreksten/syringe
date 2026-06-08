package com.threeamigos.common.util.implementations.injection.events;

import com.threeamigos.common.util.implementations.injection.types.TypeHelper;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.ObserverMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Set;

/**
 * Core observer metadata contract shared across modules.
 */
public interface ObserverMethodMetadata {

    Method getObserverMethod();

    Type getEventType();

    Set<Annotation> getQualifiers();

    Reception getReception();

    TransactionPhase getTransactionPhase();

    boolean isAsync();

    Bean<?> getDeclaringBean();

    int getPriority();

    int getObservedParameterPosition();

    ObserverMethod<?> getSyntheticObserver();

    default boolean isSynthetic() {
        return getSyntheticObserver() != null;
    }

    default boolean matches(Type eventType, Set<Annotation> eventQualifiers) {
        TypeHelper typeHelper = new TypeHelper();
        if (!typeHelper.isAssignable(getEventType(), eventType)) {
            return false;
        }
        return eventQualifiers.containsAll(getQualifiers());
    }
}
