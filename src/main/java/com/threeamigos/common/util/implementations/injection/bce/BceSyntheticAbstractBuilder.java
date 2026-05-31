package com.threeamigos.common.util.implementations.injection.bce;

import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

abstract class BceSyntheticAbstractBuilder {

    protected final Set<Annotation> qualifiers = new LinkedHashSet<>();
    protected final Map<String, Object> params = new LinkedHashMap<>();

    protected void qualifierImpl(Class<? extends Annotation> qualifier) {
        if (qualifier == null) {
            return;
        }
        try {
            Annotation annotation = BceMetadata.unwrapAnnotationInfo(
                    new BceAnnotationBuilderFactory().create(qualifier).build());
            this.qualifiers.add(annotation);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Cannot instantiate qualifier " + qualifier.getName() +
                    ". Use qualifier(Annotation) or qualifier(AnnotationInfo).", e);
        }
    }

    protected void qualifierImpl(AnnotationInfo qualifier) {
        if (qualifier != null) {
            this.qualifiers.add(BceMetadata.unwrapAnnotationInfo(qualifier));
        }
    }

    protected void qualifierImpl(Annotation qualifier) {
        if (qualifier != null) {
            this.qualifiers.add(qualifier);
        }
    }

    protected void withParamInternal(String name, Object value) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter name must not be blank");
        }
        this.params.put(name, value);
    }

    protected void withParamInternal(String name, ClassInfo[] value) {
        if (value == null) {
            withParamInternal(name, (Object)null);
            return;
        }
        Class<?>[] converted = BceMetadata.unwrapClassInfo(value);
        withParamInternal(name, converted);
    }

    protected void withParamInternal(String name, AnnotationInfo[] value) {
        if (value == null) {
            withParamInternal(name, (Object)null);
            return;
        }
        Annotation[] converted = BceMetadata.unwrapAnnotationInfo(value);
        withParamInternal(name, converted);
    }
}
