package com.threeamigos.common.util.implementations.injection.annotations.legacy;

import jakarta.enterprise.inject.spi.Bean;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Service provider interface for legacy CDI 1.0 {@code @New} qualifier support.
 *
 * <p>Discovered via {@link java.util.ServiceLoader}. If syringe-legacy-new is on the classpath,
 * {@code LegacyNewSupportImpl} is loaded; otherwise {@link NoOpLegacyNewSupport} is used.
 */
public interface LegacyNewSupport {

    /**
     * Returns {@code true} if {@code @New} qualifier resolution is active for this container.
     */
    boolean isEnabled();

    /**
     * Called when the user explicitly requests legacy {@code @New} support via
     * {@code Syringe.enableLegacyCdi10New(true)}.
     *
     * <p>Implementations backed by syringe-legacy-new treat this as a no-op (already enabled).
     * The no-op implementation throws {@link com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException}
     * to signal that the module is absent.
     */
    void enable();

    /**
     * Resolves {@code @New} selection metadata from the required type + qualifier set.
     *
     * @return selection metadata, or {@code null} when no {@code @New} qualifier is present
     */
    LegacyNewSelection resolveSelection(Type requiredType, Annotation[] qualifiers);

    /**
     * Wraps a matched bean so lookup semantics follow legacy {@code @New} rules
     * (dependent-style contextual reference).
     */
    <T> Bean<T> adaptLegacyNewBean(Bean<T> bean);

    /**
     * Hook reserved for deployment-time validation of legacy {@code @New} declarations.
     * Current implementations do not require explicit work here.
     */
    void validateNewInjectionPoints();

    /**
     * Minimal metadata used by core resolution paths.
     */
    final class LegacyNewSelection {
        private final Class<?> targetClass;

        public LegacyNewSelection(Class<?> targetClass) {
            this.targetClass = targetClass;
        }

        public Class<?> getTargetClass() {
            return targetClass;
        }
    }
}
