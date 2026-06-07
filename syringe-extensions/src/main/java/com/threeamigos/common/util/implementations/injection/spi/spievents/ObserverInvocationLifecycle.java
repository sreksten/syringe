package com.threeamigos.common.util.implementations.injection.spi.spievents;

/**
 * Internal hook for lifecycle event objects that need observer-invocation boundaries.
 *
 * <p>Some SPI APIs defer actions until the observer invocation ends (for example,
 * AnnotatedTypeConfigurator variants that are applied at the end of observer notification).
 */
public interface ObserverInvocationLifecycle {

    void beginObserverInvocation();

    void endObserverInvocation();
}
