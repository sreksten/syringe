package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet10.ordering;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.interceptor.Interceptor;

@Alternative
@Priority(Interceptor.Priority.APPLICATION + 1)
@Dependent
public class OrderedLowPriorityAlternativeService implements OrderedService {

    @Override
    public String serviceType() {
        return "lowPriorityAlternative";
    }
}
