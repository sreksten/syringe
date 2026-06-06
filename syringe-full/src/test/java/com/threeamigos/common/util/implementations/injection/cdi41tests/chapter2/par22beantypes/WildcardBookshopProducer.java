package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par22beantypes;

public class WildcardBookshopProducer {

    @jakarta.enterprise.inject.Produces
    Shop<? extends Book> shop() {
        return null;
    }
}
