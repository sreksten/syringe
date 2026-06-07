package com.threeamigos.common.util.implementations.injection.annotations.legacy;

import com.threeamigos.common.util.implementations.injection.resolution.LegacyNewBeanAdapter;
import jakarta.enterprise.inject.spi.Bean;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Full {@link LegacyNewSupport} implementation, active when syringe-legacy-new is on the
 * classpath.
 *
 * <p>When this module is available, callers can explicitly enable the feature via
 * {@link #enable()}. Until then, {@link #isEnabled()} remains {@code false} and core resolution
 * paths keep legacy {@code @New} semantics disabled.
 */
public class LegacyNewSupportImpl implements LegacyNewSupport {

    private volatile boolean enabled;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void enable() {
        enabled = true;
    }

    @Override
    public LegacyNewSelection resolveSelection(Type requiredType, Annotation[] qualifiers) {
        LegacyNewQualifierHelper.LegacyNewSelection selection =
                LegacyNewQualifierHelper.extractSelection(requiredType, qualifiers);
        if (selection == null) {
            return null;
        }
        return new LegacyNewSelection(selection.getTargetClass());
    }

    @Override
    public <T> Bean<T> adaptLegacyNewBean(Bean<T> bean) {
        return new LegacyNewBeanAdapter<>(bean);
    }

    @Override
    public void validateNewInjectionPoints() {
        // no-op
    }
}
