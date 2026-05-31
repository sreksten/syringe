package com.threeamigos.common.util.implementations.injection.interceptors;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationComparator;
import com.threeamigos.common.util.implementations.injection.builtinbeans.ActivateRequestContextInterceptor;
import com.threeamigos.common.util.implementations.injection.knowledgebase.InterceptorInfo;
import jakarta.enterprise.inject.spi.InterceptionType;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Helper for querying and ordering interceptor metadata.
 */
public class InterceptorsHelper {

    private final Collection<InterceptorInfo> interceptorInfos;
    private final ToIntFunction<Class<?>> applicationInterceptorOrder;
    private final ToIntFunction<Class<?>> interceptorBeansXmlOrder;

    public InterceptorsHelper(Collection<InterceptorInfo> interceptorInfos,
                              ToIntFunction<Class<?>> applicationInterceptorOrder,
                              ToIntFunction<Class<?>> interceptorBeansXmlOrder) {
        this.interceptorInfos = interceptorInfos;
        this.applicationInterceptorOrder = applicationInterceptorOrder;
        this.interceptorBeansXmlOrder = interceptorBeansXmlOrder;
    }

    public List<InterceptorInfo> getInterceptorsByBindingsAndType(
            InterceptionType interceptionType,
            Set<Annotation> targetBindings) {

        if (targetBindings == null || targetBindings.isEmpty()) {
            return Collections.emptyList();
        }

        return interceptorInfos.stream()
                .filter(this::isInterceptorEnabledForResolution)
                .filter(info -> supportsInterceptionType(info, interceptionType))
                .filter(info -> hasMatchingBindings(info, targetBindings))
                .sorted(this::compareInterceptors)
                .collect(Collectors.toList());
    }

    public List<InterceptorInfo> getInterceptorsByBindingAndType(
            InterceptionType interceptionType,
            Annotation binding) {

        if (binding == null) {
            return Collections.emptyList();
        }

        return getInterceptorsByBindingsAndType(interceptionType, Collections.singleton(binding));
    }

    public List<InterceptorInfo> getInterceptorsByType(InterceptionType interceptionType) {
        return interceptorInfos.stream()
                .filter(this::isInterceptorEnabledForResolution)
                .filter(info -> supportsInterceptionType(info, interceptionType))
                .sorted(this::compareInterceptors)
                .collect(Collectors.toList());
    }

    public List<InterceptorInfo> getInterceptorsByBindings(Set<Annotation> targetBindings) {
        if (targetBindings == null || targetBindings.isEmpty()) {
            return Collections.emptyList();
        }

        return interceptorInfos.stream()
                .filter(this::isInterceptorEnabledForResolution)
                .filter(info -> hasMatchingBindings(info, targetBindings))
                .sorted(this::compareInterceptors)
                .collect(Collectors.toList());
    }

    public Set<Class<? extends Annotation>> getAllInterceptorBindingTypes() {
        return interceptorInfos.stream()
                .flatMap(info -> info.getInterceptorBindings().stream())
                .map(Annotation::annotationType)
                .collect(Collectors.toSet());
    }

    private boolean isInterceptorEnabledForResolution(InterceptorInfo info) {
        if (info == null) {
            return false;
        }

        Class<?> interceptorClass = info.getInterceptorClass();
        if (interceptorClass == null) {
            return false;
        }

        if (ActivateRequestContextInterceptor.class.getName().equals(interceptorClass.getName())) {
            return true;
        }

        return applicationInterceptorOrder.applyAsInt(interceptorClass) >= 0
                || interceptorBeansXmlOrder.applyAsInt(interceptorClass) >= 0;
    }

    private boolean supportsInterceptionType(InterceptorInfo interceptorInfo, InterceptionType interceptionType) {
        switch (interceptionType) {
            case AROUND_INVOKE:
                return interceptorInfo.hasAroundInvoke();

            case AROUND_CONSTRUCT:
                return interceptorInfo.hasAroundConstruct();

            case POST_CONSTRUCT:
                return interceptorInfo.getPostConstructMethod() != null;

            case PRE_DESTROY:
                return interceptorInfo.getPreDestroyMethod() != null;

            case AROUND_TIMEOUT:
                // EJB feature - not supported in this implementation
                return false;

            default:
                return false;
        }
    }

    private boolean hasMatchingBindings(InterceptorInfo interceptorInfo, Set<Annotation> targetBindings) {
        Set<Annotation> interceptorBindings = interceptorInfo.getInterceptorBindings();

        for (Annotation interceptorBinding : interceptorBindings) {
            boolean found = false;

            for (Annotation targetBinding : targetBindings) {
                if (areBindingsEqual(interceptorBinding, targetBinding)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        return true;
    }

    private int compareInterceptors(InterceptorInfo left, InterceptorInfo right) {
        boolean leftPriority = AnnotationPredicates.hasPriorityAnnotation(left.getInterceptorClass());
        boolean rightPriority = AnnotationPredicates.hasPriorityAnnotation(right.getInterceptorClass());

        if (leftPriority && rightPriority) {
            int byPriority = Integer.compare(left.getPriority(), right.getPriority());
            if (byPriority != 0) {
                return byPriority;
            }
        } else if (leftPriority != rightPriority) {
            return leftPriority ? -1 : 1;
        }

        int leftOrder = applicationInterceptorOrder.applyAsInt(left.getInterceptorClass());
        int rightOrder = applicationInterceptorOrder.applyAsInt(right.getInterceptorClass());
        if (leftOrder >= 0 && rightOrder >= 0) {
            int byOrder = Integer.compare(leftOrder, rightOrder);
            if (byOrder != 0) {
                return byOrder;
            }
        } else if (leftOrder >= 0 || rightOrder >= 0) {
            return leftOrder >= 0 ? -1 : 1;
        }

        int leftBeansXmlOrder = interceptorBeansXmlOrder.applyAsInt(left.getInterceptorClass());
        int rightBeansXmlOrder = interceptorBeansXmlOrder.applyAsInt(right.getInterceptorClass());
        if (leftBeansXmlOrder >= 0 && rightBeansXmlOrder >= 0) {
            int byBeansXmlOrder = Integer.compare(leftBeansXmlOrder, rightBeansXmlOrder);
            if (byBeansXmlOrder != 0) {
                return byBeansXmlOrder;
            }
        } else if (leftBeansXmlOrder >= 0 || rightBeansXmlOrder >= 0) {
            return leftBeansXmlOrder >= 0 ? -1 : 1;
        }

        return left.getInterceptorClass().getName().compareTo(right.getInterceptorClass().getName());
    }

    private boolean areBindingsEqual(Annotation binding1, Annotation binding2) {
        return AnnotationComparator.equals(binding1, binding2);
    }
}
