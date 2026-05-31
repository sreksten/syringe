package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;

@Alternative
@Priority(1000)
public class AlternativeIteratorCandidate implements PlainIteratorCandidate {

    @Override
    public String id() {
        return "alt";
    }
}
