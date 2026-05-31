package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.knowledgebase.InterceptorInfo;
import jakarta.enterprise.inject.build.compatible.spi.*;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.types.Type;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

final class BceInterceptorInfo implements jakarta.enterprise.inject.build.compatible.spi.InterceptorInfo {

    private final BeanInfo beanInfoDelegate;
    private final InterceptorInfo interceptorInfo;

    private BceInterceptorInfo(InterceptorInfo interceptorInfo) {
        this.interceptorInfo = interceptorInfo;
        this.beanInfoDelegate = BceMetadata.beanInfo(interceptorInfo.getInterceptorClass());
    }

    static Collection<jakarta.enterprise.inject.build.compatible.spi.InterceptorInfo> from(
        Collection<InterceptorInfo> interceptorInfos) {
        if (interceptorInfos == null || interceptorInfos.isEmpty()) {
            return Collections.emptyList();
        }
        Collection<jakarta.enterprise.inject.build.compatible.spi.InterceptorInfo> out = new ArrayList<>();
        for (InterceptorInfo interceptorInfo : interceptorInfos) {
            out.add(new BceInterceptorInfo(interceptorInfo));
        }
        return Collections.unmodifiableCollection(out);
    }

    @Override
    public Collection<AnnotationInfo> interceptorBindings() {
        Collection<AnnotationInfo> out = new ArrayList<>();
        for (Annotation annotation : interceptorInfo.getInterceptorBindings()) {
            out.add(BceMetadata.annotationInfo(annotation));
        }
        return Collections.unmodifiableCollection(out);
    }

    @Override
    public boolean intercepts(InterceptionType interceptionType) {
        if (interceptionType == null) {
            return false;
        }
        switch (interceptionType) {
            case AROUND_INVOKE:
                return interceptorInfo.getAroundInvokeMethod() != null;
            case AROUND_CONSTRUCT:
                return interceptorInfo.getAroundConstructMethod() != null;
            case POST_CONSTRUCT:
                return interceptorInfo.getPostConstructMethod() != null;
            case PRE_DESTROY:
                return interceptorInfo.getPreDestroyMethod() != null;
            default:
                return false;
        }
    }

    @Override
    public ClassInfo declaringClass() {
        return beanInfoDelegate.declaringClass();
    }

    @Override
    public Collection<AnnotationInfo> qualifiers() {
        return beanInfoDelegate.qualifiers();
    }

    @Override
    public ScopeInfo scope() {
        return beanInfoDelegate.scope();
    }

    @Override
    public String name() {
        return beanInfoDelegate.name();
    }

    @Override
    public Collection<Type> types() {
        return beanInfoDelegate.types();
    }

    @Override
    public DisposerInfo disposer() {
        return beanInfoDelegate.disposer();
    }

    @Override
    public Collection<StereotypeInfo> stereotypes() {
        return beanInfoDelegate.stereotypes();
    }

    @Override
    public Collection<InjectionPointInfo> injectionPoints() {
        return beanInfoDelegate.injectionPoints();
    }

    @Override
    public Integer priority() {
        return interceptorInfo.getPriority();
    }

    @Override
    public boolean isClassBean() {
        return beanInfoDelegate.isClassBean();
    }

    @Override
    public boolean isProducerMethod() {
        return beanInfoDelegate.isProducerMethod();
    }

    @Override
    public boolean isProducerField() {
        return beanInfoDelegate.isProducerField();
    }

    @Override
    public boolean isSynthetic() {
        return beanInfoDelegate.isSynthetic();
    }

    @Override
    public MethodInfo producerMethod() {
        return beanInfoDelegate.producerMethod();
    }

    @Override
    public FieldInfo producerField() {
        return beanInfoDelegate.producerField();
    }

    @Override
    public boolean isAlternative() {
        return beanInfoDelegate.isAlternative();
    }
}
