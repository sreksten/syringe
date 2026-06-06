package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

public class RawInstanceInjectionPointBean {

    @Inject
    private Instance rawInstance;

    public Instance getRawInstance() {
        return rawInstance;
    }
}
