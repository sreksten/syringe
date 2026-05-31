package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par55injection.par552;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Vetoed;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Vetoed
public abstract class LifecycleBaseBean {

    @Inject
    protected BaseFieldDependency baseFieldDependency;

    protected final List<String> events = new ArrayList<>();

    protected boolean baseInitializerSawInjectedFields;
    protected boolean basePostConstructAfterInitializers;

    @Inject
    void initializeBase(BaseInitDependency ignored) {
        baseInitializerSawInjectedFields = baseFieldDependency != null;
        events.add("base-init");
    }

    @PostConstruct
    void postConstructBase() {
        basePostConstructAfterInitializers = events.contains("base-init");
        events.add("base-post");
    }

    public List<String> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public boolean isBaseInitializerSawInjectedFields() {
        return baseInitializerSawInjectedFields;
    }

    public boolean isBasePostConstructAfterInitializers() {
        return basePostConstructAfterInitializers;
    }
}
