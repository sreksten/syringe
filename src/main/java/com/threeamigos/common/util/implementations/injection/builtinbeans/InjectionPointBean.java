package com.threeamigos.common.util.implementations.injection.builtinbeans;

import com.threeamigos.common.util.implementations.injection.resolution.BeanResolver;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Built-in bean for injecting InjectionPoint metadata.
 *
 * <p>CDI 4.1 Specification (Section 3.10) requires that InjectionPoint
 * metadata is available for injection within beans:
 * <pre>{@code
 * @Produces
 * @RequestScoped
 * public Logger createLogger(InjectionPoint injectionPoint) {
 *     return Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
 * }
 * }</pre>
 *
 * <p>This bean provides access to metadata about the current injection point, including:
 * <ul>
 *   <li>Type being injected</li>
 *   <li>Qualifiers on the injection point</li>
 *   <li>Member (field, method, constructor) being injected</li>
 *   <li>Declaring bean</li>
 * </ul>
 *
 * <p><b>Special Handling:</b> This bean requires special resolution logic
 * in BeanResolver because the returned InjectionPoint instance must be
 * contextual - it changes based on the current injection point being resolved.
 *
 * <p><b>Bean Characteristics:</b>
 * <ul>
 *   <li>Type: InjectionPoint</li>
 *   <li>Scope: @Dependent (contextual per injection)</li>
 *   <li>Qualifiers: @Default, @Any</li>
 *   <li>Stereotypes: None</li>
 *   <li>Alternative: No</li>
 * </ul>
 *
 * @author Stefano Reksten
 * @see InjectionPoint
 * @see BeanResolver
 */
public class InjectionPointBean implements Bean<InjectionPoint> {

    /**
     * Creates a built-in bean for InjectionPoint.
     */
    public InjectionPointBean() {
        // No-arg constructor - instance creation is contextual
    }

    @Override
    public Class<?> getBeanClass() {
        return InjectionPoint.class;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet(); // Built-in beans have no injection points
    }

    @Override
    public InjectionPoint create(CreationalContext<InjectionPoint> context) {
        // This should never be called directly - BeanResolver handles this specially
        throw new UnsupportedOperationException(
            "InjectionPoint creation must be handled by BeanResolver with injection context");
    }

    @Override
    public void destroy(InjectionPoint instance, CreationalContext<InjectionPoint> context) {
        // InjectionPoint is a metadata object, nothing to destroy
        if (context != null) {
            context.release();
        }
    }

    @Override
    public Set<Type> getTypes() {
        Set<Type> types = new HashSet<>();
        types.add(InjectionPoint.class);
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
        // @Dependent - each injection point gets its own metadata
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
        return "InjectionPointBean[type=InjectionPoint, scope=@Dependent, qualifiers=@Default]";
    }
}
