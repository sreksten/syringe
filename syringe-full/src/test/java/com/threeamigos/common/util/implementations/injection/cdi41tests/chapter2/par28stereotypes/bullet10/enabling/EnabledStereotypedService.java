package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet10.enabling;

@GloballyEnabledMock
public class EnabledStereotypedService implements EnabledService {

    @Override
    public String serviceType() {
        return "stereotypePriorityAlternative";
    }
}
