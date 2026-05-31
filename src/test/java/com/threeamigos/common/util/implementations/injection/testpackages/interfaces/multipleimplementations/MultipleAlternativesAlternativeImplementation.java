package com.threeamigos.common.util.implementations.injection.testpackages.interfaces.multipleimplementations;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;

@Alternative
@Priority(5)
public class MultipleAlternativesAlternativeImplementation implements MultipleImplementationsInterface {
}
