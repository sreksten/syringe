package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet6;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Specializes;

@Dependent
@Specializes
public class SpecializingBean extends SpecializedBaseBean {

    @Override
    public String role() {
        return "specialized";
    }
}
