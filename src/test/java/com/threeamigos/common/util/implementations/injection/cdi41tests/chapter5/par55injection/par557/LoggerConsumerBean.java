package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par557;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import java.util.logging.Logger;

@Dependent
public class LoggerConsumerBean {

    @Inject
    private Logger logger;

    public Logger getLogger() {
        return logger;
    }
}
