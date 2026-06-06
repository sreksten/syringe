package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter23.par235alternativemetadatasources.tckparity.alternativemetadata;

import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.util.AnnotationLiteral;

/**
 * Minimal @Produces literal used by the TCK parity wrappers.
 */
public final class ProducesLiteral extends AnnotationLiteral<Produces> implements Produces {

    static final ProducesLiteral INSTANCE = new ProducesLiteral();

    private static final long serialVersionUID = 1L;

    private ProducesLiteral() {
    }
}
