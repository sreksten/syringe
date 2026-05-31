package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par53nameresolution.ambiguityhighestpriority;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Named;

@Dependent
@Alternative
@Priority(10)
@Named("resolvableByHighestPriority")
public class LowPriorityAlternativeNamedService {
}
