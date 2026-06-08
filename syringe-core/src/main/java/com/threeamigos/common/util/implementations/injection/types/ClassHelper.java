package com.threeamigos.common.util.implementations.injection.types;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class ClassHelper {

    private ClassHelper() {}

    /**
     * Given an object, returns its class hierarchy in order from leaf to root.
     * @param instance the object to start from
     * @return the class hierarchy, starting with the leaf class and ending with Object
     */
    public static List<Class<?>> collectClassHierarchyFromObject(Object instance) {
        return collectClassHierarchy(instance.getClass());
    }

    /**
     * Given a class, returns its class hierarchy in order from leaf to root.
     * @param leafClass the class to start from
     * @return the class hierarchy, starting with the leaf class and ending with Object
     */
    public static List<Class<?>> collectClassHierarchy(Class<?> leafClass) {
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = leafClass;
        while (current != null && current != Object.class) {
            hierarchy.add(0, current);
            current = current.getSuperclass();
        }
        return hierarchy;
    }

    /**
     * Returns the default bean name for a given class.
     * @param rawName the raw bean name, if any
     * @param beanClass the bean class
     * @return the default bean name
     */
    public static String defaultedBeanName(String rawName, Class<?> beanClass) {
        if (rawName != null && !rawName.trim().isEmpty()) {
            return rawName.trim();
        }
        return decapitalize(beanClass.getSimpleName());
    }

    /**
     * Converts the first character of a string to lowercase.
     * @param value the string to decapitalize
     * @return the decapitalized string
     */
    public static String decapitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * Returns the package name of a class.
     * @param clazz the class
     * @return the package name, or an empty string if the class is in the default package
     */
    public static String packageName(Class<?> clazz) {
        Package pkg = clazz.getPackage();
        return pkg == null ? "" : pkg.getName();
    }

    /**
     * Normalizes a bean name by trimming and removing any leading or trailing whitespace.
     * @param beanName the bean name to normalize
     * @return the normalized bean name, or null if the input is null or empty
     */
    public static String normalizeBeanName(String beanName) {
        if (beanName == null) {
            return null;
        }
        String trimmed = beanName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
