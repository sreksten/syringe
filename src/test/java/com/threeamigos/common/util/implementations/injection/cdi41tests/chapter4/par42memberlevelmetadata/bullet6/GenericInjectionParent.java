package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet6;

import jakarta.inject.Inject;

public abstract class GenericInjectionParent<T> {

    @Inject
    GenericService<T> service;

    public GenericService<T> getService() {
        return service;
    }
}
