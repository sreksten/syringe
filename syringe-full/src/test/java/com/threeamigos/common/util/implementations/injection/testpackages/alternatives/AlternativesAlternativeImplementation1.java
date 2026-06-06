package com.threeamigos.common.util.implementations.injection.testpackages.alternatives;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;

@Alternative
@Priority(1)
public class AlternativesAlternativeImplementation1 implements AlternativesInterface {
}
