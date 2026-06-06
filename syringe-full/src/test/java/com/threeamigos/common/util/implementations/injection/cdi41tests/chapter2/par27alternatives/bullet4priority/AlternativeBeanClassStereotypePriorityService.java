package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet4priority;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;

@Alternative
@PriorityEnabledAlternativeStereotype
@Dependent
public class AlternativeBeanClassStereotypePriorityService implements BeanClassStereotypePriorityService {
    @Override
    public String type() {
        return "beanClassStereotypePriorityAlternative";
    }
}
