package com.threeamigos.common.util.implementations.injection.annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Enumeration of JSR-330 (Dependency Injection) and CDI annotations used by the container.
 * Where available, both {@code javax.*} and {@code jakarta.*} variants are supported.
 *
 * <p>Each enum value contains one or more equivalent annotation classes
 * (javax, jakarta, or both).
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * // Instead of:
 * if (clazz.isAnnotationPresent(jakarta.enterprise.inject.Vetoed.class) ||
 *     clazz.isAnnotationPresent(javax.enterprise.inject.Vetoed.class)) {
 *     // ...
 * }
 *
 * // Use:
 * if (AnnotationPredicates.hasVetoedAnnotation(clazz)) {
 *     // ...
 * }
 * }</pre>
 *
 * @author Stefano Reksten
 * @see jakarta.inject
 * @see javax.inject
 */
public enum AnnotationsEnum {

    // ==================== JSR-330 Annotations ====================

    /**
     * Identifies injectable constructors, methods, and fields.<br/>
     * Maps: {@code javax.inject.Inject}, {@code jakarta.inject.Inject}<br/>
     * Since: JSR-330
     */
    INJECT(annotationClass("javax.inject.Inject"), jakarta.inject.Inject.class),

    /**
     * Identifies a type that the injector only instantiates once.<br/>
     * Maps: {@code javax.inject.Singleton}, {@code jakarta.inject.Singleton}<br/>
     * Since: JSR-330
     */
    SINGLETON(annotationClass("javax.inject.Singleton"), jakarta.inject.Singleton.class),

    /**
     * String-based qualifier annotation.<br/>
     * Maps: {@code javax.inject.Named}, {@code jakarta.inject.Named}<br/>
     * Since: JSR-330
     */
    NAMED(annotationClass("javax.inject.Named"), jakarta.inject.Named.class),

    /**
     * Identifies qualifier annotations (meta-annotation).<br/>
     * Maps: {@code javax.inject.Qualifier}, {@code jakarta.inject.Qualifier}<br/>
     * Since: JSR-330
     */
    QUALIFIER(annotationClass("javax.inject.Qualifier"), jakarta.inject.Qualifier.class),

    /**
     * Identifies scope annotations (meta-annotation).<br/>
     * Maps: {@code javax.inject.Scope}, {@code jakarta.inject.Scope}<br/>
     * Since: JSR-330
     */
    SCOPE(annotationClass("javax.inject.Scope"), jakarta.inject.Scope.class),

    // ==================== CDI Qualifier/Binding Annotations ====================

    /**
     * Marks qualifier or interceptor binding annotation members that should be ignored during matching.
     * When comparing two qualifier or interceptor binding annotations, members marked with
     * {@code @Nonbinding} are not considered in the equality check.<br/>
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * @Qualifier
     * @Retention(RUNTIME)
     * @Target({ FIELD, TYPE, METHOD, PARAMETER ])
     * public @interface PayBy {
     *     PaymentMethod value(); // Considered during matching
     *     @Nonbinding String description() default ""; // Ignored during matching
     * }
     * }</pre>
     *
     * Maps: {@code javax.enterprise.util.Nonbinding}, {@code jakarta.enterprise.util.Nonbinding}<br/>
     * Since: CDI 1.0
     */
    NONBINDING(annotationClass("javax.enterprise.util.Nonbinding"), jakarta.enterprise.util.Nonbinding.class),

    // ==================== JSR-250 Annotations ====================

    /**
     * Lifecycle callback executed after dependency injection.<br/>
     * Maps: {@code javax.annotation.PostConstruct}, {@code jakarta.annotation.PostConstruct}<br/>
     * Since: Common Annotations 1.0 (used by CDI since CDI 1.0)
     */
    POST_CONSTRUCT(annotationClass("javax.annotation.PostConstruct"), jakarta.annotation.PostConstruct.class),

    /**
     * Lifecycle callback executed before destruction.<br/>
     * Maps: {@code javax.annotation.PreDestroy}, {@code jakarta.annotation.PreDestroy}<br/>
     * Since: Common Annotations 1.0 (used by CDI since CDI 1.0)
     */
    PRE_DESTROY(annotationClass("javax.annotation.PreDestroy"), jakarta.annotation.PreDestroy.class),

    /**
     * Lifecycle callback executed before passivation of a stateful component.<br/>
     * Maps: {@code javax.ejb.PrePassivate}, {@code jakarta.ejb.PrePassivate}<br/>
     * Since: EJB 3.0 (used by CDI passivating scopes integration)
     */
    PRE_PASSIVATE(annotationClass("javax.ejb.PrePassivate"), annotationClass("jakarta.ejb.PrePassivate")),

    /**
     * Lifecycle callback executed after activation of a passivated component.<br/>
     * Maps: {@code javax.ejb.PostActivate}, {@code jakarta.ejb.PostActivate}<br/>
     * Since: EJB 3.0 (used by CDI passivating scopes integration)
     */
    POST_ACTIVATE(annotationClass("javax.ejb.PostActivate"), annotationClass("jakarta.ejb.PostActivate")),

    /**
     * Priority annotation for ordering.<br/>
     * Maps: {@code javax.annotation.Priority}, {@code jakarta.annotation.Priority}<br/>
     * Since: Common Annotations 1.2 (used by CDI since CDI 1.1)
     */
    PRIORITY(annotationClass("javax.annotation.Priority"), jakarta.annotation.Priority.class),

    // ==================== CDI Annotations ====================

    /**
     * Marks a bean as an alternative implementation.<br/>
     * Maps: {@code javax.enterprise.inject.Alternative}, {@code jakarta.enterprise.inject.Alternative}<br/>
     * Since: CDI 1.0
     */
    ALTERNATIVE(annotationClass("javax.enterprise.inject.Alternative"), jakarta.enterprise.inject.Alternative.class),

    /**
     * Built-in qualifier that matches all beans.<br/>
     * Maps: {@code javax.enterprise.inject.Any}, {@code jakarta.enterprise.inject.Any}<br/>
     * Since: CDI 1.0
     */
    ANY(annotationClass("javax.enterprise.inject.Any"), jakarta.enterprise.inject.Any.class),

    /**
     * The default qualifier applied when no other qualifier is present.<br/>
     * Maps: {@code javax.enterprise.inject.Default}, {@code jakarta.enterprise.inject.Default}<br/>
     * Since: CDI 1.0
     */
    DEFAULT(annotationClass("javax.enterprise.inject.Default"), jakarta.enterprise.inject.Default.class),

    /**
     * Marks a producer method or field.<br/>
     * Maps: {@code javax.enterprise.inject.Produces}, {@code jakarta.enterprise.inject.Produces}<br/>
     * Since: CDI 1.0
     */
    PRODUCES(annotationClass("javax.enterprise.inject.Produces"), jakarta.enterprise.inject.Produces.class),

    /**
     * Marks a disposer method parameter.<br/>
     * Maps: {@code javax.enterprise.inject.Disposes}, {@code jakarta.enterprise.inject.Disposes}<br/>
     * Since: CDI 1.0
     */
    DISPOSES(annotationClass("javax.enterprise.inject.Disposes"), jakarta.enterprise.inject.Disposes.class),

    /**
     * Marks a class that should not be discovered as a bean.<br/>
     * Maps: {@code javax.enterprise.inject.Vetoed}, {@code jakarta.enterprise.inject.Vetoed}<br/>
     * Since: CDI 1.1
     */
    VETOED(annotationClass("javax.enterprise.inject.Vetoed"), jakarta.enterprise.inject.Vetoed.class),

    /**
     * Restricts the bean types of a bean to only the types specified in the annotation value.
     * Per CDI 4.1 Section 2.2, this allows fine-grained control over which types from the
     * class hierarchy are included in the bean's type set.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * @ApplicationScoped
     * @Typed(PaymentProcessor.class)  // Only injectable as PaymentProcessor
     * public class PayPalProcessor implements PaymentProcessor, EmailSender {
     *     // Not injectable as EmailSender or PayPalProcessor
     * }
     * }</pre>
     *
     * Maps: {@code javax.enterprise.inject.Typed}, {@code jakarta.enterprise.inject.Typed}<br/>
     * Since: CDI 1.0
     */
    TYPED(annotationClass("javax.enterprise.inject.Typed"), jakarta.enterprise.inject.Typed.class),

    /**
     * Identifies a stereotype annotation (meta-annotation).<br/>
     * Maps: {@code javax.enterprise.inject.Stereotype}, {@code jakarta.enterprise.inject.Stereotype}<br/>
     * Since: CDI 1.0
     */
    STEREOTYPE(annotationClass("javax.enterprise.inject.Stereotype"), jakarta.enterprise.inject.Stereotype.class),

    /**
     * Indicates that a bean specializes another bean.<br/>
     * Maps: {@code javax.enterprise.inject.Specializes}, {@code jakarta.enterprise.inject.Specializes}<br/>
     * Since: CDI 1.0
     */
    SPECIALIZES(annotationClass("javax.enterprise.inject.Specializes"), jakarta.enterprise.inject.Specializes.class),

    /**
     * Built-in stereotype that combines {@code @Named} and {@code @RequestScoped}.<br/>
     * Maps: {@code javax.enterprise.inject.Model}, {@code jakarta.enterprise.inject.Model}<br/>
     * Since: CDI 1.0
     */
    MODEL(annotationClass("javax.enterprise.inject.Model"), jakarta.enterprise.inject.Model.class),

    /**
     * Deprecated qualifier indicating injection of a new instance of a bean.<br/>
     * Maps: {@code javax.enterprise.inject.New}<br/>
     * Since: CDI 1.0 (deprecated in CDI 1.1, removed from Jakarta CDI)
     */
    NEW(annotationClass("javax.enterprise.inject.New")),

    /**
     * Built-in qualifier for getting bean metadata in interceptors.<br/>
     * Maps: {@code javax.enterprise.inject.Intercepted}, {@code jakarta.enterprise.inject.Intercepted}<br/>
     * Since: CDI 1.1
     */
    INTERCEPTED(annotationClass("javax.enterprise.inject.Intercepted"), jakarta.enterprise.inject.Intercepted.class),

    /**
     * Built-in qualifier for getting decorated bean metadata in decorators.<br/>
     * Maps: {@code javax.enterprise.inject.Decorated}, {@code jakarta.enterprise.inject.Decorated}<br/>
     * Since: CDI 1.1
     */
    DECORATED(annotationClass("javax.enterprise.inject.Decorated"), annotationClass("jakarta.enterprise.inject.Decorated")),

    /**
     * Annotation for transient method or constructor parameter references.<br/>
     * Maps: {@code javax.enterprise.inject.TransientReference}, {@code jakarta.enterprise.inject.TransientReference}<br/>
     * Since: CDI 2.0
     */
    TRANSIENT_REFERENCE(annotationClass("javax.enterprise.inject.TransientReference"),
            jakarta.enterprise.inject.TransientReference.class),

    /**
     * Identifies a decorator bean.<br/>
     * Maps: {@code javax.decorator.Decorator}, {@code jakarta.decorator.Decorator}<br/>
     * Since: CDI 1.0
     */
    DECORATOR(annotationClass("javax.decorator.Decorator"), jakarta.decorator.Decorator.class),

    /**
     * Marks the delegate injection point in a decorator.<br/>
     * Maps: {@code javax.decorator.Delegate}, {@code jakarta.decorator.Delegate}<br/>
     * Since: CDI 1.0
     */
    DELEGATE(annotationClass("javax.decorator.Delegate"), jakarta.decorator.Delegate.class),

    /**
     * Identifies an interceptor bean.<br/>
     * Maps: {@code javax.interceptor.Interceptor}, {@code jakarta.interceptor.Interceptor}<br/>
     * Since: CDI 1.0
     */
    INTERCEPTOR(annotationClass("javax.interceptor.Interceptor"), jakarta.interceptor.Interceptor.class),

    /**
     * Identifies interceptor-binding annotations (meta-annotation).<br/>
     * Maps: {@code javax.interceptor.InterceptorBinding}, {@code jakarta.interceptor.InterceptorBinding}<br/>
     * Since: CDI 1.0
     */
    INTERCEPTOR_BINDING(annotationClass("javax.interceptor.InterceptorBinding"),
            jakarta.interceptor.InterceptorBinding.class),

    /**
     * Declares class or method level legacy interceptor classes.<br/>
     * Maps: {@code javax.interceptor.Interceptors}, {@code jakarta.interceptor.Interceptors}<br/>
     * Since: Interceptors 1.0
     */
    INTERCEPTORS(annotationClass("javax.interceptor.Interceptors"), annotationClass("jakarta.interceptor.Interceptors")),

    /**
     * Excludes class-level interceptors from applying to a method.<br/>
     * Maps: {@code javax.interceptor.ExcludeClassInterceptors}, {@code jakarta.interceptor.ExcludeClassInterceptors}<br/>
     * Since: Interceptors 1.0
     */
    EXCLUDE_CLASS_INTERCEPTORS(annotationClass("javax.interceptor.ExcludeClassInterceptors"),
            annotationClass("jakarta.interceptor.ExcludeClassInterceptors")),

    /**
     * Excludes globally/default enabled interceptors.<br/>
     * Maps: {@code javax.interceptor.ExcludeDefaultInterceptors}, {@code jakarta.interceptor.ExcludeDefaultInterceptors}<br/>
     * Since: Interceptors 1.0
     */
    EXCLUDE_DEFAULT_INTERCEPTORS(annotationClass("javax.interceptor.ExcludeDefaultInterceptors"),
            annotationClass("jakarta.interceptor.ExcludeDefaultInterceptors")),

    // ==================== CDI Scope Annotations ====================

    /**
     * Dependent pseudo-scope (new instance per injection point).<br/>
     * Maps: {@code javax.enterprise.context.Dependent}, {@code jakarta.enterprise.context.Dependent}<br/>
     * Since: CDI 1.0
     */
    DEPENDENT(annotationClass("javax.enterprise.context.Dependent"), jakarta.enterprise.context.Dependent.class),

    /**
     * Application scope (one instance per application).<br/>
     * Maps: {@code javax.enterprise.context.ApplicationScoped}, {@code jakarta.enterprise.context.ApplicationScoped}<br/>
     * Since: CDI 1.0
     */
    APPLICATION_SCOPED(annotationClass("javax.enterprise.context.ApplicationScoped"),
            jakarta.enterprise.context.ApplicationScoped.class),

    /**
     * Request scope (one instance per HTTP request).<br/>
     * Maps: {@code javax.enterprise.context.RequestScoped}, {@code jakarta.enterprise.context.RequestScoped}<br/>
     * Since: CDI 1.0
     */
    REQUEST_SCOPED(annotationClass("javax.enterprise.context.RequestScoped"), jakarta.enterprise.context.RequestScoped.class),

    /**
     * Session scope (one instance per HTTP session).<br/>
     * Maps: {@code javax.enterprise.context.SessionScoped}, {@code jakarta.enterprise.context.SessionScoped}<br/>
     * Since: CDI 1.0
     */
    SESSION_SCOPED(annotationClass("javax.enterprise.context.SessionScoped"), jakarta.enterprise.context.SessionScoped.class),

    /**
     * Conversation scope (one instance per conversation).<br/>
     * Maps: {@code javax.enterprise.context.ConversationScoped}, {@code jakarta.enterprise.context.ConversationScoped}<br/>
     * Since: CDI 1.0
     */
    CONVERSATION_SCOPED(annotationClass("javax.enterprise.context.ConversationScoped"),
            jakarta.enterprise.context.ConversationScoped.class),

    /**
     * Normal scope meta-annotation.<br/>
     * Maps: {@code javax.enterprise.context.NormalScope}, {@code jakarta.enterprise.context.NormalScope}<br/>
     * Since: CDI 1.0
     */
    NORMAL_SCOPE(annotationClass("javax.enterprise.context.NormalScope"), jakarta.enterprise.context.NormalScope.class),

    // ==================== CDI Event Annotations ====================

    /**
     * Qualifier for context-initialized lifecycle events.<br/>
     * Maps: {@code javax.enterprise.context.Initialized}, {@code jakarta.enterprise.context.Initialized}<br/>
     * Since: CDI 1.1
     */
    INITIALIZED(annotationClass("javax.enterprise.context.Initialized"), jakarta.enterprise.context.Initialized.class),

    /**
     * Qualifier for context before-destroyed lifecycle events.<br/>
     * Maps: {@code javax.enterprise.context.BeforeDestroyed}, {@code jakarta.enterprise.context.BeforeDestroyed}<br/>
     * Since: CDI 1.1
     */
    BEFORE_DESTROYED(annotationClass("javax.enterprise.context.BeforeDestroyed"),
            jakarta.enterprise.context.BeforeDestroyed.class),

    /**
     * Qualifier for context destroyed lifecycle events.<br/>
     * Maps: {@code javax.enterprise.context.Destroyed}, {@code jakarta.enterprise.context.Destroyed}<br/>
     * Since: CDI 1.1
     */
    DESTROYED(annotationClass("javax.enterprise.context.Destroyed"), jakarta.enterprise.context.Destroyed.class),

    /**
     * Marks a method parameter that observes CDI events (synchronous).<br/>
     * Maps: {@code javax.enterprise.event.Observes}, {@code jakarta.enterprise.event.Observes}<br/>
     * Since: CDI 1.0
     */
    OBSERVES(annotationClass("javax.enterprise.event.Observes"), jakarta.enterprise.event.Observes.class),

    /**
     * Marks a method parameter that observes CDI events (asynchronous).<br/>
     * Maps: {@code javax.enterprise.event.ObservesAsync}, {@code jakarta.enterprise.event.ObservesAsync}<br/>
     * Since: CDI 2.0
     */
    OBSERVES_ASYNC(annotationClass("javax.enterprise.event.ObservesAsync"),
            jakarta.enterprise.event.ObservesAsync.class),

    /**
     * Built-in qualifier for container startup event notifications.<br/>
     * Maps: {@code javax.enterprise.event.Startup}, {@code jakarta.enterprise.event.Startup}<br/>
     * Since: CDI 2.0/4.0
     */
    STARTUP(annotationClass("javax.enterprise.event.Startup"), annotationClass("jakarta.enterprise.event.Startup")),

    /**
     * Built-in qualifier for container shutdown event notifications.<br/>
     * Maps: {@code javax.enterprise.event.Shutdown}, {@code jakarta.enterprise.event.Shutdown}<br/>
     * Since: CDI 2.0/4.0
     */
    SHUTDOWN(annotationClass("javax.enterprise.event.Shutdown"), annotationClass("jakarta.enterprise.event.Shutdown")),

    /**
     * Interceptor method invoked around business method invocation.<br/>
     * Maps: {@code javax.interceptor.AroundInvoke}, {@code jakarta.interceptor.AroundInvoke}<br/>
     * Since: Interceptors 1.0 (used by CDI since CDI 1.0)
     */
    AROUND_INVOKE(annotationClass("javax.interceptor.AroundInvoke"), jakarta.interceptor.AroundInvoke.class),

    /**
     * Interceptor method invoked around constructor invocation.<br/>
     * Maps: {@code javax.interceptor.AroundConstruct}, {@code jakarta.interceptor.AroundConstruct}<br/>
     * Since: Interceptors 1.2 (used by CDI since CDI 1.1)
     */
    AROUND_CONSTRUCT(annotationClass("javax.interceptor.AroundConstruct"), jakarta.interceptor.AroundConstruct.class),

    /**
     * Restricts ProcessAnnotatedType observer delivery to types containing at least one of the specified annotations.<br/>
     * Maps: {@code javax.enterprise.inject.spi.WithAnnotations}, {@code jakarta.enterprise.inject.spi.WithAnnotations}<br/>
     * Since: CDI 1.1
     */
    WITH_ANNOTATIONS(annotationClass("javax.enterprise.inject.spi.WithAnnotations"),
            jakarta.enterprise.inject.spi.WithAnnotations.class),

    /**
     * Interceptor binding that activates request context for a method invocation.<br/>
     * Maps: {@code javax.enterprise.context.control.ActivateRequestContext},
     * {@code jakarta.enterprise.context.control.ActivateRequestContext}<br/>
     * Since: CDI 2.0
     */
    ACTIVATE_REQUEST_CONTEXT(annotationClass("javax.enterprise.context.control.ActivateRequestContext"),
            jakarta.enterprise.context.control.ActivateRequestContext.class),

    // ==================== Java Meta-Annotations ====================

    /**
     * Marks an annotation as inherited by subclasses.<br/>
     * Maps: {@code java.lang.annotation.Inherited}<br/>
     * Since: Java 5
     */
    INHERITED(java.lang.annotation.Inherited.class),

    /**
     * Specifies valid declaration targets for an annotation.<br/>
     * Maps: {@code java.lang.annotation.Target}<br/>
     * Since: Java 5
     */
    TARGET(java.lang.annotation.Target.class),

    /**
     * Marks an annotation as repeatable.<br/>
     * Maps: {@code java.lang.annotation.Repeatable}<br/>
     * Since: Java 8
     */
    REPEATABLE(java.lang.annotation.Repeatable.class),

    /**
     * Declares a build compatible extension method executed during discovery phase.<br/>
     * Maps: {@code jakarta.enterprise.inject.build.compatible.spi.Discovery}<br/>
     * Since: CDI 4.0
     */
    DISCOVERY(jakarta.enterprise.inject.build.compatible.spi.Discovery.class),

    /**
     * Declares a build compatible extension method executed during enhancement phase.<br/>
     * Maps: {@code jakarta.enterprise.inject.build.compatible.spi.Enhancement}<br/>
     * Since: CDI 4.0
     */
    ENHANCEMENT(jakarta.enterprise.inject.build.compatible.spi.Enhancement.class),

    /**
     * Declares a build compatible extension method executed during registration phase.<br/>
     * Maps: {@code jakarta.enterprise.inject.build.compatible.spi.Registration}<br/>
     * Since: CDI 4.0
     */
    REGISTRATION(jakarta.enterprise.inject.build.compatible.spi.Registration.class),

    /**
     * Declares a build compatible extension method executed during synthesis phase.<br/>
     * Maps: {@code jakarta.enterprise.inject.build.compatible.spi.Synthesis}<br/>
     * Since: CDI 4.0
     */
    SYNTHESIS(jakarta.enterprise.inject.build.compatible.spi.Synthesis.class),

    /**
     * Declares a build compatible extension method executed during validation phase.<br/>
     * Maps: {@code jakarta.enterprise.inject.build.compatible.spi.Validation}<br/>
     * Since: CDI 4.0
     */
    VALIDATION(jakarta.enterprise.inject.build.compatible.spi.Validation.class),

    /**
     * Declares that a build-compatible extension should be ignored when a given
     * portable extension is present.<br/>
     * Maps: {@code jakarta.enterprise.inject.build.compatible.spi.SkipIfPortableExtensionPresent}<br/>
     * Since: CDI 4.0
     */
    SKIP_IF_PORTABLE_EXTENSION_PRESENT(
            jakarta.enterprise.inject.build.compatible.spi.SkipIfPortableExtensionPresent.class),

    /**
     * Conditional lookup filter based on a configuration property value.<br/>
     * Maps: {@code jakarta.enterprise.inject.LookupIfProperty}<br/>
     * Since: CDI 4.0
     */
    LOOKUP_IF_PROPERTY(annotationClass("jakarta.enterprise.inject.LookupIfProperty")),

    /**
     * Conditional lookup filter based on absence or mismatch of a configuration property value.<br/>
     * Maps: {@code jakarta.enterprise.inject.LookupUnlessProperty}<br/>
     * Since: CDI 4.0
     */
    LOOKUP_UNLESS_PROPERTY(annotationClass("jakarta.enterprise.inject.LookupUnlessProperty"));

    // ==================== Implementation ====================

    private final Set<Class<? extends Annotation>> annotations;

    @SafeVarargs
    AnnotationsEnum(Class<? extends Annotation>... annotationClasses) {
        Set<Class<? extends Annotation>> resolved = new HashSet<>();
        if (annotationClasses != null) {
            for (Class<? extends Annotation> annotationClass : annotationClasses) {
                if (annotationClass != null) {
                    resolved.add(annotationClass);
                }
            }
        }
        this.annotations = Collections.unmodifiableSet(resolved);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> annotationClass(String fullyQualifiedClassName) {
        try {
            Class<?> loaded = Class.forName(fullyQualifiedClassName);
            if (Annotation.class.isAssignableFrom(loaded)) {
                return (Class<? extends Annotation>) loaded;
            }
            return null;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Returns the set of annotation classes for this enum value.
     *
     * @return immutable set of annotation classes
     */
    public Set<Class<? extends Annotation>> getAnnotations() {
        return annotations;
    }

    /**
     * Checks if the given element is annotated with any of the annotations
     * corresponding to this enum value.
     *
     * @param element the annotated element to check
     * @return true if the element has any of the annotations, false otherwise
     */
    public boolean isPresent(AnnotatedElement element) {
        if (element == null) {
            return false;
        }
        if (element instanceof Class && Annotation.class.isAssignableFrom((Class<?>) element)) {
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> annotationType = (Class<? extends Annotation>) element;
            if (matches(annotationType)) {
                return true;
            }
        }
        for (Class<? extends Annotation> annotationClass : annotations) {
            if (element.isAnnotationPresent(annotationClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the given annotation class matches any of the annotations
     * corresponding to this enum value.
     *
     * @param annotationClass the annotation class to check
     * @return true if the annotation class matches, false otherwise
     */
    public boolean matches(Class<? extends Annotation> annotationClass) {
        return annotations.contains(annotationClass);
    }

    /**
     * Checks whether the annotation instance type matches this enum value.
     */
    public boolean matches(Annotation annotation) {
        return annotation != null && matches(annotation.annotationType());
    }
}
