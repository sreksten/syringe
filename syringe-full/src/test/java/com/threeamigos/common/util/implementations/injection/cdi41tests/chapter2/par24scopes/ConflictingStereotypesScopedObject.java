package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par24scopes;

import java.io.Serializable;

@SessionDefaultScopeA
@RequestDefaultScope
public class ConflictingStereotypesScopedObject implements Serializable {
}
