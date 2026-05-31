package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par23qualifiers;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Test helper for qualifier-related assertions and repeatable qualifier fixtures.
 */
public final class QualifiersHelper {

    private QualifiersHelper() {
    }

    /**
     * Delegates bean qualifier normalization to production helper logic.
     */
    public static Set<Annotation> extractBeanQualifiers(Annotation[] annotations) {
        return com.threeamigos.common.util.implementations.injection.annotations.QualifiersHelper.extractBeanQualifiers(annotations);
    }

    /**
     * Extracts {@link Location} values from direct and container annotations.
     */
    public static String[] extractLocationValues(Annotation[] annotations) {
        List<String> values = new ArrayList<>();
        if (annotations == null) {
            return new String[0];
        }

        for (Annotation annotation : annotations) {
            if (annotation instanceof Location) {
                values.add(((Location) annotation).value());
            } else if (annotation instanceof Locations) {
                for (Location location : ((Locations) annotation).value()) {
                    values.add(location.value());
                }
            }
        }
        return values.toArray(new String[0]);
    }
}
