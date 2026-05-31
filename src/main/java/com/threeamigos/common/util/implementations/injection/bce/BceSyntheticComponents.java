package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserverBuilder;
import jakarta.enterprise.lang.model.types.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal SyntheticComponents implementation used by BCE Synthesis methods.
 */
public class BceSyntheticComponents implements SyntheticComponents {

    private final KnowledgeBase knowledgeBase;
    private final BeanManagerImpl beanManager;
    private final BceInvokerRegistry invokerRegistry;
    private final List<BceSyntheticBeanBuilderImpl<?>> beanBuilders = new ArrayList<>();
    private final List<BceSyntheticObserverBuilderImpl<?>> observerBuilders =
            new ArrayList<>();

    public BceSyntheticComponents(KnowledgeBase knowledgeBase,
                                  BeanManagerImpl beanManager,
                                  BceInvokerRegistry invokerRegistry) {
        this.knowledgeBase = knowledgeBase;
        this.beanManager = beanManager;
        this.invokerRegistry = invokerRegistry;
    }

    @Override
    public <T> SyntheticBeanBuilder<T> addBean(Class<T> type) {
        BceSyntheticBeanBuilderImpl<T> builder =
                new BceSyntheticBeanBuilderImpl<>(knowledgeBase, beanManager, invokerRegistry, type);
        beanBuilders.add(builder);
        return builder;
    }

    @Override
    public <T> SyntheticObserverBuilder<T> addObserver(Class<T> type) {
        BceSyntheticObserverBuilderImpl<T> builder =
                new BceSyntheticObserverBuilderImpl<>(knowledgeBase, invokerRegistry, type);
        observerBuilders.add(builder);
        return builder;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> SyntheticObserverBuilder<T> addObserver(Type type) {
        Class<?> runtimeType = BceMetadata.unwrapType(type);
        BceSyntheticObserverBuilderImpl<T> builder =
                new BceSyntheticObserverBuilderImpl<>(knowledgeBase, invokerRegistry, (Class<T>) runtimeType);
        observerBuilders.add(builder);
        return builder;
    }

    public void complete() {
        for (BceSyntheticBeanBuilderImpl<?> beanBuilder : beanBuilders) {
            beanBuilder.complete();
        }
        for (BceSyntheticObserverBuilderImpl<?> observerBuilder : observerBuilders) {
            observerBuilder.complete();
        }
    }
}
