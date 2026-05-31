package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet3;

@AlternativeStereotype
public class StereotypedAlternativeService implements AlternativeService {
    @Override
    public String type() {
        return "stereotypedAlternative";
    }
}
