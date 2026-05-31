package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter16.par161modularity;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
class ModuleAccessibility16_1_4Client {

    @Inject
    ModularityInCDIFullTest.AccessibilityContract service;

    String serviceId() {
        return service.id();
    }
}
