package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.assignability.rawtoparameterized;

public class Foo<T> {

    private final T value;

    public Foo(T value) {
        this.value = value;
    }

    public T ping() {
        return value;
    }
}
