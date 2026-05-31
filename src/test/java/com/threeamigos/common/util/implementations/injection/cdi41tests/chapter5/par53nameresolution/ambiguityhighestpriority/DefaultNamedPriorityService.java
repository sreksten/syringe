package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par53nameresolution.ambiguityhighestpriority;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;

@Dependent
@Named("resolvableByHighestPriority")
public class DefaultNamedPriorityService {
}
