package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par28stereotypes.bullet9;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class StrategyClient {

    @Inject
    private Strategy strategy;

    public String strategyKind() {
        return strategy.kind();
    }
}
