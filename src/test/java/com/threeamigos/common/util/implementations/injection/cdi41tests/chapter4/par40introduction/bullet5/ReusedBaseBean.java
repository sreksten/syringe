package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet5;

import jakarta.enterprise.context.Dependent;

@Dependent
public class ReusedBaseBean {

    public String role() {
        return "base";
    }
}
