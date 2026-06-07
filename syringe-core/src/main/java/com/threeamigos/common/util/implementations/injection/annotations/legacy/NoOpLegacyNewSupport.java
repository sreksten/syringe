package com.threeamigos.common.util.implementations.injection.annotations.legacy;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.modules.ModulesEnum;
import jakarta.enterprise.inject.spi.Bean;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasNewAnnotation;

/**
 * No-op {@link LegacyNewSupport} used when syringe-legacy-new is not on the classpath.
 *
 * <p>{@link #isEnabled()} always returns {@code false}.
 * {@link #enable()} throws {@link NotEnabledFeatureException} to give a clear error when the
 * user explicitly tries to activate {@code @New} support without the module present.
 */
public class NoOpLegacyNewSupport implements LegacyNewSupport {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void enable() {
        throw new NotEnabledFeatureException(
                "API call found at Syringe.enableLegacyCdi10New(boolean) but legacy @New support is not available.",
                ModulesEnum.LEGACY);
    }

    @Override
    public LegacyNewSelection resolveSelection(Type requiredType, Annotation[] qualifiers) {
        if (qualifiers == null) {
            return null;
        }
        for (Annotation qualifier : qualifiers) {
            if (qualifier != null && hasNewAnnotation(qualifier.annotationType())) {
                String location = requiredType != null
                        ? "required type " + requiredType.getTypeName()
                        : "unknown required type";
                throw new NotEnabledFeatureException(
                        "@New found at " + location + " but legacy @New support is not available.",
                                ModulesEnum.LEGACY);
            }
        }
        return null;
    }

    @Override
    public <T> Bean<T> adaptLegacyNewBean(Bean<T> bean) {
        return bean;
    }

    @Override
    public void validateNewInjectionPoints() {
        // no-op
    }
}
