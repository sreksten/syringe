package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet10.explicit;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.interceptor.Interceptor;

@Alternative
@Priority(Interceptor.Priority.APPLICATION + 50)
@Dependent
public class CompetingClassPriorityBean implements ExplicitPriorityService {

    @Override
    public String serviceType() {
        return "competingClassPriorityBean";
    }
}
