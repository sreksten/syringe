package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.assignability.specexamples;

import jakarta.enterprise.context.Dependent;

@Dependent
public class Dao<T extends Persistent> {
}
