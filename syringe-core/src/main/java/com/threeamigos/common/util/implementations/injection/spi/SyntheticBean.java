package com.threeamigos.common.util.implementations.injection.spi;

import com.threeamigos.common.util.implementations.injection.resolution.DestroyedInstanceTracker;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.PassivationCapable;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Implementation of Bean for synthetic beans created programmatically via BeanConfigurator.
 * Synthetic beans are not discovered via classpath scanning but are registered by
 * portable extensions during the AfterBeanDiscovery phase.
 *
 * @param <T> the bean type
 * @author CDI Container
 */
public class SyntheticBean<T> implements Bean<T>, PassivationCapable {

    // Bean attributes
    private final Class<?> beanClass;
    private final Set<Type> types;
    private final Set<Annotation> qualifiers;
    private final Class<? extends Annotation> scope;
    private final String name;
    private final String id;
    private final Set<Class<? extends Annotation>> stereotypes;
    private final boolean alternative;
    private final Integer priority;

    // Creation/destruction callbacks
    private final Function<CreationalContext<T>, T> createCallback;
    private final BiConsumer<T, CreationalContext<T>> destroyCallback;

    // Injection points
    private final Set<InjectionPoint> injectionPoints;
    private final Map<T, CreationalContext<T>> contextsByInstance =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * Constructor for building synthetic beans.
     * Package-private to allow construction from BeanConfiguratorImpl.
     */
    public SyntheticBean(
            Class<?> beanClass,
            Set<Type> types,
            Set<Annotation> qualifiers,
            Class<? extends Annotation> scope,
            String name,
            String id,
            Set<Class<? extends Annotation>> stereotypes,
            boolean alternative,
            Integer priority,
            Function<CreationalContext<T>, T> createCallback,
            BiConsumer<T, CreationalContext<T>> destroyCallback,
            Set<InjectionPoint> injectionPoints) {

        this.beanClass = beanClass;
        this.types = Collections.unmodifiableSet(new HashSet<>(types));
        this.qualifiers = Collections.unmodifiableSet(new HashSet<>(qualifiers));
        this.scope = scope;
        this.name = name;
        this.id = id;
        this.stereotypes = Collections.unmodifiableSet(new HashSet<>(stereotypes));
        this.alternative = alternative;
        this.priority = priority;
        this.createCallback = createCallback;
        this.destroyCallback = destroyCallback;
        this.injectionPoints = Collections.unmodifiableSet(new HashSet<>(injectionPoints));
    }

    @Override
    public Class<?> getBeanClass() {
        return beanClass;
    }

    @Override
    public Set<Type> getTypes() {
        return types;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return qualifiers;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return scope;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return stereotypes;
    }

    @Override
    public boolean isAlternative() {
        return alternative;
    }

    /**
     * Returns the priority for alternative ordering.
     * This is a non-standard extension to support @Priority on alternatives.
     */
    public Integer getPriority() {
        return priority;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return injectionPoints;
    }

    @Override
    public T create(CreationalContext<T> creationalContext) {
        if (createCallback == null) {
            throw new IllegalStateException(
                "Synthetic bean " + beanClass.getName() + " has no create callback defined"
            );
        }
        T instance = createCallback.apply(creationalContext);
        if (instance != null && creationalContext != null) {
            contextsByInstance.put(instance, creationalContext);
        }
        return instance;
    }

    @Override
    public void destroy(T instance, CreationalContext<T> creationalContext) {
        creationalContext = BeanManagerImpl.resolveDependentCreationalContext(creationalContext, this, instance);
        CreationalContext<T> contextToRelease = creationalContext;
        if (contextToRelease == null && instance != null) {
            contextToRelease = contextsByInstance.remove(instance);
        } else if (instance != null) {
            contextsByInstance.remove(instance);
        }

        if (DestroyedInstanceTracker.isDestroyed(instance)) {
            if (contextToRelease != null) {
                contextToRelease.release();
            }
            return;
        }

        if (destroyCallback != null) {
            destroyCallback.accept(instance, contextToRelease);
        }

        if (instance != null) {
            DestroyedInstanceTracker.markDestroyed(instance);
        }

        // Always release the creational context
        if (contextToRelease != null) {
            contextToRelease.release();
        }
    }

    @Override
    public String toString() {
        return "SyntheticBean{" +
               "beanClass=" + beanClass.getName() +
               ", types=" + types +
               ", qualifiers=" + qualifiers +
               ", scope=" + scope.getSimpleName() +
               ", name='" + name + '\'' +
               ", alternative=" + alternative +
               '}';
    }
}
