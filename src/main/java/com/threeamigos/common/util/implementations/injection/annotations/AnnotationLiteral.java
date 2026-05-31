package com.threeamigos.common.util.implementations.injection.annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;

/**
 * A class representing a dynamic annotation proxy.
 * Use to bind specific annotations to types in the dependency injection system.
 */
public class AnnotationLiteral {

    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T of(Class<T> annotationClass) {
        return (T) Proxy.newProxyInstance(
                annotationClass.getClassLoader(),
                new Class[]{annotationClass},
                (proxy, method, args) -> {
                    if (method.getName().equals("annotationType") && method.getParameterCount() == 0) {
                        return annotationClass;
                    }
                    if (method.getName().equals("equals") && method.getParameterCount() == 1) {
                        Object other = args[0];
                        return annotationClass.isInstance(other);
                    }
                    if (method.getName().equals("hashCode") && method.getParameterCount() == 0) {
                        return 0; // Standard for marker annotations
                    }
                    if (method.getName().equals("toString") && method.getParameterCount() == 0) {
                        return "@" + annotationClass.getName() + "()";
                    }
                    throw new UnsupportedOperationException(
                            "Method " + method.getName() + " is not supported on this dynamic annotation proxy. " +
                                    "This helper only supports marker annotations (no members)."
                    );
                }
        );
    }
}
