package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates;

import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import jakarta.enterprise.inject.build.compatible.spi.DisposerInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.declarations.ParameterInfo;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasDisposesAnnotation;

final class BceDisposerInfo implements DisposerInfo {

    private final Method disposerMethod;

    private BceDisposerInfo(Method disposerMethod) {
        this.disposerMethod = disposerMethod;
    }

    static DisposerInfo from(ProducerBean<?> producerBean) {
        if (producerBean == null || producerBean.getDisposerMethod() == null) {
            return null;
        }
        return new BceDisposerInfo(producerBean.getDisposerMethod());
    }

    @Override
    public MethodInfo disposerMethod() {
        return BceMetadata.methodInfo(disposerMethod);
    }

    @Override
    public ParameterInfo disposedParameter() {
        Parameter[] parameters = disposerMethod.getParameters();
        List<ParameterInfo> infos = disposerMethod().parameters();
        for (int i = 0; i < parameters.length && i < infos.size(); i++) {
            if (hasDisposesAnnotation(parameters[i])) {
                return infos.get(i);
            }
        }
        throw new IllegalStateException("Disposer method has no @Disposes parameter: " +
            disposerMethod.getDeclaringClass().getName() + "." + disposerMethod.getName());
    }
}
