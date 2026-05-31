package com.threeamigos.common.util.implementations.injection.spi.spievents;

import jakarta.enterprise.inject.spi.Extension;

/**
 * Allows SPI lifecycle events to know which extension observer is currently being invoked.
 */
public interface ExtensionAwareObserverInvocation {

    void enterObserverInvocation(Extension extension);

    void exitObserverInvocation();
}
