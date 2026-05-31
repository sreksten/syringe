package com.threeamigos.common.util.implementations.injection.resolution;

import jakarta.enterprise.inject.spi.BeanAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of BeanAttributes for programmatic bean creation.
 * BeanAttributes captures the metadata that defines a bean's characteristics:
 * name, qualifiers, scope, stereotypes, types, and alternative status.
 *
 * <p>This class is used by BeanManager.createBeanAttributes() to extract
 * bean metadata from AnnotatedType or AnnotatedMember instances, which can
 * then be used with BeanManager.createBean() to programmatically create beans.
 *
 * <p><b>CDI Spec Section 11.5.8:</b> BeanManager.createBeanAttributes()
 */
public class BeanAttributesImpl<T> implements BeanAttributes<T> {

    private final String name;
    private final Set<Annotation> qualifiers;
    private final Class<? extends Annotation> scope;
    private final Set<Class<? extends Annotation>> stereotypes;
    private final Set<Type> types;
    private final boolean alternative;

    /**
     * Creates bean attributes with all metadata specified.
     *
     * @param name the bean name (can be null for unnamed beans)
     * @param qualifiers the qualifiers (must not be null)
     * @param scope the scope annotation (must not be null)
     * @param stereotypes the stereotypes (must not be null)
     * @param types the bean types (must not be null)
     * @param alternative whether this is an alternative bean
     */
    public BeanAttributesImpl(String name,
                             Set<Annotation> qualifiers,
                             Class<? extends Annotation> scope,
                             Set<Class<? extends Annotation>> stereotypes,
                             Set<Type> types,
                             boolean alternative) {
        this.name = name;
        this.qualifiers = new HashSet<>(qualifiers);
        this.scope = scope;
        this.stereotypes = new HashSet<>(stereotypes);
        this.types = new HashSet<>(types);
        this.alternative = alternative;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return Collections.unmodifiableSet(qualifiers);
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return scope;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.unmodifiableSet(stereotypes);
    }

    @Override
    public Set<Type> getTypes() {
        return Collections.unmodifiableSet(types);
    }

    @Override
    public boolean isAlternative() {
        return alternative;
    }
}
