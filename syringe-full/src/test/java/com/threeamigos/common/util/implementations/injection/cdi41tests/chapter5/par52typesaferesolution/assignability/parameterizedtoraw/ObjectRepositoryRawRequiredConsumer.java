package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.assignability.parameterizedtoraw;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
public class ObjectRepositoryRawRequiredConsumer {

    @Inject
    private Repository repository;

    public Repository getRepository() {
        return repository;
    }
}
