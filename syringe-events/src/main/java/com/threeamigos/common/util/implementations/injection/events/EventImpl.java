package com.threeamigos.common.util.implementations.injection.events;

import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import com.threeamigos.common.util.implementations.injection.scopes.ScopeContext;
import com.threeamigos.common.util.implementations.injection.scopes.RequestScopedContext;
import com.threeamigos.common.util.implementations.injection.scopes.ConversationScopedContext;
import com.threeamigos.common.util.implementations.injection.scopes.SessionScopedContext;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import com.threeamigos.common.util.implementations.injection.resolution.BeanResolver;
import com.threeamigos.common.util.implementations.injection.resolution.InstanceImpl;
import com.threeamigos.common.util.implementations.injection.types.TypeHelper;
import com.threeamigos.common.util.implementations.injection.scopes.InjectionPointImpl;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.injection.annotations.AnnotationComparator;
import com.threeamigos.common.util.implementations.injection.resolution.GenericTypeResolver;
import com.threeamigos.common.util.implementations.injection.util.LifecycleMethodHelper;
import com.threeamigos.common.util.implementations.injection.types.TypeClosureHelper;
import com.threeamigos.common.util.implementations.injection.util.tx.TransactionServices;
import com.threeamigos.common.util.implementations.injection.util.tx.NoOpTransactionServices;
import com.threeamigos.common.util.implementations.injection.util.tx.TransactionSynchronizationCallbacks;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.event.ObserverException;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.UndeclaredThrowableException;
import java.lang.reflect.WildcardType;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.*;
import static com.threeamigos.common.util.implementations.injection.types.TypeHelper.getRawType;

/**
 * CDI 4.1 Event implementation for firing synchronous and asynchronous events.
 *
 * <p>This class implements the {@link Event} interface from CDI 4.1 specification,
 * providing mechanisms to fire events to registered observer methods annotated with
 * {@link jakarta.enterprise.event.Observes @Observes} or
 * {@link jakarta.enterprise.event.ObservesAsync @ObservesAsync}.
 *
 * <p><b>CDI 4.1 Event Features:</b>
 * <ul>
 *   <li>Synchronous event firing via {@link #fire(Object)}</li>
 *   <li>Asynchronous event firing via {@link #fireAsync(Object)} and {@link #fireAsync(Object, NotificationOptions)}</li>
 *   <li>Observer method matching based on the event type and qualifiers</li>
 *   <li>Priority-based observer ordering (lower priority = earlier execution)</li>
 *   <li>Reception condition handling (IF_EXISTS vs. ALWAYS)</li>
 *   <li>Transaction phase support for synchronous observers</li>
 *   <li>Dynamic qualifier selection via {@link #select(Annotation...)}</li>
 * </ul>
 *
 * <p><b>Observer Matching Rules:</b>
 * <ul>
 *   <li>Observer event type must be assignable from the fired event type</li>
 *   <li>All observer qualifiers must be present in the fired event qualifiers</li>
 *   <li>Synchronous observers (@Observes) are notified during fire()</li>
 *   <li>Asynchronous observers (@ObservesAsync) are notified during fireAsync()</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Event firing can be performed
 * concurrently from multiple threads. Each observer method invocation is isolated.
 *
 * @param <T> the type of events this Event instance can fire
 * @author Stefano Reksten
 * @see Event
 * @see jakarta.enterprise.event.Observes
 * @see jakarta.enterprise.event.ObservesAsync
 * @see ObserverMethodInfo
 */
public class EventImpl<T> implements Event<T>, Serializable {

    private static final long serialVersionUID = 1L;

    private final Type eventType;
    private final Set<Annotation> qualifiers;
    private final KnowledgeBase knowledgeBase;
    private final BeanResolver beanResolver;
    private final ContextManager contextManager;
    private final TypeHelper typeHelper;
    private final TransactionServices transactionServices;
    private final ContextTokenProvider tokenProvider;
    private final InjectionPoint firingInjectionPoint;
    private final boolean allowStartupEventDispatch;
    private static final AtomicBoolean TRANSACTION_DOWNGRADE_WARNED = new AtomicBoolean(false);
    private static final ConcurrentHashMap<Class<? extends Annotation>, AtomicBoolean> INACTIVE_SCOPE_WARNED =
        new ConcurrentHashMap<>();
    private static volatile ExecutorService defaultAsyncExecutor = createDefaultAsyncExecutor();

    private static ExecutorService createDefaultAsyncExecutor() {
        return Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(@Nonnull Runnable runnable) {
                Thread thread = new Thread(runnable, "syringe-event-async-" + counter.getAndIncrement());
                thread.setDaemon(true);
                try {
                    thread.setContextClassLoader(EventImpl.class.getClassLoader());
                } catch (SecurityException ignored) {
                    // Best-effort only.
                }
                return thread;
            }
        });
    }

    private static ExecutorService getDefaultAsyncExecutor() {
        ExecutorService executor = defaultAsyncExecutor;
        if (executor != null && !executor.isShutdown() && !executor.isTerminated()) {
            return executor;
        }
        synchronized (EventImpl.class) {
            executor = defaultAsyncExecutor;
            if (executor == null || executor.isShutdown() || executor.isTerminated()) {
                defaultAsyncExecutor = createDefaultAsyncExecutor();
                executor = defaultAsyncExecutor;
            }
            return executor;
        }
    }

    private static void shutdownDefaultAsyncExecutor() {
        ExecutorService executor;
        synchronized (EventImpl.class) {
            executor = defaultAsyncExecutor;
            defaultAsyncExecutor = null;
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * Creates an Event instance for firing events of a specific type with qualifiers.
     *
     * @param eventType the type of events to fire
     * @param qualifiers the qualifiers for event filtering
     * @param knowledgeBase the knowledge base containing registered observers
     * @param beanResolver the resolver for getting observer bean instances
     * @param contextManager the context manager for checking bean existence in scopes
     */
    public EventImpl(Type eventType, Set<Annotation> qualifiers, KnowledgeBase knowledgeBase,
                    BeanResolver beanResolver, ContextManager contextManager, TransactionServices transactionServices) {
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        validateEventType(this.eventType, false);
        this.qualifiers = Objects.requireNonNull(qualifiers, "qualifiers cannot be null");
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");
        this.beanResolver = Objects.requireNonNull(beanResolver, "beanResolver cannot be null");
        this.contextManager = Objects.requireNonNull(contextManager, "contextManager cannot be null");
        this.typeHelper = new TypeHelper();
        this.transactionServices = Objects.requireNonNull(transactionServices, "transactionServices cannot be null");
        this.tokenProvider = new NoopContextTokenProvider();
        this.firingInjectionPoint = null;
        this.allowStartupEventDispatch = false;
    }

    public EventImpl(Type eventType, Set<Annotation> qualifiers, KnowledgeBase knowledgeBase,
                     BeanResolver beanResolver, ContextManager contextManager,
                     TransactionServices transactionServices, ContextTokenProvider tokenProvider) {
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        validateEventType(this.eventType, false);
        this.qualifiers = Objects.requireNonNull(qualifiers, "qualifiers cannot be null");
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");
        this.beanResolver = Objects.requireNonNull(beanResolver, "beanResolver cannot be null");
        this.contextManager = Objects.requireNonNull(contextManager, "contextManager cannot be null");
        this.typeHelper = new TypeHelper();
        this.transactionServices = Objects.requireNonNull(transactionServices, "transactionServices cannot be null");
        this.tokenProvider = tokenProvider == null ? new NoopContextTokenProvider() : tokenProvider;
        this.firingInjectionPoint = null;
        this.allowStartupEventDispatch = false;
    }

    public EventImpl(Type eventType, Set<Annotation> qualifiers, KnowledgeBase knowledgeBase,
                     BeanResolver beanResolver, ContextManager contextManager,
                     TransactionServices transactionServices, ContextTokenProvider tokenProvider,
                     InjectionPoint firingInjectionPoint) {
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        validateEventType(this.eventType, firingInjectionPoint != null);
        this.qualifiers = Objects.requireNonNull(qualifiers, "qualifiers cannot be null");
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");
        this.beanResolver = Objects.requireNonNull(beanResolver, "beanResolver cannot be null");
        this.contextManager = Objects.requireNonNull(contextManager, "contextManager cannot be null");
        this.typeHelper = new TypeHelper();
        this.transactionServices = Objects.requireNonNull(transactionServices, "transactionServices cannot be null");
        this.tokenProvider = tokenProvider == null ? new NoopContextTokenProvider() : tokenProvider;
        this.firingInjectionPoint = firingInjectionPoint;
        this.allowStartupEventDispatch = false;
    }

    public EventImpl(Type eventType, Set<Annotation> qualifiers, KnowledgeBase knowledgeBase,
                     BeanResolver beanResolver, ContextManager contextManager,
                     TransactionServices transactionServices, ContextTokenProvider tokenProvider,
                     InjectionPoint firingInjectionPoint, boolean allowStartupEventDispatch) {
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        validateEventType(this.eventType, firingInjectionPoint != null);
        this.qualifiers = Objects.requireNonNull(qualifiers, "qualifiers cannot be null");
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");
        this.beanResolver = Objects.requireNonNull(beanResolver, "beanResolver cannot be null");
        this.contextManager = Objects.requireNonNull(contextManager, "contextManager cannot be null");
        this.typeHelper = new TypeHelper();
        this.transactionServices = Objects.requireNonNull(transactionServices, "transactionServices cannot be null");
        this.tokenProvider = tokenProvider == null ? new NoopContextTokenProvider() : tokenProvider;
        this.firingInjectionPoint = firingInjectionPoint;
        this.allowStartupEventDispatch = allowStartupEventDispatch;
    }

    /**
     * Fires a synchronous event, notifying all matching observer methods annotated with @Observes.
     *
     * <p>This method:
     * <ol>
     *   <li>Finds all synchronous observers matching the event type and qualifiers</li>
     *   <li>Sorts observers by priority (lower = earlier)</li>
     *   <li>Resolves observer bean instances</li>
     *   <li>Invokes each observer method with the event payload</li>
     *   <li>Handles any exceptions thrown by observers</li>
     * </ol>
     *
     * <p>Per CDI 4.1 specification, synchronous observers are invoked in the calling thread
     * during the fire() method execution. Transaction phase handling is currently limited to
     *  the IN_PROGRESS phase.
     *
     * @param event the event payload to fire (must not be null)
     * @throws IllegalArgumentException if the event is null
     * @throws RuntimeException if observer invocation fails
     */
    @Override
    public void fire(T event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        validateEventObject(event);

        // Find all matching synchronous observers
        List<ObserverMethodInfo> matchingObservers = findMatchingObservers(event, false);

        // Sort by priority (lower = earlier)
        matchingObservers.sort(Comparator.comparingInt(ObserverMethodInfo::getPriority));

        boolean txActive = transactionServices.isTransactionActive();
        List<ObserverMethodInfo> beforeCompletion = new ArrayList<>();
        List<ObserverMethodInfo> afterSuccess = new ArrayList<>();
        List<ObserverMethodInfo> afterFailure = new ArrayList<>();
        List<ObserverMethodInfo> afterCompletion = new ArrayList<>();

        // Invoke each observer
        for (ObserverMethodInfo observerInfo : matchingObservers) {
            // Check reception condition per CDI 4.1 specification (Section 10.5.2):
            // - Reception.IF_EXISTS: Only notify if bean instance already exists in scope
            // - Reception.ALWAYS (default): Always notify, creating bean instance if needed
            if (observerInfo.getReception() == Reception.IF_EXISTS) {
                // Only notify if a bean instance already exists in scope
                Bean<?> declaringBean = observerInfo.getDeclaringBean();
                if (declaringBean != null) {
                    Class<? extends Annotation> scopeType = declaringBean.getScope();
                    try {
                        ScopeContext context = contextManager.getContext(scopeType);
                        Object existingInstance = context.getIfExists(declaringBean);

                        // Skip this observer if the bean doesn't exist yet
                        if (existingInstance == null) {
                            continue;
                        }
                    } catch (IllegalArgumentException e) {
                        // Scope isn't registered - skip this observer
                        continue;
                    }
                }
            }
            // If Reception.ALWAYS: invokeObserver() will create the bean if needed via beanResolver

            TransactionPhase phase = observerInfo.getTransactionPhase();

            if (phase == TransactionPhase.IN_PROGRESS) {
                invokeObserverWithContextCheck(observerInfo, event);
            } else if (!txActive) {
                // Spec: if no transaction is active, transactional observers fire immediately
                maybeWarnTransactionalDowngrade(observerInfo);
                invokeObserverWithContextCheck(observerInfo, event);
            } else {
                switch (phase) {
                    case BEFORE_COMPLETION:
                        beforeCompletion.add(observerInfo);
                        break;
                    case AFTER_SUCCESS:
                        afterSuccess.add(observerInfo);
                        break;
                    case AFTER_FAILURE:
                        afterFailure.add(observerInfo);
                        break;
                    case AFTER_COMPLETION:
                        afterCompletion.add(observerInfo);
                        break;
                    default:
                        // fallback
                        invokeObserverWithContextCheck(observerInfo, event);
                }
            }
        }

        if (txActive && (!beforeCompletion.isEmpty() || !afterSuccess.isEmpty() ||
            !afterFailure.isEmpty() || !afterCompletion.isEmpty())) {
            try {
                transactionServices.registerSynchronization(new TransactionSynchronizationCallbacks() {
                    @Override
                    public void beforeCompletion() {
                        invokeObserverList(beforeCompletion, event);
                    }

                    @Override
                    public void afterCompletion(boolean committed) {
                        if (committed) {
                            invokeObserverList(afterSuccess, event);
                        } else {
                            invokeObserverList(afterFailure, event);
                        }
                        invokeObserverList(afterCompletion, event);
                    }
                });
            } catch (RuntimeException registrationFailure) {
                // CDI 4.1: If synchronization callbacks cannot be registered, notify BEFORE_COMPLETION,
                // AFTER_COMPLETION and AFTER_FAILURE immediately, and skip AFTER_SUCCESS observers.
                invokeObserverList(beforeCompletion, event);
                invokeObserverList(afterFailure, event);
                invokeObserverList(afterCompletion, event);
            }
        }
    }

    private void maybeWarnTransactionalDowngrade(ObserverMethodInfo observerInfo) {
        if (transactionServices instanceof NoOpTransactionServices) {
            if (TRANSACTION_DOWNGRADE_WARNED.compareAndSet(false, true)) {
                String method = observerInfo.getObserverMethod() != null
                    ? observerInfo.getObserverMethod().toGenericString()
                    : "synthetic observer";
                System.out.println("[Event] Transactional observer downgraded to immediate (no transaction services). First occurrence: " + method);
            }
        }
    }

    /**
     * Fires an asynchronous event, notifying all matching observer methods annotated with @ObservesAsync.
     *
     * <p>This method:
     * <ol>
     *   <li>Finds all asynchronous observers matching the event type and qualifiers</li>
     *   <li>Sorts observers by priority (lower = earlier)</li>
     *   <li>Creates a CompletionStage that invokes observers asynchronously</li>
     *   <li>Uses the ForkJoinPool.commonPool() as the default executor</li>
     *   <li>Returns immediately, with observers executing in background</li>
     * </ol>
     *
     * <p>Per CDI 4.1 specification, asynchronous observers are executed in a separate thread.
     * The returned CompletionStage completes when all observers have finished executing.
     *
     * @param <U> the subtype of T
     * @param event the event payload to fire (must not be null)
     * @return CompletionStage that completes when all async observers finish
     * @throws IllegalArgumentException if the event is null
     */
    @Override
    public <U extends T> CompletionStage<U> fireAsync(U event) {
        return fireAsync(event, NotificationOptions.ofExecutor(getDefaultAsyncExecutor()));
    }

    /**
     * Fires an asynchronous event with custom notification options.
     *
     * <p>This method allows customization of async event delivery through {@link NotificationOptions}:
     * <ul>
     *   <li>Custom executor for observer execution</li>
     *   <li>Timeout settings</li>
     *   <li>Other implementation-specific options</li>
     * </ul>
     *
     * <p>Observers are invoked sequentially in priority order within the async execution context.
     * If any observer throws an exception, the CompletionStage completes exceptionally.
     *
     * @param <U> the subtype of T
     * @param event the event payload to fire (must not be null)
     * @param options notification options including executor (must not be null)
     * @return CompletionStage that completes when all async observers finish
     * @throws IllegalArgumentException if event or options is null
     */
    @Override
    public <U extends T> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        validateEventObject(event);
        if (options == null) {
            throw new IllegalArgumentException("NotificationOptions cannot be null");
        }

        // Find all matching asynchronous observers
        List<ObserverMethodInfo> matchingObservers = findMatchingObservers(event, true);

        // Sort by priority (lower = earlier)
        matchingObservers.sort(Comparator.comparingInt(ObserverMethodInfo::getPriority));

        // Get executor from options or use default
        Executor executor = options.getExecutor();
        if (executor == null) {
            executor = getDefaultAsyncExecutor();
        }

        // Create an async task

        return CompletableFuture.supplyAsync(() -> {
            ScopeContext requestScopeContext = contextManager.getContext(RequestScoped.class);
            boolean activatedRequestContext = false;
            if (!requestScopeContext.isActive() && requestScopeContext instanceof RequestScopedContext) {
                ((RequestScopedContext) requestScopeContext).activateRequest();
                activatedRequestContext = true;
            }
            try {
                List<Throwable> observerFailures = new ArrayList<>();
                for (ObserverMethodInfo observerInfo : matchingObservers) {
                    // Check reception condition per CDI 4.1 specification (Section 10.5.2):
                    // - Reception.IF_EXISTS: Only notify if bean instance already exists in scope
                    // - Reception.ALWAYS (default): Always notify, creating bean instance if needed
                    if (observerInfo.getReception() == Reception.IF_EXISTS) {
                        // Only notify if a bean instance already exists in scope
                        Bean<?> declaringBean = observerInfo.getDeclaringBean();
                        if (declaringBean != null) {
                            Class<? extends Annotation> scopeType = declaringBean.getScope();
                            try {
                                ScopeContext context = contextManager.getContext(scopeType);
                                Object existingInstance = context.getIfExists(declaringBean);

                                // Skip this observer if the bean doesn't exist yet
                                if (existingInstance == null) {
                                    continue;
                                }
                            } catch (IllegalArgumentException e) {
                                // Scope isn't registered - skip this observer
                                continue;
                            }
                        }
                    }
                    // If Reception.ALWAYS: invokeObserver() will create the bean if needed via beanResolver

                    ContextActivation activation = ensureObserverContext(observerInfo);
                    if (activation.isSkip()) {
                        continue;
                    }
                    try {
                        invokeObserver(observerInfo, event);
                    } catch (RuntimeException e) {
                        // Async observer failure aborts that observer, not whole event delivery.
                        observerFailures.add(e);
                    } finally {
                        activation.close();
                    }
                }
                if (!observerFailures.isEmpty()) {
                    Throwable primary = observerFailures.get(0);
                    CompletionException completionException =
                        new CompletionException("Asynchronous observer notification failed", primary);
                    for (Throwable failure : observerFailures) {
                        completionException.addSuppressed(failure);
                    }
                    throw completionException;
                }
            } finally {
                if (activatedRequestContext) {
                    ((RequestScopedContext) requestScopeContext).deactivateRequest();
                }
            }
            return event;
        }, executor);
    }

    /**
     * Creates a refined Event instance with additional qualifiers for more specific observer selection.
     *
     * <p>This method allows dynamic narrowing of the observer set at runtime by adding qualifiers.
     * The new Event instance will only notify observers that match ALL qualifiers (original and new).
     *
     * <p>Example:
     * <pre>{@code
     * @Inject Event<String> event;
     *
     * // Fire to all observers
     * event.fire("message");
     *
     * // Fire only to @Important observers
     * event.select(new ImportantLiteral()).fire("urgent message");
     * }</pre>
     *
     * @param qualifiers additional qualifiers to filter observers
     * @return new Event instance with combined qualifiers
     * @throws IllegalArgumentException if any qualifier is null
     */
    @Override
    public Event<T> select(Annotation... qualifiers) {
        validateAdditionalQualifiers(qualifiers);
        Set<Annotation> newQualifiers = mergeSelectedQualifiers(this.qualifiers, qualifiers);
        return new EventImpl<>(eventType, newQualifiers, knowledgeBase, beanResolver, contextManager,
                transactionServices, tokenProvider, firingInjectionPoint, allowStartupEventDispatch);
    }

    /**
     * Creates a refined Event instance for a subtype with additional qualifiers.
     *
     * <p>This method allows both type narrowing and qualifier refinement. The subtype must
     * be assignable from the current event type.
     *
     * <p>Example:
     * <pre>{@code
     * @Inject Event<Object> event;
     *
     * // Fire specific type with qualifier
     * event.select(String.class, new ImportantLiteral()).fire("message");
     * }</pre>
     *
     * @param <U> the subtype of T
     * @param subtype the class of the subtype
     * @param qualifiers additional qualifiers to filter observers
     * @return new Event instance for the subtype with combined qualifiers
     * @throws IllegalArgumentException if subtype or any qualifier is null
     */
    @Override
    public <U extends T> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
        if (subtype == null) {
            throw new IllegalArgumentException("Subtype cannot be null");
        }
        validateEventType(subtype);
        validateAdditionalQualifiers(qualifiers);

        Set<Annotation> newQualifiers = mergeSelectedQualifiers(this.qualifiers, qualifiers);

        // Create raw EventImpl and cast - this is safe because EventImpl<U> implements Event<U>
        return new EventImpl<>(subtype, newQualifiers, knowledgeBase, beanResolver, contextManager,
                transactionServices, tokenProvider, firingInjectionPoint, allowStartupEventDispatch);
    }

    /**
     * Creates a refined Event instance for a type literal with additional qualifiers.
     *
     * <p>This method is used for generic type selection where type parameters need to be preserved.
     *
     * <p>Example:
     * <pre>{@code
     * @Inject Event<Object> event;
     *
     * // Fire generic type with qualifier
     * event.select(new TypeLiteral<List<String>>(){}, new ImportantLiteral()).fire(list);
     * }</pre>
     *
     * @param <U> the subtype of T
     * @param subtype the type literal of the subtype
     * @param qualifiers additional qualifiers to filter observers
     * @return new Event instance for the subtype with combined qualifiers
     * @throws IllegalArgumentException if subtype or any qualifier is null
     */
    @Override
    public <U extends T> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        if (subtype == null) {
            throw new IllegalArgumentException("Subtype cannot be null");
        }
        validateEventType(subtype.getType());
        validateAdditionalQualifiers(qualifiers);

        Set<Annotation> newQualifiers = mergeSelectedQualifiers(this.qualifiers, qualifiers);

        // Create raw EventImpl and cast - this is safe because EventImpl<U> implements Event<U>
        return new EventImpl<>(subtype.getType(), newQualifiers, knowledgeBase, beanResolver, contextManager,
                transactionServices, tokenProvider, firingInjectionPoint, allowStartupEventDispatch);
    }

    private void validateEventType(Type type) {
        validateEventType(type, false);
    }

    private void validateEventType(Type type, boolean allowUnresolvableTypeVariables) {
        if (!allowUnresolvableTypeVariables && containsUnresolvableTypeVariable(type)) {
            throw new IllegalArgumentException(
                    "Event type may not contain an unresolvable type variable: " + type.getTypeName());
        }
    }

    private void validateAdditionalQualifiers(Annotation[] qualifiers) {
        if (qualifiers == null || qualifiers.length == 0) {
            return;
        }

        Set<Class<? extends Annotation>> seenNonRepeatableQualifiers = new HashSet<>();
        for (Annotation qualifier : qualifiers) {
            if (qualifier == null) {
                throw new IllegalArgumentException("Qualifier cannot be null");
            }

            Class<? extends Annotation> qualifierType = qualifier.annotationType();
            if (!hasQualifierAnnotation(qualifierType)) {
                throw new IllegalArgumentException(
                        "Annotation is not a qualifier type: " + qualifierType.getName());
            }
            if (!hasRuntimeRetention(qualifierType)) {
                throw new IllegalArgumentException(
                        "Qualifier annotation must have @Retention(RUNTIME): " + qualifierType.getName());
            }

            if (!isRepeatableQualifier(qualifierType) && !seenNonRepeatableQualifiers.add(qualifierType)) {
                throw new IllegalArgumentException(
                        "Duplicate non-repeatable qualifier type passed to select(): " + qualifierType.getName());
            }
        }
    }

    private Set<Annotation> mergeSelectedQualifiers(Set<Annotation> existing, Annotation... additional) {
        Set<Annotation> merged = new LinkedHashSet<>();
        if (existing != null) {
            for (Annotation qualifier : existing) {
                if (qualifier == null) {
                    continue;
                }
                merged.add(qualifier);
            }
        }

        Map<Class<? extends Annotation>, Annotation> replacements =
                new LinkedHashMap<>();
        Set<Class<? extends Annotation>> replacedNonRepeatableTypes =
                new HashSet<>();

        if (additional != null) {
            for (Annotation qualifier : additional) {
                if (qualifier == null) {
                    throw new IllegalArgumentException("Qualifier cannot be null");
                }
                Class<? extends Annotation> qualifierType = qualifier.annotationType();
                if (isRepeatableQualifier(qualifierType)) {
                    merged.add(qualifier);
                    continue;
                }
                replacements.put(qualifierType, qualifier);
                replacedNonRepeatableTypes.add(qualifierType);
            }
        }

        if (!replacedNonRepeatableTypes.isEmpty()) {
            merged.removeIf(existingQualifier ->
                    existingQualifier != null &&
                            replacedNonRepeatableTypes.contains(existingQualifier.annotationType()) &&
                            !isRepeatableQualifier(existingQualifier.annotationType()));
        }

        merged.addAll(replacements.values());

        if (containsExplicitNonDefaultQualifier(merged)) {
            merged.removeIf(qualifier -> qualifier != null && isDefaultQualifierType(qualifier.annotationType()));
        }
        return merged;
    }

    private boolean containsExplicitNonDefaultQualifier(Collection<Annotation> qualifiers) {
        if (qualifiers == null || qualifiers.isEmpty()) {
            return false;
        }
        for (Annotation qualifier : qualifiers) {
            if (qualifier == null) {
                continue;
            }
            Class<? extends Annotation> qualifierType = qualifier.annotationType();
            if (isAnyQualifierType(qualifierType) || isDefaultQualifierType(qualifierType)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean isDefaultQualifierType(Class<? extends Annotation> qualifierType) {
        return hasDefaultAnnotation(qualifierType);
    }

    private boolean isAnyQualifierType(Class<? extends Annotation> qualifierType) {
        return hasAnyAnnotation(qualifierType);
    }

    private boolean isRepeatableQualifier(Class<? extends Annotation> qualifierType) {
        return hasRepeatableAnnotation(qualifierType);
    }

    private boolean hasRuntimeRetention(Class<? extends Annotation> annotationType) {
        java.lang.annotation.Retention retention = getRetentionAnnotation(annotationType);
        return retention != null && RetentionPolicy.RUNTIME.equals(retention.value());
    }

    private void validateEventObject(Object event) {
        Class<?> runtimeType = event.getClass();
        if (runtimeType.getTypeParameters().length > 0) {
            if (!areRuntimeTypeVariablesResolvable(runtimeType, eventType)) {
                throw new IllegalArgumentException(
                        "Runtime type of event object contains unresolvable type variable(s): " +
                                runtimeType.getName());
            }
        }

        if (isContainerLifecyclePayloadTypeName(runtimeType.getName()) && !allowStartupEventDispatch) {
            throw new IllegalArgumentException(
                    "Application must not manually fire events with payload type " + runtimeType.getName());
        }

        if (isContainerLifecycleEventType(runtimeType)) {
            throw new IllegalArgumentException(
                    "Runtime type of event object is assignable to a container lifecycle event type: " +
                            runtimeType.getName());
        }
    }

    private boolean areRuntimeTypeVariablesResolvable(Class<?> runtimeType, Type selectedEventType) {
        Set<Type> runtimeTypeClosure = TypeClosureHelper.extractTypesFromClass(runtimeType, true);
        Map<TypeVariable<?>, Type> resolvedTypeVariables =
                resolveRuntimeTypeVariables(selectedEventType, runtimeTypeClosure);

        for (TypeVariable<?> runtimeTypeVariable : runtimeType.getTypeParameters()) {
            Type resolvedType = resolveTypeVariable(runtimeTypeVariable, resolvedTypeVariables, new HashSet<>());
            if (resolvedType == null || containsUnresolvableTypeVariable(resolvedType)) {
                return false;
            }
        }
        return true;
    }

    private Map<TypeVariable<?>, Type> resolveRuntimeTypeVariables(Type selectedEventType, Set<Type> runtimeTypeClosure) {
        Map<TypeVariable<?>, Type> resolvedTypeVariables = new HashMap<>();
        Class<?> selectedRawType = getRawType(selectedEventType);
        if (selectedRawType == null) {
            return resolvedTypeVariables;
        }

        Type selectedTemplate = null;
        for (Type candidate : runtimeTypeClosure) {
            Class<?> candidateRawType = getRawType(candidate);
            if (selectedRawType.equals(candidateRawType)) {
                selectedTemplate = candidate;
                break;
            }
        }

        if (selectedTemplate != null) {
            unifyTypeTemplates(selectedTemplate, selectedEventType, resolvedTypeVariables);
        }
        return resolvedTypeVariables;
    }

    private void unifyTypeTemplates(Type templateType, Type selectedType, Map<TypeVariable<?>, Type> resolvedTypeVariables) {
        if (templateType instanceof TypeVariable<?>) {
            resolvedTypeVariables.put((TypeVariable<?>) templateType, selectedType);
            return;
        }

        if (templateType instanceof ParameterizedType && selectedType instanceof ParameterizedType) {
            ParameterizedType templateParameterizedType = (ParameterizedType) templateType;
            ParameterizedType selectedParameterizedType = (ParameterizedType) selectedType;
            Type templateRawType = templateParameterizedType.getRawType();
            Type selectedRawType = selectedParameterizedType.getRawType();
            if (!Objects.equals(templateRawType, selectedRawType)) {
                return;
            }
            Type[] templateArguments = templateParameterizedType.getActualTypeArguments();
            Type[] selectedArguments = selectedParameterizedType.getActualTypeArguments();
            for (int i = 0; i < templateArguments.length && i < selectedArguments.length; i++) {
                unifyTypeTemplates(templateArguments[i], selectedArguments[i], resolvedTypeVariables);
            }
            return;
        }

        if (templateType instanceof GenericArrayType && selectedType instanceof GenericArrayType) {
            GenericArrayType templateArrayType = (GenericArrayType) templateType;
            GenericArrayType selectedArrayType = (GenericArrayType) selectedType;
            unifyTypeTemplates(
                    templateArrayType.getGenericComponentType(),
                    selectedArrayType.getGenericComponentType(),
                    resolvedTypeVariables
            );
            return;
        }

        if (templateType instanceof GenericArrayType
                && selectedType instanceof Class<?>
                && ((Class<?>) selectedType).isArray()) {
            unifyTypeTemplates(
                    ((GenericArrayType) templateType).getGenericComponentType(),
                    ((Class<?>) selectedType).getComponentType(),
                    resolvedTypeVariables
            );
        }
    }

    private Type resolveTypeVariable(TypeVariable<?> typeVariable,
                                     Map<TypeVariable<?>, Type> resolvedTypeVariables,
                                     Set<TypeVariable<?>> visited) {
        if (!visited.add(typeVariable)) {
            return null;
        }

        Type resolved = resolvedTypeVariables.get(typeVariable);
        if (resolved == null) {
            return null;
        }
        if (resolved instanceof TypeVariable<?>) {
            return resolveTypeVariable((TypeVariable<?>) resolved, resolvedTypeVariables, visited);
        }
        return resolved;
    }

    private Type resolveTypeVariables(Type type, Map<TypeVariable<?>, Type> resolvedTypeVariables) {
        if (type instanceof TypeVariable<?>) {
            Type resolved = resolveTypeVariable((TypeVariable<?>) type, resolvedTypeVariables, new HashSet<>());
            return resolved != null ? resolved : type;
        }

        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type[] originalArguments = parameterizedType.getActualTypeArguments();
            Type[] resolvedArguments = new Type[originalArguments.length];
            boolean changed = false;
            for (int i = 0; i < originalArguments.length; i++) {
                Type resolvedArgument = resolveTypeVariables(originalArguments[i], resolvedTypeVariables);
                resolvedArguments[i] = resolvedArgument;
                if (!Objects.equals(resolvedArgument, originalArguments[i])) {
                    changed = true;
                }
            }
            Type ownerType = parameterizedType.getOwnerType();
            Type resolvedOwnerType = ownerType == null ? null : resolveTypeVariables(ownerType, resolvedTypeVariables);
            if (!Objects.equals(ownerType, resolvedOwnerType)) {
                changed = true;
            }
            if (!changed) {
                return type;
            }
            return new SerializableParameterizedType(
                    parameterizedType.getRawType(),
                    resolvedOwnerType,
                    resolvedArguments
            );
        }

        if (type instanceof GenericArrayType) {
            GenericArrayType arrayType = (GenericArrayType) type;
            Type componentType = arrayType.getGenericComponentType();
            Type resolvedComponentType = resolveTypeVariables(componentType, resolvedTypeVariables);
            if (Objects.equals(componentType, resolvedComponentType)) {
                return type;
            }
            return new SerializableGenericArrayType(resolvedComponentType);
        }

        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            Type[] upperBounds = wildcardType.getUpperBounds();
            Type[] lowerBounds = wildcardType.getLowerBounds();
            Type[] resolvedUpperBounds = new Type[upperBounds.length];
            Type[] resolvedLowerBounds = new Type[lowerBounds.length];
            boolean changed = false;

            for (int i = 0; i < upperBounds.length; i++) {
                resolvedUpperBounds[i] = resolveTypeVariables(upperBounds[i], resolvedTypeVariables);
                if (!Objects.equals(upperBounds[i], resolvedUpperBounds[i])) {
                    changed = true;
                }
            }
            for (int i = 0; i < lowerBounds.length; i++) {
                resolvedLowerBounds[i] = resolveTypeVariables(lowerBounds[i], resolvedTypeVariables);
                if (!Objects.equals(lowerBounds[i], resolvedLowerBounds[i])) {
                    changed = true;
                }
            }

            if (!changed) {
                return type;
            }
            return new SerializableWildcardType(resolvedUpperBounds, resolvedLowerBounds);
        }

        return type;
    }

    private Set<Type> getResolvedEventDispatchTypes(Object event) {
        Class<?> runtimeType = event.getClass();
        Set<Type> runtimeTypeClosure = TypeClosureHelper.extractTypesFromClass(runtimeType, true);
        Map<TypeVariable<?>, Type> resolvedTypeVariables =
                resolveRuntimeTypeVariables(eventType, runtimeTypeClosure);

        Set<Type> resolvedDispatchTypes = new LinkedHashSet<>();
        for (Type candidate : runtimeTypeClosure) {
            resolvedDispatchTypes.add(resolveTypeVariables(candidate, resolvedTypeVariables));
        }
        return resolvedDispatchTypes;
    }

    private boolean isNotMatchingObservedType(Type observedType, Set<Type> eventDispatchTypes) {
        for (Type dispatchType : eventDispatchTypes) {
            if (typeHelper.isEventTypeAssignable(observedType, dispatchType)) {
                return false;
            }
        }
        return true;
    }

    private boolean isContainerLifecycleEventType(Class<?> type) {
        Set<Class<?>> allTypes = new HashSet<>();
        collectTypeClosure(type, allTypes);
        for (Class<?> candidate : allTypes) {
            String name = candidate.getName();
            if (CONTAINER_LIFECYCLE_EVENT_TYPES.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private void collectTypeClosure(Class<?> type, Set<Class<?>> acc) {
        if (type == null || !acc.add(type)) {
            return;
        }
        collectTypeClosure(type.getSuperclass(), acc);
        for (Class<?> itf : type.getInterfaces()) {
            collectTypeClosure(itf, acc);
        }
    }

    private static final Set<String> CONTAINER_LIFECYCLE_EVENT_TYPES = new HashSet<>(Arrays.asList(
            "jakarta.enterprise.inject.spi.BeforeBeanDiscovery",
            "jakarta.enterprise.inject.spi.AfterTypeDiscovery",
            "jakarta.enterprise.inject.spi.AfterBeanDiscovery",
            "jakarta.enterprise.inject.spi.AfterDeploymentValidation",
            "jakarta.enterprise.inject.spi.BeforeShutdown",
            "jakarta.enterprise.inject.spi.ProcessAnnotatedType",
            "jakarta.enterprise.inject.spi.ProcessSyntheticAnnotatedType",
            "jakarta.enterprise.inject.spi.ProcessInjectionTarget",
            "jakarta.enterprise.inject.spi.ProcessInjectionPoint",
            "jakarta.enterprise.inject.spi.ProcessBeanAttributes",
            "jakarta.enterprise.inject.spi.ProcessBean",
            "jakarta.enterprise.inject.spi.ProcessManagedBean",
            "jakarta.enterprise.inject.spi.ProcessSessionBean",
            "jakarta.enterprise.inject.spi.ProcessSyntheticBean",
            "jakarta.enterprise.inject.spi.ProcessProducer",
            "jakarta.enterprise.inject.spi.ProcessProducerMethod",
            "jakarta.enterprise.inject.spi.ProcessProducerField",
            "jakarta.enterprise.inject.spi.ProcessObserverMethod",
            "jakarta.enterprise.inject.spi.ProcessSyntheticObserverMethod",
            "javax.enterprise.inject.spi.BeforeBeanDiscovery",
            "javax.enterprise.inject.spi.AfterTypeDiscovery",
            "javax.enterprise.inject.spi.AfterBeanDiscovery",
            "javax.enterprise.inject.spi.AfterDeploymentValidation",
            "javax.enterprise.inject.spi.BeforeShutdown",
            "javax.enterprise.inject.spi.ProcessAnnotatedType",
            "javax.enterprise.inject.spi.ProcessInjectionTarget",
            "javax.enterprise.inject.spi.ProcessInjectionPoint",
            "javax.enterprise.inject.spi.ProcessBeanAttributes",
            "javax.enterprise.inject.spi.ProcessBean",
            "javax.enterprise.inject.spi.ProcessManagedBean",
            "javax.enterprise.inject.spi.ProcessSessionBean",
            "javax.enterprise.inject.spi.ProcessProducer",
            "javax.enterprise.inject.spi.ProcessProducerMethod",
            "javax.enterprise.inject.spi.ProcessProducerField",
            "javax.enterprise.inject.spi.ProcessObserverMethod"
    ));

    public static void clearStaticState() {
        TRANSACTION_DOWNGRADE_WARNED.set(false);
        INACTIVE_SCOPE_WARNED.clear();
        shutdownDefaultAsyncExecutor();
    }

    private boolean containsUnresolvableTypeVariable(Type type) {
        if (type instanceof TypeVariable) {
            return true;
        }

        if (type instanceof ParameterizedType) {
            for (Type argument : ((ParameterizedType) type).getActualTypeArguments()) {
                if (containsUnresolvableTypeVariable(argument)) {
                    return true;
                }
            }
            return false;
        }

        if (type instanceof GenericArrayType) {
            return containsUnresolvableTypeVariable(((GenericArrayType) type).getGenericComponentType());
        }

        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            for (Type lowerBound : wildcardType.getLowerBounds()) {
                if (containsUnresolvableTypeVariable(lowerBound)) {
                    return true;
                }
            }
            for (Type upperBound : wildcardType.getUpperBounds()) {
                if (containsUnresolvableTypeVariable(upperBound)) {
                    return true;
                }
            }
            return false;
        }

        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;
            if (clazz.isArray()) {
                return containsUnresolvableTypeVariable(clazz.getComponentType());
            }
        }

        return false;
    }

    /**
     * Finds all observer methods that match the given event and synchronization mode.
     *
     * <p>An observer matches if:
     * <ul>
     *   <li>The observer's async flag matches the requested mode</li>
     *   <li>The observer's event type is assignable from the event's type</li>
     *   <li>All observer qualifiers are present in this Event's qualifiers</li>
     * </ul>
     *
     * @param event the event to match
     * @param async true for @ObservesAsync observers, false for @Observes observers
     * @return list of matching observer method metadata
     */
    private List<ObserverMethodInfo> findMatchingObservers(Object event, boolean async) {
        List<ObserverMethodInfo> matching = new ArrayList<>();
        Set<Type> eventDispatchTypes = getResolvedEventDispatchTypes(event);

        // Find matching reflection-based observers (from @Observes/@ObservesAsync methods)
        for (ObserverMethodMetadata metadata : knowledgeBase.getObserverMethodInfos()) {
            ObserverMethodInfo observerInfo = toObserverMethodInfo(metadata);
            if (observerInfo == null) {
                continue;
            }
            if (!isObserverDispatchCandidate(observerInfo, async)) {
                continue;
            }

            // Match async/sync mode
            if (observerInfo.isAsync() != async) {
                continue;
            }

            // Check if observer event type is assignable from at least one resolved dispatch type.
            if (isNotMatchingObservedType(observerInfo.getEventType(), eventDispatchTypes)) {
                continue;
            }

            // CDI qualifier matching must honor annotation members and @Nonbinding.
            if (notEventQualifiersMatch(observerInfo.getQualifiers(), qualifiers)) {
                continue;
            }

            matching.add(observerInfo);
        }

        // Also find matching synthetic observers (registered via AfterBeanDiscovery.addObserverMethod())
        for (ObserverMethod<?> syntheticObserver : knowledgeBase.getSyntheticObserverMethods()) {
            // Match async/sync mode
            if (syntheticObserver.isAsync() != async) {
                continue;
            }

            // Check if observer event type is assignable from at least one resolved dispatch type.
            if (isNotMatchingObservedType(syntheticObserver.getObservedType(), eventDispatchTypes)) {
                continue;
            }

            // CDI qualifier matching must honor annotation members and @Nonbinding.
            if (notEventQualifiersMatch(syntheticObserver.getObservedQualifiers(), qualifiers)) {
                continue;
            }

            // Wrap the synthetic ObserverMethod as an ObserverMethodInfo
            // Note: Synthetic observers don't have a Method, so we create a minimal wrapper
            matching.add(createSyntheticObserverInfo(syntheticObserver));
        }

        List<ObserverMethodInfo> deduped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ObserverMethodInfo observerInfo : matching) {
            String key = observerDedupKey(observerInfo);
            if (seen.add(key)) {
                deduped.add(observerInfo);
            }
        }
        return deduped;
    }

    private ObserverMethodInfo toObserverMethodInfo(ObserverMethodMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        if (metadata instanceof ObserverMethodInfo) {
            return (ObserverMethodInfo) metadata;
        }
        if (metadata.isSynthetic()) {
            ObserverMethod<?> syntheticObserver = metadata.getSyntheticObserver();
            return syntheticObserver != null ? createSyntheticObserverInfo(syntheticObserver) : null;
        }
        Method observerMethod = metadata.getObserverMethod();
        if (observerMethod == null) {
            return null;
        }
        return new ObserverMethodInfo(
                observerMethod,
                metadata.getEventType(),
                metadata.getQualifiers(),
                metadata.getReception(),
                metadata.getTransactionPhase(),
                metadata.isAsync(),
                metadata.getDeclaringBean(),
                metadata.getPriority(),
                metadata.getObservedParameterPosition()
        );
    }

    private boolean isObserverDispatchCandidate(ObserverMethodInfo observerInfo, boolean async) {
        if (observerInfo == null) {
            return false;
        }
        if (observerInfo.isSynthetic()) {
            return true;
        }
        if (!isDeclaringBeanEnabledForObserver(observerInfo.getDeclaringBean())) {
            return false;
        }

        Method observerMethod = observerInfo.getObserverMethod();
        if (observerMethod == null) {
            return false;
        }
        // Observer metadata is validated during discovery; avoid revalidating it via reflection
        // so ProcessAnnotatedType wrapper metadata stays effective at runtime.
        return observerInfo.isAsync() == async;
    }

    private boolean isDeclaringBeanEnabledForObserver(Bean<?> declaringBean) {
        if (declaringBean == null) {
            return true;
        }
        if (isNotBeanEnabledForObserverDispatch(declaringBean)) {
            return false;
        }
        return !isSpecializedOutForObserverDispatch(declaringBean);
    }

    private boolean isNotBeanEnabledForObserverDispatch(Bean<?> bean) {
        if (bean == null) {
            return true;
        }
        if (!bean.isAlternative()) {
            return false;
        }
        if (bean instanceof BeanImpl<?>) {
            return !((BeanImpl<?>) bean).isAlternativeEnabled();
        }
        return false;
    }

    private boolean isSpecializedOutForObserverDispatch(Bean<?> bean) {
        Class<?> beanClass = bean.getBeanClass();
        if (beanClass == null) {
            return false;
        }

        for (Bean<?> candidate : knowledgeBase.getBeans()) {
            if (candidate == null || candidate == bean) {
                continue;
            }
            if (isNotBeanEnabledForObserverDispatch(candidate)) {
                continue;
            }
            Class<?> candidateClass = candidate.getBeanClass();
            if (!hasSpecializesAnnotation(candidateClass)) {
                continue;
            }
            if (collectSpecializedSuperclasses(candidateClass).contains(beanClass)) {
                return true;
            }
        }
        return false;
    }

    private Set<Class<?>> collectSpecializedSuperclasses(Class<?> beanClass) {
        Set<Class<?>> out = new HashSet<>();
        if (!hasSpecializesAnnotation(beanClass)) {
            return out;
        }
        Class<?> current = beanClass.getSuperclass();
        while (current != null && !Object.class.equals(current)) {
            out.add(current);
            if (!hasSpecializesAnnotation(current)) {
                break;
            }
            current = current.getSuperclass();
        }
        return out;
    }

    private String observerDedupKey(ObserverMethodInfo observerInfo) {
        if (observerInfo == null) {
            return "";
        }
        Method method = observerInfo.getObserverMethod();
        String methodKey;
        if (method != null) {
            StringBuilder signature = new StringBuilder();
            signature.append(method.getDeclaringClass().getName())
                    .append('#')
                    .append(method.getName())
                    .append('(');
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (i > 0) {
                    signature.append(',');
                }
                signature.append(parameterTypes[i].getName());
            }
            signature.append(')');
            methodKey = signature.toString();
        } else if (observerInfo.getSyntheticObserver() != null) {
            methodKey = "synthetic:" + Integer.toHexString(
                    System.identityHashCode(observerInfo.getSyntheticObserver()));
        } else {
            methodKey = "unknown";
        }
        String declaringBeanClass = observerInfo.getDeclaringBean() != null &&
                observerInfo.getDeclaringBean().getBeanClass() != null
                ? observerInfo.getDeclaringBean().getBeanClass().getName()
                : "";
        return methodKey + "|" + declaringBeanClass + "|" + observerInfo.getEventType() + "|" +
                qualifierIdentity(observerInfo.getQualifiers()) + "|" + observerInfo.getReception() + "|" +
                observerInfo.isAsync() + "|" + observerInfo.getTransactionPhase() + "|" +
                observerInfo.getPriority();
    }

    private String qualifierIdentity(Set<Annotation> qualifiers) {
        if (qualifiers == null || qualifiers.isEmpty()) {
            return "";
        }

        List<String> entries = new ArrayList<>();
        for (Annotation qualifier : qualifiers) {
            if (qualifier == null || qualifier.annotationType() == null) {
                continue;
            }
            entries.add(qualifier.annotationType().getName() + ":" + AnnotationComparator.hashCode(qualifier));
        }
        Collections.sort(entries);
        return String.join(",", entries);
    }

    /**
     * Creates an ObserverMethodInfo wrapper for a synthetic ObserverMethod.
     *
     * <p>Synthetic observers registered via AfterBeanDiscovery.addObserverMethod()
     * don't have an underlying java.lang.reflect.Method, so we create a minimal
     * ObserverMethodInfo that delegates to the ObserverMethod's notify() method.
     */
    private ObserverMethodInfo createSyntheticObserverInfo(ObserverMethod<?> syntheticObserver) {
        return new ObserverMethodInfo(
            syntheticObserver.getObservedType(),
            syntheticObserver.getObservedQualifiers(),
            syntheticObserver.getReception(),
            syntheticObserver.getTransactionPhase(),
            syntheticObserver.getPriority(),
            syntheticObserver.isAsync(),
            null, // No declaring bean for synthetic observers
            syntheticObserver // Store the synthetic observer for invocation
        );
    }

    /**
     * Invokes a single observer method with the event payload.
     *
     * <p>This method:
     * <ol>
     *   <li>Resolves the observer bean instance from the declaring bean</li>
     *   <li>Resolves any additional injection point parameters</li>
     *   <li>Invokes the observer method with event + parameters</li>
     *   <li>Handles reflection accessibility</li>
     * </ol>
     *
     * <p><b>CDI 4.1 Bean Creation Behavior (Section 10.5.2):</b>
     * <ul>
     *   <li>If the observer has {@link Reception#ALWAYS}, the bean instance is created if it
     *       doesn't already exist in its scope. This is the default behavior.</li>
     *   <li>The {@code beanResolver.resolveDeclaringBeanInstance()} call handles this by:
     *       <ul>
     *         <li>Checking if an instance exists in the appropriate scope context</li>
     *         <li>Creating and storing a new instance if none exists</li>
     *         <li>Storing the instance in the scope (ApplicationScoped, RequestScoped, etc.)</li>
     *       </ul>
     *   </li>
     *   <li>This ensures lazy initialization of observer beans - they're only created when
     *       a matching event is fired.</li>
     * </ul>
     *
     * @param observerInfo the observer method metadata
     * @param event the event payload
     * @throws RuntimeException if observer invocation fails
     */
    @SuppressWarnings("unchecked")
    private void invokeObserver(ObserverMethodInfo observerInfo, Object event) {
        try {
            Type metadataType = resolveMetadataType(event);
            // Check if this is a synthetic observer (registered via AfterBeanDiscovery.addObserverMethod())
            if (observerInfo.isSynthetic()) {
                // Invoke the synthetic observer's notify() method
                @SuppressWarnings("rawtypes")
                ObserverMethod syntheticObserver = observerInfo.getSyntheticObserver();
                final Object eventPayload = event;
                final EventMetadata metadata = new EventMetadataImpl(qualifiers, firingInjectionPoint, metadataType);
                syntheticObserver.notify(new EventContext<Object>() {
                    @Override
                    public Object getEvent() {
                        return eventPayload;
                    }

                    @Override
                    public EventMetadata getMetadata() {
                        return metadata;
                    }
                });
                return;
            }

            // Handle reflection-based observers (from @Observes/@ObservesAsync methods)
            Method method = observerInfo.getObserverMethod();
            Bean<?> declaringBean = observerInfo.getDeclaringBean();

            // Resolve method parameters
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];
            int observedParameterPosition = resolveObservedParameterPosition(observerInfo, parameters);

            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];

                // The @Observes or @ObservesAsync parameter gets the event
                if (i == observedParameterPosition) {
                    args[i] = event;
                } else if (EventMetadata.class.equals(param.getType())) {
                    args[i] = new EventMetadataImpl(qualifiers, firingInjectionPoint, metadataType);
                } else {
                    // Other parameters are injection points - resolve them
                    InjectionPoint registeredInjectionPoint =
                            findRegisteredObserverInjectionPoint(declaringBean, method, i);
                    Type paramType = registeredInjectionPoint != null
                            ? registeredInjectionPoint.getType()
                            : param.getParameterizedType();
                    if (declaringBean != null && declaringBean.getBeanClass() != null) {
                        paramType = GenericTypeResolver.resolve(
                                paramType,
                                declaringBean.getBeanClass(),
                                method.getDeclaringClass()
                        );
                    }
                    Annotation[] paramAnnotations = registeredInjectionPoint != null
                            ? registeredInjectionPoint.getQualifiers().toArray(new Annotation[0])
                            : param.getAnnotations();
                    args[i] = resolveObserverParameterWithContext(
                            param,
                            paramType,
                            paramAnnotations,
                            declaringBean,
                            registeredInjectionPoint
                    );
                }
            }

            // For static observer methods, invocation must not require declaring bean instance creation.
            Object beanInstance = null;
            CreationalContext<?> observerCreationalContext = null;
            boolean destroyDependentReceiverExplicitly = false;
            if (!Modifier.isStatic(method.getModifiers())) {
                // Get the bean instance that declares this observer method
                // For Reception.ALWAYS (default): This will create the bean if it doesn't exist
                // For Reception.IF_EXISTS: This is only called after checking bean existence
                if (declaringBean != null && hasDependentAnnotation(method.getDeclaringClass())) {
                    BeanManager beanManager = resolveBeanManager();
                    observerCreationalContext = beanManager.createCreationalContext(declaringBean);
                    beanInstance = beanManager.getReference(declaringBean, declaringBean.getBeanClass(),
                            observerCreationalContext);
                    destroyDependentReceiverExplicitly = true;
                } else if (declaringBean != null) {
                    // Observer receiver must be the current contextual instance, not a client proxy shell.
                    if (contextManager != null
                            && contextManager.isNormalScope(declaringBean.getScope())
                            && !isContainerLifecycleEvent(event)
                            && !isContextLifecycleEvent()) {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        CreationalContext<Object> context =
                                (CreationalContext<Object>) resolveBeanManager()
                                        .createCreationalContext((Contextual) declaringBean);
                        ScopeContext scopeContext = contextManager.getContext(declaringBean.getScope());
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        Object contextualInstance = scopeContext.get((Bean) declaringBean, context);
                        beanInstance = contextualInstance;
                    } else {
                        beanInstance = beanResolver.resolveDeclaringBeanInstance(declaringBean.getBeanClass());
                    }
                } else {
                    // If no bean metadata, try to resolve by method's declaring class
                    beanInstance = beanResolver.resolveDeclaringBeanInstance(method.getDeclaringClass());
                }
            }

            try {
                // Invoke the observer method
                if (shouldUseRuntimeMethodDispatch(declaringBean, method, beanInstance)) {
                    invokeOnRuntimeMethod(beanInstance, method, args);
                } else {
                    method.setAccessible(true);
                    method.invoke(beanInstance, args);
                }
            } finally {
                destroyDependentInvocationParameters(parameters, args, observedParameterPosition);
                if (destroyDependentReceiverExplicitly && declaringBean != null) {
                    if (!destroyBeanInstance(declaringBean, beanInstance, observerCreationalContext)) {
                        LifecycleMethodHelper.invokeLifecycleMethod(beanInstance, PRE_DESTROY);
                    }
                } else {
                    destroyDependentObserverReceiver(beanInstance, method, declaringBean);
                }
            }

        } catch (Exception e) {
            throw toRuntimeObserverFailure(e);
        }
    }

    private Type resolveMetadataType(Object event) {
        Class<?> runtimeType = event.getClass();
        Set<Type> dispatchTypes = getResolvedEventDispatchTypes(event);
        Type fallbackMatch = null;

        for (Type dispatchType : dispatchTypes) {
            Class<?> dispatchRawType = getRawType(dispatchType);
            if (!runtimeType.equals(dispatchRawType)) {
                continue;
            }
            if (dispatchType instanceof Class<?>) {
                if (fallbackMatch == null) {
                    fallbackMatch = dispatchType;
                }
                continue;
            }
            if (!containsUnresolvableTypeVariable(dispatchType)) {
                return dispatchType;
            }
            if (fallbackMatch == null) {
                fallbackMatch = dispatchType;
            }
        }

        return fallbackMatch != null ? fallbackMatch : runtimeType;
    }

    private RuntimeException toRuntimeObserverFailure(Throwable throwable) {
        Throwable cause = unwrapObserverInvocationThrowable(throwable);
        if (cause instanceof RuntimeException) {
            return (RuntimeException) cause;
        }
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        return new ObserverException(cause);
    }

    private Throwable unwrapObserverInvocationThrowable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InvocationTargetException) {
                Throwable target = ((InvocationTargetException) current).getTargetException();
                if (target == null || target == current) {
                    return current;
                }
                current = target;
                continue;
            }
            if (current instanceof UndeclaredThrowableException) {
                Throwable undeclared = ((UndeclaredThrowableException) current).getUndeclaredThrowable();
                if (undeclared == null || undeclared == current) {
                    return current;
                }
                current = undeclared;
                continue;
            }
            Throwable nested = current.getCause();
            if (nested instanceof InvocationTargetException || nested instanceof UndeclaredThrowableException) {
                current = nested;
                continue;
            }
            return current;
        }
        return throwable;
    }

    private void invokeObserverWithContextCheck(ObserverMethodInfo observerInfo, Object event) {
        ContextActivation activation = ensureObserverContext(observerInfo);
        if (activation.isSkip()) {
            return;
        }
        try {
            invokeObserver(observerInfo, event);
        } finally {
            activation.close();
        }
    }

    private void destroyDependentInvocationParameters(Parameter[] parameters,
                                                      Object[] args,
                                                      int observedParameterPosition) throws Exception {
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object arg = args[i];
            if (arg == null) {
                continue;
            }
            if (i == observedParameterPosition) {
                continue;
            }
            if (cleanupDependentInstancesFromProgrammaticLookup(parameter, arg)) {
                continue;
            }
            if (!isDependentParameter(parameter)) {
                continue;
            }
            if (!destroyDependentInstance(parameter.getType(), parameter.getAnnotations(), arg)) {
                LifecycleMethodHelper.invokeLifecycleMethod(arg, PRE_DESTROY);
            }
        }
    }

    private boolean cleanupDependentInstancesFromProgrammaticLookup(Parameter parameter, Object arg) {
        if (parameter == null || arg == null) {
            return false;
        }

        Class<?> parameterType = parameter.getType();
        if (parameterType == null || !Instance.class.isAssignableFrom(parameterType)) {
            return false;
        }

        if (arg instanceof InstanceImpl<?>) {
            ((InstanceImpl<?>) arg).destroyTrackedDependentInstances();
            return true;
        }
        return false;
    }

    private void destroyDependentObserverReceiver(Object beanInstance, Method method, Bean<?> declaringBean) throws Exception {
        if (beanInstance == null || method == null || Modifier.isStatic(method.getModifiers())) {
            return;
        }

        if (hasDependentAnnotation(method.getDeclaringClass())) {
            if (destroyBeanInstance(declaringBean, beanInstance)) {
                return;
            }
            if (destroyDependentInstance(method.getDeclaringClass(), method.getDeclaringClass().getAnnotations(),
                    beanInstance)) {
                return;
            }
        } else {
            return;
        }

        LifecycleMethodHelper.invokeLifecycleMethod(beanInstance, PRE_DESTROY);
    }

    private boolean destroyDependentInstance(Class<?> requiredType, Annotation[] annotations, Object instance) {
        try {
            BeanManager beanManager = resolveBeanManager();
            Set<Bean<?>> beans = beanManager.getBeans(requiredType, extractQualifiers(annotations));
            if (beans == null || beans.isEmpty()) {
                return false;
            }

            Bean<?> bean = beanManager.resolve(beans);
            if (bean == null || !hasDependentAnnotation(bean.getScope())) {
                return false;
            }

            return destroyBeanInstance(bean, instance);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean destroyBeanInstance(Bean<?> bean, Object instance) {
        return destroyBeanInstance(bean, instance, null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean destroyBeanInstance(Bean<?> bean, Object instance, CreationalContext<?> creationalContext) {
        if (bean == null || instance == null) {
            return false;
        }
        try {
            CreationalContext context = creationalContext;
            if (context == null) {
                BeanManager beanManager = resolveBeanManager();
                context = beanManager.createCreationalContext(bean);
            }
            ((Bean) bean).destroy(instance, context);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private BeanManager resolveBeanManager() {
        if (beanResolver != null) {
            BeanManager manager = beanResolver.getOwningBeanManager();
            if (manager != null) {
                return manager;
            }
        }
        return CDI.current().getBeanManager();
    }

    private boolean isContainerLifecycleEvent(Object event) {
        if (event == null) {
            return false;
        }
        String eventClassName = event.getClass().getName();
        return isContainerLifecyclePayloadTypeName(eventClassName);
    }

    private boolean isContextLifecycleEvent() {
        if (qualifiers == null || qualifiers.isEmpty()) {
            return false;
        }
        for (Annotation qualifier : qualifiers) {
            if (qualifier == null || qualifier.annotationType() == null) {
                continue;
            }
            if (hasInitializedAnnotation(qualifier.annotationType())
                    || hasDestroyedAnnotation(qualifier.annotationType())) {
                return true;
            }
        }
        return false;
    }

    private Annotation[] extractQualifiers(Annotation[] annotations) {
        if (annotations == null || annotations.length == 0) {
            return new Annotation[0];
        }
        Set<Annotation> qualifierSet = new HashSet<>();
        for (Annotation annotation : annotations) {
            if (annotation == null) {
                continue;
            }
            if (hasQualifierAnnotation(annotation.annotationType())) {
                qualifierSet.add(annotation);
            }
        }
        return qualifierSet.toArray(new Annotation[0]);
    }

    private void invokeOnRuntimeMethod(Object targetInstance, Method method, Object[] args) throws Exception {
        Method invocable = method;
        if (targetInstance != null && !Modifier.isStatic(method.getModifiers())) {
            Method resolved = findMethodInHierarchy(targetInstance.getClass(), method.getName(), method.getParameterTypes());
            if (resolved != null) {
                invocable = resolved;
            }
        }
        invocable.setAccessible(true);
        invocable.invoke(targetInstance, args);
    }

    private Method findMethodInHierarchy(Class<?> type, String methodName, Class<?>[] parameterTypes) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private boolean shouldUseRuntimeMethodDispatch(Bean<?> declaringBean, Method method, Object beanInstance) {
        if (beanInstance == null || method == null || Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        if (!(declaringBean instanceof BeanImpl<?>)) {
            return false;
        }
        return ((BeanImpl<?>) declaringBean).hasInterceptors();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object resolveObserverParameterWithContext(Parameter parameter,
                                                       Type parameterType,
                                                       Annotation[] parameterAnnotations,
                                                       Bean<?> declaringBean,
                                                       InjectionPoint registeredInjectionPoint) {
        InjectionPoint injectionPoint = registeredInjectionPoint != null
                ? registeredInjectionPoint
                : new InjectionPointImpl(parameter, declaringBean);
        beanResolver.setCurrentInjectionPoint(injectionPoint);
        try {
            return beanResolver.resolve(parameterType, parameterAnnotations);
        } finally {
            beanResolver.clearCurrentInjectionPoint();
        }
    }

    private int resolveObservedParameterPosition(ObserverMethodInfo observerInfo, Parameter[] parameters) {
        int position = observerInfo.getObservedParameterPosition();
        if (position >= 0 && position < parameters.length) {
            return position;
        }

        Method method = observerInfo.getObserverMethod();
        if (method == null) {
            return -1;
        }

        int discovered = -1;
        for (int i = 0; i < parameters.length; i++) {
            if (hasObservesAnnotation(parameters[i]) || hasObservesAsyncAnnotation(parameters[i])) {
                if (discovered >= 0) {
                    return -1;
                }
                discovered = i;
            }
        }
        return discovered;
    }

    private InjectionPoint findRegisteredObserverInjectionPoint(Bean<?> declaringBean, Method method, int parameterIndex) {
        if (declaringBean == null || method == null || parameterIndex < 0) {
            return null;
        }

        if (declaringBean instanceof BeanImpl<?>) {
            for (InjectionPoint injectionPoint : declaringBean.getInjectionPoints()) {
                if (injectionPoint == null || !method.equals(injectionPoint.getMember())) {
                    continue;
                }

                Annotated annotated = injectionPoint.getAnnotated();
                if (annotated instanceof AnnotatedParameter<?>) {
                    AnnotatedParameter<?> annotatedParameter = (AnnotatedParameter<?>) annotated;
                    if (annotatedParameter.getPosition() == parameterIndex) {
                        return injectionPoint;
                    }
                }
            }
        }

        Parameter[] parameters = method.getParameters();
        if (parameterIndex >= parameters.length) {
            return null;
        }

        Class<?> beanClass = declaringBean.getBeanClass();
        AnnotatedType<?> override = beanClass != null ? knowledgeBase.getAnnotatedTypeOverride(beanClass) : null;
        if (override != null) {
            Parameter parameter = parameters[parameterIndex];
            AnnotatedParameter<?> annotatedParameter = findAnnotatedParameter(override, parameter);
            if (annotatedParameter != null) {
                return new InjectionPointImpl<>(
                        parameter,
                        declaringBean,
                        annotatedParameter.getBaseType(),
                        annotatedParameter.getAnnotations().toArray(new Annotation[0]),
                        annotatedParameter
                );
            }
        }

        return null;
    }

    private boolean isDependentParameter(Parameter parameter) {
        Class<?> parameterType = parameter.getType();
        if (parameterType == null) {
            return false;
        }
        return hasDependentAnnotation(parameterType);
    }

    private void invokeObserverList(List<ObserverMethodInfo> observers, Object event) {
        for (ObserverMethodInfo observer : observers) {
            ContextActivation activation = ensureObserverContext(observer);
            if (activation.isSkip()) {
                continue;
            }
            try {
                invokeObserver(observer, event);
            } catch (Exception e) {
                // Per spec, observer exceptions must not affect transaction outcome
                System.err.println("Transactional observer error (" + observer + "): " + e.getMessage());
            } finally {
                activation.close();
            }
        }
    }

    /**
     * Guards transactional observer callbacks by ensuring required normal scopes are active.
     * If the declaring bean uses an inactive normal scope (e.g., @RequestScoped after the request ends),
     * the invocation is skipped and a warning is logged once per scope type.
     */
    private ContextActivation ensureObserverContext(ObserverMethodInfo observer) {
        Method observerMethod = observer.getObserverMethod();
        if (observerMethod != null && Modifier.isStatic(observerMethod.getModifiers())) {
            // Static observer methods do not require a contextual instance.
            return ContextActivation.NOOP;
        }

        Bean<?> declaringBean = observer.getDeclaringBean();
        if (declaringBean == null) {
            return ContextActivation.NOOP; // Synthetic or unknown bean; proceed
        }
        Class<? extends Annotation> scope = declaringBean.getScope();
        // Dependent/Application are always safe
        if (hasDependentAnnotation(scope) || hasApplicationScopedAnnotation(scope)) {
            return ContextActivation.NOOP;
        }

        try {
            ScopeContext ctx = contextManager.getContext(scope);
            if (ctx.isActive()) {
                return ContextActivation.NOOP;
            }

            // Per CDI 4.1 (9.5), observers are not called when declaring scope context is inactive.
            warnOnce(scope, declaringBean);
            return ContextActivation.SKIP;
        } catch (IllegalArgumentException e) {
            // Unknown scope; proceed to avoid hiding functionality
            return ContextActivation.NOOP;
        }
    }

    private void warnOnce(Class<? extends Annotation> scope, Bean<?> bean) {
        AtomicBoolean flag = INACTIVE_SCOPE_WARNED.computeIfAbsent(scope, k -> new AtomicBoolean(false));
        if (flag.compareAndSet(false, true)) {
            System.out.println("[Event] Skipping observer for @" +
                scope.getSimpleName() + " (" + bean.getBeanClass().getName() + ") because scope inactive");
        }
    }

    /**
     * Tracks temporary context activations (currently RequestScoped only).
     */
    private static class ContextActivation implements AutoCloseable {
        static final ContextActivation NOOP = new ContextActivation();
        static final ContextActivation SKIP = new ContextActivation(true);

        private final boolean skip;
        private final RequestScopedContext requestCtx;
        private final ConversationScopedContext conversationCtx;
        private final SessionScopedContext sessionCtx;
        private final String conversationId;
        private final String sessionId;
        private final boolean deactivateRequest;
        private final boolean endConversation;
        private final boolean deactivateSession;

        private ContextActivation() {
            this.skip = false;
            this.requestCtx = null;
            this.conversationCtx = null;
            this.sessionCtx = null;
            this.conversationId = null;
            this.sessionId = null;
            this.deactivateRequest = false;
            this.endConversation = false;
            this.deactivateSession = false;
        }

        private ContextActivation(boolean skip) {
            this.skip = skip;
            this.requestCtx = null;
            this.conversationCtx = null;
            this.sessionCtx = null;
            this.conversationId = null;
            this.sessionId = null;
            this.deactivateRequest = false;
            this.endConversation = false;
            this.deactivateSession = false;
        }

        boolean isSkip() {
            return skip;
        }

        @Override
        public void close() {
            if (deactivateRequest && requestCtx != null) {
                requestCtx.deactivateRequest();
            }
            if (endConversation && conversationCtx != null && conversationId != null) {
                conversationCtx.endConversation(conversationId);
            }
            if (deactivateSession && sessionCtx != null && sessionId != null) {
                sessionCtx.deactivateSession();
            }
        }
    }

    private static class NoopContextTokenProvider implements ContextTokenProvider {
        @Override
        public ContextSnapshot capture() {
            return null;
        }
    }

    private Object writeReplace() throws ObjectStreamException {
        String beanManagerId = resolveBeanManagerId();
        if (beanManagerId == null) {
            throw new NotSerializableException("Cannot serialize Event - no registered BeanManager available");
        }
        return new SerializedEventReference(
                makeSerializableType(eventType),
                new LinkedHashSet<>(qualifiers),
                allowStartupEventDispatch,
                beanManagerId
        );
    }

    private String resolveBeanManagerId() {
        BeanManagerImpl owningBeanManager = beanResolver.getOwningBeanManager();
        if (owningBeanManager != null) {
            return owningBeanManager.getBeanManagerId();
        }

        BeanManagerImpl byContextClassLoader =
                BeanManagerImpl.getRegisteredBeanManager(Thread.currentThread().getContextClassLoader());
        if (byContextClassLoader != null) {
            return byContextClassLoader.getBeanManagerId();
        }

        BeanManagerImpl byEventClassLoader =
                BeanManagerImpl.getRegisteredBeanManager(EventImpl.class.getClassLoader());
        if (byEventClassLoader != null) {
            return byEventClassLoader.getBeanManagerId();
        }

        return null;
    }

    private Type makeSerializableType(Type type) throws NotSerializableException {
        if (type == null || type instanceof Class<?>) {
            return type;
        }

        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type rawType = makeSerializableType(parameterizedType.getRawType());
            Type ownerType = makeSerializableType(parameterizedType.getOwnerType());
            Type[] originalArguments = parameterizedType.getActualTypeArguments();
            Type[] serializableArguments = new Type[originalArguments.length];
            for (int i = 0; i < originalArguments.length; i++) {
                serializableArguments[i] = makeSerializableType(originalArguments[i]);
            }
            return new SerializableParameterizedType(rawType, ownerType, serializableArguments);
        }

        if (type instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) type).getGenericComponentType();
            return new SerializableGenericArrayType(makeSerializableType(componentType));
        }

        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            Type[] upperBounds = wildcardType.getUpperBounds();
            Type[] lowerBounds = wildcardType.getLowerBounds();
            Type[] serializableUpperBounds = new Type[upperBounds.length];
            Type[] serializableLowerBounds = new Type[lowerBounds.length];
            for (int i = 0; i < upperBounds.length; i++) {
                serializableUpperBounds[i] = makeSerializableType(upperBounds[i]);
            }
            for (int i = 0; i < lowerBounds.length; i++) {
                serializableLowerBounds[i] = makeSerializableType(lowerBounds[i]);
            }
            return new SerializableWildcardType(serializableUpperBounds, serializableLowerBounds);
        }

        if (type instanceof TypeVariable<?>) {
            throw new NotSerializableException(
                    "Cannot serialize Event with unresolved type variable: " + type.getTypeName());
        }

        if (type instanceof Serializable) {
            return type;
        }

        throw new NotSerializableException(
                "Cannot serialize Event with unsupported type representation: " + type.getTypeName());
    }

    private static final class EventMetadataImpl implements EventMetadata {
        private final Set<Annotation> qualifiers;
        private final InjectionPoint injectionPoint;
        private final Type type;

        private EventMetadataImpl(Set<Annotation> qualifiers, InjectionPoint injectionPoint, Type type) {
            this.qualifiers = Collections.unmodifiableSet(withRepeatableQualifierContainers(qualifiers));
            this.injectionPoint = injectionPoint;
            this.type = type;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return qualifiers;
        }

        @Override
        public InjectionPoint getInjectionPoint() {
            return injectionPoint;
        }

        @Override
        public Type getType() {
            return type;
        }

        private static Set<Annotation> withRepeatableQualifierContainers(Set<Annotation> qualifiers) {
            Set<Annotation> normalized = new LinkedHashSet<>();
            if (qualifiers == null || qualifiers.isEmpty()) {
                return normalized;
            }
            normalized.addAll(qualifiers);

            Map<Class<? extends Annotation>, List<Annotation>> repeatablesByContainer = new LinkedHashMap<>();
            for (Annotation qualifier : qualifiers) {
                if (qualifier == null) {
                    continue;
                }
                java.lang.annotation.Repeatable repeatable =
                        getRepeatableAnnotation(qualifier.annotationType());
                if (repeatable == null || repeatable.value() == null) {
                    continue;
                }
                Class<? extends Annotation> containerType = repeatable.value();
                List<Annotation> repeated = repeatablesByContainer.computeIfAbsent(containerType,
                        k -> new ArrayList<>());
                repeated.add(qualifier);
            }

            for (Map.Entry<Class<? extends Annotation>, List<Annotation>> entry : repeatablesByContainer.entrySet()) {
                Class<? extends Annotation> containerType = entry.getKey();
                if (containsQualifierType(normalized, containerType)) {
                    continue;
                }
                Annotation container = createRepeatableContainerAnnotation(containerType, entry.getValue());
                if (container != null) {
                    normalized.add(container);
                }
            }
            return normalized;
        }

        private static boolean containsQualifierType(Set<Annotation> qualifiers, Class<? extends Annotation> type) {
            if (qualifiers == null || qualifiers.isEmpty() || type == null) {
                return false;
            }
            for (Annotation qualifier : qualifiers) {
                if (qualifier != null && type.equals(qualifier.annotationType())) {
                    return true;
                }
            }
            return false;
        }

        private static Annotation createRepeatableContainerAnnotation(Class<? extends Annotation> containerType,
                                                                      List<Annotation> repeatedQualifiers) {
            if (containerType == null || repeatedQualifiers == null || repeatedQualifiers.isEmpty()) {
                return null;
            }

            final Method valueMethod;
            try {
                valueMethod = containerType.getMethod("value");
            } catch (NoSuchMethodException e) {
                return null;
            }

            Class<?> returnType = valueMethod.getReturnType();
            if (!returnType.isArray()) {
                return null;
            }

            Class<?> componentType = returnType.getComponentType();
            if (componentType == null) {
                return null;
            }

            final Object valuesArray = Array.newInstance(componentType, repeatedQualifiers.size());
            for (int i = 0; i < repeatedQualifiers.size(); i++) {
                Annotation qualifier = repeatedQualifiers.get(i);
                if (qualifier == null || !componentType.isAssignableFrom(qualifier.annotationType())) {
                    return null;
                }
                Array.set(valuesArray, i, qualifier);
            }

            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if ("annotationType".equals(name) && method.getParameterCount() == 0) {
                    return containerType;
                }
                if ("value".equals(name) && method.getParameterCount() == 0) {
                    return cloneArray(valuesArray);
                }
                if ("equals".equals(name) && method.getParameterCount() == 1) {
                    Object other = (args != null && args.length == 1) ? args[0] : null;
                    return other instanceof Annotation &&
                            AnnotationComparator.equals((Annotation) proxy, (Annotation) other);
                }
                if ("hashCode".equals(name) && method.getParameterCount() == 0) {
                    return AnnotationComparator.hashCode((Annotation) proxy);
                }
                if ("toString".equals(name) && method.getParameterCount() == 0) {
                    return "@" + containerType.getName() + "(value=" +
                            Arrays.toString((Object[]) cloneArray(valuesArray)) + ")";
                }
                if (method.getParameterCount() == 0 && method.getDefaultValue() != null) {
                    return method.getDefaultValue();
                }
                throw new UnsupportedOperationException("Unsupported method on repeatable container annotation: " + method);
            };

            return (Annotation) Proxy.newProxyInstance(
                    containerType.getClassLoader(),
                    new Class<?>[]{containerType},
                    handler
            );
        }

        private static Object cloneArray(Object array) {
            if (array == null || !array.getClass().isArray()) {
                return array;
            }
            int length = Array.getLength(array);
            Object copy = Array.newInstance(array.getClass().getComponentType(), length);
            System.arraycopy(array, 0, copy, 0, length);
            return copy;
        }
    }

    private static final class SerializedEventReference implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Type eventType;
        private final Set<Annotation> qualifiers;
        private final boolean allowStartupEventDispatch;
        private final String beanManagerId;

        private SerializedEventReference(Type eventType,
                                         Set<Annotation> qualifiers,
                                         boolean allowStartupEventDispatch,
                                         String beanManagerId) {
            this.eventType = eventType;
            this.qualifiers = qualifiers;
            this.allowStartupEventDispatch = allowStartupEventDispatch;
            this.beanManagerId = beanManagerId;
        }

        private Object readResolve() throws ObjectStreamException {
            BeanManagerImpl beanManager = BeanManagerImpl.getRegisteredBeanManager(beanManagerId);
            if (beanManager == null) {
                beanManager = BeanManagerImpl.getRegisteredBeanManager(Thread.currentThread().getContextClassLoader());
            }
            if (beanManager == null) {
                beanManager = BeanManagerImpl.getRegisteredBeanManager(EventImpl.class.getClassLoader());
            }
            if (beanManager == null) {
                throw new InvalidObjectException(
                        "Cannot restore Event - no registered BeanManager available for id " + beanManagerId);
            }

            Set<Annotation> restoredQualifiers = qualifiers == null
                    ? Collections.emptySet()
                    : qualifiers;

            return new EventImpl<>(
                    eventType,
                    new LinkedHashSet<>(restoredQualifiers),
                    beanManager.getKnowledgeBase(),
                    beanManager.getBeanResolver(),
                    beanManager.getContextManager(),
                    beanManager.getBeanResolver().getTransactionServices(),
                    null,
                    null,
                    allowStartupEventDispatch
            );
        }
    }

    private static final class SerializableParameterizedType implements ParameterizedType, Serializable {
        private static final long serialVersionUID = 1L;

        private final Type rawType;
        private final Type ownerType;
        private final Type[] actualTypeArguments;

        private SerializableParameterizedType(Type rawType, Type ownerType, Type[] actualTypeArguments) {
            this.rawType = rawType;
            this.ownerType = ownerType;
            this.actualTypeArguments = actualTypeArguments.clone();
        }

        @Override
        public @Nonnull Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public @Nonnull Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ParameterizedType)) {
                return false;
            }
            ParameterizedType that = (ParameterizedType) other;
            return Objects.equals(rawType, that.getRawType())
                    && Objects.equals(ownerType, that.getOwnerType())
                    && Arrays.equals(actualTypeArguments, that.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(actualTypeArguments)
                    ^ Objects.hashCode(ownerType)
                    ^ Objects.hashCode(rawType);
        }
    }

    private static final class SerializableGenericArrayType implements GenericArrayType, Serializable {
        private static final long serialVersionUID = 1L;

        private final Type componentType;

        private SerializableGenericArrayType(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public @Nonnull Type getGenericComponentType() {
            return componentType;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof GenericArrayType)) {
                return false;
            }
            GenericArrayType that = (GenericArrayType) other;
            return Objects.equals(componentType, that.getGenericComponentType());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(componentType);
        }
    }

    private static final class SerializableWildcardType implements WildcardType, Serializable {
        private static final long serialVersionUID = 1L;

        private final Type[] upperBounds;
        private final Type[] lowerBounds;

        private SerializableWildcardType(Type[] upperBounds, Type[] lowerBounds) {
            this.upperBounds = upperBounds.clone();
            this.lowerBounds = lowerBounds.clone();
        }

        @Override
        public @Nonnull Type[] getUpperBounds() {
            return upperBounds.clone();
        }

        @Override
        public @Nonnull Type[] getLowerBounds() {
            return lowerBounds.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof WildcardType)) {
                return false;
            }
            WildcardType that = (WildcardType) other;
            return Arrays.equals(upperBounds, that.getUpperBounds())
                    && Arrays.equals(lowerBounds, that.getLowerBounds());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(upperBounds) ^ Arrays.hashCode(lowerBounds);
        }
    }

    @Override
    public String toString() {
        return "EventImpl{" +
                "eventType=" + eventType.getTypeName() +
                ", qualifiers=" + qualifiers +
                '}';
    }
}
