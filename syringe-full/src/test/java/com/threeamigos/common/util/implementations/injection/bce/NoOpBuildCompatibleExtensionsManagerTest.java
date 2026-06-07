package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoOpBuildCompatibleExtensionsManagerTest {

    @Test
    void addBuildCompatibleExtensionThrowsCanonicalNotEnabledMessage() {
        NoOpBuildCompatibleExtensionsManager noOpManager = new NoOpBuildCompatibleExtensionsManager();

        NotEnabledFeatureException ex = assertThrows(
                NotEnabledFeatureException.class,
                () -> noOpManager.addBuildCompatibleExtension("com.example.MyBce")
        );

        assertTrue(ex.getMessage().startsWith(
                "API call found at Syringe.addBuildCompatibleExtension(String) but build-compatible extension support is not available."));
    }
}
