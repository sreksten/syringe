package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par53nameresolution.ambiguityunresolvable;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;

@Dependent
@Named("unresolvableName")
public class FirstUnresolvableNamedBean {
}
