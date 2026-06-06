package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet7;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

import java.util.IdentityHashMap;
import java.util.Map;

@Dependent
public class MultiProducerSingleDisposerBean {

    public static int disposeCount = 0;
    public static final Map<Object, Boolean> disposedInstances = new IdentityHashMap<>();

    public static void reset() {
        disposeCount = 0;
        disposedInstances.clear();
    }

    @Produces
    MethodProduct produceMethodProduct() {
        return new MethodProduct();
    }

    @Produces
    FieldProduct producedFieldProduct = new FieldProduct();

    void disposeAnyProduct(@Disposes Product product) {
        disposeCount++;
        disposedInstances.put(product, Boolean.TRUE);
    }

    public interface Product {
    }

    public static class MethodProduct implements Product {
    }

    public static class FieldProduct implements Product {
    }
}
