package com.threeamigos.common.util.implementations.injection.resolution;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Resolves dependencies for managed and producer bean creation.
 */
public interface DependencyResolver {

    /**
     * Resolves a dependency by type and qualifiers.
     */
    Object resolve(Type type, Annotation[] qualifiers);

    /**
     * Gets or creates an instance of the declaring bean class.
     */
    Object resolveDeclaringBeanInstance(Class<?> declaringClass);
}
