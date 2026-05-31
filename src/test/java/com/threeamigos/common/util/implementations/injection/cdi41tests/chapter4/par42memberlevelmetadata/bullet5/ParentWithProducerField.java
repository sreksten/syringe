package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.par42memberlevelmetadata.bullet5;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

@Dependent
public class ParentWithProducerField {

    @Produces
    ProducedByField producedByField = new ProducedByField("parent-field");
}
