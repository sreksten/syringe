package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet3;

import jakarta.enterprise.context.Dependent;

@Dependent
public class StandardAlternativeService implements AlternativeService {
    @Override
    public String type() {
        return "standard";
    }
}
