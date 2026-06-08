package com.threeamigos.common.util.implementations.injection.util;

import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import jakarta.enterprise.inject.spi.Bean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.getPriorityValue;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.hasSpecializesAnnotation;
import static com.threeamigos.common.util.implementations.injection.util.ClassHelper.getClassDepth;

/**
 *
 * @author Stefano Reksten
 */
public class BeansHelper {

    private BeansHelper() {}

    public static Class<?> resolveProcessBeanAttributesDeclaringClass(Bean<?> bean) {
        if (bean instanceof ProducerBean<?>) {
            return ((ProducerBean<?>) bean).getDeclaringClass();
        }
        return bean != null ? bean.getBeanClass() : null;
    }

    public static int processBeanAttributesBeanKindOrder(Bean<?> bean) {
        if (bean instanceof BeanImpl<?>) {
            return 0;
        }
        if (bean instanceof ProducerBean<?>) {
            return 1;
        }
        return 2;
    }

    public static int processBeanAttributesClassDepth(Bean<?> bean) {
        Class<?> clazz = resolveProcessBeanAttributesDeclaringClass(bean);
        return getClassDepth(clazz);
    }

    public static String processBeanAttributesClassName(Bean<?> bean) {
        Class<?> clazz = resolveProcessBeanAttributesDeclaringClass(bean);
        return clazz != null ? clazz.getName() : "";
    }

    public static List<Bean<?>> orderBeansForProcessBeanAttributes(Collection<Bean<?>> beans) {
        List<Bean<?>> ordered = new ArrayList<>();
        if (beans != null) {
            ordered.addAll(beans);
        }
        ordered.sort((left, right) -> {
            int leftDepth = processBeanAttributesClassDepth(left);
            int rightDepth = processBeanAttributesClassDepth(right);
            int depthCompare = Integer.compare(rightDepth, leftDepth);
            if (depthCompare != 0) {
                return depthCompare;
            }

            String leftClassName = processBeanAttributesClassName(left);
            String rightClassName = processBeanAttributesClassName(right);
            int classCompare = leftClassName.compareTo(rightClassName);
            if (classCompare != 0) {
                return classCompare;
            }

            int kindCompare = Integer.compare(processBeanAttributesBeanKindOrder(left),
                    processBeanAttributesBeanKindOrder(right));
            if (kindCompare != 0) {
                return kindCompare;
            }

            return Integer.compare(System.identityHashCode(left), System.identityHashCode(right));
        });
        return ordered;
    }

    public static boolean isBeanEnabledForObserverLifecycle(Bean<?> bean) {
        if (bean == null) {
            return false;
        }
        if (!bean.isAlternative()) {
            return true;
        }
        if (bean instanceof BeanImpl<?>) {
            return ((BeanImpl<?>) bean).isAlternativeEnabled();
        }
        return true;
    }

    public static boolean isProcessBeanAttributesCandidate(Bean<?> bean) {
        return bean instanceof BeanImpl<?> || bean instanceof ProducerBean<?>;
    }

    /**
     * Returns {@code @Priority} declared directly on a producer method/field, if present.
     */
    public static Integer extractPriorityFromProducerMember(ProducerBean<?> producerBean) {
        if (producerBean == null) {
            return null;
        }
        if (producerBean.getProducerMethod() != null) {
            return getPriorityValue(producerBean.getProducerMethod().getAnnotations());
        }
        if (producerBean.getProducerField() != null) {
            return getPriorityValue(producerBean.getProducerField().getAnnotations());
        }
        return null;
    }

    public static Set<Class<?>> collectSpecializedSuperclasses(Class<?> beanClass) {
        Set<Class<?>> out = new HashSet<>();
        if (!hasSpecializesAnnotation(beanClass)) {
            return out;
        }
        Class<?> current = beanClass.getSuperclass();
        while (current != null && !Object.class.equals(current)) {
            out.add(current);
            if (!hasSpecializesAnnotation(current)) {
                break;
            }
            current = current.getSuperclass();
        }
        return out;
    }

    /**
     * Collects all superclasses suppressed by specialization for the given bean candidates.
     */
    public static Set<Class<?>> collectSpecializedSuperclasses(Collection<? extends Bean<?>> candidates) {
        Set<Class<?>> out = new HashSet<>();
        if (candidates == null || candidates.isEmpty()) {
            return out;
        }
        for (Bean<?> candidate : candidates) {
            out.addAll(collectSpecializedSuperclasses(candidate != null ? candidate.getBeanClass() : null));
        }
        return out;
    }

    /**
     * Removes beans that are specialized away by another candidate in the same set.
     */
    public static <B extends Bean<?>> Set<B> filterSpecializedBeans(Set<B> candidates) {
        if (candidates == null || candidates.size() < 2) {
            return candidates;
        }

        Set<Class<?>> specializedSuperclasses = collectSpecializedSuperclasses(candidates);
        if (specializedSuperclasses.isEmpty()) {
            return candidates;
        }

        Set<B> filtered = new LinkedHashSet<>();
        for (B candidate : candidates) {
            if (!specializedSuperclasses.contains(candidate.getBeanClass())) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    /**
     * Removes beans that are specialized away by another candidate in the same collection.
     */
    public static <B extends Bean<?>> Collection<B> filterSpecializedBeans(Collection<B> candidates) {
        if (candidates == null || candidates.size() < 2) {
            return candidates;
        }

        Set<Class<?>> specializedSuperclasses = collectSpecializedSuperclasses(candidates);
        if (specializedSuperclasses.isEmpty()) {
            return candidates;
        }

        List<B> filtered = new ArrayList<>();
        for (B candidate : candidates) {
            if (!specializedSuperclasses.contains(candidate.getBeanClass())) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

}
