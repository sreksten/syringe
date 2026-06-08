package com.threeamigos.common.util.implementations.injection.util;

import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import jakarta.enterprise.inject.spi.Bean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

}
