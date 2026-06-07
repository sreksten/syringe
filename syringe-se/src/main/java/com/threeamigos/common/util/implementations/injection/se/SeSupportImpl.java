package com.threeamigos.common.util.implementations.injection.se;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.spi.SyringeCDIProvider;

/**
 * Full {@link SeSupport} implementation, active when syringe-se is on the classpath.
 *
 * <p>Registers the Syringe CDI provider so that {@code CDI.current()} resolves to this
 * container instance, and unregisters it on shutdown.
 */
public class SeSupportImpl implements SeSupport {

    @Override
    public void registerCdiProvider(Syringe syringe) {
        SyringeCDIProvider.ensureProviderConfigured();
        SyringeCDIProvider.registerGlobalCDI(syringe.getCDI());
    }

    @Override
    public void unregisterCdiProvider() {
        SyringeCDIProvider.unregisterThreadLocalCDI();
        SyringeCDIProvider.unregisterGlobalCDI();
    }
}
