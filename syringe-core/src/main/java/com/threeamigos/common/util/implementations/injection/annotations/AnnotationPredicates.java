package com.threeamigos.common.util.implementations.injection.annotations;

import com.threeamigos.common.util.implementations.injection.annotations.DynamicAnnotationRegistry;

import jakarta.annotation.Nonnull;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

/**
 * Predicate helpers for annotation presence and classification.
 */
public final class AnnotationPredicates {

    private static final String JAKARTA_STARTUP_EVENT_TYPE_NAME = "jakarta.enterprise.event.Startup";
    private static final String JAVAX_STARTUP_EVENT_TYPE_NAME = "javax.enterprise.event.Startup";
    private static final String JAKARTA_SHUTDOWN_EVENT_TYPE_NAME = "jakarta.enterprise.event.Shutdown";
    private static final String JAVAX_SHUTDOWN_EVENT_TYPE_NAME = "javax.enterprise.event.Shutdown";

    private AnnotationPredicates() {
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
}
