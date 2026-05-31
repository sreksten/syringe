package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet11;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

@Dependent
public class InvalidMultipleDisposersForSingleProducerMethodBean {

    @Produces
    Product produceProduct() {
        return new Product();
    }

    void disposeFirst(@Disposes Product product) {
    }

    void disposeSecond(@Disposes Product product) {
    }

    public static class Product {
    }
}
