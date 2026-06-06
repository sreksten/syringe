package com.threeamigos.common.util.implementations.injection.builtinbeans;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.InterceptionFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Built-in bean for injecting InterceptionFactory metadata.
 *
 * <p>The actual InterceptionFactory instance is contextual and must be created
 * based on the current injection point by the resolver.
 */
public class InterceptionFactoryBean implements Bean<InterceptionFactory<?>> {

    @Override
    public Class<?> getBeanClass() {
        return InterceptionFactory.class;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet();
    }

    @Override
    public InterceptionFactory<?> create(CreationalContext<InterceptionFactory<?>> context) {
        throw new UnsupportedOperationException(
                "InterceptionFactory creation must be handled contextually by BeanResolver");
    }

    @Override
    public void destroy(InterceptionFactory<?> instance, CreationalContext<InterceptionFactory<?>> context) {
        if (context != null) {
            context.release();
        }
    }

    @Override
    public Set<Type> getTypes() {
        Set<Type> types = new HashSet<>();
        types.add(InterceptionFactory.class);
        types.add(Object.class);
        return types;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        Set<Annotation> qualifiers = new HashSet<>();
        qualifiers.add(Default.Literal.INSTANCE);
        qualifiers.add(Any.Literal.INSTANCE);
        return qualifiers;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return Dependent.class;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.emptySet();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }
}
