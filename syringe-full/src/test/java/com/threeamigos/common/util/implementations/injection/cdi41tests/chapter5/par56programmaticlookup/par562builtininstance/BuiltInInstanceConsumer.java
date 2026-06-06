package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par562builtininstance;

import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.HandleLazyBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.PaymentProcessor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.Special;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class BuiltInInstanceConsumer {

    @Inject
    private Instance<PaymentProcessor> defaultInstance;

    @Inject
    private Provider<PaymentProcessor> defaultProvider;

    @Inject
    @Any
    private Instance<PaymentProcessor> anyInstance;

    @Inject
    @Any
    private Provider<PaymentProcessor> anyProvider;

    @Inject
    @Special
    private Instance<PaymentProcessor> specialInstance;

    @Inject
    @Special
    private Provider<PaymentProcessor> specialProvider;

    @Inject
    private Instance<HandleLazyBean> dependentInstanceA;

    @Inject
    private Instance<HandleLazyBean> dependentInstanceB;

    public boolean hasAllBuiltInLookupsInjected() {
        return defaultInstance != null && defaultProvider != null &&
                anyInstance != null && anyProvider != null &&
                specialInstance != null && specialProvider != null;
    }

    public String defaultInstanceValue() {
        return defaultInstance.get().process();
    }

    public String defaultProviderValue() {
        return defaultProvider.get().process();
    }

    public String specialInstanceValue() {
        return specialInstance.get().process();
    }

    public String specialProviderValue() {
        return specialProvider.get().process();
    }

    public boolean dependentBuiltInBeanScopeLooksDependent() {
        return dependentInstanceA != dependentInstanceB;
    }
}
