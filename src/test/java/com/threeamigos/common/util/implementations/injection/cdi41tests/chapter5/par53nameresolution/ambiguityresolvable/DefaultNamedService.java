package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par53nameresolution.ambiguityresolvable;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;

@Dependent
@Named("resolvableByAlternativeName")
public class DefaultNamedService {
}
