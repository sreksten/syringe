package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.implementations.injection.spi.configurators.AnnotatedTypeConfiguratorImpl;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasNonbindingAnnotation;
import static com.threeamigos.common.util.implementations.injection.annotations.DynamicAnnotationRegistry.registerDynamicNonbindingMember;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.toList;

/**
 * BeforeBeanDiscovery event implementation.
 * 
 * <p>Fired before the bean discovery process starts. Extensions can use this event to:
 * <ul>
 *   <li>Add qualifiers via {@link #addQualifier(Class)}</li>
 *   <li>Add scopes via {@link #addScope(Class, boolean, boolean)}</li>
 *   <li>Add stereotypes via {@link #addStereotype(Class, Annotation...)}</li>
 *   <li>Add interceptor bindings via {@link #addInterceptorBinding(Class, Annotation...)}</li>
 *   <li>Add annotated types programmatically via {@link #addAnnotatedType(AnnotatedType, String)}</li>
 * </ul>
 *
 * @see BeforeBeanDiscovery
 */
public class BeforeBeanDiscoveryImpl extends PhaseAware implements BeforeBeanDiscovery, ObserverInvocationLifecycle, ExtensionAwareObserverInvocation {

    private final KnowledgeBase knowledgeBase;
    private final BeanManager beanManager;
    private final ThreadLocal<Extension> currentObserverExtension = new ThreadLocal<>();
    private final ThreadLocal<java.util.List<Runnable>> endOfObserverActions =
            ThreadLocal.withInitial(ArrayList::new);
    private final ThreadLocal<Boolean> observerInvocationActive = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public BeforeBeanDiscoveryImpl(MessageHandler messageHandler, KnowledgeBase knowledgeBase, BeanManager beanManager) {
        super(messageHandler);
        this.knowledgeBase = knowledgeBase;
        this.beanManager = beanManager;
    }

    @Override
    public void addAnnotatedType(AnnotatedType<?> type, String id) {
        assertObserverInvocationActive();
        checkNotNull(type, "AnnotatedType");
        checkNotNull(id, "ID");
        info(Phase.BEFORE_BEAN_DISCOVERY, "Adding annotated type: " + type.getJavaClass().getName() +
                " with ID: " + id);
        knowledgeBase.addAnnotatedType(type, id, currentObserverExtension.get());
    }

    /**
     * Return a configurator that will update the annotated type's metadata when complete() is called.
     * This allows extensions to add/modify annotations on the annotated type
     * (e.g., add @Nonbinding to specific members, add meta-annotations)
     * @param type class of the annotated type
     * @param id unique identifier for the annotated type
     * @return an AnnotatedTypeConfigurator that can be used to configure the annotated type
     * @param <T> type of the annotated type
     */
    @Override
    public <T> AnnotatedTypeConfigurator<T> addAnnotatedType(Class<T> type, String id) {
        assertObserverInvocationActive();
        checkNotNull(type, "Class");
        checkNotNull(id, "ID");

        info(Phase.BEFORE_BEAN_DISCOVERY, "Creating AnnotatedTypeConfigurator for: " +
                type.getName() + " with ID: " + id);

        // Create an AnnotatedType from the class using BeanManager
        AnnotatedType<T> annotatedType = beanManager.createAnnotatedType(type);

        AnnotatedTypeConfiguratorImpl<T> configurator = getAnnotatedTypeConfigurator(id, annotatedType);
        registerEndOfObserverAction(configurator::complete);
        return configurator;
    }

    @Nonnull
    private <T> AnnotatedTypeConfiguratorImpl<T> getAnnotatedTypeConfigurator(String id, AnnotatedType<T> annotatedType) {
        final AtomicBoolean applied = new AtomicBoolean(false);
        return new AnnotatedTypeConfiguratorImpl<T>(annotatedType) {
            @Override
            public AnnotatedType<T> complete() {
                AnnotatedType<T> configured = super.complete();
                if (applied.compareAndSet(false, true)) {
                    knowledgeBase.addAnnotatedType(configured, id, currentObserverExtension.get());
                }
                return configured;
            }
        };
    }

    @Override
    public void addQualifier(Class<? extends Annotation> qualifier) {
        assertObserverInvocationActive();
        checkNotNull(qualifier, "Qualifier");
        info(Phase.BEFORE_BEAN_DISCOVERY, "Adding qualifier: " + qualifier.getSimpleName());
        knowledgeBase.addQualifier(qualifier);
    }

    @Override
    public void addQualifier(AnnotatedType<? extends Annotation> qualifier) {
        assertObserverInvocationActive();
        checkNotNull(qualifier, "Qualifier");
        info(Phase.BEFORE_BEAN_DISCOVERY, "Adding qualifier from AnnotatedType: " +
                qualifier.getJavaClass().getSimpleName());
        registerQualifierMetadata(qualifier.getJavaClass(), qualifier);
    }

    /**
     * Return a configurator that will update the qualifier's metadata when complete() is called.
     * This allows extensions to add/modify annotations on the qualifier annotation type
     * (e.g., add @Nonbinding to specific members, add meta-annotations)
     * @param qualifier class of the qualifier annotation
     * @return an AnnotatedTypeConfigurator that can be used to configure the qualifier
     * @param <T> type of the qualifier annotation
     */
    @Override
    public <T extends Annotation> AnnotatedTypeConfigurator<T> configureQualifier(Class<T> qualifier) {
        assertObserverInvocationActive();
        checkNotNull(qualifier, "Qualifier");
        info(Phase.BEFORE_BEAN_DISCOVERY, "Configuring qualifier: " + qualifier.getSimpleName());

        // Create an AnnotatedType for the qualifier annotation class
        AnnotatedType<T> annotatedType = beanManager.createAnnotatedType(qualifier);

        final AtomicBoolean applied = new AtomicBoolean(false);
        AnnotatedTypeConfiguratorImpl<T> configurator = new AnnotatedTypeConfiguratorImpl<T>(annotatedType) {
            @Override
            public AnnotatedType<T> complete() {
                AnnotatedType<T> configured = super.complete();
                if (applied.compareAndSet(false, true)) {
                    registerQualifierMetadata(qualifier, configured);
                }
                return configured;
            }
        };
        registerEndOfObserverAction(configurator::complete);
        return configurator;
    }

    @Override
    public void addScope(Class<? extends Annotation> scopeType, boolean normal, boolean passivating) {
        assertObserverInvocationActive();
        checkNotNull(scopeType, "Scope type");
        info(Phase.BEFORE_BEAN_DISCOVERY, "Adding scope: " + scopeType.getSimpleName() +
                " (normal=" + normal + ", passivating=" + passivating + ")");
        knowledgeBase.addScope(scopeType, normal, passivating);
    }

    private void registerQualifierMetadata(Class<? extends Annotation> qualifierType,
                                           AnnotatedType<? extends Annotation> qualifierAnnotatedType) {
        if (qualifierType == null) {
            return;
        }

        boolean alreadyRegistered = knowledgeBase.isRegisteredQualifier(qualifierType);
        if (!alreadyRegistered) {
            knowledgeBase.addQualifier(qualifierType);
            info(Phase.BEFORE_BEAN_DISCOVERY, "Completed qualifier configuration: " +
                    qualifierType.getSimpleName());
        } else {
            info(Phase.BEFORE_BEAN_DISCOVERY, "Qualifier already configured: " +
                    qualifierType.getSimpleName());
        }

        if (qualifierAnnotatedType == null) {
            return;
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        java.util.Set<AnnotatedMethod<?>> methods =
                (java.util.Set) qualifierAnnotatedType.getMethods();
        for (AnnotatedMethod<?> method : methods) {
            if (method == null || method.getJavaMember() == null) {
                continue;
            }
            if (!qualifierType.equals(method.getJavaMember().getDeclaringClass())) {
                continue;
            }
            if (hasNonbindingMarker(method) || hasNonbindingAnnotation(method.getJavaMember())) {
                registerDynamicNonbindingMember(qualifierType, method.getJavaMember().getName());
            }
        }
    }

    private boolean hasNonbindingMarker(AnnotatedMethod<?> method) {
        if (method == null || method.getAnnotations() == null) {
            return false;
        }
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation != null && hasNonbindingAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addStereotype(Class<? extends Annotation> stereotype, Annotation... stereotypeDef) {
        assertObserverInvocationActive();
        checkNotNull(stereotype, "Stereotype");
        info(Phase.BEFORE_BEAN_DISCOVERY, "Adding stereotype: " + stereotype.getSimpleName() +
                " with meta-annotations: " + toList(stereotypeDef));
        knowledgeBase.addStereotype(stereotype, stereotypeDef);
    }

    @Override
    public void addInterceptorBinding(AnnotatedType<? extends Annotation> bindingType) {
        assertObserverInvocationActive();
        checkNotNull(bindingType, "AnnotatedType");
        // Extract meta-annotations from the AnnotatedType
        Annotation[] metaAnnotations = bindingType.getAnnotations().toArray(new Annotation[0]);
        info(Phase.BEFORE_BEAN_DISCOVERY, "Adding interceptor binding from AnnotatedType: " +
                bindingType.getJavaClass().getSimpleName() + " with meta-annotations: " + toList(metaAnnotations));
        knowledgeBase.addInterceptorBinding(bindingType.getJavaClass(), metaAnnotations);
    }

    @Override
    public void addInterceptorBinding(Class<? extends Annotation> bindingType, Annotation... bindingTypeDef) {
        assertObserverInvocationActive();
        checkNotNull(bindingType, "Interceptor binding type");
        info(Phase.BEFORE_BEAN_DISCOVERY, "Adding interceptor binding: " + bindingType.getSimpleName() +
                " with meta-annotations: " + toList(bindingTypeDef));
        knowledgeBase.addInterceptorBinding(bindingType, bindingTypeDef);
    }

    /**
     * Return a configurator that will update the binding's metadata when complete() is called.
     * This allows extensions to add/modify annotations on the interceptor binding annotation type
     * (e.g., add @Nonbinding to specific members, add meta-annotations)
     * @param bindingType class of the interceptor binding annotation
     * @return an AnnotatedTypeConfigurator that can be used to configure the interceptor binding
     * @param <T> type of the interceptor binding annotation
     */
    @Override
    public <T extends Annotation> AnnotatedTypeConfigurator<T> configureInterceptorBinding(Class<T> bindingType) {
        assertObserverInvocationActive();
        checkNotNull(bindingType, "Interceptor binding type");
        info(Phase.BEFORE_BEAN_DISCOVERY, "Configuring interceptor binding: " + bindingType.getSimpleName());

        // Create an AnnotatedType for the interceptor binding annotation class
        AnnotatedType<T> annotatedType = beanManager.createAnnotatedType(bindingType);
        final AtomicBoolean applied = new AtomicBoolean(false);
        AnnotatedTypeConfiguratorImpl<T> configurator = new AnnotatedTypeConfiguratorImpl<T>(annotatedType) {
            @Override
            public AnnotatedType<T> complete() {
                AnnotatedType<T> configured = super.complete();
                if (applied.compareAndSet(false, true)) {
                    // Extract any meta-annotations from the configured type
                    Annotation[] metaAnnotations = configured.getAnnotations().toArray(new Annotation[0]);

                    // After configuration, register this as an interceptor binding if not already one
                    if (!knowledgeBase.isRegisteredInterceptorBinding(bindingType)) {
                        knowledgeBase.addInterceptorBinding(bindingType, metaAnnotations);

                        info(Phase.BEFORE_BEAN_DISCOVERY, "Completed interceptor binding configuration: " +
                                bindingType.getSimpleName() + " with meta-annotations: " + toList(metaAnnotations));
                    } else {
                        info(Phase.BEFORE_BEAN_DISCOVERY, "Interceptor with meta-annotations " +
                                toList(metaAnnotations) + " already configured");
                    }
                }
                return configured;
            }
        };
        registerEndOfObserverAction(configurator::complete);
        return configurator;
    }

    @Override
    public void beginObserverInvocation() {
        observerInvocationActive.set(Boolean.TRUE);
    }

    @Override
    public void enterObserverInvocation(Extension extension) {
        currentObserverExtension.set(extension);
    }

    @Override
    public void exitObserverInvocation() {
        currentObserverExtension.remove();
    }

    @Override
    public void endObserverInvocation() {
        try {
            java.util.List<Runnable> actions = endOfObserverActions.get();
            if (actions.isEmpty()) {
                return;
            }
            java.util.List<Runnable> pending = new ArrayList<>(actions);
            actions.clear();
            for (Runnable action : pending) {
                action.run();
            }
        } finally {
            observerInvocationActive.set(Boolean.FALSE);
        }
    }

    private void registerEndOfObserverAction(Runnable action) {
        if (action == null) {
            return;
        }
        endOfObserverActions.get().add(action);
    }

    private void assertObserverInvocationActive() {
        if (!observerInvocationActive.get()) {
            throw new IllegalStateException("BeforeBeanDiscovery methods may only be called during observer method invocation");
        }
    }
}
