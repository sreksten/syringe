package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par54clientproxies;

import jakarta.enterprise.context.Dependent;

@Dependent
public class DependentService {

    public String ping() {
        return "dependent";
    }
}
