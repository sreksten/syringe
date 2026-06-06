package com.threeamigos.common.util.implementations.injection.annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registry for dynamic annotations added at runtime by extensions.
 */
public final class DynamicAnnotationRegistry {

    private static final Set<Class<? extends Annotation>> DYNAMIC_QUALIFIERS =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<Class<? extends Annotation>> DYNAMIC_SCOPES =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<Class<? extends Annotation>> DYNAMIC_STEREOTYPES =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<Class<? extends Annotation>> DYNAMIC_INTERCEPTOR_BINDINGS =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<Class<? extends Annotation>, Set<String>> DYNAMIC_NONBINDING_MEMBERS =
            new ConcurrentHashMap<>();
    private static final Map<ClassLoader, AtomicInteger> DYNAMIC_ANNOTATION_USERS =
            new ConcurrentHashMap<>();

    private DynamicAnnotationRegistry() {
    }

    public static void registerDynamicQualifier(Class<? extends Annotation> qualifierType) {
        registerDynamicAnnotation(qualifierType, DYNAMIC_QUALIFIERS);
    }

    public static void registerDynamicScope(Class<? extends Annotation> scopeType) {
        registerDynamicAnnotation(scopeType, DYNAMIC_SCOPES);
    }

    public static void registerDynamicStereotype(Class<? extends Annotation> stereotypeType) {
        registerDynamicAnnotation(stereotypeType, DYNAMIC_STEREOTYPES);
    }

    public static void registerDynamicInterceptorBinding(Class<? extends Annotation> bindingType) {
        registerDynamicAnnotation(bindingType, DYNAMIC_INTERCEPTOR_BINDINGS);
    }

    public static void registerDynamicNonbindingMember(Class<? extends Annotation> annotationType,
                                                        String memberName) {
        if (annotationType == null || memberName == null || memberName.trim().isEmpty()) {
            return;
        }
        DYNAMIC_NONBINDING_MEMBERS
                .computeIfAbsent(annotationType, key -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(memberName);
    }

    public static void retainDynamicAnnotationsForClassLoader(ClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }
        DYNAMIC_ANNOTATION_USERS
                .computeIfAbsent(classLoader, key -> new AtomicInteger())
                .incrementAndGet();
    }

    public static void releaseDynamicAnnotationsForClassLoader(ClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }

        AtomicInteger users = DYNAMIC_ANNOTATION_USERS.get(classLoader);
        if (users == null) {
            return;
        }

        int remaining = users.decrementAndGet();
        if (remaining <= 0 && DYNAMIC_ANNOTATION_USERS.remove(classLoader, users)) {
            clearDynamicAnnotationsForClassLoader(classLoader);
        }
    }

    public static void clearDynamicAnnotationsForClassLoader(ClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }
        DYNAMIC_ANNOTATION_USERS.remove(classLoader);
        DYNAMIC_QUALIFIERS.removeIf(type -> type != null && type.getClassLoader() == classLoader);
        DYNAMIC_SCOPES.removeIf(type -> type != null && type.getClassLoader() == classLoader);
        DYNAMIC_STEREOTYPES.removeIf(type -> type != null && type.getClassLoader() == classLoader);
        DYNAMIC_INTERCEPTOR_BINDINGS.removeIf(type -> type != null && type.getClassLoader() == classLoader);
        DYNAMIC_NONBINDING_MEMBERS.keySet()
                .removeIf(type -> type != null && type.getClassLoader() == classLoader);
    }

    public static boolean hasDynamicQualifier(AnnotatedElement element) {
        return matchesDynamicAnnotation(element, DYNAMIC_QUALIFIERS);
    }

    public static boolean hasDynamicScope(AnnotatedElement element) {
        return matchesDynamicAnnotation(element, DYNAMIC_SCOPES);
    }

    public static boolean hasDynamicStereotype(AnnotatedElement element) {
        return matchesDynamicAnnotation(element, DYNAMIC_STEREOTYPES);
    }

    public static boolean hasDynamicInterceptorBinding(AnnotatedElement element) {
        return matchesDynamicAnnotation(element, DYNAMIC_INTERCEPTOR_BINDINGS);
    }

    public static boolean hasDynamicNonbindingMember(Class<? extends Annotation> annotationType,
                                                     String memberName) {
        if (annotationType == null || memberName == null) {
            return false;
        }
        Set<String> members = DYNAMIC_NONBINDING_MEMBERS.get(annotationType);
        return members != null && members.contains(memberName);
    }

    private static void registerDynamicAnnotation(Class<? extends Annotation> annotationType,
                                                  Set<Class<? extends Annotation>> sink) {
        if (annotationType == null || sink == null) {
            return;
        }
        sink.add(annotationType);
    }

    private static boolean matchesDynamicAnnotation(AnnotatedElement element,
                                                    Set<Class<? extends Annotation>> dynamicSet) {
        if (!(element instanceof Class)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Class<? extends Annotation> annotationType = (Class<? extends Annotation>) element;
        return dynamicSet.contains(annotationType);
    }
}
