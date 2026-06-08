package com.threeamigos.common.util.implementations.injection.spi.configured;

import com.threeamigos.common.util.implementations.injection.spi.configurators.AnnotatedMethodConfiguratorImpl;
import com.threeamigos.common.util.implementations.injection.spi.configurators.AnnotatedParameterConfiguratorImpl;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;

class ConfiguredAnnotatedMethod<X> implements AnnotatedMethod<X> {
    private final AnnotatedMethod<X> delegate;
    private final Set<Annotation> annotations;
    private final AnnotatedType<X> declaringType;
    private final List<AnnotatedParameter<X>> parameters;

    ConfiguredAnnotatedMethod(AnnotatedMethodConfiguratorImpl<? super X> configurator,
                              AnnotatedType<X> declaringType) {
        @SuppressWarnings("unchecked")
        AnnotatedMethod<X> originalMethod = (AnnotatedMethod<X>) configurator.getOriginalMethod();
        this.delegate = originalMethod;
        this.annotations = Collections.unmodifiableSet(new HashSet<>(configurator.getAnnotations()));
        this.declaringType = declaringType;
        this.parameters = buildParameters(configurator.getParameterConfigurators());
    }

    @Override
    public java.lang.reflect.Method getJavaMember() {
        return delegate.getJavaMember();
    }

    @Override
    public boolean isStatic() {
        return delegate.isStatic();
    }

    @Override
    public AnnotatedType<X> getDeclaringType() {
        return declaringType;
    }

    @Override
    public Type getBaseType() {
        return delegate.getBaseType();
    }

    @Override
    public Set<Type> getTypeClosure() {
        return delegate.getTypeClosure();
    }

    @Override
    public List<AnnotatedParameter<X>> getParameters() {
        return parameters;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        for (Annotation annotation : annotations) {
            if (annotationType.isInstance(annotation)) {
                return annotationType.cast(annotation);
            }
        }
        return null;
    }

    @Override
    public Set<Annotation> getAnnotations() {
        return annotations;
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return getAnnotation(annotationType) != null;
    }

    private List<AnnotatedParameter<X>> buildParameters(List<? extends AnnotatedParameterConfiguratorImpl<? super X>> parameterConfigurators) {
        List<AnnotatedParameter<X>> configured = new ArrayList<>();
        for (AnnotatedParameterConfiguratorImpl<? super X> paramConfigurator : parameterConfigurators) {
            @SuppressWarnings("unchecked")
            AnnotatedParameter<X> originalParam = (AnnotatedParameter<X>) paramConfigurator.getOriginalParameter();
            configured.add(new ConfiguredAnnotatedParameter<>(
                    originalParam,
                    paramConfigurator.getAnnotations(),
                    this
            ));
        }
        return Collections.unmodifiableList(configured);
    }
}
