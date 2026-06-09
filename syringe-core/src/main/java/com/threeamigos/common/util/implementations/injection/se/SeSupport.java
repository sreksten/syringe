package com.threeamigos.common.util.implementations.injection.se;

import com.threeamigos.common.util.implementations.injection.Syringe;

/**
 * Service provider interface for SE (Java SE) container support.
 *
 * <p>Discovered via {@link java.util.ServiceLoader}. If syringe-se is on the classpath,
 * {@code SeSupportImpl} is loaded; otherwise {@link NoOpSeSupport} is used.
 *
 * <p>The no-op is fully silent: absence of this module simply means {@code CDI.current()}
 * will not be registered and the container functions as a programmatically driven container.
 * No {@code NotEnabledFeatureException} is thrown.
 */
public interface SeSupport {

    /**
     * Called at the end of {@code Syringe.start()} to register the CDI provider so that
     * {@code CDI.current()} resolves to this container instance.
     */
    void registerCdiProvider(Syringe syringe);

    /**
     * Called during {@code Syringe.shutdown()} to unregister the CDI provider.
     */
    void unregisterCdiProvider();
}
