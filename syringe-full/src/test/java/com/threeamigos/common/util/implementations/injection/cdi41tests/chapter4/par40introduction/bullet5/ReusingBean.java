package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par40introduction.bullet5;

import jakarta.enterprise.context.Dependent;

@Dependent
public class ReusingBean extends ReusedBaseBean {

    @Override
    public String role() {
        return "reused";
    }
}
