package com.threeamigos.common.util.implementations.injection.bce;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.*;

public class BceCommonFunctions {

    static Annotation buildAnnotationProxy(Class<? extends Annotation> annotationType, Map<String, Object> members) {
        return (Annotation) Proxy.newProxyInstance(
                annotationType.getClassLoader(),
                new Class<?>[]{annotationType},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    switch (methodName) {
                        case "annotationType":
                            return annotationType;
                        case "toString":
                            return "@" + annotationType.getName() + members;
                        case "hashCode":
                            return members.hashCode();
                        case "equals":
                            return proxy == args[0];
                    }
                    if (members.containsKey(methodName)) {
                        return members.get(methodName);
                    }
                    return method.getDefaultValue();
                }
        );
    }

}
