package com.threeamigos.common.util.implementations.injection.knowledgebase;

import com.threeamigos.common.util.implementations.injection.events.ObserverMethodInfo;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.ObserverMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

final class KnowledgeBaseBeanRegistryStore {

    private final Collection<Bean<?>> beans = new ConcurrentLinkedQueue<>();
    private final Collection<ProducerBean<?>> producerBeans = new ConcurrentLinkedQueue<>();
    private final Collection<Class<?>> interceptors = new ConcurrentLinkedQueue<>();
    private final Collection<Class<?>> decorators = new ConcurrentLinkedQueue<>();
    private final Collection<InterceptorInfo> interceptorInfos = new ConcurrentLinkedQueue<>();
    private final Collection<DecoratorInfo> decoratorInfos = new ConcurrentLinkedQueue<>();
    private final Collection<ObserverMethodInfo> observerMethodInfos = new ConcurrentLinkedQueue<>();
    private final Collection<ObserverMethod<?>> syntheticObserverMethods = new ConcurrentLinkedQueue<>();
    private final Set<Bean<?>> ignoreFinalMethodsBeans = Collections.newSetFromMap(new IdentityHashMap<>());
    private volatile boolean observerMethodsDiscovered;

    void addBean(Bean<?> bean) {
        beans.add(bean);
    }

    Collection<Bean<?>> getBeans() {
        return beans;
    }

    void addProducerBean(ProducerBean<?> producerBean) {
        producerBeans.add(producerBean);
    }

    Collection<ProducerBean<?>> getProducerBeans() {
        return producerBeans;
    }

    void addInterceptorClass(Class<?> interceptorClass) {
        interceptors.add(interceptorClass);
    }

    Collection<Class<?>> getInterceptorClasses() {
        return interceptors;
    }

    void addDecoratorClass(Class<?> decoratorClass) {
        decorators.add(decoratorClass);
    }

    Collection<Class<?>> getDecoratorClasses() {
        return decorators;
    }

    void addInterceptorInfo(InterceptorInfo interceptorInfo) {
        interceptorInfos.add(interceptorInfo);
    }

    Collection<InterceptorInfo> getInterceptorInfos() {
        return interceptorInfos;
    }

    void addDecoratorInfo(DecoratorInfo decoratorInfo) {
        decoratorInfos.add(decoratorInfo);
    }

    Collection<DecoratorInfo> getDecoratorInfos() {
        return decoratorInfos;
    }

    void addObserverMethodInfo(ObserverMethodInfo observerMethodInfo) {
        observerMethodInfos.add(observerMethodInfo);
    }

    Collection<ObserverMethodInfo> getObserverMethodInfos() {
        return observerMethodInfos;
    }

    void addSyntheticObserverMethod(ObserverMethod<?> observerMethod) {
        syntheticObserverMethods.add(observerMethod);
    }

    Collection<ObserverMethod<?>> getSyntheticObserverMethods() {
        return syntheticObserverMethods;
    }

    void markIgnoreFinalMethods(Bean<?> bean) {
        ignoreFinalMethodsBeans.add(bean);
    }

    boolean shouldIgnoreFinalMethods(Bean<?> bean) {
        return ignoreFinalMethodsBeans.contains(bean);
    }

    boolean isObserverMethodsDiscovered() {
        return observerMethodsDiscovered;
    }

    void setObserverMethodsDiscovered(boolean discovered) {
        this.observerMethodsDiscovered = discovered;
    }

    void clear() {
        beans.clear();
        producerBeans.clear();
        interceptors.clear();
        decorators.clear();
        interceptorInfos.clear();
        decoratorInfos.clear();
        observerMethodInfos.clear();
        syntheticObserverMethods.clear();
        ignoreFinalMethodsBeans.clear();
        observerMethodsDiscovered = false;
    }
}
