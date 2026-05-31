package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par553;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;

@Dependent
public class DependentChildBean {

    @PreDestroy
    void onPreDestroy() {
        DependentDestructionRecorder.markChildDestroyed();
        DependentDestructionRecorder.record("child-pre");
    }
}
