package com.threeamigos.common.util.implementations.injection.spi.spievents;

import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import com.threeamigos.common.util.implementations.injection.scopes.CustomContextAdapter;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.implementations.injection.spi.configurators.BeanConfiguratorImpl;
import com.threeamigos.common.util.implementations.injection.spi.configurators.ObserverMethodConfiguratorImpl;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.inject.spi.configurator.BeanConfigurator;
import jakarta.enterprise.inject.spi.configurator.ObserverMethodConfigurator;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * AfterBeanDiscovery event implementation.
 * 
 * <p>Fired after bean discovery completes, before validation. Extensions can use this event to:
 * <ul>
 *   <li>Add custom beans via {@link #addBean()}</li>
 *   <li>Add observer methods via {@link #addObserverMethod()}</li>
 *   <li>Add custom contexts via {@link #addContext(Context)}</li>
 *   <li>Register deployment problems via {@link #addDefinitionError(Throwable)}</li>
 * </ul>
 *
 * @see AfterBeanDiscovery
 */
public class AfterBeanDiscoveryImpl extends PhaseAware
        implements AfterBeanDiscovery, ObserverInvocationLifecycle, ExtensionAwareObserverInvocation {

    private final KnowledgeBase knowledgeBase;
    private final BeanManager beanManager;
    private final Consumer<Object> eventDispatcher;
    private final ThreadLocal<Extension> currentObserverExtension = new ThreadLocal<>();
    private final ThreadLocal<List<Runnable>> endOfObserverActions = ThreadLocal.withInitial(ArrayList::new);
    private final ThreadLocal<Boolean> observerInvocationActive = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final Map<String, AnnotatedType<?>> registeredAnnotatedTypeViews = new ConcurrentHashMap<>();
    private final Map<Class<?>, AnnotatedType<?>> discoveredAnnotatedTypeViews = new ConcurrentHashMap<>();

    public AfterBeanDiscoveryImpl(MessageHandler messageHandler,
                                  KnowledgeBase knowledgeBase,
                                  BeanManager beanManager,
                                  Consumer<Object> eventDispatcher) {
        super(messageHandler);
        this.knowledgeBase = knowledgeBase;
        this.beanManager = beanManager;
        this.eventDispatcher = eventDispatcher;
    }

    @Override
    public void addDefinitionError(Throwable t) {
        assertObserverInvocationActive();
        knowledgeBase.addDefinitionError(Phase.AFTER_BEAN_DISCOVERY, "Definition error from extension", t);
    }

    @Override
    public void addBean(Bean<?> bean) {
        assertObserverInvocationActive();
        checkNotNull(bean, "Bean");
        info(Phase.AFTER_BEAN_DISCOVERY, "Registering synthetic bean: " + bean.getBeanClass().getSimpleName() +
                " with types: " + bean.getTypes() +
                " and stereotypes: " + bean.getStereotypes());
        fireProcessSyntheticBean(bean);
        knowledgeBase.addBean(bean);
    }

    /**
     * Return the configurator directly.<br/>
     * Note: The configurator's complete() method will be called when the extension method returns.<br/>
     * Extensions are responsible for calling createWith() to provide the creation callback.<br/>
     * @return the configurator
     * @param <T> the bean type
     */
    @Override
    public <T> BeanConfigurator<T> addBean() {
        assertObserverInvocationActive();
        info(Phase.AFTER_BEAN_DISCOVERY, "Creating BeanConfigurator for synthetic bean");
        final BeanConfiguratorImpl<T> configurator = new BeanConfiguratorImpl<>(messageHandler, knowledgeBase,
                beanManager instanceof BeanManagerImpl ? (BeanManagerImpl) beanManager : null,
                this::fireProcessSyntheticBean);
        final AtomicBoolean applied = new AtomicBoolean(false);
        registerEndOfObserverAction(() -> {
            if (applied.compareAndSet(false, true)) {
                configurator.complete();
            }
        });
        return configurator;
    }

    /**
     * Return the configurator directly.<br/>
     * Note: The configurator's complete() method returns an ObserverMethod which should be added via
     * addObserverMethod(ObserverMethod)
     * @return the configurator
     * @param <T> the observed type
     */
    @Override
    public <T> ObserverMethodConfigurator<T> addObserverMethod() {
        assertObserverInvocationActive();
        info(Phase.AFTER_BEAN_DISCOVERY, "Creating ObserverMethodConfigurator for synthetic observer");
        final AtomicBoolean applied = new AtomicBoolean(false);
        final ObserverMethodConfiguratorImpl<T> configurator = new ObserverMethodConfiguratorImpl<T>(false) {
            @Override
            public ObserverMethod<T> complete() {
                ObserverMethod<T> observer = super.complete();
                if (applied.compareAndSet(false, true)) {
                    addObserverMethod(observer);
                }
                return observer;
            }
        };
        registerEndOfObserverAction(configurator::complete);
        return configurator;
    }

    @Override
    public void addObserverMethod(ObserverMethod<?> observerMethod) {
        assertObserverInvocationActive();
        checkNotNull(observerMethod, "ObserverMethod");
        Extension sourceExtension = currentObserverExtension.get();
        ObserverMethod<?> effectiveObserverMethod = applyDefaultObserverBeanClass(observerMethod, sourceExtension);
        if (!hasNotifyOverride(effectiveObserverMethod)) {
            knowledgeBase.addDefinitionError(Phase.AFTER_BEAN_DISCOVERY,
                    "ObserverMethod " + effectiveObserverMethod.getClass().getName() +
                            " must override notify(T) or notify(EventContext<T>)", null);
            return;
        }
        info(Phase.AFTER_BEAN_DISCOVERY, "Registering synthetic observer method: observedType=" +
                effectiveObserverMethod.getObservedType() + ", async=" + effectiveObserverMethod.isAsync());
        ProcessSyntheticObserverMethodImpl<?, ?> event = fireProcessSyntheticObserverMethod(effectiveObserverMethod);
        if (event != null && event.isVetoed()) {
            return;
        }
        ObserverMethod<?> finalObserver = event != null ? event.getFinalObserverMethod() : effectiveObserverMethod;
        finalObserver = applyDefaultObserverBeanClass(finalObserver, sourceExtension);
        knowledgeBase.addSyntheticObserverMethod(finalObserver);
    }

    /**
     * Registers a custom context with the container.
     * <p>
     * This method allows portable extensions to register custom scopes by providing
     * a Context implementation. The context will be used for all beans with the
     * corresponding scope annotation.
     * <p>
     * <h3>Example:</h3>
     * <pre>{@code
     * public class MyExtension implements Extension {
     *     public void registerCustomScope(@Observes AfterBeanDiscovery event) {
     *         event.addContext(new MyCustomScopeContext());
     *     }
     * }
     *
     * // Custom context implementation
     * public class MyCustomScopeContext implements Context {
     *     public Class<? extends Annotation> getScope() {
     *         return MyCustomScope.class;
     *     }
     *
     *     public <T> T get(Contextual<T> contextual, CreationalContext<T> ctx) {
     *         // Custom scope logic
     *     }
     *
     *     public <T> T get(Contextual<T> contextual) {
     *         // Return existing instance or null
     *     }
     *
     *     public boolean isActive() {
     *         // Return whether this scope is currently active
     *     }
     * }
     * }</pre>
     *
     * @param context the custom context to register
     * @throws IllegalStateException if the BeanManager is not properly initialized
     * @throws IllegalArgumentException if context is null or if a context for the same scope already exists
     */
    @Override
    public void addContext(Context context) {
        assertObserverInvocationActive();
        checkNotNull(context, "Context");
        info(Phase.AFTER_BEAN_DISCOVERY, "Registering custom context: " + context.getClass().getName() +
                " for scope @" + context.getScope().getSimpleName());

        BeanManagerImpl beanManagerImpl = (BeanManagerImpl) beanManager;
        ContextManager contextManager = beanManagerImpl.getContextManager();

        // Wrap the Jakarta CDI Context in our internal ScopeContext adapter
        CustomContextAdapter adaptedContext = new CustomContextAdapter(context);

        try {
            contextManager.registerContext(context.getScope(), adaptedContext);
        } catch (Exception e) {
            knowledgeBase.addError(Phase.AFTER_BEAN_DISCOVERY, "Failed to register custom context for scope @" +
                    context.getScope().getSimpleName(), e);
        }
    }

    @Override
    public <T> List<AnnotatedType<T>> getAnnotatedTypes(Class<T> type) {
        assertObserverInvocationActive();
        checkNotNull(type, "Class");
        info(Phase.AFTER_BEAN_DISCOVERY, "Getting annotated types for: " + type.getName());
        List<AnnotatedType<T>> result = new ArrayList<>();
        for (Map.Entry<String, AnnotatedType<?>> entry : knowledgeBase.getRegisteredAnnotatedTypes().entrySet()) {
            AnnotatedType<?> annotatedType = entry.getValue();
            if (annotatedType.getJavaClass().equals(type)) {
                result.add(stabilizeRegisteredAnnotatedType(entry.getKey(), annotatedType));
            }
        }
        for (Class<?> discoveredClass : knowledgeBase.getClasses()) {
            if (!type.equals(discoveredClass)) {
                continue;
            }
            AnnotatedType<?> annotatedType = knowledgeBase.getAnnotatedTypeOverride(discoveredClass);
            if (annotatedType == null) {
                annotatedType = beanManager.createAnnotatedType(discoveredClass);
            }
            result.add(stabilizeDiscoveredAnnotatedType(type, annotatedType));
        }
        info(Phase.AFTER_BEAN_DISCOVERY, "Found " + result.size() + " annotated type(s) for: " + type.getName());
        return result;
    }

    @Override
    public <T> AnnotatedType<T> getAnnotatedType(Class<T> type, String id) {
        assertObserverInvocationActive();
        checkNotNull(type, "Class");
        if (id == null) {
            AnnotatedType<?> discovered = knowledgeBase.getAnnotatedTypeOverride(type);
            if (discovered == null && knowledgeBase.getClasses().contains(type)) {
                discovered = beanManager.createAnnotatedType(type);
            }
            if (discovered != null) {
                return stabilizeDiscoveredAnnotatedType(type, discovered);
            }
            for (Map.Entry<String, AnnotatedType<?>> entry : knowledgeBase.getRegisteredAnnotatedTypes().entrySet()) {
                AnnotatedType<?> annotatedType = entry.getValue();
                if (annotatedType.getJavaClass().equals(type)) {
                    return stabilizeRegisteredAnnotatedType(entry.getKey(), annotatedType);
                }
            }
            return null;
        }

        info(Phase.AFTER_BEAN_DISCOVERY, "Getting annotated type: " + type.getName() + " with ID: " + id);

        AnnotatedType<?> annotatedType = knowledgeBase.getRegisteredAnnotatedType(id);
        if (annotatedType != null) {
            // Verify the class matches
            if (!annotatedType.getJavaClass().equals(type)) {
                knowledgeBase.addWarning(Phase.AFTER_BEAN_DISCOVERY, "AnnotatedType with ID '" + id +
                        "' has class " + annotatedType.getJavaClass().getName() + " but requested type is " +
                        type.getName());
                return null;
            }
            return stabilizeRegisteredAnnotatedType(id, annotatedType);
        }

        info(Phase.AFTER_BEAN_DISCOVERY, "No annotated type found with ID: " + id);
        return null;
    }

    @Override
    public void beginObserverInvocation() {
        observerInvocationActive.set(Boolean.TRUE);
    }

    @Override
    public void endObserverInvocation() {
        try {
            List<Runnable> actions = endOfObserverActions.get();
            if (actions.isEmpty()) {
                return;
            }
            List<Runnable> pending = new ArrayList<>(actions);
            actions.clear();
            for (Runnable action : pending) {
                action.run();
            }
        } finally {
            observerInvocationActive.set(Boolean.FALSE);
        }
    }

    @Override
    public void enterObserverInvocation(Extension extension) {
        currentObserverExtension.set(extension);
    }

    @Override
    public void exitObserverInvocation() {
        currentObserverExtension.remove();
    }

    private void registerEndOfObserverAction(Runnable action) {
        if (action == null) {
            return;
        }
        endOfObserverActions.get().add(action);
    }

    private void assertObserverInvocationActive() {
        if (!observerInvocationActive.get()) {
            throw new IllegalStateException("AfterBeanDiscovery methods may only be called during observer method invocation");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> AnnotatedType<T> stabilizeRegisteredAnnotatedType(String id, AnnotatedType<?> annotatedType) {
        if (id == null || annotatedType == null) {
            return (AnnotatedType<T>) annotatedType;
        }
        AnnotatedType<?> stable = registeredAnnotatedTypeViews.computeIfAbsent(
                id,
                ignored -> createStableAnnotatedTypeView(annotatedType));
        return (AnnotatedType<T>) stable;
    }

    @SuppressWarnings("unchecked")
    private <T> AnnotatedType<T> stabilizeDiscoveredAnnotatedType(Class<T> type, AnnotatedType<?> annotatedType) {
        if (type == null || annotatedType == null) {
            return (AnnotatedType<T>) annotatedType;
        }
        AnnotatedType<?> stable = discoveredAnnotatedTypeViews.computeIfAbsent(
                type,
                ignored -> createStableAnnotatedTypeView(annotatedType));
        return (AnnotatedType<T>) stable;
    }

    private AnnotatedType<?> createStableAnnotatedTypeView(AnnotatedType<?> annotatedType) {
        if (annotatedType == null || isReflexiveEquals(annotatedType)) {
            return annotatedType;
        }
        return new StableAnnotatedTypeView<>(annotatedType);
    }

    //FIXME ???
    private boolean isReflexiveEquals(AnnotatedType<?> annotatedType) {
        try {
            return annotatedType.equals(annotatedType);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static final class StableAnnotatedTypeView<T> implements AnnotatedType<T> {

        private final AnnotatedType<T> delegate;

        @SuppressWarnings("unchecked")
        private StableAnnotatedTypeView(AnnotatedType<?> delegate) {
            this.delegate = (AnnotatedType<T>) delegate;
        }

        @Override
        public Class<T> getJavaClass() {
            return delegate.getJavaClass();
        }

        @Override
        public Set<AnnotatedConstructor<T>> getConstructors() {
            return delegate.getConstructors();
        }

        @Override
        public Set<AnnotatedMethod<? super T>> getMethods() {
            return delegate.getMethods();
        }

        @Override
        public Set<AnnotatedField<? super T>> getFields() {
            return delegate.getFields();
        }

        @Override
        public java.lang.reflect.Type getBaseType() {
            return delegate.getBaseType();
        }

        @Override
        public Set<java.lang.reflect.Type> getTypeClosure() {
            return delegate.getTypeClosure();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return delegate.getAnnotation(annotationType);
        }

        @Override
        public Set<Annotation> getAnnotations() {
            return delegate.getAnnotations();
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return delegate.isAnnotationPresent(annotationType);
        }

        @Override
        public <A extends Annotation> Set<A> getAnnotations(Class<A> annotationType) {
            return delegate.getAnnotations(annotationType);
        }
    }

    private void fireProcessSyntheticBean(Bean<?> bean) {
        if (eventDispatcher == null || bean == null) {
            return;
        }
        ProcessSyntheticBeanImpl<?> event = new ProcessSyntheticBeanImpl<>(
                messageHandler, knowledgeBase, bean, currentObserverExtension.get());
        eventDispatcher.accept(event);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ProcessSyntheticObserverMethodImpl<?, ?> fireProcessSyntheticObserverMethod(ObserverMethod<?> observerMethod) {
        if (eventDispatcher == null || observerMethod == null) {
            return null;
        }
        ProcessSyntheticObserverMethodImpl event =
                new ProcessSyntheticObserverMethodImpl(
                        messageHandler, knowledgeBase, observerMethod, currentObserverExtension.get());
        eventDispatcher.accept(event);
        return event;
    }

    private ObserverMethod<?> applyDefaultObserverBeanClass(ObserverMethod<?> observerMethod, Extension sourceExtension) {
        if (observerMethod == null || observerMethod.getBeanClass() != null || sourceExtension == null) {
            return observerMethod;
        }
        return new ObserverMethodWithDefaultBeanClass<>(observerMethod, sourceExtension.getClass());
    }

    private static final class ObserverMethodWithDefaultBeanClass<T> implements ObserverMethod<T> {
        private final ObserverMethod<?> delegate;
        private final Class<?> defaultBeanClass;

        private ObserverMethodWithDefaultBeanClass(ObserverMethod<?> delegate, Class<?> defaultBeanClass) {
            this.delegate = delegate;
            this.defaultBeanClass = defaultBeanClass;
        }

        @Override
        public Class<?> getBeanClass() {
            return defaultBeanClass;
        }

        @Override
        public java.lang.reflect.Type getObservedType() {
            return delegate.getObservedType();
        }

        @Override
        public Set<Annotation> getObservedQualifiers() {
            Set<Annotation> qualifiers = delegate.getObservedQualifiers();
            return qualifiers == null ? Collections.emptySet() : qualifiers;
        }

        @Override
        public jakarta.enterprise.event.Reception getReception() {
            return delegate.getReception();
        }

        @Override
        public jakarta.enterprise.event.TransactionPhase getTransactionPhase() {
            return delegate.getTransactionPhase();
        }

        @Override
        public void notify(T event) {
            @SuppressWarnings("unchecked")
            ObserverMethod<T> typedDelegate = (ObserverMethod<T>) delegate;
            typedDelegate.notify(event);
        }

        @Override
        public void notify(EventContext<T> eventContext) {
            @SuppressWarnings("unchecked")
            ObserverMethod<T> typedDelegate = (ObserverMethod<T>) delegate;
            typedDelegate.notify(eventContext);
        }

        @Override
        public boolean isAsync() {
            return delegate.isAsync();
        }

        @Override
        public int getPriority() {
            return delegate.getPriority();
        }
    }

    private boolean hasNotifyOverride(ObserverMethod<?> observerMethod) {
        Class<?> observerClass = observerMethod.getClass();
        try {
            java.lang.reflect.Method notifyWithEventContext =
                    observerClass.getMethod("notify", EventContext.class);
            if (!ObserverMethod.class.equals(notifyWithEventContext.getDeclaringClass())) {
                return true;
            }
        } catch (NoSuchMethodException ignored) {
            // Fall through.
        }

        try {
            java.lang.reflect.Method notifyWithPayload = observerClass.getMethod("notify", Object.class);
            return !ObserverMethod.class.equals(notifyWithPayload.getDeclaringClass());
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }
}
