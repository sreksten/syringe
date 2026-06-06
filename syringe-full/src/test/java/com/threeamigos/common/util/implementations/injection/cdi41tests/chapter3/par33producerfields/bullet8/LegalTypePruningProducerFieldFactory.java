package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par33producerfields.bullet8;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class LegalTypePruningProducerFieldFactory {

    @Produces
    VariableArrayBucket<String> producedBucket = null;

    public interface GenericBucket<T> {
    }

    public static class VariableArrayBucket<T> implements GenericBucket<T[]> {
    }
}
