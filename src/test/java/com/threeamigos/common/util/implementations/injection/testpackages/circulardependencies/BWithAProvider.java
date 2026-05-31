package com.threeamigos.common.util.implementations.injection.testpackages.circulardependencies;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class BWithAProvider {

    @Inject
    @SuppressWarnings("all")
    private Provider<AWithBProvider> provider;

    public AWithBProvider getA() {
        return provider.get();
    }

}
