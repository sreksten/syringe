package com.threeamigos.common.util.implementations.injection.bce;

import jakarta.enterprise.inject.build.compatible.spi.BuildServices;
import jakarta.enterprise.inject.build.compatible.spi.BuildServicesResolver;

import java.lang.reflect.Method;

/**
 * Installs BuildServices into BuildServicesResolver for the duration of a BCE phase.
 */
final class BceBuildServicesScope implements AutoCloseable {

    private static final BuildServices FALLBACK = new BceBuildServices();
    private final BuildServices previous;

    BceBuildServicesScope(BuildServices current) {
        this.previous = getCurrentBuildServicesReflective();
        BuildServicesResolver.setBuildServices(current);
    }

    @Override
    public void close() {
        BuildServicesResolver.setBuildServices(previous != null ? previous : FALLBACK);
    }

    private static BuildServices getCurrentBuildServicesReflective() {
        try {
            Method getMethod = BuildServicesResolver.class.getDeclaredMethod("get");
            getMethod.setAccessible(true);
            return (BuildServices) getMethod.invoke(null);
        } catch (Exception e) {
            return null;
        }
    }
}
