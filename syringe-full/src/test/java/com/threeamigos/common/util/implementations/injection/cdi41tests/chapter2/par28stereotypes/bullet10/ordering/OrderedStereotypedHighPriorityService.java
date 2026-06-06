package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet10.ordering;

@OrderedMock
public class OrderedStereotypedHighPriorityService implements OrderedService {

    @Override
    public String serviceType() {
        return "highPriorityStereotypeAlternative";
    }
}
