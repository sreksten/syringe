package com.threeamigos.common.util.implementations.injection.knowledgebase;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class KnowledgeBaseEnablementStore {

    private final Set<String> programmaticallyEnabledAlternatives = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> applicationInterceptorOrder = new ConcurrentHashMap<>();
    private final Map<String, Integer> applicationAlternativeOrder = new ConcurrentHashMap<>();
    private final Map<String, Integer> applicationDecoratorOrder = new ConcurrentHashMap<>();
    private volatile boolean afterTypeDiscoveryAlternativesCustomized;
    private volatile boolean afterTypeDiscoveryInterceptorsCustomized;
    private volatile boolean afterTypeDiscoveryDecoratorsCustomized;

    void enableAlternative(String className) {
        programmaticallyEnabledAlternatives.add(className);
    }

    boolean isAlternativeEnabled(String className) {
        return programmaticallyEnabledAlternatives.contains(className);
    }

    void setApplicationInterceptorOrder(List<Class<?>> orderedInterceptors) {
        setApplicationOrder(applicationInterceptorOrder, orderedInterceptors);
    }

    int getApplicationInterceptorOrder(Class<?> interceptorClass) {
        return getApplicationOrder(applicationInterceptorOrder, interceptorClass);
    }

    boolean hasApplicationInterceptorSelection() {
        return !applicationInterceptorOrder.isEmpty();
    }

    void setApplicationAlternativeOrder(List<Class<?>> orderedAlternatives) {
        setApplicationOrder(applicationAlternativeOrder, orderedAlternatives);
    }

    int getApplicationAlternativeOrder(Class<?> alternativeClass) {
        return getApplicationOrder(applicationAlternativeOrder, alternativeClass);
    }

    boolean hasApplicationAlternativeSelection() {
        return !applicationAlternativeOrder.isEmpty();
    }

    void setApplicationDecoratorOrder(List<Class<?>> orderedDecorators) {
        setApplicationOrder(applicationDecoratorOrder, orderedDecorators);
    }

    int getApplicationDecoratorOrder(Class<?> decoratorClass) {
        return getApplicationOrder(applicationDecoratorOrder, decoratorClass);
    }

    boolean hasApplicationDecoratorSelection() {
        return !applicationDecoratorOrder.isEmpty();
    }

    private void setApplicationOrder(Map<String, Integer> applicationOrder, List<Class<?>> orderedTypes) {
        applicationOrder.clear();
        if (orderedTypes == null || orderedTypes.isEmpty()) {
            return;
        }

        int index = 0;
        for (Class<?> type : orderedTypes) {
            if (type == null) {
                continue;
            }
            String className = type.getName();
            if (!applicationOrder.containsKey(className)) {
                applicationOrder.put(className, index++);
            }
        }
    }

    private int getApplicationOrder(Map<String, Integer> applicationOrder, Class<?> type) {
        if (type == null) {
            return -1;
        }
        Integer index = applicationOrder.get(type.getName());
        return index != null ? index : -1;
    }

    boolean isAfterTypeDiscoveryAlternativesCustomized() {
        return afterTypeDiscoveryAlternativesCustomized;
    }

    void setAfterTypeDiscoveryAlternativesCustomized(boolean customized) {
        this.afterTypeDiscoveryAlternativesCustomized = customized;
    }

    boolean isAfterTypeDiscoveryInterceptorsCustomized() {
        return afterTypeDiscoveryInterceptorsCustomized;
    }

    void setAfterTypeDiscoveryInterceptorsCustomized(boolean customized) {
        this.afterTypeDiscoveryInterceptorsCustomized = customized;
    }

    boolean isAfterTypeDiscoveryDecoratorsCustomized() {
        return afterTypeDiscoveryDecoratorsCustomized;
    }

    void setAfterTypeDiscoveryDecoratorsCustomized(boolean customized) {
        this.afterTypeDiscoveryDecoratorsCustomized = customized;
    }

    void clear() {
        programmaticallyEnabledAlternatives.clear();
        applicationInterceptorOrder.clear();
        applicationAlternativeOrder.clear();
        applicationDecoratorOrder.clear();
        afterTypeDiscoveryAlternativesCustomized = false;
        afterTypeDiscoveryInterceptorsCustomized = false;
        afterTypeDiscoveryDecoratorsCustomized = false;
    }
}
