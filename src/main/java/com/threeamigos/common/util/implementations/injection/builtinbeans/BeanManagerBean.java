package com.threeamigos.common.util.implementations.injection.builtinbeans;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Built-in bean for injecting the BeanManager itself.
 *
 * <p>CDI 4.1 Specification (Section 3.10) requires that the BeanManager
 * is available for injection:
 * <pre>{@code
 * @Inject BeanManager beanManager;
 * }</pre>
 *
 * <p><b>Bean Characteristics:</b>
 * <ul>
 *   <li>Type: BeanManager</li>
 *   <li>Scope: @Dependent (new proxy per injection point)</li>
 *   <li>Qualifiers: @Default, @Any</li>
 *   <li>Stereotypes: None</li>
 *   <li>Alternative: No</li>
 * </ul>
 *
 * @author Stefano Reksten
 * @see BeanManager
 */
public class BeanManagerBean implements Bean<BeanManager> {

    private final BeanManager beanManager;

    /**
     * Creates a built-in bean for the given BeanManager instance.
     *
     * @param beanManager the BeanManager instance to inject
     */
    public BeanManagerBean(@Nonnull BeanManager beanManager) {
        this.beanManager = beanManager;
    }

    @Override
    public Class<?> getBeanClass() {
        return BeanManager.class;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet(); // Built-in beans have no injection points
    }

    @Override
    public BeanManager create(CreationalContext<BeanManager> context) {
        // Return the container's BeanManager instance
        return beanManager;
    }

    @Override
    public void destroy(BeanManager instance, CreationalContext<BeanManager> context) {
        // BeanManager is a container singleton, nothing to destroy
        if (context != null) {
            context.release();
        }
    }

    @Override
    public Set<Type> getTypes() {
        // BeanManager and its parent interface BeanContainer
        Set<Type> types = new HashSet<>();
        types.add(BeanManager.class);
        types.add(BeanContainer.class);
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
        // @Dependent means a new instance (actually the same instance) per injection point
        return Dependent.class;
    }

    @Override
    public String getName() {
        return null; // Not a named bean
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.emptySet();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

    @Override
    public String toString() {
        return "BeanManagerBean[type=BeanManager, scope=@Dependent, qualifiers=@Default]";
    }
}
