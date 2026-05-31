package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet12;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class MultiParameterProducerFactory {

    @Produces
    MultiParameterProduct produce(MultiParameterDependencyA dependencyA,
                                  MultiParameterDependencyB dependencyB,
                                  MultiParameterDependencyC dependencyC,
                                  MultiParameterDependencyD dependencyD) {
        return new MultiParameterProduct(
                dependencyA.code() + dependencyB.code() + dependencyC.code() + dependencyD.code());
    }
}
