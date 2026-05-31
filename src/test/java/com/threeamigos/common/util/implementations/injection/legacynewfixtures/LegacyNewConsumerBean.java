package com.threeamigos.common.util.implementations.injection.legacynewfixtures;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import javax.enterprise.inject.New;

@Dependent
public class LegacyNewConsumerBean {

    @Inject
    LegacyNewTargetBean normalReference;

    @Inject
    @New
    LegacyNewTargetBean newImplicitReference;

    @Inject
    @New(LegacyNewTargetBean.class)
    LegacyNewTargetBean newExplicitReference;

    public LegacyNewTargetBean getNormalReference() {
        return normalReference;
    }

    public LegacyNewTargetBean getNewImplicitReference() {
        return newImplicitReference;
    }

    public LegacyNewTargetBean getNewExplicitReference() {
        return newExplicitReference;
    }
}
