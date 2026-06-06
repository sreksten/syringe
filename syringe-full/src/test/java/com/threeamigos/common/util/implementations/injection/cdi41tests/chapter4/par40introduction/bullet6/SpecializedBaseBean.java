package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet6;

import jakarta.enterprise.context.Dependent;

@Dependent
public class SpecializedBaseBean {

    public String role() {
        return "base";
    }
}
