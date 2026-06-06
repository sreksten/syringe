package com.threeamigos.common.util.implementations.injection.bce;

import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.FieldConfig;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;
import jakarta.enterprise.inject.build.compatible.spi.ParameterConfig;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationMember;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.declarations.ParameterInfo;
import jakarta.enterprise.lang.model.types.Type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

final class BceEnhancementModels {

    private BceEnhancementModels() {
    }

    static ClassConfig classConfig(Class<?> clazz) {
        return new SimpleClassConfig(clazz);
    }

    static ClassConfig classConfig(Class<?> clazz,
                                   Map<Method, MethodConfig> sharedMethodConfigs,
                                   Map<Field, FieldConfig> sharedFieldConfigs) {
        return new SimpleClassConfig(clazz, sharedMethodConfigs, sharedFieldConfigs);
    }

    static MethodConfig methodConfig(Method method) {
        return new SimpleMethodConfig(method);
    }

    static MethodConfig methodConfig(Constructor<?> constructor) {
        return new SimpleMethodConfig(constructor);
    }

    static FieldConfig fieldConfig(Field field) {
        return new SimpleFieldConfig(field);
    }

    private abstract static class AnnotationMutatorSupport {
        protected final List<AnnotationInfo> addedAnnotations = new ArrayList<>();
        protected final List<Predicate<AnnotationInfo>> removedAnnotationPredicates = new ArrayList<>();
        protected boolean removeAllAnnotationsRequested = false;

        protected void addAnnotationInternal(Class<? extends Annotation> annotationType) {
            if (annotationType != null) {
                addedAnnotations.add(new SyntheticAnnotationInfo(annotationType));
            }
        }

        protected void addAnnotationInternal(AnnotationInfo annotationInfo) {
            if (annotationInfo != null) {
                addedAnnotations.add(annotationInfo);
            }
        }

        protected void addAnnotationInternal(Annotation annotation) {
            if (annotation != null) {
                addedAnnotations.add(BceMetadata.annotationInfo(annotation));
            }
        }

        protected void removeAnnotationInternal(Predicate<AnnotationInfo> predicate) {
            if (predicate == null) {
                return;
            }
            removedAnnotationPredicates.add(predicate);
        }

        protected void removeAllAnnotationsInternal() {
            removeAllAnnotationsRequested = true;
            addedAnnotations.clear();
            removedAnnotationPredicates.clear();
        }

        protected List<AnnotationInfo> effectiveAnnotations(Collection<AnnotationInfo> base) {
            List<AnnotationInfo> out = new ArrayList<>();
            if (!removeAllAnnotationsRequested && base != null) {
                for (AnnotationInfo annotationInfo : base) {
                    if (doesNotMatchAnyRemovalPredicate(annotationInfo)) {
                        out.add(annotationInfo);
                    }
                }
            }
            for (AnnotationInfo added : addedAnnotations) {
                if (doesNotMatchAnyRemovalPredicate(added)) {
                    out.add(added);
                }
            }
            return out;
        }

        private boolean doesNotMatchAnyRemovalPredicate(AnnotationInfo annotationInfo) {
            return !matchesAnyRemovalPredicate(annotationInfo);
        }

        private boolean matchesAnyRemovalPredicate(AnnotationInfo annotationInfo) {
            for (Predicate<AnnotationInfo> predicate : removedAnnotationPredicates) {
                if (predicate.test(annotationInfo)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class SimpleClassConfig extends AnnotationMutatorSupport implements ClassConfig {
        private final ClassInfo info;
        private final Collection<MethodConfig> constructors;
        private final Collection<MethodConfig> methods;
        private final Collection<FieldConfig> fields;
        private final Map<Method, MethodConfig> sharedMethodConfigs;
        private final Map<Field, FieldConfig> sharedFieldConfigs;

        private SimpleClassConfig(Class<?> clazz) {
            this(clazz, null, null);
        }

        private SimpleClassConfig(Class<?> clazz,
                                  Map<Method, MethodConfig> sharedMethodConfigs,
                                  Map<Field, FieldConfig> sharedFieldConfigs) {
            this.info = BceMetadata.classInfo(clazz);
            this.sharedMethodConfigs = sharedMethodConfigs;
            this.sharedFieldConfigs = sharedFieldConfigs;
            this.constructors = buildConstructors(clazz);
            this.methods = buildMethods(clazz);
            this.fields = buildFields(clazz);
        }

        @Override
        public ClassInfo info() {
            return new MutableClassInfo(info, effectiveAnnotations(info.annotations()));
        }

        @Override
        public ClassConfig addAnnotation(Class<? extends Annotation> annotationType) {
            addAnnotationInternal(annotationType);
            return this;
        }

        @Override
        public ClassConfig addAnnotation(AnnotationInfo annotation) {
            addAnnotationInternal(annotation);
            return this;
        }

        @Override
        public ClassConfig addAnnotation(Annotation annotation) {
            addAnnotationInternal(annotation);
            return this;
        }

        @Override
        public ClassConfig removeAnnotation(Predicate<AnnotationInfo> predicate) {
            removeAnnotationInternal(predicate);
            return this;
        }

        @Override
        public ClassConfig removeAllAnnotations() {
            removeAllAnnotationsInternal();
            return this;
        }

        @Override
        public Collection<MethodConfig> constructors() {
            return constructors;
        }

        @Override
        public Collection<MethodConfig> methods() {
            return methods;
        }

        @Override
        public Collection<FieldConfig> fields() {
            return fields;
        }

        private Collection<MethodConfig> buildConstructors(Class<?> targetClass) {
            List<MethodConfig> out = new ArrayList<>();
            Constructor<?>[] declaredConstructors = targetClass.getDeclaredConstructors();
            List<Constructor<?>> sorted = new ArrayList<>();
            Collections.addAll(sorted, declaredConstructors);
            sorted.sort(Comparator.comparingInt(Constructor::getParameterCount));
            for (Constructor<?> constructor : sorted) {
                out.add(new SimpleMethodConfig(constructor));
            }
            return Collections.unmodifiableList(out);
        }

        private Collection<MethodConfig> buildMethods(Class<?> targetClass) {
            List<MethodConfig> out = new ArrayList<>();
            Method[] declaredMethods = targetClass.getDeclaredMethods();
            List<Method> sorted = new ArrayList<>();
            Collections.addAll(sorted, declaredMethods);
            sorted.sort(Comparator.comparing(Method::getName).thenComparingInt(Method::getParameterCount));
            for (Method method : sorted) {
                if (sharedMethodConfigs != null) {
                    MethodConfig existing = sharedMethodConfigs.get(method);
                    if (existing != null) {
                        out.add(existing);
                    } else {
                        MethodConfig created = new SimpleMethodConfig(method);
                        sharedMethodConfigs.put(method, created);
                        out.add(created);
                    }
                } else {
                    out.add(new SimpleMethodConfig(method));
                }
            }
            return Collections.unmodifiableList(out);
        }

        private Collection<FieldConfig> buildFields(Class<?> targetClass) {
            List<FieldConfig> out = new ArrayList<>();
            Field[] declaredFields = targetClass.getDeclaredFields();
            List<Field> sorted = new ArrayList<>();
            Collections.addAll(sorted, declaredFields);
            sorted.sort(Comparator.comparing(Field::getName));
            for (Field field : sorted) {
                if (sharedFieldConfigs != null) {
                    FieldConfig existing = sharedFieldConfigs.get(field);
                    if (existing != null) {
                        out.add(existing);
                    } else {
                        FieldConfig created = new SimpleFieldConfig(field);
                        sharedFieldConfigs.put(field, created);
                        out.add(created);
                    }
                } else {
                    out.add(new SimpleFieldConfig(field));
                }
            }
            return Collections.unmodifiableList(out);
        }
    }

    private static final class SimpleMethodConfig extends AnnotationMutatorSupport implements MethodConfig {
        private final MethodInfo info;
        private final List<ParameterConfig> parameters;

        private SimpleMethodConfig(Method method) {
            info = BceMetadata.methodInfo(method);
            parameters = evaluateInfo(info);
        }

        private SimpleMethodConfig(Constructor<?> constructor) {
            info = BceMetadata.methodInfo(constructor);
            parameters = evaluateInfo(info);
        }

        private List<ParameterConfig> evaluateInfo(MethodInfo info) {
            List<ParameterConfig> out = new ArrayList<>();
            List<ParameterInfo> parameterInfos = info.parameters();
            for (ParameterInfo parameterInfo : parameterInfos) {
                out.add(new SimpleParameterConfig(parameterInfo));
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public MethodInfo info() {
            return new MutableMethodInfo(info, effectiveAnnotations(info.annotations()), parameterInfos());
        }

        @Override
        public MethodConfig addAnnotation(Class<? extends Annotation> annotationType) {
            addAnnotationInternal(annotationType);
            return this;
        }

        @Override
        public MethodConfig addAnnotation(AnnotationInfo annotation) {
            addAnnotationInternal(annotation);
            return this;
        }

        @Override
        public MethodConfig addAnnotation(Annotation annotation) {
            addAnnotationInternal(annotation);
            return this;
        }

        @Override
        public MethodConfig removeAnnotation(Predicate<AnnotationInfo> predicate) {
            removeAnnotationInternal(predicate);
            return this;
        }

        @Override
        public MethodConfig removeAllAnnotations() {
            removeAllAnnotationsInternal();
            return this;
        }

        @Override
        public List<ParameterConfig> parameters() {
            return parameters;
        }

        private List<ParameterInfo> parameterInfos() {
            List<ParameterInfo> out = new ArrayList<>();
            for (ParameterConfig parameterConfig : parameters) {
                out.add(parameterConfig.info());
            }
            return Collections.unmodifiableList(out);
        }
    }

    private static final class SimpleFieldConfig extends AnnotationMutatorSupport implements FieldConfig {
        private final FieldInfo info;

        private SimpleFieldConfig(Field field) {
            this.info = BceMetadata.fieldInfo(field);
        }

        @Override
        public FieldInfo info() {
            return new MutableFieldInfo(info, effectiveAnnotations(info.annotations()));
        }

        @Override
        public FieldConfig addAnnotation(Class<? extends Annotation> annotationType) {
            addAnnotationInternal(annotationType);
            return this;
        }

        @Override
        public FieldConfig addAnnotation(AnnotationInfo annotation) {
            addAnnotationInternal(annotation);
            return this;
        }

        @Override
        public FieldConfig addAnnotation(Annotation annotation) {
            addAnnotationInternal(annotation);
            return this;
        }

        @Override
        public FieldConfig removeAnnotation(Predicate<AnnotationInfo> predicate) {
            removeAnnotationInternal(predicate);
            return this;
        }

        @Override
        public FieldConfig removeAllAnnotations() {
            removeAllAnnotationsInternal();
            return this;
        }
    }

    private static final class SimpleParameterConfig extends AnnotationMutatorSupport implements ParameterConfig {
        private final ParameterInfo info;

        private SimpleParameterConfig(ParameterInfo info) {
            this.info = info;
        }

        @Override
        public ParameterInfo info() {
            return new MutableParameterInfo(info, effectiveAnnotations(info.annotations()));
        }

        @Override
        public ParameterConfig addAnnotation(Class<? extends Annotation> annotationType) {
            addAnnotationInternal(annotationType);
            return this;
        }

        @Override
        public ParameterConfig addAnnotation(AnnotationInfo annotation) {
            addAnnotationInternal(annotation);
            return this;
        }

        @Override
        public ParameterConfig addAnnotation(Annotation annotation) {
            addAnnotationInternal(annotation);
            return this;
        }

        @Override
        public ParameterConfig removeAnnotation(Predicate<AnnotationInfo> predicate) {
            removeAnnotationInternal(predicate);
            return this;
        }

        @Override
        public ParameterConfig removeAllAnnotations() {
            removeAllAnnotationsInternal();
            return this;
        }
    }

    private abstract static class AnnotationAwareDeclaration implements DeclarationInfo {
        private final DeclarationInfo delegate;
        private final Collection<AnnotationInfo> annotations;

        private AnnotationAwareDeclaration(DeclarationInfo delegate, Collection<AnnotationInfo> annotations) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.annotations = Collections.unmodifiableCollection(new ArrayList<>(
                    annotations != null ? annotations : Collections.emptyList()));
        }

        @Override
        public Kind kind() {
            return delegate.kind();
        }

        @Override
        public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
            return annotation(annotationType) != null;
        }

        @Override
        public boolean hasAnnotation(Predicate<AnnotationInfo> predicate) {
            return !annotations(predicate).isEmpty();
        }

        @Override
        public <T extends Annotation> AnnotationInfo annotation(Class<T> annotationType) {
            if (annotationType == null) {
                return null;
            }
            for (AnnotationInfo annotationInfo : annotations) {
                if (annotationType.getName().equals(annotationInfo.declaration().name())) {
                    return annotationInfo;
                }
            }
            return null;
        }

        @Override
        public <T extends Annotation> Collection<AnnotationInfo> repeatableAnnotation(Class<T> annotationType) {
            List<AnnotationInfo> out = new ArrayList<>();
            if (annotationType == null) {
                return out;
            }
            for (AnnotationInfo annotationInfo : annotations) {
                if (annotationType.getName().equals(annotationInfo.declaration().name())) {
                    out.add(annotationInfo);
                    continue;
                }
                out.addAll(BceMetadata.extractRepeatableContainedAnnotations(annotationInfo, annotationType));
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public Collection<AnnotationInfo> annotations(Predicate<AnnotationInfo> predicate) {
            List<AnnotationInfo> out = new ArrayList<>();
            if (predicate == null) {
                out.addAll(annotations);
                return Collections.unmodifiableList(out);
            }
            for (AnnotationInfo annotationInfo : annotations) {
                if (predicate.test(annotationInfo)) {
                    out.add(annotationInfo);
                }
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public Collection<AnnotationInfo> annotations() {
            return annotations;
        }
    }

    private static final class MutableClassInfo extends AnnotationAwareDeclaration implements ClassInfo {
        private final ClassInfo delegate;

        private MutableClassInfo(ClassInfo delegate, Collection<AnnotationInfo> annotations) {
            super(delegate, annotations);
            this.delegate = delegate;
        }

        @Override public String name() { return delegate.name(); }
        @Override public String simpleName() { return delegate.simpleName(); }
        @Override public jakarta.enterprise.lang.model.declarations.PackageInfo packageInfo() { return delegate.packageInfo(); }
        @Override public List<jakarta.enterprise.lang.model.types.TypeVariable> typeParameters() { return delegate.typeParameters(); }
        @Override public Type superClass() { return delegate.superClass(); }
        @Override public ClassInfo superClassDeclaration() { return delegate.superClassDeclaration(); }
        @Override public List<Type> superInterfaces() { return delegate.superInterfaces(); }
        @Override public List<ClassInfo> superInterfacesDeclarations() { return delegate.superInterfacesDeclarations(); }
        @Override public boolean isPlainClass() { return delegate.isPlainClass(); }
        @Override public boolean isInterface() { return delegate.isInterface(); }
        @Override public boolean isEnum() { return delegate.isEnum(); }
        @Override public boolean isAnnotation() { return delegate.isAnnotation(); }
        @Override public boolean isRecord() { return delegate.isRecord(); }
        @Override public boolean isAbstract() { return delegate.isAbstract(); }
        @Override public boolean isFinal() { return delegate.isFinal(); }
        @Override public int modifiers() { return delegate.modifiers(); }
        @Override public Collection<MethodInfo> constructors() { return delegate.constructors(); }
        @Override public Collection<MethodInfo> methods() { return delegate.methods(); }
        @Override public Collection<FieldInfo> fields() { return delegate.fields(); }
        @Override
        public Collection<jakarta.enterprise.lang.model.declarations.RecordComponentInfo> recordComponents() {
            return delegate.recordComponents();
        }
    }

    private static final class MutableMethodInfo extends AnnotationAwareDeclaration implements MethodInfo {
        private final MethodInfo delegate;
        private final List<ParameterInfo> parameters;

        private MutableMethodInfo(MethodInfo delegate,
                                  Collection<AnnotationInfo> annotations,
                                  List<ParameterInfo> parameters) {
            super(delegate, annotations);
            this.delegate = delegate;
            this.parameters = parameters != null ? parameters : delegate.parameters();
        }

        @Override public String name() { return delegate.name(); }
        @Override public List<ParameterInfo> parameters() { return parameters; }
        @Override public Type returnType() { return delegate.returnType(); }
        @Override public Type receiverType() { return delegate.receiverType(); }
        @Override public List<Type> throwsTypes() { return delegate.throwsTypes(); }
        @Override public List<jakarta.enterprise.lang.model.types.TypeVariable> typeParameters() { return delegate.typeParameters(); }
        @Override public boolean isConstructor() { return delegate.isConstructor(); }
        @Override public boolean isStatic() { return delegate.isStatic(); }
        @Override public boolean isAbstract() { return delegate.isAbstract(); }
        @Override public boolean isFinal() { return delegate.isFinal(); }
        @Override public int modifiers() { return delegate.modifiers(); }
        @Override public ClassInfo declaringClass() { return delegate.declaringClass(); }
    }

    private static final class MutableFieldInfo extends AnnotationAwareDeclaration implements FieldInfo {
        private final FieldInfo delegate;

        private MutableFieldInfo(FieldInfo delegate, Collection<AnnotationInfo> annotations) {
            super(delegate, annotations);
            this.delegate = delegate;
        }

        @Override public String name() { return delegate.name(); }
        @Override public Type type() { return delegate.type(); }
        @Override public boolean isStatic() { return delegate.isStatic(); }
        @Override public boolean isFinal() { return delegate.isFinal(); }
        @Override public int modifiers() { return delegate.modifiers(); }
        @Override public ClassInfo declaringClass() { return delegate.declaringClass(); }
    }

    private static final class MutableParameterInfo extends AnnotationAwareDeclaration implements ParameterInfo {
        private final ParameterInfo delegate;

        private MutableParameterInfo(ParameterInfo delegate, Collection<AnnotationInfo> annotations) {
            super(delegate, annotations);
            this.delegate = delegate;
        }

        @Override public String name() { return delegate.name(); }
        @Override public Type type() { return delegate.type(); }
        @Override public MethodInfo declaringMethod() { return delegate.declaringMethod(); }
    }

    private static final class SyntheticAnnotationInfo implements AnnotationInfo {
        private final Class<? extends Annotation> annotationType;

        private SyntheticAnnotationInfo(Class<? extends Annotation> annotationType) {
            this.annotationType = annotationType;
        }

        @Override
        public ClassInfo declaration() {
            return BceMetadata.classInfo(annotationType);
        }

        @Override
        public boolean hasMember(String name) {
            return false;
        }

        @Override
        public AnnotationMember member(String name) {
            throw new IllegalArgumentException("Synthetic annotation has no member: " + name);
        }

        @Override
        public Map<String, AnnotationMember> members() {
            return Collections.unmodifiableMap(new LinkedHashMap<>());
        }
    }
}
