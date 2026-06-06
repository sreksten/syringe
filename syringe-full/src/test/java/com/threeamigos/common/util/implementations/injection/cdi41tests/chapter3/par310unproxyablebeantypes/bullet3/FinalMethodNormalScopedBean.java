package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet3;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FinalMethodNormalScopedBean {

    final String ping() {
        return "pong";
    }
}
