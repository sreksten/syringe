package com.threeamigos.common.util.implementations.injection.testpackages.bind;

import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME) @Qualifier
public @interface BindingNotMatchingQualifier {
}
