package com.threeamigos.common.util.implementations.injection.spi;

import com.threeamigos.common.util.implementations.injection.resolution.DestroyedInstanceTracker;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanAttributes;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.Producer;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.Map;
import java.util.Collections;
import java.util.IdentityHashMap;

/**
 * Implementation of Bean for programmatically created producer beans.
 *
 * <p>This class wraps a Producer to provide bean lifecycle management
 * for synthetic producer beans created via BeanManager.createBean(BeanAttributes, Class, ProducerFactory).
 *
 * <p>The Producer handles the actual instance creation:
 * <ul>
 *   <li>produce() - Creates the produced instance</li>
 *   <li>dispose() - Cleanup on destruction</li>
 * </ul>
 *
 * <p><b>CDI Spec Section 11.5.11:</b> Programmatic bean creation
 */
public class SyntheticProducerBeanImpl<T> implements Bean<T> {

    private final BeanAttributes<T> attributes;
    private final Class<?> beanClass;
    private final Producer<T> producer;
    private final Map<T, CreationalContext<T>> ctxByInstance =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * Creates a synthetic producer bean from attributes, class, and producer.
     *
     * @param attributes the bean attributes (metadata)
     * @param beanClass the bean class
     * @param producer the producer for instance creation
     */
    public SyntheticProducerBeanImpl(BeanAttributes<T> attributes, Class<?> beanClass, Producer<T> producer) {
        this.attributes = attributes;
        this.beanClass = beanClass;
        this.producer = producer;
    }

    @Override
    public T create(CreationalContext<T> creationalContext) {
        if (creationalContext == null) {
            throw new IllegalArgumentException("CreationalContext must not be null");
        }
        // Use Producer to create the instance
        T instance = producer.produce(creationalContext);
        if (instance != null) { // null instances are allowed; nothing to track
            ctxByInstance.put(instance, creationalContext);
        }
        return instance;
    }

    @Override
    public void destroy(T instance, CreationalContext<T> creationalContext) {
        if (instance == null || DestroyedInstanceTracker.isDestroyed(instance)) {
            return;
        }
        creationalContext = BeanManagerImpl.resolveDependentCreationalContext(creationalContext, this, instance);
        producer.dispose(instance);
        CreationalContext<T> ctx = creationalContext != null
                ? creationalContext
                : ctxByInstance.remove(instance);
        ctxByInstance.remove(instance);
        if (ctx == null) {
            throw new IllegalStateException("No CreationalContext available to destroy instance of " + beanClass.getName());
        }
        DestroyedInstanceTracker.markDestroyed(instance);
        ctx.release();
    }

    @Override
    public Class<?> getBeanClass() {
        return beanClass;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return producer.getInjectionPoints();
    }

    // Delegate all BeanAttributes methods to the attributes

    @Override
    public String getName() {
        return attributes.getName();
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return attributes.getQualifiers();
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return attributes.getScope();
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return attributes.getStereotypes();
    }

    @Override
    public Set<Type> getTypes() {
        return attributes.getTypes();
    }

    @Override
    public boolean isAlternative() {
        return attributes.isAlternative();
    }
}
