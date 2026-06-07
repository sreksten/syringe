package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.annotations.DynamicAnnotationRegistry;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.FieldConfig;
import jakarta.enterprise.inject.build.compatible.spi.MetaAnnotations;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;
import jakarta.enterprise.inject.build.compatible.spi.ParameterConfig;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.*;

final class BceMetaAnnotations implements MetaAnnotations {

    private final KnowledgeBase knowledgeBase;
    private final MessageHandler messageHandler;
    private final BceAnnotationBuilderFactory annotationBuilderFactory = new BceAnnotationBuilderFactory();

    private enum DynamicAnnotationKind {
        QUALIFIER,
        INTERCEPTOR_BINDING,
        STEREOTYPE
    }

    BceMetaAnnotations(KnowledgeBase knowledgeBase, MessageHandler messageHandler) {
        this.knowledgeBase = knowledgeBase;
        this.messageHandler = messageHandler;
    }

    @Override
    public ClassConfig addQualifier(Class<? extends Annotation> qualifierAnnotation) {
        knowledgeBase.addQualifier(qualifierAnnotation);
        return new DynamicClassConfig(qualifierAnnotation, DynamicAnnotationKind.QUALIFIER);
    }

    @Override
    public ClassConfig addInterceptorBinding(Class<? extends Annotation> interceptorBindingAnnotation) {
        knowledgeBase.addInterceptorBinding(interceptorBindingAnnotation);
        return new DynamicClassConfig(interceptorBindingAnnotation, DynamicAnnotationKind.INTERCEPTOR_BINDING);
    }

    @Override
    public ClassConfig addStereotype(Class<? extends Annotation> stereotypeAnnotation) {
        knowledgeBase.addStereotype(stereotypeAnnotation);
        return new DynamicClassConfig(stereotypeAnnotation, DynamicAnnotationKind.STEREOTYPE);
    }

    @Override
    public void addContext(Class<? extends Annotation> scopeAnnotation,
                           Class<? extends AlterableContext> contextImplementation) {
        addContext(scopeAnnotation, hasNormalScopeAnnotation(scopeAnnotation), contextImplementation);
    }

    @Override
    public void addContext(Class<? extends Annotation> scopeAnnotation,
                           boolean isNormal,
                           Class<? extends AlterableContext> contextImplementation) {
        boolean passivating = Boolean.TRUE.equals(getNormalScopePassivatingValue(scopeAnnotation));
        knowledgeBase.addScope(scopeAnnotation, isNormal, isNormal && passivating);
        knowledgeBase.addContextImplementation(scopeAnnotation, contextImplementation);
        messageHandler.info("[BCE] Registered context for scope " +
            scopeAnnotation.getName() + " using " + contextImplementation.getName());
    }

    private final class DynamicClassConfig implements ClassConfig {
        private final Class<? extends Annotation> annotationType;
        private final DynamicAnnotationKind kind;
        private final ClassInfo classInfo;
        private final Collection<MethodConfig> methods;

        private DynamicClassConfig(Class<? extends Annotation> annotationType, DynamicAnnotationKind kind) {
            this.annotationType = annotationType;
            this.kind = kind;
            this.classInfo = BceMetadata.classInfo(annotationType);
            this.methods = buildMethodConfigs(annotationType);
        }

        @Override
        public ClassInfo info() {
            return classInfo;
        }

        @Override
        public ClassConfig addAnnotation(Class<? extends Annotation> annotationType) {
            registerClassLevelMetaAnnotation(materializeAnnotation(annotationType));
            return this;
        }

        @Override
        public ClassConfig addAnnotation(AnnotationInfo annotation) {
            if (annotation != null) {
                registerClassLevelMetaAnnotation(BceMetadata.unwrapAnnotationInfo(annotation));
            }
            return this;
        }

        @Override
        public ClassConfig addAnnotation(Annotation annotation) {
            registerClassLevelMetaAnnotation(annotation);
            return this;
        }

        @Override
        public ClassConfig removeAnnotation(Predicate<AnnotationInfo> predicate) {
            return this;
        }

        @Override
        public ClassConfig removeAllAnnotations() {
            return this;
        }

        @Override
        public Collection<MethodConfig> constructors() {
            return Collections.emptyList();
        }

        @Override
        public Collection<MethodConfig> methods() {
            return methods;
        }

        @Override
        public Collection<FieldConfig> fields() {
            return Collections.emptyList();
        }

        private Collection<MethodConfig> buildMethodConfigs(Class<? extends Annotation> annotationType) {
            Method[] declaredMethods = annotationType.getDeclaredMethods();
            List<MethodConfig> configs = new ArrayList<>(declaredMethods.length);
            for (Method declaredMethod : declaredMethods) {
                configs.add(new DynamicMethodConfig(this.annotationType, declaredMethod));
            }
            return Collections.unmodifiableList(configs);
        }

        private void registerClassLevelMetaAnnotation(Annotation metaAnnotation) {
            if (metaAnnotation == null) {
                return;
            }
            registerMetaAnnotation(kind, annotationType, metaAnnotation);
        }
    }

    private static final class DynamicMethodConfig implements MethodConfig {
        private final Class<? extends Annotation> annotationType;
        private final String methodName;
        private final MethodInfo methodInfo;

        private DynamicMethodConfig(Class<? extends Annotation> annotationType, Method method) {
            this.annotationType = annotationType;
            this.methodName = method.getName();
            this.methodInfo = BceMetadata.methodInfo(method);
        }

        @Override
        public MethodInfo info() {
            return methodInfo;
        }

        @Override
        public MethodConfig addAnnotation(Class<? extends Annotation> annotationType) {
            if (hasNonbindingAnnotation(annotationType)) {
                DynamicAnnotationRegistry.registerDynamicNonbindingMember(this.annotationType, methodName);
            }
            return this;
        }

        @Override
        public MethodConfig addAnnotation(AnnotationInfo annotation) {
            if (annotation != null &&
                    isNonbindingAnnotationName(annotation.declaration() != null ? annotation.declaration().name() : null)) {
                DynamicAnnotationRegistry.registerDynamicNonbindingMember(this.annotationType, methodName);
            }
            return this;
        }

        @Override
        public MethodConfig addAnnotation(Annotation annotation) {
            if (annotation != null && hasNonbindingAnnotation(annotation.annotationType())) {
                DynamicAnnotationRegistry.registerDynamicNonbindingMember(this.annotationType, methodName);
            }
            return this;
        }

        @Override
        public MethodConfig removeAnnotation(Predicate<AnnotationInfo> predicate) {
            return this;
        }

        @Override
        public MethodConfig removeAllAnnotations() {
            return this;
        }

        @Override
        public List<ParameterConfig> parameters() {
            return Collections.emptyList();
        }
    }

    private static boolean isNonbindingAnnotationName(String annotationName) {
        if (annotationName == null) {
            return false;
        }
        for (Class<? extends Annotation> type : AnnotationsEnum.NONBINDING.getAnnotations()) {
            if (type != null && annotationName.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private Annotation materializeAnnotation(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            return null;
        }
        try {
            return BceMetadata.unwrapAnnotationInfo(annotationBuilderFactory.create(annotationType).build());
        } catch (RuntimeException ex) {
            messageHandler.warn("[BCE] Unable to materialize annotation @" +
                    annotationType.getName() + " for dynamic meta-annotation registration: " + ex.getMessage());
            return null;
        }
    }

    private void registerMetaAnnotation(DynamicAnnotationKind kind,
                                        Class<? extends Annotation> declarationType,
                                        Annotation metaAnnotation) {
        if (declarationType == null || metaAnnotation == null) {
            return;
        }

        switch (kind) {
            case STEREOTYPE:
                mergeStereotypeDefinition(declarationType, metaAnnotation);
                break;
            case INTERCEPTOR_BINDING:
                mergeInterceptorBindingDefinition(declarationType, metaAnnotation);
                break;
            case QUALIFIER:
            default:
                break;
        }
    }

    private void mergeStereotypeDefinition(Class<? extends Annotation> stereotypeType, Annotation metaAnnotation) {
        Set<Annotation> merged = new LinkedHashSet<>();
        Set<Annotation> existing = knowledgeBase.getStereotypeDefinition(stereotypeType);
        if (existing != null) {
            merged.addAll(existing);
        }
        merged.add(metaAnnotation);
        knowledgeBase.addStereotype(stereotypeType, merged.toArray(new Annotation[0]));
    }

    private void mergeInterceptorBindingDefinition(Class<? extends Annotation> bindingType, Annotation metaAnnotation) {
        Set<Annotation> merged = new LinkedHashSet<>();
        Set<Annotation> existing = knowledgeBase.getInterceptorBindingDefinition(bindingType);
        if (existing != null) {
            merged.addAll(existing);
        }
        merged.add(metaAnnotation);
        knowledgeBase.addInterceptorBinding(bindingType, merged.toArray(new Annotation[0]));
    }
}
