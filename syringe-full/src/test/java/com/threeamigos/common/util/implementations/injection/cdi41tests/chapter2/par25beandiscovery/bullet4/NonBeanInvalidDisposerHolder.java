package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par25beandiscovery.bullet4;

import jakarta.enterprise.inject.Disposes;

public class NonBeanInvalidDisposerHolder {

    void invalidDisposer(@Disposes String first, @Disposes Integer second) {
        // Intentionally invalid: two @Disposes parameters should be a definition error if discovered.
    }
}
