package com.threeamigos.common.util.implementations.injection.annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Utility class for comparing CDI annotations with @Nonbinding support.
 *
 * <p>According to CDI 4.1 specification, when comparing qualifiers, interceptor bindings,
 * and stereotypes, annotation members marked with @Nonbinding must be ignored during the
 * equality check.
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * @Qualifier
 * @Retention(RUNTIME)
 * public @interface PayBy {
 *     PaymentMethod value();                    // Considered during matching
 *     @Nonbinding String description() default "";  // Ignored during matching
 * }
 *
 * PayBy p1 = ... // value=CREDIT_CARD, description="Online"
 * PayBy p2 = ... // value=CREDIT_CARD, description="POS"
 *
 * // These are considered equal because description is @Nonbinding
 * AnnotationComparator.equals(p1, p2); // true
 * }</pre>
 *
 * @author Stefano Reksten
 * @see jakarta.enterprise.util.Nonbinding
 */
public class AnnotationComparator {

    /**
     * Compares two annotations for equality, respecting @Nonbinding members.
     *
     * <p>Two annotations are considered equal if:
     * <ul>
     *   <li>They are the same annotation type</li>
     *   <li>All non-@Nonbinding members have equal values</li>
     * </ul>
     *
     * @param a1 first annotation
     * @param a2 second annotation
     * @return true if annotations are equal (ignoring @Nonbinding members)
     */
    public static boolean equals(Annotation a1, Annotation a2) {
        if (a1 == a2) {
            return true;
        }

        if (a1 == null || a2 == null) {
            return false;
        }

        // Annotations must be of the same type
        if (!a1.annotationType().equals(a2.annotationType())) {
            return false;
        }

        // Get all methods (annotation members) from the annotation type
        Method[] methods = a1.annotationType().getDeclaredMethods();

        for (Method method : methods) {
            // Skip members marked with @Nonbinding
            if (AnnotationPredicates.hasNonbindingAnnotation(method)) {
                continue;
            }

            try {
                if (!method.isAccessible()) {
                    method.setAccessible(true);
                }
                // Get values from both annotations
                Object value1 = method.invoke(a1);
                Object value2 = method.invoke(a2);

                // Compare values
                if (!valuesEqual(value1, value2)) {
                    return false;
                }
            } catch (Exception e) {
                // If we can't jakarta.enterprise.invoke the method, consider annotations unequal
                return false;
            }
        }

        return true;
    }

    /**
     * Compares two annotation member values for equality.
     *
     * <p>Handles arrays specially since arrays must be compared with Arrays.equals()
     * rather than Object.equals().
     */
    private static boolean valuesEqual(Object value1, Object value2) {
        if (value1 == value2) {
            return true;
        }

        if (value1 == null || value2 == null) {
            return false;
        }

        // Handle array values
        if (value1.getClass().isArray() && value2.getClass().isArray()) {
            return arraysEqual(value1, value2);
        }

        // Handle regular values
        return value1.equals(value2);
    }

    /**
     * Compares two arrays for equality, handling different array types.
     */
    private static boolean arraysEqual(Object array1, Object array2) {
        // Determine array component type
        Class<?> componentType = array1.getClass().getComponentType();

        if (componentType == boolean.class) {
            return Arrays.equals((boolean[]) array1, (boolean[]) array2);
        } else if (componentType == byte.class) {
            return Arrays.equals((byte[]) array1, (byte[]) array2);
        } else if (componentType == char.class) {
            return Arrays.equals((char[]) array1, (char[]) array2);
        } else if (componentType == short.class) {
            return Arrays.equals((short[]) array1, (short[]) array2);
        } else if (componentType == int.class) {
            return Arrays.equals((int[]) array1, (int[]) array2);
        } else if (componentType == long.class) {
            return Arrays.equals((long[]) array1, (long[]) array2);
        } else if (componentType == float.class) {
            return Arrays.equals((float[]) array1, (float[]) array2);
        } else if (componentType == double.class) {
            return Arrays.equals((double[]) array1, (double[]) array2);
        } else {
            // Object arrays (including annotation arrays)
            return Arrays.equals((Object[]) array1, (Object[]) array2);
        }
    }

    /**
     * Computes a hash code for an annotation, respecting @Nonbinding members.
     *
     * <p>This hash code is compatible with {@link #equals(Annotation, Annotation)},
     * meaning that if two annotations are equal, according to equals(), they will have
     * the same hash code.
     *
     * @param annotation the annotation
     * @return hash code ignoring @Nonbinding members
     */
    public static int hashCode(Annotation annotation) {
        if (annotation == null) {
            return 0;
        }

        int result = annotation.annotationType().hashCode();

        Method[] methods = annotation.annotationType().getDeclaredMethods();
        for (Method method : methods) {
            // Skip members marked with @Nonbinding
            if (AnnotationPredicates.hasNonbindingAnnotation(method)) {
                continue;
            }

            try {
                if (!method.isAccessible()) {
                    method.setAccessible(true);
                }
                Object value = method.invoke(annotation);
                result = 31 * result + valueHashCode(value);
            } catch (Exception e) {
                // Ignore exceptions during hash code computation
            }
        }

        return result;
    }

    /**
     * Computes hash code for an annotation member value, handling arrays.
     */
    private static int valueHashCode(Object value) {
        if (value == null) {
            return 0;
        }

        if (value.getClass().isArray()) {
            return arrayHashCode(value);
        }

        return value.hashCode();
    }

    /**
     * Computes hash code for an array, handling different array types.
     */
    private static int arrayHashCode(Object array) {
        Class<?> componentType = array.getClass().getComponentType();

        if (componentType == boolean.class) {
            return Arrays.hashCode((boolean[]) array);
        } else if (componentType == byte.class) {
            return Arrays.hashCode((byte[]) array);
        } else if (componentType == char.class) {
            return Arrays.hashCode((char[]) array);
        } else if (componentType == short.class) {
            return Arrays.hashCode((short[]) array);
        } else if (componentType == int.class) {
            return Arrays.hashCode((int[]) array);
        } else if (componentType == long.class) {
            return Arrays.hashCode((long[]) array);
        } else if (componentType == float.class) {
            return Arrays.hashCode((float[]) array);
        } else if (componentType == double.class) {
            return Arrays.hashCode((double[]) array);
        } else {
            return Arrays.hashCode((Object[]) array);
        }
    }
}
