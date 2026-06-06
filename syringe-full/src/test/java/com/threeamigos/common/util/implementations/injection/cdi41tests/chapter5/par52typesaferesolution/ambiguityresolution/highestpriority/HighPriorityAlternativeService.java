package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguityresolution.highestpriority;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;

@Dependent
@Alternative
@Priority(300)
public class HighPriorityAlternativeService implements ResolutionServiceHighestPriority {
}
