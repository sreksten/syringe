package com.threeamigos.common.util.implementations.injection.util;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.threeamigos.common.util.implementations.injection.types.ClassHelper.collectClassHierarchyFromObject;

public class LifecycleMethodHelper {

    public static void invokeLifecycleMethod(Object instance, Class<? extends Annotation> annotation) throws InvocationTargetException, IllegalAccessException {
        // Collect all classes in the hierarchy, from parent to child
        List<Class<?>> hierarchy = collectClassHierarchyFromObject(instance);

        // Invoke @PreDestroy methods from parent to child
        for (Class<?> clazz : hierarchy) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(annotation)) {
                    method.setAccessible(true);
                    method.invoke(instance);
                }
            }
        }
    }

    public static void invokeLifecycleMethod(Object instance, AnnotationsEnum annotation) throws InvocationTargetException, IllegalAccessException {
        // Collect all classes in the hierarchy, from parent to child
        List<Class<?>> hierarchy = collectClassHierarchyFromObject(instance);

        // Invoke lifecycle methods from parent to child
        for (Class<?> clazz : hierarchy) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (annotation != null && annotation.isPresent(method)) {
                    method.setAccessible(true);
                    method.invoke(instance);
                }
            }
        }
    }
}
