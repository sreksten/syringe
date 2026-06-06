package com.threeamigos.common.util.implementations.injection.bce;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationExtractors;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates;

import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import com.threeamigos.common.util.implementations.injection.spi.SyntheticBean;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.DisposerInfo;
import jakarta.enterprise.inject.build.compatible.spi.InjectionPointInfo;
import jakarta.enterprise.inject.build.compatible.spi.ScopeInfo;
import jakarta.enterprise.inject.build.compatible.spi.StereotypeInfo;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationMember;
import jakarta.enterprise.lang.model.AnnotationTarget;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.declarations.PackageInfo;
import jakarta.enterprise.lang.model.declarations.ParameterInfo;
import jakarta.enterprise.lang.model.declarations.RecordComponentInfo;
import jakarta.enterprise.lang.model.types.ArrayType;
import jakarta.enterprise.lang.model.types.ClassType;
import jakarta.enterprise.lang.model.types.ParameterizedType;
import jakarta.enterprise.lang.model.types.PrimitiveType;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.enterprise.lang.model.types.TypeVariable;
import jakarta.enterprise.lang.model.types.VoidType;
import jakarta.enterprise.lang.model.types.WildcardType;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reflection-backed language model implementations for BCE metadata.
 */
public final class BceMetadata {

    private static final Method CLASS_IS_RECORD_METHOD = findMethod(Class.class, "isRecord");
    private static final Method CLASS_GET_RECORD_COMPONENTS_METHOD = findMethod(Class.class, "getRecordComponents");

    private BceMetadata() {
    }

    public static BeanInfo beanInfo(Class<?> beanClass) {
        return new ReflectionBeanInfo(beanClass);
    }

    public static BeanInfo beanInfo(Bean<?> bean) {
        return new RuntimeBeanInfo(bean);
    }

    public static MethodInfo methodInfo(Method method) {
        return new ReflectionMethodInfo(method);
    }

    public static MethodInfo methodInfo(Constructor<?> constructor) {
        return new ReflectionMethodInfo(constructor);
    }

    public static ClassInfo classInfo(Class<?> beanClass) {
        return new ReflectionClassInfo(beanClass);
    }

    public static FieldInfo fieldInfo(Field field) {
        return new ReflectionFieldInfo(field);
    }

    public static Type type(Class<?> clazz) {
        return toType(clazz, null);
    }

    public static Type type(java.lang.reflect.Type reflectType) {
        return toType(reflectType, null);
    }

    public static AnnotationInfo annotationInfo(Annotation annotation) {
        return new ReflectionAnnotationInfo(annotation);
    }

    static Class<?> unwrapBeanClass(BeanInfo beanInfo) {
        if (beanInfo instanceof ReflectionBeanInfo) {
            return ((ReflectionBeanInfo) beanInfo).beanClass;
        }
        if (beanInfo instanceof RuntimeBeanInfo) {
            return ((RuntimeBeanInfo) beanInfo).beanClass;
        }
        return unwrapClassInfo(beanInfo.declaringClass());
    }

    static Method unwrapMethod(MethodInfo methodInfo) {
        if (methodInfo instanceof ReflectionMethodInfo) {
            return ((ReflectionMethodInfo) methodInfo).method;
        }
        Class<?> declaring = unwrapClassInfo(methodInfo.declaringClass());
        List<ParameterInfo> params = methodInfo.parameters();
        Class<?>[] signature = new Class<?>[params.size()];
        for (int i = 0; i < params.size(); i++) {
            signature[i] = unwrapType(params.get(i).type());
        }
        try {
            return declaring.getDeclaredMethod(methodInfo.name(), signature);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot resolve MethodInfo for " + declaring.getName() +
                "." + methodInfo.name(), e);
        }
    }

    static Class<?>[] unwrapClassInfo(ClassInfo[] infos) {
        Class<?>[] out = new Class<?>[infos.length];
        for (int i = 0; i < infos.length; i++) {
            out[i] = infos[i] != null ? BceMetadata.unwrapClassInfo(infos[i]) : null;
        }
        return out;
    }

    static Class<?> unwrapClassInfo(ClassInfo classInfo) {
        if (classInfo instanceof ReflectionClassInfo) {
            return ((ReflectionClassInfo) classInfo).clazz;
        }
        String className = classInfo.name();
        try {
            return resolveClass(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot resolve ClassInfo to runtime class: " + className, e);
        }
    }

    private static Class<?> resolveClass(String className) throws ClassNotFoundException {
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        if (ccl != null) {
            try {
                return Class.forName(className, false, ccl);
            } catch (ClassNotFoundException ignored) {
                // Fall through to this module class loader.
            }
        }

        ClassLoader fallback = BceMetadata.class.getClassLoader();
        if (fallback != null && fallback != ccl) {
            return Class.forName(className, false, fallback);
        }
        return Class.forName(className);
    }

    static Class<?>[] unwrapTypes(Type[] types) {
        Class<?>[] out = new Class<?>[types.length];
        for (int i = 0; i < types.length; i++) {
            out[i] = types[i] != null ? BceMetadata.unwrapType(types[i]) : null;
        }
        return out;
    }

    static Class<?> unwrapType(Type type) {
        if (type instanceof ReflectionType) {
            return ((ReflectionType) type).toRawClass();
        }
        if (type.isClass()) {
            return unwrapClassInfo(type.asClass().declaration());
        }
        if (type.isArray()) {
            Class<?> component = unwrapType(type.asArray().componentType());
            return Array.newInstance(component, 0).getClass();
        }
        throw new IllegalArgumentException("Unsupported Type for runtime unwrapping: " + type.kind());
    }

    static Annotation[] unwrapAnnotationInfo(AnnotationInfo[] infos) {
        Annotation[] out = new Annotation[infos.length];
        for (int i = 0; i < infos.length; i++) {
            out[i] = infos[i] != null ? BceMetadata.unwrapAnnotationInfo(infos[i]) : null;
        }
        return out;
    }

    static Annotation unwrapAnnotationInfo(AnnotationInfo annotationInfo) {
        if (annotationInfo instanceof ReflectionAnnotationInfo) {
            return ((ReflectionAnnotationInfo) annotationInfo).annotation;
        }
        Class<?> annotationClass = unwrapClassInfo(annotationInfo.declaration());
        if (!annotationClass.isAnnotation()) {
            throw new IllegalArgumentException("AnnotationInfo declaration is not annotation type: " +
                annotationClass.getName());
        }
        @SuppressWarnings("unchecked")
        Class<? extends Annotation> annotationType = (Class<? extends Annotation>) annotationClass;
        Map<String, Object> members = new LinkedHashMap<>();
        for (Map.Entry<String, AnnotationMember> member : annotationInfo.members().entrySet()) {
            try {
                Method m = annotationType.getDeclaredMethod(member.getKey());
                members.put(member.getKey(), fromAnnotationMember(member.getValue(), m.getReturnType()));
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Unknown annotation member " + member.getKey() + " on " +
                    annotationType.getName(), e);
            }
        }
        return BceCommonFunctions.buildAnnotationProxy(annotationType, members);
    }

    static <T extends Annotation> Collection<AnnotationInfo> extractRepeatableContainedAnnotations(
            AnnotationInfo candidateContainer,
            Class<T> repeatableType) {
        if (candidateContainer == null || repeatableType == null) {
            return Collections.emptyList();
        }
        Method valueMethod;
        try {
            Class<?> containerClass = unwrapClassInfo(candidateContainer.declaration());
            valueMethod = containerClass.getDeclaredMethod("value");
        } catch (Exception e) {
            return Collections.emptyList();
        }
        if (!valueMethod.getReturnType().isArray() ||
                !repeatableType.equals(valueMethod.getReturnType().getComponentType())) {
            return Collections.emptyList();
        }
        if (!candidateContainer.hasMember("value")) {
            return Collections.emptyList();
        }
        AnnotationMember valueMember = candidateContainer.member("value");
        if (valueMember.kind() != AnnotationMember.Kind.ARRAY) {
            return Collections.emptyList();
        }
        List<AnnotationInfo> out = new ArrayList<>();
        for (AnnotationMember item : valueMember.asArray()) {
            if (item.kind() == AnnotationMember.Kind.NESTED_ANNOTATION) {
                AnnotationInfo nested = item.asNestedAnnotation();
                if (repeatableType.getName().equals(nested.declaration().name())) {
                    out.add(nested);
                }
            }
        }
        return Collections.unmodifiableList(out);
    }
    private static Object fromAnnotationMember(AnnotationMember member, Class<?> expectedType) {
        switch (member.kind()) {
            case BOOLEAN:
                return member.asBoolean();
            case BYTE:
                return member.asByte();
            case SHORT:
                return member.asShort();
            case INT:
                return member.asInt();
            case LONG:
                return member.asLong();
            case FLOAT:
                return member.asFloat();
            case DOUBLE:
                return member.asDouble();
            case CHAR:
                return member.asChar();
            case STRING:
                return member.asString();
            case ENUM:
                if (expectedType != null && expectedType.isEnum()) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Class<? extends Enum> enumType = (Class<? extends Enum>) expectedType;
                    return member.asEnum(enumType);
                }
                @SuppressWarnings({"unchecked", "rawtypes"})
                Class<? extends Enum> enumType = (Class<? extends Enum>) unwrapClassInfo(member.asEnumClass());
                return Enum.valueOf(enumType, member.asEnumConstant());
            case CLASS:
                return unwrapType(member.asType());
            case NESTED_ANNOTATION:
                return unwrapAnnotationInfo(member.asNestedAnnotation());
            case ARRAY:
                List<AnnotationMember> items = member.asArray();
                Class<?> componentType = expectedType != null && expectedType.isArray()
                    ? expectedType.getComponentType() : Object.class;
                Object array = Array.newInstance(componentType, items.size());
                for (int i = 0; i < items.size(); i++) {
                    Array.set(array, i, fromAnnotationMember(items.get(i), componentType));
                }
                return array;
            default:
                return null;
        }
    }

    private static Type toType(java.lang.reflect.Type type, AnnotatedType annotatedType) {
        if (type == null) {
            return new ReflectionVoidType();
        }
        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;
            if (clazz == void.class) {
                return new ReflectionVoidType();
            }
            if (clazz.isPrimitive()) {
                return new ReflectionPrimitiveType(clazz);
            }
            if (clazz.isArray()) {
                AnnotatedType componentAnnotated = annotatedType instanceof AnnotatedArrayType
                    ? ((AnnotatedArrayType) annotatedType).getAnnotatedGenericComponentType()
                    : null;
                return new ReflectionArrayType(toType(clazz.getComponentType(), componentAnnotated), annotatedType);
            }
            return new ReflectionClassType(clazz, annotatedType);
        }
        if (type instanceof java.lang.reflect.ParameterizedType) {
            java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) type;
            Class<?> raw = (Class<?>) pt.getRawType();
            List<Type> args = new ArrayList<>();
            for (java.lang.reflect.Type arg : pt.getActualTypeArguments()) {
                args.add(toType(arg, null));
            }
            return new ReflectionParameterizedType(new ReflectionClassType(raw, annotatedType), args, annotatedType);
        }
        if (type instanceof GenericArrayType) {
            GenericArrayType gat = (GenericArrayType) type;
            return new ReflectionArrayType(toType(gat.getGenericComponentType(), null), annotatedType);
        }
        if (type instanceof java.lang.reflect.TypeVariable<?>) {
            java.lang.reflect.TypeVariable<?> tv = (java.lang.reflect.TypeVariable<?>) type;
            List<Type> bounds = new ArrayList<>();
            for (java.lang.reflect.Type bound : tv.getBounds()) {
                bounds.add(toType(bound, null));
            }
            return new ReflectionTypeVariable(tv.getName(), bounds, annotatedType);
        }
        if (type instanceof java.lang.reflect.WildcardType) {
            java.lang.reflect.WildcardType wt = (java.lang.reflect.WildcardType) type;
            Type upper = wt.getUpperBounds().length > 0 ? toType(wt.getUpperBounds()[0], null) : null;
            Type lower = wt.getLowerBounds().length > 0 ? toType(wt.getLowerBounds()[0], null) : null;
            return new ReflectionWildcardType(upper, lower, annotatedType);
        }
        throw new IllegalArgumentException("Unsupported java.lang.reflect.Type: " + type);
    }

    private static Collection<AnnotationInfo> toAnnotationInfos(Annotation[] annotations) {
        if (annotations == null || annotations.length == 0) {
            return Collections.emptyList();
        }
        List<AnnotationInfo> out = new ArrayList<>(annotations.length);
        for (Annotation annotation : annotations) {
            out.add(new ReflectionAnnotationInfo(annotation));
        }
        return Collections.unmodifiableList(out);
    }

    private abstract static class AnnotationTargetSupport implements AnnotationTarget {
        protected abstract Collection<AnnotationInfo> directAnnotations();

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
            for (AnnotationInfo annotationInfo : directAnnotations()) {
                if (annotationInfo.declaration().name().equals(annotationType.getName())) {
                    return annotationInfo;
                }
            }
            return null;
        }

        @Override
        public <T extends Annotation> Collection<AnnotationInfo> repeatableAnnotation(Class<T> annotationType) {
            List<AnnotationInfo> result = new ArrayList<>();
            for (AnnotationInfo annotationInfo : directAnnotations()) {
                if (annotationInfo.declaration().name().equals(annotationType.getName())) {
                    result.add(annotationInfo);
                    continue;
                }
                result.addAll(extractRepeatableContainedAnnotations(annotationInfo, annotationType));
            }
            return Collections.unmodifiableList(result);
        }

        @Override
        public Collection<AnnotationInfo> annotations(Predicate<AnnotationInfo> predicate) {
            List<AnnotationInfo> result = new ArrayList<>();
            for (AnnotationInfo annotationInfo : directAnnotations()) {
                if (predicate.test(annotationInfo)) {
                    result.add(annotationInfo);
                }
            }
            return Collections.unmodifiableList(result);
        }

        @Override
        public Collection<AnnotationInfo> annotations() {
            return directAnnotations();
        }
    }

    private abstract static class ReflectionDeclaration extends AnnotationTargetSupport implements DeclarationInfo {
        @Override
        public boolean isDeclaration() {
            return true;
        }

        @Override
        public boolean isType() {
            return false;
        }

        @Override
        public DeclarationInfo asDeclaration() {
            return this;
        }

        @Override
        public Type asType() {
            throw new IllegalStateException("Declaration cannot be viewed as Type");
        }
    }

    private abstract static class ReflectionType extends AnnotationTargetSupport implements Type {
        private final AnnotatedType annotatedType;

        private ReflectionType(AnnotatedType annotatedType) {
            this.annotatedType = annotatedType;
        }

        abstract Class<?> toRawClass();

        @Override
        public boolean isDeclaration() {
            return false;
        }

        @Override
        public boolean isType() {
            return true;
        }

        @Override
        public DeclarationInfo asDeclaration() {
            throw new IllegalStateException("Type cannot be viewed as Declaration");
        }

        @Override
        public Type asType() {
            return this;
        }

        @Override
        protected Collection<AnnotationInfo> directAnnotations() {
            if (annotatedType == null) {
                return Collections.emptyList();
            }
            return toAnnotationInfos(annotatedType.getAnnotations());
        }
    }

    private static final class ReflectionBeanInfo implements BeanInfo {
        private final Class<?> beanClass;
        private final ClassInfo declaringClass;

        private ReflectionBeanInfo(Class<?> beanClass) {
            this.beanClass = Objects.requireNonNull(beanClass, "beanClass");
            this.declaringClass = new ReflectionClassInfo(beanClass);
        }

        @Override
        public ScopeInfo scope() {
            return new ReflectionScopeInfo(resolveScope(beanClass));
        }

        @Override
        public Collection<Type> types() {
            Set<Type> types = new LinkedHashSet<>();
            types.add(new ReflectionClassType(beanClass, null));
            Class<?> current = beanClass.getSuperclass();
            while (current != null && current != Object.class) {
                types.add(new ReflectionClassType(current, null));
                current = current.getSuperclass();
            }
            for (Class<?> face : beanClass.getInterfaces()) {
                types.add(new ReflectionClassType(face, null));
            }
            types.add(new ReflectionClassType(Object.class, null));
            return Collections.unmodifiableSet(types);
        }

        @Override
        public Collection<AnnotationInfo> qualifiers() {
            List<AnnotationInfo> result = new ArrayList<>();
            for (Annotation annotation : beanClass.getAnnotations()) {
                if (AnnotationPredicates.hasQualifierAnnotation(annotation.annotationType())) {
                    result.add(new ReflectionAnnotationInfo(annotation));
                }
            }
            return Collections.unmodifiableList(result);
        }

        @Override
        public ClassInfo declaringClass() {
            return declaringClass;
        }

        @Override
        public boolean isClassBean() {
            return true;
        }

        @Override
        public boolean isProducerMethod() {
            return false;
        }

        @Override
        public boolean isProducerField() {
            return false;
        }

        @Override
        public boolean isSynthetic() {
            return false;
        }

        @Override
        public MethodInfo producerMethod() {
            return null;
        }

        @Override
        public FieldInfo producerField() {
            return null;
        }

        @Override
        public boolean isAlternative() {
            return AnnotationPredicates.hasAlternativeAnnotation(beanClass);
        }

        @Override
        public Integer priority() {
            return AnnotationExtractors.getPriorityValue(beanClass);
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public DisposerInfo disposer() {
            return null;
        }

        @Override
        public Collection<StereotypeInfo> stereotypes() {
            List<StereotypeInfo> out = new ArrayList<>();
            for (Annotation annotation : beanClass.getAnnotations()) {
                if (AnnotationPredicates.hasStereotypeAnnotation(annotation.annotationType())) {
                    out.add(new ReflectionStereotypeInfo(annotation.annotationType()));
                }
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public Collection<InjectionPointInfo> injectionPoints() {
            List<InjectionPointInfo> out = new ArrayList<>();
            Class<?> current = beanClass;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (AnnotationPredicates.hasInjectAnnotation(field)) {
                        out.add(new ReflectionInjectionPointInfo(
                            toType(field.getGenericType(), field.getAnnotatedType()),
                            qualifierAnnotations(field.getAnnotations()),
                            new ReflectionFieldInfo(field)
                        ));
                    }
                }
                for (Constructor<?> constructor : current.getDeclaredConstructors()) {
                    if (AnnotationPredicates.hasInjectAnnotation(constructor)) {
                        eval(new ReflectionMethodInfo(constructor), constructor.getParameters(), out);
                    }
                }
                for (Method method : current.getDeclaredMethods()) {
                    if (AnnotationPredicates.hasInjectAnnotation(method)) {
                        eval(new ReflectionMethodInfo(method), method.getParameters(), out);
                    }
                }
                current = current.getSuperclass();
            }
            return Collections.unmodifiableList(out);
        }
    }

    private static void eval(ReflectionMethodInfo methodInfo, Parameter[] parameters, List<InjectionPointInfo> out) {
        for (Parameter parameter : parameters) {
            out.add(new ReflectionInjectionPointInfo(
                    toType(parameter.getParameterizedType(), parameter.getAnnotatedType()),
                    qualifierAnnotations(parameter.getAnnotations()),
                    new ReflectionParameterInfo(methodInfo, parameter)
            ));
        }
    }

    private static final class RuntimeBeanInfo implements BeanInfo {
        private final Bean<?> bean;
        private final Class<?> beanClass;
        private final ClassInfo declaringClass;

        private RuntimeBeanInfo(Bean<?> bean) {
            this.bean = Objects.requireNonNull(bean, "bean");
            this.beanClass = bean.getBeanClass() != null ? bean.getBeanClass() : Object.class;

            if (bean instanceof ProducerBean<?>) {
                ProducerBean<?> producerBean = (ProducerBean<?>) bean;
                Class<?> producerDeclaringClass = producerBean.getDeclaringClass();
                this.declaringClass = new ReflectionClassInfo(
                    producerDeclaringClass != null ? producerDeclaringClass : this.beanClass
                );
            } else {
                this.declaringClass = new ReflectionClassInfo(this.beanClass);
            }
        }

        @Override
        public ScopeInfo scope() {
            Class<? extends Annotation> scopeType = bean.getScope();
            if (scopeType == null) {
                scopeType = resolveScope(beanClass);
            }
            return new ReflectionScopeInfo(scopeType);
        }

        @Override
        public Collection<Type> types() {
            Set<Type> out = new LinkedHashSet<>();
            for (java.lang.reflect.Type reflectType : bean.getTypes()) {
                try {
                    out.add(toType(reflectType, null));
                } catch (RuntimeException ignored) {
                    // Keep resilient behavior for unresolved generic signatures.
                }
            }
            if (out.isEmpty()) {
                out.add(new ReflectionClassType(beanClass, null));
                out.add(new ReflectionClassType(Object.class, null));
            }
            return Collections.unmodifiableSet(out);
        }

        @Override
        public Collection<AnnotationInfo> qualifiers() {
            return qualifierAnnotations(bean.getQualifiers());
        }

        @Override
        public ClassInfo declaringClass() {
            return declaringClass;
        }

        @Override
        public boolean isClassBean() {
            return !isProducerMethod() && !isProducerField();
        }

        @Override
        public boolean isProducerMethod() {
            return bean instanceof ProducerBean<?> && ((ProducerBean<?>) bean).isMethod();
        }

        @Override
        public boolean isProducerField() {
            return bean instanceof ProducerBean<?> && ((ProducerBean<?>) bean).isField();
        }

        @Override
        public boolean isSynthetic() {
            return bean instanceof SyntheticBean<?>;
        }

        @Override
        public MethodInfo producerMethod() {
            if (!(bean instanceof ProducerBean<?>)) {
                return null;
            }
            ProducerBean<?> producerBean = (ProducerBean<?>) bean;
            Method method = producerBean.getProducerMethod();
            return method != null ? BceMetadata.methodInfo(method) : null;
        }

        @Override
        public FieldInfo producerField() {
            if (!(bean instanceof ProducerBean<?>)) {
                return null;
            }
            ProducerBean<?> producerBean = (ProducerBean<?>) bean;
            Field field = producerBean.getProducerField();
            return field != null ? BceMetadata.fieldInfo(field) : null;
        }

        @Override
        public boolean isAlternative() {
            return bean.isAlternative();
        }

        @Override
        public Integer priority() {
            if (bean instanceof SyntheticBean<?>) {
                Integer priority = ((SyntheticBean<?>) bean).getPriority();
                if (priority != null) {
                    return priority;
                }
            }
            return AnnotationExtractors.getPriorityValue(beanClass);
        }

        @Override
        public String name() {
            return bean.getName();
        }

        @Override
        public DisposerInfo disposer() {
            if (bean instanceof ProducerBean<?>) {
                return BceDisposerInfo.from((ProducerBean<?>) bean);
            }
            return null;
        }

        @Override
        public Collection<StereotypeInfo> stereotypes() {
            List<StereotypeInfo> out = new ArrayList<>();
            for (Class<? extends Annotation> stereotype : bean.getStereotypes()) {
                out.add(new ReflectionStereotypeInfo(stereotype));
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public Collection<InjectionPointInfo> injectionPoints() {
            return toInjectionPointInfos(bean.getInjectionPoints());
        }
    }

    private static Collection<AnnotationInfo> qualifierAnnotations(Annotation[] annotations) {
        if (annotations == null || annotations.length == 0) {
            return Collections.emptyList();
        }
        List<AnnotationInfo> out = new ArrayList<>();
        for (Annotation annotation : annotations) {
            if (AnnotationPredicates.hasQualifierAnnotation(annotation.annotationType())) {
                out.add(new ReflectionAnnotationInfo(annotation));
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static Collection<AnnotationInfo> qualifierAnnotations(Collection<Annotation> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return Collections.emptyList();
        }
        List<AnnotationInfo> out = new ArrayList<>(annotations.size());
        for (Annotation annotation : annotations) {
            if (annotation != null) {
                out.add(new ReflectionAnnotationInfo(annotation));
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static Collection<InjectionPointInfo> toInjectionPointInfos(Set<InjectionPoint> injectionPoints) {
        if (injectionPoints == null || injectionPoints.isEmpty()) {
            return Collections.emptyList();
        }
        List<InjectionPointInfo> out = new ArrayList<>(injectionPoints.size());
        for (InjectionPoint injectionPoint : injectionPoints) {
            if (injectionPoint == null) {
                continue;
            }
            Type modelType = toType(injectionPoint.getType(), null);
            Collection<AnnotationInfo> qualifiers = qualifierAnnotations(injectionPoint.getQualifiers());
            DeclarationInfo declaration = declarationFromInjectionPoint(injectionPoint);
            if (declaration == null) {
                continue;
            }
            out.add(new ReflectionInjectionPointInfo(modelType, qualifiers, declaration));
        }
        return Collections.unmodifiableList(out);
    }

    private static DeclarationInfo declarationFromInjectionPoint(InjectionPoint injectionPoint) {
        Member member = injectionPoint.getMember();
        if (member instanceof Field) {
            return new ReflectionFieldInfo((Field) member);
        }

        if (injectionPoint.getAnnotated() instanceof AnnotatedParameter<?>) {
            AnnotatedParameter<?> annotatedParameter = (AnnotatedParameter<?>) injectionPoint.getAnnotated();
            if (member instanceof Executable) {
                Executable executable = (Executable) member;
                int position = annotatedParameter.getPosition();
                Parameter[] parameters = executable.getParameters();
                if (position >= 0 && position < parameters.length) {
                    MethodInfo methodInfo = executable instanceof Method
                        ? BceMetadata.methodInfo((Method) executable)
                        : BceMetadata.methodInfo((Constructor<?>) executable);
                    return new ReflectionParameterInfo((ReflectionMethodInfo) methodInfo, parameters[position]);
                }
            }
        }

        return null;
    }

    private static Class<? extends Annotation> resolveScope(Class<?> beanClass) {
        for (Annotation annotation : beanClass.getAnnotations()) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (AnnotationPredicates.hasNormalScopeAnnotation(type) ||
                AnnotationPredicates.hasScopeAnnotation(type)) {
                return type;
            }
        }
        return jakarta.enterprise.context.Dependent.class;
    }

    private static final class ReflectionScopeInfo implements ScopeInfo {
        private final ClassInfo annotation;

        private ReflectionScopeInfo(Class<? extends Annotation> scopeType) {
            this.annotation = new ReflectionClassInfo(scopeType);
        }

        @Override
        public ClassInfo annotation() {
            return annotation;
        }

        @Override
        public boolean isNormal() {
            Class<?> clazz = unwrapClassInfo(annotation);
            return AnnotationPredicates.hasNormalScopeAnnotation(clazz);
        }
    }

    private static final class ReflectionInjectionPointInfo implements InjectionPointInfo {
        private final Type type;
        private final Collection<AnnotationInfo> qualifiers;
        private final DeclarationInfo declaration;

        private ReflectionInjectionPointInfo(Type type,
                                             Collection<AnnotationInfo> qualifiers,
                                             DeclarationInfo declaration) {
            this.type = type;
            this.qualifiers = qualifiers != null ? qualifiers : Collections.emptyList();
            this.declaration = declaration;
        }

        @Override
        public Type type() {
            return type;
        }

        @Override
        public Collection<AnnotationInfo> qualifiers() {
            return qualifiers;
        }

        @Override
        public DeclarationInfo declaration() {
            return declaration;
        }
    }

    private static final class ReflectionStereotypeInfo implements StereotypeInfo {
        private final Class<? extends Annotation> stereotypeType;

        private ReflectionStereotypeInfo(Class<? extends Annotation> stereotypeType) {
            this.stereotypeType = stereotypeType;
        }

        @Override
        public ScopeInfo defaultScope() {
            for (Annotation annotation : stereotypeType.getAnnotations()) {
                Class<? extends Annotation> annotationType = annotation.annotationType();
                if (AnnotationPredicates.hasNormalScopeAnnotation(annotationType) ||
                    AnnotationPredicates.hasScopeAnnotation(annotationType)) {
                    return new ReflectionScopeInfo(annotationType);
                }
            }
            return null;
        }

        @Override
        public Collection<AnnotationInfo> interceptorBindings() {
            List<AnnotationInfo> out = new ArrayList<>();
            for (Annotation annotation : stereotypeType.getAnnotations()) {
                if (AnnotationPredicates.hasInterceptorBindingAnnotation(annotation.annotationType())) {
                    out.add(new ReflectionAnnotationInfo(annotation));
                }
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public boolean isAlternative() {
            return AnnotationPredicates.hasAlternativeAnnotation(stereotypeType);
        }

        @Override
        public Integer priority() {
            return AnnotationExtractors.getPriorityValue(stereotypeType);
        }

        @Override
        public boolean isNamed() {
            return AnnotationPredicates.hasNamedAnnotation(stereotypeType);
        }
    }

    private static final class ReflectionClassInfo extends ReflectionDeclaration implements ClassInfo {
        private final Class<?> clazz;
        private final Collection<AnnotationInfo> annotations;

        private ReflectionClassInfo(Class<?> clazz) {
            this(clazz, true);
        }

        private ReflectionClassInfo(Class<?> clazz, boolean includeAnnotations) {
            this.clazz = Objects.requireNonNull(clazz, "clazz");
            this.annotations = includeAnnotations ? toAnnotationInfos(clazz.getAnnotations()) : Collections.emptyList();
        }

        @Override
        public String name() {
            return clazz.getName();
        }

        @Override
        public String simpleName() {
            return clazz.getSimpleName();
        }

        @Override
        public PackageInfo packageInfo() {
            Package pkg = clazz.getPackage();
            return pkg != null ? new ReflectionPackageInfo(pkg) : null;
        }

        @Override
        public List<TypeVariable> typeParameters() {
            List<TypeVariable> out = new ArrayList<>();
            for (java.lang.reflect.TypeVariable<?> tv : clazz.getTypeParameters()) {
                out.add((TypeVariable) toType(tv, null));
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public Type superClass() {
            Class<?> superclass = clazz.getSuperclass();
            return superclass != null ? new ReflectionClassType(superclass, null) : null;
        }

        @Override
        public ClassInfo superClassDeclaration() {
            Class<?> superclass = clazz.getSuperclass();
            return superclass != null ? new ReflectionClassInfo(superclass) : null;
        }

        @Override
        public List<Type> superInterfaces() {
            List<Type> out = new ArrayList<>();
            for (Class<?> face : clazz.getInterfaces()) {
                out.add(new ReflectionClassType(face, null));
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public List<ClassInfo> superInterfacesDeclarations() {
            List<ClassInfo> out = new ArrayList<>();
            for (Class<?> face : clazz.getInterfaces()) {
                out.add(new ReflectionClassInfo(face));
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public boolean isPlainClass() {
            return !clazz.isInterface() && !clazz.isEnum() && !clazz.isAnnotation() && !isRecordType(clazz);
        }

        @Override
        public boolean isInterface() {
            return clazz.isInterface();
        }

        @Override
        public boolean isEnum() {
            return clazz.isEnum();
        }

        @Override
        public boolean isAnnotation() {
            return clazz.isAnnotation();
        }

        @Override
        public boolean isRecord() {
            return isRecordType(clazz);
        }

        @Override
        public boolean isAbstract() {
            return Modifier.isAbstract(clazz.getModifiers());
        }

        @Override
        public boolean isFinal() {
            return Modifier.isFinal(clazz.getModifiers());
        }

        @Override
        public int modifiers() {
            return clazz.getModifiers();
        }

        @Override
        public Collection<MethodInfo> constructors() {
            List<MethodInfo> out = new ArrayList<>();
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                out.add(new ReflectionMethodInfo(constructor));
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public Collection<MethodInfo> methods() {
            List<MethodInfo> out = new ArrayList<>();
            for (Method method : declaredMethodsInTypeClosure(clazz)) {
                out.add(new ReflectionMethodInfo(method));
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public Collection<FieldInfo> fields() {
            List<FieldInfo> out = new ArrayList<>();
            for (Field field : declaredFieldsInTypeClosure(clazz)) {
                out.add(new ReflectionFieldInfo(field));
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public Collection<RecordComponentInfo> recordComponents() {
            if (!isRecordType(clazz)) {
                return Collections.emptyList();
            }
            List<RecordComponentInfo> out = new ArrayList<>();
            for (Object component : getRecordComponents(clazz)) {
                out.add(new ReflectionRecordComponentInfo(component, this));
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public Kind kind() {
            return Kind.CLASS;
        }

        @Override
        protected Collection<AnnotationInfo> directAnnotations() {
            return annotations;
        }

        @Override
        public int hashCode() {
            return clazz.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ReflectionClassInfo && clazz.equals(((ReflectionClassInfo) other).clazz);
        }

        private static Collection<Method> declaredMethodsInTypeClosure(Class<?> root) {
            List<Method> methods = new ArrayList<>();
            if (root == null) {
                return methods;
            }

            Class<?> current = root;
            while (current != null) {
                if (current == Object.class && root != Object.class) {
                    break;
                }
                Collections.addAll(methods, current.getDeclaredMethods());
                current = current.getSuperclass();
            }

            collectInterfaceMethods(root, methods, new LinkedHashSet<>());
            return methods;
        }

        private static void collectInterfaceMethods(Class<?> type,
                                                    List<Method> sink,
                                                    Set<Class<?>> visitedInterfaces) {
            if (type == null) {
                return;
            }

            Class<?>[] interfaces = type.getInterfaces();
            for (Class<?> interfaceType : interfaces) {
                if (interfaceType == null || !visitedInterfaces.add(interfaceType)) {
                    continue;
                }
                Collections.addAll(sink, interfaceType.getDeclaredMethods());
                collectInterfaceMethods(interfaceType, sink, visitedInterfaces);
            }

            if (!type.isInterface()) {
                collectInterfaceMethods(type.getSuperclass(), sink, visitedInterfaces);
            }
        }

        private static Collection<Field> declaredFieldsInTypeClosure(Class<?> root) {
            List<Field> fields = new ArrayList<>();
            if (root == null) {
                return fields;
            }

            Class<?> current = root;
            while (current != null) {
                if (current == Object.class && root != Object.class) {
                    break;
                }
                Collections.addAll(fields, current.getDeclaredFields());
                current = current.getSuperclass();
            }

            collectInterfaceFields(root, fields, new LinkedHashSet<>());
            return fields;
        }

        private static void collectInterfaceFields(Class<?> type,
                                                   List<Field> sink,
                                                   Set<Class<?>> visitedInterfaces) {
            if (type == null) {
                return;
            }

            Class<?>[] interfaces = type.getInterfaces();
            for (Class<?> interfaceType : interfaces) {
                if (interfaceType == null || !visitedInterfaces.add(interfaceType)) {
                    continue;
                }
                Collections.addAll(sink, interfaceType.getDeclaredFields());
                collectInterfaceFields(interfaceType, sink, visitedInterfaces);
            }

            if (!type.isInterface()) {
                collectInterfaceFields(type.getSuperclass(), sink, visitedInterfaces);
            }
        }
    }

    private static final class ReflectionMethodInfo extends ReflectionDeclaration implements MethodInfo {
        private final Method method;
        private final Constructor<?> constructor;
        private final Executable executable;
        private final ClassInfo declaringClass;
        private final Collection<AnnotationInfo> annotations;

        private ReflectionMethodInfo(Method method) {
            this.method = Objects.requireNonNull(method, "method");
            this.constructor = null;
            this.executable = method;
            this.declaringClass = new ReflectionClassInfo(method.getDeclaringClass());
            this.annotations = toAnnotationInfos(method.getAnnotations());
        }

        private ReflectionMethodInfo(Constructor<?> constructor) {
            this.method = null;
            this.constructor = Objects.requireNonNull(constructor, "constructor");
            this.executable = constructor;
            this.declaringClass = new ReflectionClassInfo(constructor.getDeclaringClass());
            this.annotations = toAnnotationInfos(constructor.getAnnotations());
        }

        @Override
        public String name() {
            return method != null ? method.getName() : constructor.getName();
        }

        @Override
        public List<ParameterInfo> parameters() {
            Parameter[] params = executable.getParameters();
            List<ParameterInfo> out = new ArrayList<>(params.length);
            for (Parameter p : params) {
                out.add(new ReflectionParameterInfo(this, p));
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public Type returnType() {
            if (method == null) {
                return new ReflectionVoidType();
            }
            return toType(method.getGenericReturnType(), method.getAnnotatedReturnType());
        }

        @Override
        public Type receiverType() {
            AnnotatedType at = executable.getAnnotatedReceiverType();
            if (at == null) {
                return new ReflectionClassType(unwrapClassInfo(declaringClass), null);
            }
            return toType(at.getType(), at);
        }

        @Override
        public List<Type> throwsTypes() {
            List<Type> out = new ArrayList<>();
            java.lang.reflect.Type[] genericExceptionTypes = executable.getGenericExceptionTypes();
            for (java.lang.reflect.Type exceptionType : genericExceptionTypes) {
                out.add(toType(exceptionType, null));
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public List<TypeVariable> typeParameters() {
            List<TypeVariable> out = new ArrayList<>();
            for (java.lang.reflect.TypeVariable<?> tv : executable.getTypeParameters()) {
                out.add((TypeVariable) toType(tv, null));
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public boolean isConstructor() {
            return constructor != null;
        }

        @Override
        public boolean isStatic() {
            return Modifier.isStatic(executable.getModifiers());
        }

        @Override
        public boolean isAbstract() {
            return Modifier.isAbstract(executable.getModifiers());
        }

        @Override
        public boolean isFinal() {
            return Modifier.isFinal(executable.getModifiers());
        }

        @Override
        public int modifiers() {
            return executable.getModifiers();
        }

        @Override
        public ClassInfo declaringClass() {
            return declaringClass;
        }

        @Override
        public Kind kind() {
            return Kind.METHOD;
        }

        @Override
        protected Collection<AnnotationInfo> directAnnotations() {
            return annotations;
        }

        @Override
        public int hashCode() {
            return executable.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ReflectionMethodInfo &&
                executable.equals(((ReflectionMethodInfo) other).executable);
        }
    }

    private static final class ReflectionParameterInfo extends ReflectionDeclaration implements ParameterInfo {
        private final ReflectionMethodInfo declaringMethod;
        private final Parameter parameter;
        private final Collection<AnnotationInfo> annotations;

        private ReflectionParameterInfo(ReflectionMethodInfo declaringMethod, Parameter parameter) {
            this.declaringMethod = declaringMethod;
            this.parameter = parameter;
            this.annotations = toAnnotationInfos(parameter.getAnnotations());
        }

        @Override
        public String name() {
            return parameter.getName();
        }

        @Override
        public Type type() {
            return toType(parameter.getParameterizedType(), parameter.getAnnotatedType());
        }

        @Override
        public MethodInfo declaringMethod() {
            return declaringMethod;
        }

        @Override
        public Kind kind() {
            return Kind.PARAMETER;
        }

        @Override
        protected Collection<AnnotationInfo> directAnnotations() {
            return annotations;
        }
    }

    private static final class ReflectionFieldInfo extends ReflectionDeclaration implements FieldInfo {
        private final Field field;
        private final Collection<AnnotationInfo> annotations;

        private ReflectionFieldInfo(Field field) {
            this.field = field;
            this.annotations = toAnnotationInfos(field.getAnnotations());
        }

        @Override
        public String name() {
            return field.getName();
        }

        @Override
        public Type type() {
            return toType(field.getGenericType(), field.getAnnotatedType());
        }

        @Override
        public boolean isStatic() {
            return Modifier.isStatic(field.getModifiers());
        }

        @Override
        public boolean isFinal() {
            return Modifier.isFinal(field.getModifiers());
        }

        @Override
        public int modifiers() {
            return field.getModifiers();
        }

        @Override
        public ClassInfo declaringClass() {
            return new ReflectionClassInfo(field.getDeclaringClass());
        }

        @Override
        public Kind kind() {
            return Kind.FIELD;
        }

        @Override
        protected Collection<AnnotationInfo> directAnnotations() {
            return annotations;
        }
    }

    private static final class ReflectionRecordComponentInfo extends ReflectionDeclaration implements RecordComponentInfo {
        private final Object component;
        private final ClassInfo declaringRecord;

        private ReflectionRecordComponentInfo(Object component, ClassInfo declaringRecord) {
            this.component = component;
            this.declaringRecord = declaringRecord;
        }

        @Override
        public String name() {
            return (String) invokeMethod(component, "getName");
        }

        @Override
        public Type type() {
            java.lang.reflect.Type genericType = (java.lang.reflect.Type) invokeMethod(component, "getGenericType");
            AnnotatedType annotatedType = (AnnotatedType) invokeMethod(component, "getAnnotatedType");
            return toType(genericType, annotatedType);
        }

        @Override
        public FieldInfo field() {
            try {
                return new ReflectionFieldInfo(unwrapClassInfo(declaringRecord).getDeclaredField(name()));
            } catch (NoSuchFieldException e) {
                return null;
            }
        }

        @Override
        public MethodInfo accessor() {
            return new ReflectionMethodInfo((Method) invokeMethod(component, "getAccessor"));
        }

        @Override
        public ClassInfo declaringRecord() {
            return declaringRecord;
        }

        @Override
        public Kind kind() {
            return Kind.RECORD_COMPONENT;
        }

        @Override
        protected Collection<AnnotationInfo> directAnnotations() {
            return toAnnotationInfos((Annotation[]) invokeMethod(component, "getAnnotations"));
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static boolean isRecordType(Class<?> clazz) {
        if (CLASS_IS_RECORD_METHOD == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(CLASS_IS_RECORD_METHOD.invoke(clazz));
        } catch (IllegalAccessException | InvocationTargetException e) {
            return false;
        }
    }

    private static Collection<Object> getRecordComponents(Class<?> clazz) {
        if (CLASS_GET_RECORD_COMPONENTS_METHOD == null) {
            return Collections.emptyList();
        }
        try {
            Object result = CLASS_GET_RECORD_COMPONENTS_METHOD.invoke(clazz);
            if (!(result instanceof Object[])) {
                return Collections.emptyList();
            }
            Object[] array = (Object[]) result;
            List<Object> out = new ArrayList<>(array.length);
            Collections.addAll(out, array);
            return out;
        } catch (IllegalAccessException | InvocationTargetException e) {
            return Collections.emptyList();
        }
    }

    private static Object invokeMethod(Object target, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Method not available: " + methodName + " on " + target.getClass().getName(), e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Method not accessible: " + methodName + " on " + target.getClass().getName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("Invocation failed: " + methodName + " on " + target.getClass().getName(), cause);
        }
    }

    private static final class ReflectionPackageInfo extends ReflectionDeclaration implements PackageInfo {
        private final Package pkg;

        private ReflectionPackageInfo(Package pkg) {
            this.pkg = pkg;
        }

        @Override
        public String name() {
            return pkg.getName();
        }

        @Override
        public Kind kind() {
            return Kind.PACKAGE;
        }

        @Override
        protected Collection<AnnotationInfo> directAnnotations() {
            return toAnnotationInfos(pkg.getAnnotations());
        }
    }

    private static final class ReflectionAnnotationInfo implements AnnotationInfo {
        private final Annotation annotation;
        private final ClassInfo declaration;
        private final Map<String, AnnotationMember> members;

        private ReflectionAnnotationInfo(Annotation annotation) {
            this.annotation = Objects.requireNonNull(annotation, "annotation");
            // Avoid infinite recursion through annotation type meta-annotations.
            this.declaration = new ReflectionClassInfo(annotation.annotationType(), false);
            this.members = buildMembers(annotation);
        }

        @Override
        public ClassInfo declaration() {
            return declaration;
        }

        @Override
        public boolean hasMember(String name) {
            return members.containsKey(name);
        }

        @Override
        public AnnotationMember member(String name) {
            if (!members.containsKey(name)) {
                throw new IllegalArgumentException("No member '" + name + "' in annotation " + declaration.name());
            }
            return members.get(name);
        }

        @Override
        public Map<String, AnnotationMember> members() {
            return members;
        }

        private static Map<String, AnnotationMember> buildMembers(Annotation annotation) {
            Map<String, AnnotationMember> result = new LinkedHashMap<>();
            for (Method method : annotation.annotationType().getDeclaredMethods()) {
                try {
                    Object value = method.invoke(annotation);
                    result.put(method.getName(), new ReflectionAnnotationMember(value, method.getReturnType()));
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot read annotation member '" + method.getName() +
                        "' on " + annotation.annotationType().getName(), e);
                }
            }
            return Collections.unmodifiableMap(result);
        }
    }

    private static final class ReflectionAnnotationMember implements AnnotationMember {
        private final Object value;
        private final Class<?> declaredType;

        private ReflectionAnnotationMember(Object value, Class<?> declaredType) {
            this.value = value;
            this.declaredType = declaredType;
        }

        @Override
        public Kind kind() {
            Class<?> type = declaredType != null ? declaredType : (value != null ? value.getClass() : Object.class);
            if (type.isArray()) return Kind.ARRAY;
            if (type == boolean.class || type == Boolean.class) return Kind.BOOLEAN;
            if (type == byte.class || type == Byte.class) return Kind.BYTE;
            if (type == short.class || type == Short.class) return Kind.SHORT;
            if (type == int.class || type == Integer.class) return Kind.INT;
            if (type == long.class || type == Long.class) return Kind.LONG;
            if (type == float.class || type == Float.class) return Kind.FLOAT;
            if (type == double.class || type == Double.class) return Kind.DOUBLE;
            if (type == char.class || type == Character.class) return Kind.CHAR;
            if (type == String.class) return Kind.STRING;
            if (type.isEnum()) return Kind.ENUM;
            if (Annotation.class.isAssignableFrom(type)) return Kind.NESTED_ANNOTATION;
            if (Class.class.isAssignableFrom(type)) return Kind.CLASS;
            return Kind.STRING;
        }

        @Override
        public boolean asBoolean() {
            return (Boolean) value;
        }

        @Override
        public byte asByte() {
            return (Byte) value;
        }

        @Override
        public short asShort() {
            return (Short) value;
        }

        @Override
        public int asInt() {
            return (Integer) value;
        }

        @Override
        public long asLong() {
            return (Long) value;
        }

        @Override
        public float asFloat() {
            return (Float) value;
        }

        @Override
        public double asDouble() {
            return (Double) value;
        }

        @Override
        public char asChar() {
            return (Character) value;
        }

        @Override
        public String asString() {
            return (String) value;
        }

        @Override
        public <E extends Enum<E>> E asEnum(Class<E> enumClass) {
            return enumClass.cast(value);
        }

        @Override
        public ClassInfo asEnumClass() {
            return new ReflectionClassInfo(((Enum<?>) value).getDeclaringClass());
        }

        @Override
        public String asEnumConstant() {
            return ((Enum<?>) value).name();
        }

        @Override
        public Type asType() {
            return value instanceof Class<?> ? new ReflectionClassType((Class<?>) value, null) : null;
        }

        @Override
        public AnnotationInfo asNestedAnnotation() {
            return new ReflectionAnnotationInfo((Annotation) value);
        }

        @Override
        public List<AnnotationMember> asArray() {
            if (value == null || !value.getClass().isArray()) {
                return Collections.emptyList();
            }
            int length = Array.getLength(value);
            List<AnnotationMember> out = new ArrayList<>(length);
            Class<?> componentType = value.getClass().getComponentType();
            for (int i = 0; i < length; i++) {
                out.add(new ReflectionAnnotationMember(Array.get(value, i), componentType));
            }
            return Collections.unmodifiableList(out);
        }
    }

    private static final class ReflectionVoidType extends ReflectionType implements VoidType {
        private ReflectionVoidType() {
            super(null);
        }

        @Override
        Class<?> toRawClass() {
            return void.class;
        }

        @Override
        public Kind kind() {
            return Kind.VOID;
        }

        @Override
        public String name() {
            return "void";
        }
    }

    private static final class ReflectionPrimitiveType extends ReflectionType implements PrimitiveType {
        private final Class<?> primitive;

        private ReflectionPrimitiveType(Class<?> primitive) {
            super(null);
            this.primitive = primitive;
        }

        @Override
        Class<?> toRawClass() {
            return primitive;
        }

        @Override
        public Kind kind() {
            return Kind.PRIMITIVE;
        }

        @Override
        public String name() {
            return primitive.getName();
        }

        @Override
        public PrimitiveKind primitiveKind() {
            String n = primitive.getName();
            switch (n) {
                case "boolean":
                    return PrimitiveKind.BOOLEAN;
                case "byte":
                    return PrimitiveKind.BYTE;
                case "short":
                    return PrimitiveKind.SHORT;
                case "int":
                    return PrimitiveKind.INT;
                case "long":
                    return PrimitiveKind.LONG;
                case "float":
                    return PrimitiveKind.FLOAT;
                case "double":
                    return PrimitiveKind.DOUBLE;
            }
            return PrimitiveKind.CHAR;
        }
    }

    private static final class ReflectionClassType extends ReflectionType implements ClassType {
        private final Class<?> clazz;
        private final ClassInfo declaration;

        private ReflectionClassType(Class<?> clazz, AnnotatedType annotatedType) {
            super(annotatedType);
            this.clazz = clazz;
            this.declaration = new ReflectionClassInfo(clazz);
        }

        @Override
        Class<?> toRawClass() {
            return clazz;
        }

        @Override
        public Kind kind() {
            return Kind.CLASS;
        }

        @Override
        public ClassInfo declaration() {
            return declaration;
        }

        @Override
        public int hashCode() {
            return clazz.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ReflectionClassType && clazz.equals(((ReflectionClassType) other).clazz);
        }
    }

    private static final class ReflectionArrayType extends ReflectionType implements ArrayType {
        private final Type componentType;

        private ReflectionArrayType(Type componentType, AnnotatedType annotatedType) {
            super(annotatedType);
            this.componentType = componentType;
        }

        @Override
        Class<?> toRawClass() {
            return Array.newInstance(((ReflectionType) componentType).toRawClass(), 0).getClass();
        }

        @Override
        public Kind kind() {
            return Kind.ARRAY;
        }

        @Override
        public Type componentType() {
            return componentType;
        }
    }

    private static final class ReflectionParameterizedType extends ReflectionType implements ParameterizedType {
        private final ClassType genericClass;
        private final List<Type> typeArguments;

        private ReflectionParameterizedType(ClassType genericClass, List<Type> typeArguments, AnnotatedType annotatedType) {
            super(annotatedType);
            this.genericClass = genericClass;
            this.typeArguments = Collections.unmodifiableList(new ArrayList<>(typeArguments));
        }

        @Override
        Class<?> toRawClass() {
            return unwrapClassInfo(genericClass.declaration());
        }

        @Override
        public Kind kind() {
            return Kind.PARAMETERIZED_TYPE;
        }

        @Override
        public ClassType genericClass() {
            return genericClass;
        }

        @Override
        public List<Type> typeArguments() {
            return typeArguments;
        }
    }

    private static final class ReflectionTypeVariable extends ReflectionType implements TypeVariable {
        private final String name;
        private final List<Type> bounds;

        private ReflectionTypeVariable(String name, List<Type> bounds, AnnotatedType annotatedType) {
            super(annotatedType);
            this.name = name;
            this.bounds = Collections.unmodifiableList(new ArrayList<>(bounds));
        }

        @Override
        Class<?> toRawClass() {
            if (!bounds.isEmpty() && bounds.get(0) instanceof ReflectionType) {
                return ((ReflectionType) bounds.get(0)).toRawClass();
            }
            return Object.class;
        }

        @Override
        public Kind kind() {
            return Kind.TYPE_VARIABLE;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<Type> bounds() {
            return bounds;
        }
    }

    private static final class ReflectionWildcardType extends ReflectionType implements WildcardType {
        private final Type upperBound;
        private final Type lowerBound;

        private ReflectionWildcardType(Type upperBound, Type lowerBound, AnnotatedType annotatedType) {
            super(annotatedType);
            this.upperBound = upperBound;
            this.lowerBound = lowerBound;
        }

        @Override
        Class<?> toRawClass() {
            if (upperBound instanceof ReflectionType) {
                return ((ReflectionType) upperBound).toRawClass();
            }
            return Object.class;
        }

        @Override
        public Kind kind() {
            return Kind.WILDCARD_TYPE;
        }

        @Override
        public Type upperBound() {
            return upperBound;
        }

        @Override
        public Type lowerBound() {
            return lowerBound;
        }
    }
}
