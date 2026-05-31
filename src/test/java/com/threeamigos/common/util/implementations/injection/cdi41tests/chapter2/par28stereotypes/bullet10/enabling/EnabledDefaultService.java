package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet10.enabling;

import jakarta.enterprise.context.Dependent;

@Dependent
public class EnabledDefaultService implements EnabledService {

    @Override
    public String serviceType() {
        return "defaultService";
    }
}
