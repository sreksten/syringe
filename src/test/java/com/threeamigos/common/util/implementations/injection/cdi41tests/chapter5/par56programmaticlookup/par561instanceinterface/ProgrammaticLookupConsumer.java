package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationLiteral;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

public class ProgrammaticLookupConsumer {

    @Inject
    private Instance<PaymentProcessor> defaultProcessors;

    @Inject
    @Any
    private Instance<PaymentProcessor> anyProcessors;

    @Inject
    private Instance<DependentDestroyableBean> dependentDestroyables;

    @Inject
    private Instance<NormalScopedDestroyableBean> normalDestroyables;

    @Inject
    private Instance<HandleLazyBean> lazyHandleBeans;

    public String resolveDefaultFromParent() {
        return defaultProcessors.get().process();
    }

    public String resolveSpecialFromAnyChildSelect() {
        return anyProcessors.select(AnnotationLiteral.of(Special.class)).get().process();
    }

    public boolean isSpecialUnsatisfiedWhenSelectedFromDefaultParent() {
        return defaultProcessors.select(AnnotationLiteral.of(Special.class)).isUnsatisfied();
    }

    public void selectWithDuplicateNonRepeatingQualifier() {
        anyProcessors.select(
                AnnotationLiteral.of(Special.class),
                AnnotationLiteral.of(Special.class)
        );
    }

    public void selectWithNonQualifierAnnotation() {
        anyProcessors.select(AnnotationLiteral.of(NotAQualifier.class));
    }

    public void getFromAmbiguousAnyInstance() {
        anyProcessors.get();
    }

    public void getFromUnsatisfiedChildInstance() {
        anyProcessors.select(AnnotationLiteral.of(MissingQualifier.class)).get();
    }

    public boolean isUnsatisfiedForMissingQualifier() {
        return anyProcessors.select(AnnotationLiteral.of(MissingQualifier.class)).isUnsatisfied();
    }

    public boolean isAmbiguousForAnyProcessors() {
        return anyProcessors.isAmbiguous();
    }

    public boolean isResolvableForAnyProcessors() {
        return anyProcessors.isResolvable();
    }

    public boolean isResolvableForDefaultProcessors() {
        return defaultProcessors.isResolvable();
    }

    public boolean isUnsatisfiedForDefaultProcessors() {
        return defaultProcessors.isUnsatisfied();
    }

    public int destroyDependentInstanceAndGetPreDestroyCalls() {
        DependentDestroyableBean bean = dependentDestroyables.get();
        dependentDestroyables.destroy(bean);
        return DependentDestroyableBean.getPreDestroyCalls();
    }

    public void destroyNormalScopedProxy() {
        NormalScopedDestroyableBean bean = normalDestroyables.get();
        normalDestroyables.destroy(bean);
    }

    public Instance.Handle<HandleLazyBean> getLazyBeanHandle() {
        return lazyHandleBeans.getHandle();
    }

    public void getAmbiguousHandle() {
        anyProcessors.getHandle();
    }

    public void getUnsatisfiedHandle() {
        anyProcessors.select(AnnotationLiteral.of(MissingQualifier.class)).getHandle();
    }
}
