package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet8;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class InterfaceReturnProducerFactory {

    @Produces
    ProducerSpecificContract produceContract() {
        return null;
    }

    public interface BaseContract {
    }

    public interface ProducerSpecificContract extends BaseContract {
    }
}
