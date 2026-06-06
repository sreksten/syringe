package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet6;

import jakarta.enterprise.context.Dependent;

@Dependent
public class StringGenericService implements GenericService<String> {

    @Override
    public Class<?> payloadType() {
        return String.class;
    }
}
