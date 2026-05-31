package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par24scopes;

import jakarta.enterprise.inject.Produces;

public class DefaultScopeProducerFactory {

    @Produces
    private final FieldProducedDefaultScopeObject fieldProducedDefaultScopeObject =
            new FieldProducedDefaultScopeObject();

    @Produces
    public MethodProducedDefaultScopeObject createMethodProducedDefaultScopeObject() {
        return new MethodProducedDefaultScopeObject();
    }
}
