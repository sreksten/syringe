package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet2;

import jakarta.enterprise.context.Dependent;

@Dependent
public class InterfaceAnnotatedServiceImpl implements InterfaceAnnotatedService {

    @Override
    public String process() {
        return "ok";
    }
}
