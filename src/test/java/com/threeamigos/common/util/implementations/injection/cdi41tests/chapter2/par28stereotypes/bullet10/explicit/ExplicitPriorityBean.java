package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet10.explicit;

import jakarta.annotation.Priority;
import jakarta.interceptor.Interceptor;

@LowPriorityAlternativeStereotype
@HighPriorityAlternativeStereotype
@Priority(Interceptor.Priority.APPLICATION + 20)
public class ExplicitPriorityBean implements ExplicitPriorityService {

    @Override
    public String serviceType() {
        return "explicitPriorityBean";
    }
}
