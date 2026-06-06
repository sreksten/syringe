package com.threeamigos.common.util.implementations.injection.testpackages.circulardependencies;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class AWithBProvider {

    @Inject
    @SuppressWarnings("all")
    Provider<BWithAProvider> provider;

    public BWithAProvider getB() {
        return provider.get();
    }
}
