package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par53nameresolution.availability;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Named;

@Dependent
@Alternative
@Named("availabilityService")
public class DisabledAlternativeNamedBean {
}
