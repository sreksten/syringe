package com.threeamigos.common.util.implementations.injection.spi.configured;

import com.threeamigos.common.util.implementations.injection.spi.configurators.InjectionPointConfiguratorImpl;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Simple InjectionPoint implementation used by {@link InjectionPointConfiguratorImpl}
 * to materialize configured injection point metadata.
 */
public class ConfiguredInjectionPoint implements InjectionPoint {

    private final Member member;
    private final Bean<?> bean;
    private final Type type;
    private final Set<Annotation> qualifiers;
    private final Annotated annotated;
    private final boolean isDelegate;
    private final boolean isTransient;

    public ConfiguredInjectionPoint(InjectionPoint original,
                                    Type type,
                                    Set<Annotation> qualifiers,
                                    boolean isDelegate,
                                    boolean isTransient) {
        this.member = original.getMember();
        this.bean = original.getBean();
        this.annotated = original.getAnnotated();
        this.type = type != null ? type : original.getType();
        this.qualifiers = new HashSet<>(qualifiers);
        this.isDelegate = isDelegate;
        this.isTransient = isTransient;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return Collections.unmodifiableSet(qualifiers);
    }

    @Override
    public Bean<?> getBean() {
        return bean;
    }

    @Override
    public Member getMember() {
        return member;
    }

    @Override
    public Annotated getAnnotated() {
        return annotated;
    }

    @Override
    public boolean isDelegate() {
        return isDelegate;
    }

    @Override
    public boolean isTransient() {
        return isTransient;
    }
}
