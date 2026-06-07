package com.threeamigos.common.util.implementations.injection.annotations;

import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.types.TypeClosureHelper;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.inject.spi.AnnotatedType;

import java.lang.annotation.Annotation;
import java.lang.annotation.Target;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum.PRIORITY;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum.WITH_ANNOTATIONS;

public class AnnotationsHelper {

    private static final String JAKARTA_STARTUP_EVENT_TYPE_NAME = "jakarta.enterprise.event.Startup";
    private static final String JAVAX_STARTUP_EVENT_TYPE_NAME = "javax.enterprise.event.Startup";
    private static final String JAKARTA_SHUTDOWN_EVENT_TYPE_NAME = "jakarta.enterprise.event.Shutdown";
    private static final String JAVAX_SHUTDOWN_EVENT_TYPE_NAME = "javax.enterprise.event.Shutdown";

    private AnnotationsHelper() {
    }

    @Nonnull
    public static String toList(Collection<Annotation> annotationDef) {
        String metaAnnotationList;
        if (annotationDef != null && !annotationDef.isEmpty()) {
            metaAnnotationList = toList(annotationDef.stream());
        } else {
            metaAnnotationList = "[]";
        }
        return metaAnnotationList;
    }

    @Nonnull
    public static String toList(Annotation[] annotationDef) {
        String metaAnnotationList;
        if (annotationDef != null && annotationDef.length > 0) {
            metaAnnotationList = toList(Stream.of(annotationDef));
        } else {
            metaAnnotationList = "[]";
        }
        return metaAnnotationList;
    }

    private static String toList(Stream<Annotation> annotationDef) {
        return "[" +
                annotationDef
                        .map(def -> "@" + def.annotationType().getSimpleName())
                        .collect(Collectors.joining(", "))
                 + "]";
    }

    public static boolean hasAnnotation(AnnotatedElement element, AnnotationsEnum annotation) {
        if (element == null || annotation == null) {
            return false;
        }
        return hasAnnotation(element.getAnnotations(), annotation);
    }

    public static boolean hasAnnotation(Annotation[] annotations, AnnotationsEnum annotation) {
        if (annotations == null) {
            return false;
        }
        return hasAnnotation(Arrays.asList(annotations), annotation);
    }

    public static boolean hasAnnotation(Iterable<? extends Annotation> annotations, AnnotationsEnum annotation) {
        if (annotations == null || annotation == null) {
            return false;
        }
        for (Annotation candidate : annotations) {
            if (candidate != null && annotation.matches(candidate.annotationType())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAroundInvokeAnnotation(Iterable<? extends Annotation> annotations) {
        return hasAnnotation(annotations, AnnotationsEnum.AROUND_INVOKE);
    }

    public static boolean hasAroundInvokeAnnotation(Method method, AnnotatedType<?> override) {
        if (method == null) {
            return false;
        }
        Annotation[] annotations = override != null
                ? AnnotationsHelper.annotationsOf(override, method)
                : method.getAnnotations();
        return hasAnnotation(annotations, AnnotationsEnum.AROUND_INVOKE);
    }

    public static boolean hasExcludeClassInterceptorsAnnotation(Annotation[] annotations) {
        return hasAnnotation(annotations, AnnotationsEnum.EXCLUDE_CLASS_INTERCEPTORS);
    }

    public static boolean isInterceptorBindingMetaAnnotation(Class<? extends Annotation> annotationType) {
        return annotationType != null && AnnotationsEnum.INTERCEPTOR_BINDING.matches(annotationType);
    }

    public static boolean hasAnyRequiredAnnotation(AnnotatedElement element,
                                                   Class<? extends Annotation>[] requiredAnnotations) {
        if (element == null || requiredAnnotations == null) {
            return false;
        }
        for (Class<? extends Annotation> annotation : requiredAnnotations) {
            if (annotation != null && element.isAnnotationPresent(annotation)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasRequiredEnhancementAnnotation(AnnotatedElement element,
                                                           Class<? extends Annotation>[] requiredAnnotations) {
        if (element == null) {
            return false;
        }

        if (hasAnyRequiredAnnotation(element, requiredAnnotations)) {
            return true;
        }

        if (!(element instanceof Class<?>)) {
            return false;
        }

        Class<?> clazz = (Class<?>) element;
        for (Field field : clazz.getDeclaredFields()) {
            if (hasAnyRequiredAnnotation(field, requiredAnnotations)) {
                return true;
            }
        }

        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (hasAnyRequiredAnnotation(constructor, requiredAnnotations) ||
                    parametersHaveAnyRequiredAnnotation(constructor.getParameters(), requiredAnnotations)) {
                return true;
            }
        }

        for (Method method : clazz.getDeclaredMethods()) {
            if (hasAnyRequiredAnnotation(method, requiredAnnotations) ||
                    parametersHaveAnyRequiredAnnotation(method.getParameters(), requiredAnnotations)) {
                return true;
            }
        }

        return false;
    }

    public static boolean parametersHaveAnyRequiredAnnotation(Parameter[] parameters,
                                                              Class<? extends Annotation>[] requiredAnnotations) {
        if (parameters == null || requiredAnnotations == null) {
            return false;
        }
        for (Parameter parameter : parameters) {
            if (hasAnyRequiredAnnotation(parameter, requiredAnnotations)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasInjectAnnotation(Collection<? extends Annotation> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return false;
        }
        for (Annotation annotation : annotations) {
            if (annotation != null && AnnotationsHelper.hasInjectAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isScopeOrNormalScopeAnnotation(Class<? extends Annotation> annotationType) {
        return AnnotationsHelper.hasScopeAnnotation(annotationType)
                || AnnotationsHelper.hasNormalScopeAnnotation(annotationType);
    }

    public static boolean isCdiInheritableTypeAnnotation(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            return false;
        }
        if (isScopeOrNormalScopeAnnotation(annotationType)) {
            return true;
        }
        return AnnotationsHelper.hasQualifierAnnotation(annotationType)
                || AnnotationsHelper.hasStereotypeAnnotation(annotationType)
                || AnnotationsHelper.hasInterceptorBindingAnnotation(annotationType);
    }

    public static String readNamedValue(Annotation namedAnnotation) {
        try {
            Method value = namedAnnotation.annotationType().getMethod("value");
            Object raw = value.invoke(namedAnnotation);
            return raw == null ? "" : raw.toString();
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    public static Annotation findNamedQualifier(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (hasNamedAnnotation(annotation.annotationType())) {
                return annotation;
            }
        }
        return null;
    }

    public static Annotation[] annotationsOf(AnnotatedType<?> annotatedType, AnnotatedElement element) {
        Annotated annotated = annotatedOf(annotatedType, element);
        if (annotated != null) {
            return annotated.getAnnotations().toArray(new Annotation[0]);
        }
        if (annotatedType != null && isMemberElement(element)) {
            // ProcessAnnotatedType#setAnnotatedType may replace the member set entirely.
            // If a reflection member is absent from the replacement AnnotatedType, treat it
            // as non-existent for metadata purposes.
            return new Annotation[0];
        }
        return element.getAnnotations();
    }

    public static Annotated annotatedOf(AnnotatedType<?> annotatedType, AnnotatedElement element) {
        if (annotatedType == null || element == null) {
            return null;
        }
        if (element instanceof Field) {
            return findAnnotatedField(annotatedType, (Field) element);
        }
        if (element instanceof Method) {
            return findAnnotatedMethod(annotatedType, (Method) element);
        }
        if (element instanceof Constructor) {
            return findAnnotatedConstructor(annotatedType, (Constructor<?>) element);
        }
        if (element instanceof Parameter) {
            return findAnnotatedParameter(annotatedType, (Parameter) element);
        }
        return null;
    }

    public static Type baseTypeOf(AnnotatedType<?> annotatedType, Field field) {
        AnnotatedField<?> annotatedField = findAnnotatedField(annotatedType, field);
        return annotatedField != null ? annotatedField.getBaseType() : field.getGenericType();
    }

    public static Type baseTypeOf(AnnotatedType<?> annotatedType, Method method) {
        AnnotatedMethod<?> annotatedMethod = findAnnotatedMethod(annotatedType, method);
        return annotatedMethod != null ? annotatedMethod.getBaseType() : method.getGenericReturnType();
    }

    public static Type baseTypeOf(AnnotatedType<?> annotatedType, Parameter parameter) {
        AnnotatedParameter<?> annotatedParameter = findAnnotatedParameter(annotatedType, parameter);
        return annotatedParameter != null ? annotatedParameter.getBaseType() : parameter.getParameterizedType();
    }

    public static Set<Type> typeClosureOf(AnnotatedType<?> annotatedType, Field field) {
        AnnotatedField<?> annotatedField = findAnnotatedField(annotatedType, field);
        if (annotatedField != null && annotatedField.getTypeClosure() != null && !annotatedField.getTypeClosure().isEmpty()) {
            return new LinkedHashSet<>(annotatedField.getTypeClosure());
        }
        return TypeClosureHelper.extractTypesFromType(field.getGenericType());
    }

    public static Set<Type> typeClosureOf(AnnotatedType<?> annotatedType, Method method) {
        AnnotatedMethod<?> annotatedMethod = findAnnotatedMethod(annotatedType, method);
        if (annotatedMethod != null && annotatedMethod.getTypeClosure() != null && !annotatedMethod.getTypeClosure().isEmpty()) {
            return new LinkedHashSet<>(annotatedMethod.getTypeClosure());
        }
        return TypeClosureHelper.extractTypesFromType(method.getGenericReturnType());
    }

    public static AnnotatedField<?> findAnnotatedField(AnnotatedType<?> annotatedType, Field field) {
        if (annotatedType == null || field == null) {
            return null;
        }
        for (AnnotatedField<?> annotatedField : annotatedType.getFields()) {
            if (matchesMember(annotatedField.getJavaMember(), field)) {
                return annotatedField;
            }
        }
        return null;
    }

    public static AnnotatedMethod<?> findAnnotatedMethod(AnnotatedType<?> annotatedType, Method method) {
        if (annotatedType == null || method == null) {
            return null;
        }
        for (AnnotatedMethod<?> annotatedMethod : annotatedType.getMethods()) {
            if (matchesMember(annotatedMethod.getJavaMember(), method)) {
                return annotatedMethod;
            }
        }
        return null;
    }

    public static AnnotatedConstructor<?> findAnnotatedConstructor(AnnotatedType<?> annotatedType, Constructor<?> constructor) {
        if (annotatedType == null || constructor == null) {
            return null;
        }
        for (AnnotatedConstructor<?> annotatedConstructor : annotatedType.getConstructors()) {
            if (matchesMember(annotatedConstructor.getJavaMember(), constructor)) {
                return annotatedConstructor;
            }
        }
        return null;
    }

    public static AnnotatedParameter<?> findAnnotatedParameter(AnnotatedType<?> annotatedType, Parameter parameter) {
        if (annotatedType == null || parameter == null) {
            return null;
        }
        Executable declaringExecutable = parameter.getDeclaringExecutable();
        int position = parameterPosition(parameter);
        if (position < 0) {
            return null;
        }

        if (declaringExecutable instanceof Method) {
            AnnotatedMethod<?> annotatedMethod = findAnnotatedMethod(annotatedType, (Method) declaringExecutable);
            return parameterAt(annotatedMethod, position);
        }
        if (declaringExecutable instanceof Constructor<?>) {
            AnnotatedConstructor<?> annotatedConstructor =
                    findAnnotatedConstructor(annotatedType, (Constructor<?>) declaringExecutable);
            return parameterAt(annotatedConstructor, position);
        }
        return null;
    }

    public static AnnotatedParameter<?> findAnnotatedParameter(AnnotatedMethod<?> annotatedMethod, int position) {
        if (annotatedMethod == null) {
            return null;
        }
        for (AnnotatedParameter<?> parameter : annotatedMethod.getParameters()) {
            if (parameter.getPosition() == position) {
                return parameter;
            }
        }
        return null;
    }

    private static AnnotatedParameter<?> parameterAt(AnnotatedCallable<?> callable, int position) {
        if (callable == null) {
            return null;
        }
        for (AnnotatedParameter<?> parameter : callable.getParameters()) {
            if (parameter.getPosition() == position) {
                return parameter;
            }
        }
        return null;
    }

    private static int parameterPosition(Parameter parameter) {
        Parameter[] parameters = parameter.getDeclaringExecutable().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].equals(parameter)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesMember(Member lhs, Member rhs) {
        return lhs != null && lhs.equals(rhs);
    }

    private static boolean isMemberElement(AnnotatedElement element) {
        return element instanceof Field
                || element instanceof Method
                || element instanceof Constructor
                || element instanceof Parameter;
    }

    public static jakarta.enterprise.event.Observes getObservesAnnotationFrom(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof jakarta.enterprise.event.Observes) {
                return (jakarta.enterprise.event.Observes) annotation;
            }
        }
        return null;
    }

    public static boolean hasObservesAsyncAnnotationIn(Annotation[] annotations) {
        return getObservesAsyncAnnotationFrom(annotations) != null;
    }

    public static jakarta.enterprise.event.ObservesAsync getObservesAsyncAnnotationFrom(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof jakarta.enterprise.event.ObservesAsync) {
                return (jakarta.enterprise.event.ObservesAsync) annotation;
            }
        }
        return null;
    }

    public static Set<Annotation> extractObserverQualifiers(Annotation[] observedParameterAnnotations) {
        if (observedParameterAnnotations == null) {
            return new HashSet<>();
        }
        return new HashSet<>(extractQualifierAnnotations(observedParameterAnnotations));
    }

    public static boolean hasObservesAnnotationIn(Annotation[] annotations) {
        return getObservesAnnotationFrom(annotations) != null;
    }

    public static Integer getPriorityValueFromAnnotations(Annotation[] annotations) {
        if (annotations == null) {
            return null;
        }
        for (Annotation annotation : annotations) {
            if (annotation == null) {
                continue;
            }
            if (PRIORITY.matches(annotation.annotationType())) {
                try {
                    Method valueMethod = annotation.annotationType().getMethod("value");
                    Object value = valueMethod.invoke(annotation);
                    if (value instanceof Integer) {
                        return (Integer) value;
                    }
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public static boolean hasDisposesAnnotationInAnnotatedParameter(AnnotatedParameter<?> parameter) {
        if (parameter == null || parameter.getAnnotations() == null) {
            return false;
        }
        for (Annotation annotation : parameter.getAnnotations()) {
            if (annotation != null && hasDisposesAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static Set<Class<? extends Annotation>> resolveWithAnnotationsFilter(Parameter parameter) {
        if (!hasWithAnnotationsAnnotation(parameter)) {
            return null;
        }
        Annotation[] annotations = parameter.getAnnotations();
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (!WITH_ANNOTATIONS.matches(annotationType)) {
                continue;
            }
            try {
                Method valueMethod = annotationType.getMethod("value");
                Object value = valueMethod.invoke(annotation);
                if (!(value instanceof Class[])) {
                    return Collections.emptySet();
                }
                Class<?>[] rawValues = (Class<?>[]) value;
                Set<Class<? extends Annotation>> filter = new LinkedHashSet<>();
                for (Class<?> rawValue : rawValues) {
                    if (rawValue != null && Annotation.class.isAssignableFrom(rawValue)) {
                        filter.add((Class<? extends Annotation>) rawValue);
                    }
                }
                return filter;
            } catch (Exception e) {
                throw new DefinitionException("Unable to read @WithAnnotations value on parameter " + parameter, e);
            }
        }
        return null;
    }

    public static boolean hasNoQualifierOrOnlyAnyQualifier(Parameter observedParameter) {
        List<Annotation> qualifierAnnotations = new ArrayList<>();
        for (Annotation annotation : observedParameter.getAnnotations()) {
            if (hasQualifierAnnotation(annotation.annotationType())) {
                qualifierAnnotations.add(annotation);
            }
        }

        if (qualifierAnnotations.isEmpty()) {
            return true;
        }

        return qualifierAnnotations.size() == 1 &&
                hasAnyAnnotation(qualifierAnnotations.get(0).annotationType());
    }

    public static Integer extractPriorityValue(Annotation annotation) {
        if (annotation == null || !PRIORITY.matches(annotation.annotationType())) {
            return null;
        }
        try {
            Object value = annotation.annotationType().getMethod("value").invoke(annotation);
            return value instanceof Integer ? (Integer) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean isStereotype(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            return false;
        }
        return hasStereotypeAnnotation(annotationType);
    }

    public static boolean shouldSkipProcessAnnotatedTypeEvent(Class<?> clazz) {
        if (clazz == null) {
            return true;
        }
        if (clazz.isAnnotation()) {
            return true;
        }
        if (hasVetoedAnnotation(clazz)) {
            return true;
        }
        return isPackageOrParentPackageVetoed(clazz.getPackage());
    }

    public static boolean isPackageOrParentPackageVetoed(Package pkg) {
        if (pkg == null) {
            return false;
        }
        if (hasVetoedAnnotation(pkg)) {
            return true;
        }
        String packageName = pkg.getName();
        while (packageName.contains(".")) {
            packageName = packageName.substring(0, packageName.lastIndexOf('.'));
            try {
                Class<?> packageInfo = Class.forName(packageName + ".package-info");
                Package parent = packageInfo.getPackage();
                if (hasVetoedAnnotation(parent)) {
                    return true;
                }
            } catch (ClassNotFoundException ignored) {
                // No package-info, continue with next parent package.
            }
        }
        return false;
    }

    public static boolean hasInjectAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.INJECT.isPresent(element);
    }

    public static boolean hasSingletonAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.SINGLETON.isPresent(element);
    }

    public static Class<? extends Annotation> normalizeSingletonToApplicationScoped(
            @Nonnull Class<? extends Annotation> scopeType) {
        if (hasSingletonAnnotation(scopeType)) {
            return jakarta.enterprise.context.ApplicationScoped.class;
        }
        return scopeType;
    }

    public static boolean hasNamedAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.NAMED.isPresent(element);
    }

    public static boolean hasQualifierAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.QUALIFIER.isPresent(element) || DynamicAnnotationRegistry.hasDynamicQualifier(element);
    }

    public static boolean hasScopeAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.SCOPE.isPresent(element) || DynamicAnnotationRegistry.hasDynamicScope(element);
    }

    public static boolean hasNonbindingAnnotation(AnnotatedElement element) {
        if (AnnotationsEnum.NONBINDING.isPresent(element)) {
            return true;
        }

        if (!(element instanceof Method)) {
            return false;
        }

        Method method = (Method) element;
        Class<?> declaringClass = method.getDeclaringClass();
        if (!Annotation.class.isAssignableFrom(declaringClass)) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Class<? extends Annotation> annotationType = (Class<? extends Annotation>) declaringClass;
        return DynamicAnnotationRegistry.hasDynamicNonbindingMember(annotationType, method.getName());
    }

    public static boolean hasPostConstructAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.POST_CONSTRUCT.isPresent(element);
    }

    public static boolean hasPreDestroyAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.PRE_DESTROY.isPresent(element);
    }

    public static boolean hasPrePassivateAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.PRE_PASSIVATE.isPresent(element);
    }

    public static boolean hasPostActivateAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.POST_ACTIVATE.isPresent(element);
    }

    public static boolean hasPriorityAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.PRIORITY.isPresent(element);
    }

    public static boolean hasAlternativeAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.ALTERNATIVE.isPresent(element);
    }

    public static boolean hasAnyAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.ANY.isPresent(element);
    }

    public static boolean hasDefaultAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.DEFAULT.isPresent(element);
    }

    public static boolean hasProducesAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.PRODUCES.isPresent(element);
    }

    public static boolean hasDisposesAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.DISPOSES.isPresent(element);
    }

    public static boolean hasVetoedAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.VETOED.isPresent(element);
    }

    public static boolean hasTypedAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.TYPED.isPresent(element);
    }

    public static boolean hasStereotypeAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.STEREOTYPE.isPresent(element) || DynamicAnnotationRegistry.hasDynamicStereotype(element);
    }

    public static boolean hasSpecializesAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.SPECIALIZES.isPresent(element);
    }

    public static boolean hasModelAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.MODEL.isPresent(element);
    }

    public static boolean hasNewAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.NEW.isPresent(element);
    }

    public static boolean hasInterceptedAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.INTERCEPTED.isPresent(element);
    }

    public static boolean hasDecoratedAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.DECORATED.isPresent(element);
    }

    public static boolean hasTransientReferenceAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.TRANSIENT_REFERENCE.isPresent(element);
    }

    public static boolean hasDecoratorAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.DECORATOR.isPresent(element);
    }

    public static boolean hasDelegateAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.DELEGATE.isPresent(element);
    }

    public static boolean hasInterceptorAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.INTERCEPTOR.isPresent(element);
    }

    public static boolean hasInterceptorBindingAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.INTERCEPTOR_BINDING.isPresent(element)
                || DynamicAnnotationRegistry.hasDynamicInterceptorBinding(element);
    }

    public static boolean hasInterceptorsAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.INTERCEPTORS.isPresent(element);
    }

    public static boolean hasExcludeClassInterceptorsAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.EXCLUDE_CLASS_INTERCEPTORS.isPresent(element);
    }

    public static boolean hasExcludeDefaultInterceptorsAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.EXCLUDE_DEFAULT_INTERCEPTORS.isPresent(element);
    }

    public static boolean hasDependentAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.DEPENDENT.isPresent(element);
    }

    public static boolean hasApplicationScopedAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.APPLICATION_SCOPED.isPresent(element);
    }

    public static boolean hasRequestScopedAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.REQUEST_SCOPED.isPresent(element);
    }

    public static boolean hasSessionScopedAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.SESSION_SCOPED.isPresent(element);
    }

    public static boolean hasConversationScopedAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.CONVERSATION_SCOPED.isPresent(element);
    }

    public static boolean hasBuiltInNormalScopeAnnotation(AnnotatedElement element) {
        return hasApplicationScopedAnnotation(element)
                || hasRequestScopedAnnotation(element)
                || hasSessionScopedAnnotation(element)
                || hasConversationScopedAnnotation(element);
    }

    public static boolean hasBuiltInPassivatingScopeAnnotation(AnnotatedElement element) {
        return hasSessionScopedAnnotation(element) || hasConversationScopedAnnotation(element);
    }

    public static boolean hasNormalScopeAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.NORMAL_SCOPE.isPresent(element);
    }

    public static boolean hasInitializedAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.INITIALIZED.isPresent(element);
    }

    public static boolean hasBeforeDestroyedAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.BEFORE_DESTROYED.isPresent(element);
    }

    public static boolean hasDestroyedAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.DESTROYED.isPresent(element);
    }

    public static boolean hasObservesAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.OBSERVES.isPresent(element);
    }

    public static boolean hasObservesAsyncAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.OBSERVES_ASYNC.isPresent(element);
    }

    public static boolean hasStartupAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.STARTUP.isPresent(element);
    }

    public static boolean hasShutdownAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.SHUTDOWN.isPresent(element);
    }

    public static boolean isStartupEventTypeName(String typeName) {
        return JAKARTA_STARTUP_EVENT_TYPE_NAME.equals(typeName)
                || JAVAX_STARTUP_EVENT_TYPE_NAME.equals(typeName);
    }

    public static boolean isShutdownEventTypeName(String typeName) {
        return JAKARTA_SHUTDOWN_EVENT_TYPE_NAME.equals(typeName)
                || JAVAX_SHUTDOWN_EVENT_TYPE_NAME.equals(typeName);
    }

    public static boolean isContainerLifecyclePayloadTypeName(String typeName) {
        return isStartupEventTypeName(typeName) || isShutdownEventTypeName(typeName);
    }

    public static boolean hasAroundInvokeAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.AROUND_INVOKE.isPresent(element);
    }

    public static boolean hasAroundConstructAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.AROUND_CONSTRUCT.isPresent(element);
    }

    public static boolean hasWithAnnotationsAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.WITH_ANNOTATIONS.isPresent(element);
    }

    public static boolean hasActivateRequestContextAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.ACTIVATE_REQUEST_CONTEXT.isPresent(element);
    }

    public static boolean hasInheritedAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.INHERITED.isPresent(element);
    }

    public static boolean hasTargetAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.TARGET.isPresent(element);
    }

    public static boolean hasRepeatableAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.REPEATABLE.isPresent(element);
    }

    public static boolean hasDiscoveryAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.DISCOVERY.isPresent(element);
    }

    public static boolean hasEnhancementAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.ENHANCEMENT.isPresent(element);
    }

    public static boolean hasRegistrationAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.REGISTRATION.isPresent(element);
    }

    public static boolean hasSynthesisAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.SYNTHESIS.isPresent(element);
    }

    public static boolean hasValidationAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.VALIDATION.isPresent(element);
    }

    public static boolean hasSkipIfPortableExtensionPresentAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.SKIP_IF_PORTABLE_EXTENSION_PRESENT.isPresent(element);
    }

    public static boolean hasLookupIfPropertyAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.LOOKUP_IF_PROPERTY.isPresent(element);
    }

    public static boolean hasLookupUnlessPropertyAnnotation(AnnotatedElement element) {
        return AnnotationsEnum.LOOKUP_UNLESS_PROPERTY.isPresent(element);
    }


    public static boolean declaresAlternative(Class<? extends Annotation> stereotypeType) {
        return declaresAlternative(stereotypeType, new HashSet<>());
    }

    public static boolean declaresAlternative(Class<? extends Annotation> stereotypeType,
                                              Set<Class<? extends Annotation>> visited) {
        if (stereotypeType == null) {
            return false;
        }

        if (!visited.add(stereotypeType)) {
            return false;
        }

        if (hasAlternativeAnnotation(stereotypeType)) {
            return true;
        }

        for (Annotation meta : stereotypeType.getAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (hasStereotypeAnnotation(metaType) && declaresAlternative(metaType, visited)) {
                return true;
            }
        }

        return false;
    }


    /**
     * Extracts qualifiers from an annotation array, defaulting to @Default when empty.
     * This is intended for required-qualifier extraction during resolution.
     */
    public static Set<Annotation> extractQualifiers(Annotation[] annotations) {
        Set<Annotation> qualifiers = extractQualifierAnnotations(annotations);
        if (qualifiers.isEmpty()) {
            qualifiers.add(new DefaultLiteral());
        }
        return qualifiers;
    }

    /**
     * Normalizes a collection of annotations to a qualifier set (adds @Default if none).
     * This is intended for required/available qualifier matching utilities.
     */
    public static Set<Annotation> normalizeQualifiers(Collection<Annotation> annotations) {
        Set<Annotation> qualifiers = annotations == null ? new HashSet<>() :
                extractQualifierAnnotations(annotations.toArray(new Annotation[0]));
        if (qualifiers.isEmpty()) {
            qualifiers.add(new DefaultLiteral());
        }
        return qualifiers;
    }

    /**
     * Extracts qualifier annotations from an annotation array without adding implicit qualifiers.
     */
    public static Set<Annotation> extractQualifierAnnotations(Annotation[] annotations) {
        Set<Annotation> qualifiers = new HashSet<>();
        if (annotations != null) {
            for (Annotation ann : annotations) {
                collectQualifierAnnotation(ann, qualifiers);
            }
        }
        return qualifiers;
    }

    /**
     * Extracts bean qualifiers from an annotation array and applies CDI built-ins:
     * adds {@code @Default} when no qualifier other than {@code @Named}/{@code @Any} exists,
     * and always adds {@code @Any}.
     */
    public static Set<Annotation> extractBeanQualifiers(Annotation[] annotations) {
        return normalizeBeanQualifiers(Arrays.asList(annotations == null ? new Annotation[0] : annotations));
    }

    /**
     * Normalizes bean qualifiers according to CDI bean qualifier rules:
     * adds {@code @Default} when no qualifier other than {@code @Named}/{@code @Any} exists,
     * and always adds {@code @Any}.
     */
    public static Set<Annotation> normalizeBeanQualifiers(Collection<Annotation> annotations) {
        Set<Annotation> qualifiers = annotations == null ? new HashSet<>() :
                extractQualifierAnnotations(annotations.toArray(new Annotation[0]));

        boolean hasNonNamedNonAnyQualifier = qualifiers.stream()
                .map(Annotation::annotationType)
                .anyMatch(type -> !hasNamedAnnotation(type) && !hasAnyAnnotation(type) && !hasDefaultAnnotation(type));

        if (hasNonNamedNonAnyQualifier) {
            qualifiers.removeIf(q -> hasDefaultAnnotation(q.annotationType()));
        } else {
            qualifiers.add(new DefaultLiteral());
        }
        qualifiers.add(new AnyLiteral());
        return qualifiers;
    }

    /**
     * Collects qualifier annotations, expanding repeatable qualifier container annotations.
     */
    private static void collectQualifierAnnotation(Annotation annotation, Set<Annotation> sink) {
        if (annotation == null) {
            return;
        }

        if (hasQualifierAnnotation(annotation.annotationType())) {
            sink.add(annotation);
            return;
        }

        Collections.addAll(sink, extractQualifierAnnotationsFromContainer(annotation));
    }

    /**
     * Extracts nested qualifier annotations from a repeatable container annotation.
     *
     * <p>For example, for {@code @Locations({@Location("north"), @Location("south")})}
     * this returns the nested {@code @Location} annotations.
     */
    private static Annotation[] extractQualifierAnnotationsFromContainer(Annotation containerAnnotation) {
        try {
            Method valueMethod = containerAnnotation.annotationType().getMethod("value");
            Class<?> returnType = valueMethod.getReturnType();
            if (!returnType.isArray()) {
                return new Annotation[0];
            }

            Class<?> componentType = returnType.getComponentType();
            if (componentType == null || !Annotation.class.isAssignableFrom(componentType)) {
                return new Annotation[0];
            }

            @SuppressWarnings("unchecked")
            Class<? extends Annotation> nestedAnnotationType = (Class<? extends Annotation>) componentType;
            if (!hasQualifierAnnotation(nestedAnnotationType)) {
                return new Annotation[0];
            }

            Object value = valueMethod.invoke(containerAnnotation);
            if (value instanceof Annotation[]) {
                return (Annotation[]) value;
            }
        } catch (ReflectiveOperationException ignored) {
            // Not a repeatable qualifier container annotation; ignore.
        }
        return new Annotation[0];
    }

    public static boolean isQualifierAnnotation(Annotation annotation) {
        if (annotation == null) {
            return false;
        }
        Class<? extends Annotation> annotationType = annotation.annotationType();
        return hasQualifierAnnotation(annotationType) || hasNamedAnnotation(annotationType);
    }

    public static boolean isAnyQualifier(Annotation annotation) {
        return annotation != null && hasAnyAnnotation(annotation.annotationType());
    }

    /**
     * Returns true if the available set contains all required qualifiers, respecting @Named values
     * and @Nonbinding semantics.
     */
    public static boolean qualifiersMatch(Set<Annotation> requiredQualifiers, Set<Annotation> availableQualifiers) {
        // Special case: @Named requires an exact match
        Annotation requiredNamed = findNamedAnnotation(requiredQualifiers);
        Annotation availableNamed = findNamedAnnotation(availableQualifiers);

        if (requiredNamed != null) {
            if (availableNamed == null) {
                return false;
            }
            if (!getNamedValue(requiredNamed).equals(getNamedValue(availableNamed))) {
                return false;
            }
        }

        for (Annotation required : requiredQualifiers) {
            if (hasAnyAnnotation(required.annotationType())) {
                continue;
            }
            if (hasNamedAnnotation(required.annotationType())) {
                continue;
            }
            boolean found = false;
            for (Annotation avail : availableQualifiers) {
                if (qualifiersEqual(required, avail)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * Event-observer qualifier matching where @Default observer methods only match
     * events with no explicit non-default qualifiers.
     */
    public static boolean notEventQualifiersMatch(Set<Annotation> observedQualifiers, Set<Annotation> eventQualifiers) {
        Annotation observedNamed = findNamedAnnotation(observedQualifiers);
        Annotation eventNamed = findNamedAnnotation(eventQualifiers);

        if (observedNamed != null) {
            if (eventNamed == null) {
                return true;
            }
            if (!getNamedValue(observedNamed).equals(getNamedValue(eventNamed))) {
                return true;
            }
        }

        for (Annotation required : observedQualifiers) {
            if (hasAnyAnnotation(required.annotationType())) {
                continue;
            }
            if (isDefaultQualifier(required)) {
                if (hasExplicitNonDefaultQualifier(eventQualifiers)) {
                    return true;
                }
                continue;
            }
            if (hasNamedAnnotation(required.annotationType())) {
                continue;
            }
            boolean found = false;
            for (Annotation avail : eventQualifiers) {
                if (qualifiersEqual(required, avail)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExplicitNonDefaultQualifier(Set<Annotation> qualifiers) {
        if (qualifiers == null || qualifiers.isEmpty()) {
            return false;
        }
        for (Annotation qualifier : qualifiers) {
            Class<? extends Annotation> type = qualifier.annotationType();
            if (hasAnyAnnotation(type)) {
                continue;
            }
            if (hasNamedAnnotation(type)) {
                continue;
            }
            if (isDefaultQualifier(qualifier)) {
                continue;
            }
            return true;
        }
        return false;
    }

    public static boolean isDefaultQualifier(Annotation annotation) {
        if (annotation == null) {
            return false;
        }
        return hasDefaultAnnotation(annotation.annotationType());
    }

    private static Annotation findNamedAnnotation(Set<Annotation> annotations) {
        if (annotations == null) {
            return null;
        }
        for (Annotation ann : annotations) {
            if (hasNamedAnnotation(ann.annotationType())) {
                return ann;
            }
        }
        return null;
    }

    public static boolean qualifiersEqual(Annotation q1, Annotation q2) {
        return AnnotationComparator.equals(q1, q2);
    }

    public static Annotation findAnnotation(Set<Annotation> annotations, Class<? extends Annotation> type) {
        if (annotations == null) {
            return null;
        }
        for (Annotation ann : annotations) {
            if (ann.annotationType().equals(type)) {
                return ann;
            }
        }
        return null;
    }

    public static String getNamedValue(Annotation namedAnnotation) {
        try {
            Method value = namedAnnotation.annotationType().getMethod("value");
            Object raw = value.invoke(namedAnnotation);
            return raw == null ? "" : raw.toString();
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    public static Integer getPriorityValue(Annotation[] annotations) {
        if (annotations == null) {
            return null;
        }
        for (Annotation annotation : annotations) {
            if (annotation != null && PRIORITY.matches(annotation.annotationType())) {
                return getPriorityValue(annotation);
            }
        }
        return null;
    }

    public static Integer getPriorityValue(Annotation priorityAnnotation) {
        try {
            Method valueMethod = priorityAnnotation.annotationType().getMethod("value");
            Object value = valueMethod.invoke(priorityAnnotation);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (ReflectiveOperationException ignored) {
            // Ignore malformed annotation implementations and treat as absent.
        }
        return null;
    }


    public static <T extends Annotation> T getTypedAnnotation(AnnotatedElement element) {
        return getFirstAnnotation(element, AnnotationsEnum.TYPED);
    }

    public static <T extends Annotation> T getPriorityAnnotation(AnnotatedElement element) {
        return getFirstAnnotation(element, AnnotationsEnum.PRIORITY);
    }

    public static <T extends Annotation> T getNamedAnnotation(AnnotatedElement element) {
        return getFirstAnnotation(element, AnnotationsEnum.NAMED);
    }

    public static Target getTargetAnnotation(AnnotatedElement element) {
        if (element == null) {
            return null;
        }
        return element.getAnnotation(Target.class);
    }

    public static java.lang.annotation.Retention getRetentionAnnotation(AnnotatedElement element) {
        if (element == null) {
            return null;
        }
        return element.getAnnotation(java.lang.annotation.Retention.class);
    }

    public static java.lang.annotation.Repeatable getRepeatableAnnotation(AnnotatedElement element) {
        if (element == null) {
            return null;
        }
        return element.getAnnotation(java.lang.annotation.Repeatable.class);
    }

    public static <T extends Annotation> T getObservesAnnotation(AnnotatedElement element) {
        return getFirstAnnotation(element, AnnotationsEnum.OBSERVES);
    }

    public static <T extends Annotation> T getObservesAsyncAnnotation(AnnotatedElement element) {
        return getFirstAnnotation(element, AnnotationsEnum.OBSERVES_ASYNC);
    }

    public static jakarta.enterprise.inject.build.compatible.spi.Registration getRegistrationAnnotation(AnnotatedElement element) {
        if (element == null) {
            return null;
        }
        return element.getAnnotation(jakarta.enterprise.inject.build.compatible.spi.Registration.class);
    }

    public static jakarta.enterprise.inject.build.compatible.spi.Enhancement getEnhancementAnnotation(AnnotatedElement element) {
        if (element == null) {
            return null;
        }
        return element.getAnnotation(jakarta.enterprise.inject.build.compatible.spi.Enhancement.class);
    }

    public static jakarta.enterprise.inject.build.compatible.spi.SkipIfPortableExtensionPresent
    getSkipIfPortableExtensionPresentAnnotation(AnnotatedElement element) {
        if (element == null) {
            return null;
        }
        return element.getAnnotation(jakarta.enterprise.inject.build.compatible.spi.SkipIfPortableExtensionPresent.class);
    }

    public static Integer getPriorityValue(AnnotatedElement element) {
        Annotation priority = getPriorityAnnotation(element);
        if (priority == null) {
            return null;
        }
        try {
            Object value = priority.annotationType().getMethod("value").invoke(priority);
            return value instanceof Integer ? (Integer) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Boolean getNormalScopePassivatingValue(AnnotatedElement element) {
        if (element == null) {
            return null;
        }
        Annotation normalScope = getFirstAnnotation(element, AnnotationsEnum.NORMAL_SCOPE);
        if (normalScope == null) {
            return null;
        }
        try {
            Object value = normalScope.annotationType().getMethod("passivating").invoke(normalScope);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Annotation> T getFirstAnnotation(AnnotatedElement element, AnnotationsEnum alias) {
        if (element == null || alias == null) {
            return null;
        }
        for (Class<? extends Annotation> annotationClass : alias.getAnnotations()) {
            T annotation = (T) element.getAnnotation(annotationClass);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    /**
     * Checks if the given class is an alternative declaration.
     * @param beanClass the class to check
     * @return true if the class is an alternative declaration, false otherwise
     */
    public static boolean isAlternativeViaAnnotationOrStereotype(Class<?> beanClass) {
        if (beanClass == null) {
            return false;
        }
        if (hasAlternativeAnnotation(beanClass)) {
            return true;
        }
        for (Annotation annotation : beanClass.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (hasStereotypeAnnotation(annotationType) && declaresAlternative(annotationType)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAlternativeEnabledInBeansXml(String className, Collection<BeansXml> beansXmlConfigurations) {
        if (className == null || className.isEmpty() || beansXmlConfigurations == null) {
            return false;
        }

        for (BeansXml beansXml : beansXmlConfigurations) {
            if (beansXml.getAlternatives() != null) {
                if (beansXml.getAlternatives().getClasses().contains(className)) {
                    return true;
                }
                if (beansXml.getAlternatives().getStereotypes().contains(className)) {
                    return true;
                }
            }
        }

        return false;
    }
}
