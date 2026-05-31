package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet8;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class InterfaceFieldTypeProducerFactory {

    @Produces
    ProducerSpecificContract producedContract = null;

    public interface RootContract {
    }

    public interface BaseContract extends RootContract {
    }

    public interface ProducerSpecificContract extends BaseContract {
    }
}
