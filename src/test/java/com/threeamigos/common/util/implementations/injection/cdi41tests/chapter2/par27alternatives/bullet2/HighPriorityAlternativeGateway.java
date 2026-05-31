package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet2;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;

@Alternative
@Priority(20)
@Dependent
public class HighPriorityAlternativeGateway implements Gateway {
    @Override
    public String type() {
        return "highPriorityAlternative";
    }
}
