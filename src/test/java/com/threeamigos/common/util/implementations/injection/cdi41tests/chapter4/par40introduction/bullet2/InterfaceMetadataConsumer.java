package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet2;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class InterfaceMetadataConsumer {

    @Inject
    @InterfaceLevelQualifier
    InterfaceAnnotatedService service;
}
