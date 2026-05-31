package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet2;

import jakarta.enterprise.context.Dependent;

@Dependent
public class StandardGateway implements Gateway {
    @Override
    public String type() {
        return "standard";
    }
}
