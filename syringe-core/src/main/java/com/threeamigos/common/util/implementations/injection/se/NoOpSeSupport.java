package com.threeamigos.common.util.implementations.injection.se;

import com.threeamigos.common.util.implementations.injection.Syringe;

/**
 * No-op {@link SeSupport} used when syringe-se is absent from the classpath.
 *
 * <p>SE container integration ({@code CDI.current()} registration) is an
 * infrastructure concern — its absence is fully silent and does not affect
 * programmatically-driven container usage.
 */
public class NoOpSeSupport implements SeSupport {

    @Override
    public void registerCdiProvider(Syringe syringe) {
        // no-op — SE integration is unavailable without syringe-se on the classpath
    }

    @Override
    public void unregisterCdiProvider() {
        // no-op — SE integration is unavailable without syringe-se on the classpath
    }
}
