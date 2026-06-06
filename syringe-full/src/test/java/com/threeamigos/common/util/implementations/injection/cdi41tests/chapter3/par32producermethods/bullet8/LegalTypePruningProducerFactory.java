package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par32producermethods.bullet8;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class LegalTypePruningProducerFactory {

    @Produces
    VariableArrayBucket<String> produceBucket() {
        return null;
    }

    public interface GenericBucket<T> {
    }

    public static class VariableArrayBucket<T> implements GenericBucket<T[]> {
    }
}
