package com.threeamigos.common.util.implementations.injection.spi;

public enum Phase {

    AFTER_BEAN_DISCOVERY("AfterBeanDiscovery"),
    AFTER_DEPLOYMENT_VALIDATION("AfterDeploymentValidation"),
    BEFORE_BEAN_DISCOVERY("BeforeBeanDiscovery"),
    PROCESS_ANNOTATED_TYPE("ProcessAnnotatedType"),
    PROCESS_BEAN_ATTRIBUTES("ProcessBeanAttributes"),
    PROCESS_BEAN("ProcessBean"),
    PROCESS_INJECTION_POINT("ProcessInjectionPoint"),
    PROCESS_INJECTION_TARGET("ProcessInjectionTarget"),
    PROCESS_MANAGED_BEAN("ProcessManagedBean"),
    PROCESS_OBSERVER_METHOD("ProcessObserverMethod"),
    PROCESS_PRODUCER_FIELD("ProcessProducerField"),
    PROCESS_PRODUCER_METHOD("ProcessProducerMethod"),
    PROCESS_SYNTHETIC_BEAN("ProcessSyntheticBean"),
    ;

    private final String description;

    Phase(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

}
