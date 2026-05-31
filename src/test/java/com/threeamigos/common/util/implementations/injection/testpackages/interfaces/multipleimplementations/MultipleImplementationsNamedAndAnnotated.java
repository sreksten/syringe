package com.threeamigos.common.util.implementations.injection.testpackages.interfaces.multipleimplementations;

import jakarta.inject.Named;

@Named("name")
@MyQualifier
public class MultipleImplementationsNamedAndAnnotated implements MultipleImplementationsInterface {
}
