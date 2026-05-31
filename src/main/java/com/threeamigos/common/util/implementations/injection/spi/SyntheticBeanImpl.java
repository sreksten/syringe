package com.threeamigos.common.util.implementations.injection.spi;

import com.threeamigos.common.util.implementations.injection.resolution.DestroyedInstanceTracker;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanAttributes;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.PassivationCapable;
import jakarta.enterprise.inject.spi.Annotated;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of Bean for programmatically created beans using InjectionTarget.
 *
 * <p>This class wraps an InjectionTarget to provide full bean lifecycle management
 * for synthetic beans created via BeanManager.createBean(BeanAttributes, Class, InjectionTargetFactory).
 *
 * <p>The InjectionTarget handles the actual bean lifecycle:
 * <ul>
 *   <li>produce() - Instantiates the bean</li>
 *   <li>inject() - Performs injection</li>
 *   <li>postConstruct() - Invokes lifecycle callbacks</li>
 *   <li>preDestroy() - Cleanup on destruction</li>
 * </ul>
 *
 * <p><b>CDI Spec Section 11.5.11:</b> Programmatic bean creation
 */
public class SyntheticBeanImpl<T> implements Bean<T>, PassivationCapable {

    private final BeanAttributes<T> attributes;
    private final Class<?> beanClass;
    private final InjectionTarget<T> injectionTarget;
    private final String id;
    private volatile Set<InjectionPoint> injectionPoints;

    /**
     * Creates a synthetic bean from attributes, class, and injection target.
     *
     * @param attributes the bean attributes (metadata)
     * @param beanClass the bean class
     * @param injectionTarget the injection target for lifecycle management
     */
    public SyntheticBeanImpl(BeanAttributes<T> attributes, Class<?> beanClass, InjectionTarget<T> injectionTarget) {
        this.attributes = attributes;
        this.beanClass = beanClass;
        this.injectionTarget = injectionTarget;
        this.id = beanClass.getName() + "#SyntheticBean#" + UUID.randomUUID();
    }

    @Override
    public T create(CreationalContext<T> creationalContext) {
        // Use InjectionTarget to manage the full lifecycle
        T instance = injectionTarget.produce(creationalContext);
        injectionTarget.inject(instance, creationalContext);
        injectionTarget.postConstruct(instance);
        return instance;
    }

    @Override
    public void destroy(T instance, CreationalContext<T> creationalContext) {
        if (instance == null || DestroyedInstanceTracker.isDestroyed(instance)) {
            return;
        }
        creationalContext = BeanManagerImpl.resolveDependentCreationalContext(creationalContext, this, instance);
        injectionTarget.preDestroy(instance);
        injectionTarget.dispose(instance);
        DestroyedInstanceTracker.markDestroyed(instance);
        if (creationalContext != null) {
            creationalContext.release();
        }
    }

    @Override
    public Class<?> getBeanClass() {
        return beanClass;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        Set<InjectionPoint> cached = injectionPoints;
        if (cached != null) {
            return cached;
        }

        Set<InjectionPoint> adapted = new LinkedHashSet<>();
        for (InjectionPoint injectionPoint : injectionTarget.getInjectionPoints()) {
            adapted.add(new BeanBackedInjectionPoint(injectionPoint, this));
        }
        Set<InjectionPoint> immutable = Collections.unmodifiableSet(adapted);
        injectionPoints = immutable;
        return immutable;
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

    @Override
    public String getId() {
        return id;
    }

    private static final class BeanBackedInjectionPoint implements InjectionPoint {
        private final InjectionPoint delegate;
        private final Bean<?> bean;

        private BeanBackedInjectionPoint(InjectionPoint delegate, Bean<?> bean) {
            this.delegate = delegate;
            this.bean = bean;
        }

        @Override
        public Type getType() {
            return delegate.getType();
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return delegate.getQualifiers();
        }

        @Override
        public Bean<?> getBean() {
            return bean;
        }

        @Override
        public Member getMember() {
            return delegate.getMember();
        }

        @Override
        public Annotated getAnnotated() {
            return delegate.getAnnotated();
        }

        @Override
        public boolean isDelegate() {
            return delegate.isDelegate();
        }

        @Override
        public boolean isTransient() {
            return delegate.isTransient();
        }
    }
}
