package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NormalScopedDestroyableBean {

    public String ping() {
        return "pong";
    }
}
