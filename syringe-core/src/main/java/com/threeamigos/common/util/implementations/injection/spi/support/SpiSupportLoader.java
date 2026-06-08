package com.threeamigos.common.util.implementations.injection.spi.support;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Loads optional SPI support implementation.
 */
public final class SpiSupportLoader {

    private static final SpiSupport NO_OP = new NoOpSpiSupport();

    private SpiSupportLoader() {
    }

    public static SpiSupport load() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        SpiSupport loaded = load(tccl);
        if (loaded != null) {
            return loaded;
        }

        ClassLoader fallback = SpiSupportLoader.class.getClassLoader();
        if (fallback != null && fallback != tccl) {
            loaded = load(fallback);
            if (loaded != null) {
                return loaded;
            }
        }

        return NO_OP;
    }

    private static SpiSupport load(ClassLoader classLoader) {
        if (classLoader == null) {
            return null;
        }
        Iterator<SpiSupport> iterator = ServiceLoader.load(SpiSupport.class, classLoader).iterator();
        if (iterator.hasNext()) {
            return iterator.next();
        }
        return null;
    }
}
