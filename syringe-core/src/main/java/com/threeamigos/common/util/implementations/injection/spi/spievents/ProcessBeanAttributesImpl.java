package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.implementations.injection.spi.configurators.BeanAttributesConfiguratorImpl;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.BeanAttributes;
import jakarta.enterprise.inject.spi.ProcessBeanAttributes;
import jakarta.enterprise.inject.spi.configurator.BeanAttributesConfigurator;

/**
 * ProcessBeanAttributes event implementation.
 */
public class ProcessBeanAttributesImpl<T> extends PhaseAware
        implements ProcessBeanAttributes<T>, ObserverInvocationLifecycle {

    private final Annotated annotated;
    private BeanAttributes<T> beanAttributes;
    private boolean vetoed = false;
    private boolean ignoreFinalMethods = false;
    private final KnowledgeBase knowledgeBase;
    private BeanAttributesConfiguratorImpl<T> configurator;
    private final ThreadLocal<Boolean> observerInvocationActive = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> setCalledInCurrentInvocation = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ThreadLocal<Boolean> configureCalledInCurrentInvocation = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public ProcessBeanAttributesImpl(MessageHandler messageHandler, Annotated annotated,
                                     BeanAttributes<T> beanAttributes,
                                     KnowledgeBase knowledgeBase) {
        super(messageHandler);
        checkNotNull(annotated, "Annotated");
        checkNotNull(beanAttributes, "BeanAttributes");
        this.annotated = annotated;
        this.beanAttributes = beanAttributes;
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public Annotated getAnnotated() {
        assertObserverInvocationActive();
        return annotated;
    }

    public Annotated getAnnotatedInternal() {
        return annotated;
    }

    @Override
    public BeanAttributes<T> getBeanAttributes() {
        assertObserverInvocationActive();
        return beanAttributes;
    }

    public BeanAttributes<T> getBeanAttributesInternal() {
        return beanAttributes;
    }

    @Override
    public void setBeanAttributes(BeanAttributes<T> beanAttributes) {
        assertObserverInvocationActive();
        if (configureCalledInCurrentInvocation.get()) {
            throw new IllegalStateException("setBeanAttributes() and configureBeanAttributes() cannot both be used in the same observer invocation");
        }
        checkNotNull(beanAttributes, "BeanAttributes");
        this.beanAttributes = beanAttributes;
        setCalledInCurrentInvocation.set(Boolean.TRUE);
    }

    @Override
    public BeanAttributesConfigurator<T> configureBeanAttributes() {
        assertObserverInvocationActive();
        if (setCalledInCurrentInvocation.get()) {
            throw new IllegalStateException("setBeanAttributes() and configureBeanAttributes() cannot both be used in the same observer invocation");
        }
        configureCalledInCurrentInvocation.set(Boolean.TRUE);
        if (configurator == null) {
            configurator = new BeanAttributesConfiguratorImpl<>(beanAttributes);
        }
        return configurator;
    }

    @Override
    public void veto() {
        assertObserverInvocationActive();
        info(Phase.PROCESS_BEAN_ATTRIBUTES, "Veto on " + annotated.getBaseType().getClass().getName());
        this.vetoed = true;
    }

    @Override
    public void ignoreFinalMethods() {
        assertObserverInvocationActive();
        info(Phase.PROCESS_BEAN_ATTRIBUTES, "Ignoring final methods on " +
                annotated.getBaseType().getClass().getName());
        this.ignoreFinalMethods = true;
    }

    @Override
    public void addDefinitionError(Throwable t) {
        assertObserverInvocationActive();
        knowledgeBase.addDefinitionError(Phase.PROCESS_BEAN_ATTRIBUTES, "Definition error from extension", t);
    }

    public boolean isVetoed() {
        return vetoed;
    }

    public boolean isIgnoreFinalMethods() {
        return ignoreFinalMethods;
    }

    @Override
    public void beginObserverInvocation() {
        observerInvocationActive.set(Boolean.TRUE);
        setCalledInCurrentInvocation.set(Boolean.FALSE);
        configureCalledInCurrentInvocation.set(Boolean.FALSE);
    }

    @Override
    public void endObserverInvocation() {
        if (configureCalledInCurrentInvocation.get() && configurator != null) {
            beanAttributes = configurator.complete();
        }
        observerInvocationActive.set(Boolean.FALSE);
        setCalledInCurrentInvocation.set(Boolean.FALSE);
        configureCalledInCurrentInvocation.set(Boolean.FALSE);
    }

    private void assertObserverInvocationActive() {
        if (!observerInvocationActive.get()) {
            throw new IllegalStateException("ProcessBeanAttributes methods may only be called during observer method invocation");
        }
    }
}
