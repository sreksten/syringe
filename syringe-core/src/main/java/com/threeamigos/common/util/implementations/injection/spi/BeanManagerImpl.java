package com.threeamigos.common.util.implementations.injection.spi;

import com.threeamigos.common.util.implementations.injection.annotations.*;

import com.threeamigos.common.util.implementations.injection.scopes.InjectionPointImpl;
import com.threeamigos.common.util.implementations.injection.events.ObserverSupport;
import com.threeamigos.common.util.implementations.injection.events.NoOpObserverSupport;
import com.threeamigos.common.util.implementations.injection.interceptors.InterceptorSupport;
import com.threeamigos.common.util.implementations.injection.interceptors.NoOpInterceptorSupport;
import com.threeamigos.common.util.implementations.injection.decorators.DecoratorSupport;
import com.threeamigos.common.util.implementations.injection.decorators.NoOpDecoratorSupport;
import com.threeamigos.common.util.implementations.injection.annotations.legacy.LegacyNewSupport;
import com.threeamigos.common.util.implementations.injection.annotations.legacy.NoOpLegacyNewSupport;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.resolution.*;
import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import com.threeamigos.common.util.implementations.injection.scopes.CustomContextAdapter;
import com.threeamigos.common.util.implementations.injection.scopes.NoOpScopeSupport;
import com.threeamigos.common.util.implementations.injection.scopes.ScopeSupport;
import com.threeamigos.common.util.implementations.injection.el.ELSupport;
import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.InterceptorInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.events.ObserverMethodMetadata;
import com.threeamigos.common.util.implementations.injection.spi.support.NoOpSpiSupport;
import com.threeamigos.common.util.implementations.injection.spi.support.SpiSupport;
import com.threeamigos.common.util.implementations.injection.spi.support.SpiSupportLoader;
import com.threeamigos.common.util.implementations.injection.spi.support.SyntheticBeanPriority;
import com.threeamigos.common.util.implementations.injection.spi.support.SyntheticProducerBeanMarker;
import com.threeamigos.common.util.implementations.injection.spi.spievents.SimpleAnnotatedType;
import com.threeamigos.common.util.implementations.injection.util.TypesHelper;
import com.threeamigos.common.util.implementations.injection.util.LifecycleMethodHelper;
import com.threeamigos.common.util.implementations.injection.util.tx.TransactionServicesFactory;
import jakarta.annotation.Nonnull;
import jakarta.el.ELResolver;
import jakarta.el.ExpressionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.*;
import jakarta.inject.Singleton;

import java.io.Serializable;
import java.io.ObjectStreamException;
import java.lang.annotation.Annotation;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsEnum.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper.*;
import static com.threeamigos.common.util.implementations.injection.util.TypesHelper.*;

/**
 * Implementation of the CDI 4.1 BeanManager interface.
 *
 * <p>This class provides access to the CDI container's core functionality, including:
 * <ul>
 *   <li>Bean discovery and resolution</li>
 *   <li>Dependency injection</li>
 *   <li>Event firing and observer resolution</li>
 *   <li>Context management</li>
 *   <li>Interceptor and decorator resolution</li>
 * </ul>
 *
 * <p>The implementation delegates to existing components:
 * <ul>
 *   <li>{@link KnowledgeBase} - stores discovered beans and metadata</li>
 *   <li>{@link BeanResolver} - resolves dependencies and creates instances</li>
 *   <li>{@link ContextManager} - manages scoped contexts</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * // Create BeanManager
 * KnowledgeBase kb = new KnowledgeBase();
 * ContextManager cm = new ContextManager();
 * BeanManager bm = new BeanManagerImpl(kb, cm);
 *
 * // Find beans by type
 * Set<Bean<?>> beans = bm.getBeans(MyService.class);
 * Bean<?> bean = bm.resolve(beans);
 *
 * // Get bean instance
 * CreationalContext<?> ctx = bm.createCreationalContext(bean);
 * MyService service = (MyService) bm.getReference(bean, MyService.class, ctx);
 *
 * // Fire events
 * Event<Object> event = bm.getEvent();
 * event.select(MyEvent.class).fire(new MyEvent());
 * }</pre>
 *
 * @author Stefano Reksten
 * @see BeanManager
 */
public class BeanManagerImpl implements BeanManager, Serializable {
    private static final long serialVersionUID = 1L;

    private final KnowledgeBase knowledgeBase;
    private final BeanResolver beanResolver;
    private final ContextManager contextManager;
    private final TypesHelper typesHelper;
    private final List<Extension> registeredExtensions;
    private final String beanManagerId;
    private final ClassLoader registrationClassLoader;
    private volatile boolean afterBeanDiscoveryFired;
    private volatile boolean afterDeploymentValidationFired;
    private transient volatile ELResolver beanManagerELResolver;
    private final Map<Class<? extends Annotation>, List<Context>> bceContextInstances =
            new ConcurrentHashMap<>();
    private volatile boolean requireActiveContextForGetContext;
    private volatile ObserverSupport observerSupport;
    private volatile InterceptorSupport interceptorSupport;
    private volatile DecoratorSupport decoratorSupport;
    private volatile LegacyNewSupport legacyNewSupport;
    private volatile ScopeSupport scopeSupport;
    private volatile SpiSupport spiSupport;

    /**
     * Creates a new BeanManager implementation.
     *
     * @param knowledgeBase the knowledge base containing all discovered beans
     * @param contextManager the context manager for scope handling
     */
    public BeanManagerImpl(KnowledgeBase knowledgeBase, ContextManager contextManager) {
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase cannot be null");
        this.contextManager = Objects.requireNonNull(contextManager, "contextManager cannot be null");
        this.beanResolver = new BeanResolver(knowledgeBase, contextManager, TransactionServicesFactory.create());
        this.beanManagerId = UUID.randomUUID().toString();
        this.beanResolver.setOwningBeanManager(this);
        this.typesHelper = new TypesHelper();
        this.registeredExtensions = new ArrayList<>();
        this.requireActiveContextForGetContext = false;
        this.observerSupport = new NoOpObserverSupport();
        this.interceptorSupport = new NoOpInterceptorSupport();
        this.decoratorSupport = new NoOpDecoratorSupport();
        this.legacyNewSupport = new NoOpLegacyNewSupport();
        this.scopeSupport = new NoOpScopeSupport();
        this.spiSupport = SpiSupportLoader.load();
        this.beanResolver.setObserverSupport(this.observerSupport);
        this.beanResolver.setDecoratorSupport(this.decoratorSupport);
        this.beanResolver.setLegacyNewSupport(this.legacyNewSupport);

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = BeanManagerImpl.class.getClassLoader();
        }
        this.registrationClassLoader = classLoader;
        BeanManagerRegistry.register(this, classLoader);
    }

    public ClassLoader getRegistrationClassLoader() {
        return registrationClassLoader;
    }

    public void unregisterFromGlobalRegistries() {
        BeanManagerRegistry.unregister(this, registrationClassLoader);
    }

    public void setLegacyCdi10NewEnabled(boolean enabled) {
        if (enabled) {
            legacyNewSupport.enable();
        }
    }

    /** Wired by Syringe after ServiceLoader discovery. */
    public void setLegacyNewSupport(LegacyNewSupport legacyNewSupport) {
        this.legacyNewSupport = legacyNewSupport != null
                ? legacyNewSupport
                : new NoOpLegacyNewSupport();
        this.beanResolver.setLegacyNewSupport(this.legacyNewSupport);
    }

    public void registerExtensions(Collection<Extension> extensions) {
        registeredExtensions.clear();
        if (extensions == null || extensions.isEmpty()) {
            return;
        }
        registeredExtensions.addAll(extensions);
        for (Extension extension : extensions) {
            if (extension == null) {
                continue;
            }
            knowledgeBase.addBean(new ExtensionServiceProviderBean<>(extension));
        }
    }

    public void clearRegisteredExtensions() {
        registeredExtensions.clear();
    }

    public void setRequireActiveContextForGetContext(boolean requireActiveContextForGetContext) {
        this.requireActiveContextForGetContext = requireActiveContextForGetContext;
    }

    /** Wired by Syringe after ServiceLoader discovery. */
    public void setObserverSupport(ObserverSupport observerSupport) {
        this.observerSupport = observerSupport != null
                ? observerSupport
                : new NoOpObserverSupport();
        this.beanResolver.setObserverSupport(this.observerSupport);
    }

    /** Wired by Syringe after ServiceLoader discovery. */
    public void setInterceptorSupport(InterceptorSupport interceptorSupport) {
        this.interceptorSupport = interceptorSupport != null
                ? interceptorSupport
                : new NoOpInterceptorSupport();
    }

    /** Wired by Syringe after ServiceLoader discovery. */
    public void setDecoratorSupport(DecoratorSupport decoratorSupport) {
        this.decoratorSupport = decoratorSupport != null
                ? decoratorSupport
                : new NoOpDecoratorSupport();
        this.beanResolver.setDecoratorSupport(this.decoratorSupport);
    }

    public DecoratorSupport getDecoratorSupport() {
        return decoratorSupport;
    }

    /** Wired by Syringe after ServiceLoader discovery. */
    public void setScopeSupport(ScopeSupport scopeSupport) {
        this.scopeSupport = scopeSupport != null
                ? scopeSupport
                : new NoOpScopeSupport();
    }

    public ScopeSupport getScopeSupport() {
        return scopeSupport;
    }

    /** Wired by Syringe after ServiceLoader discovery. */
    public void setSpiSupport(SpiSupport spiSupport) {
        this.spiSupport = spiSupport != null
                ? spiSupport
                : new NoOpSpiSupport();
    }

    public SpiSupport getSpiSupport() {
        return spiSupport;
    }

    /**
     * Clears runtime state retained by the BeanManager.
     * Intended for container shutdown.
     */
    public void clearRuntimeState() {
        registeredExtensions.clear();
        bceContextInstances.clear();
        beanResolver.clearRuntimeState();
        if (decoratorSupport != null) {
            decoratorSupport.clear();
        }
        clearTransientReferencesForOwner(beanManagerId);
    }

    public static BeanManagerImpl getRegisteredBeanManager(ClassLoader classLoader) {
        return BeanManagerRegistry.getRegisteredBeanManager(classLoader);
    }

    public static BeanManagerImpl getRegisteredBeanManager(String beanManagerId) {
        return BeanManagerRegistry.getRegisteredBeanManager(beanManagerId);
    }

    public static Collection<BeanManagerImpl> getRegisteredBeanManagersSnapshot() {
        return BeanManagerRegistry.getRegisteredBeanManagersSnapshot();
    }

    public boolean destroyOwnedTransientReference(Object instance) {
        return destroyTransientReference(beanManagerId, instance);
    }

    public <T> void registerOwnedTransientReference(Bean<T> bean,
                                                    T instance,
                                                    CreationalContext<T> creationalContext) {
        registerTransientReference(beanManagerId, bean, instance, creationalContext);
    }

    public static boolean destroyTransientReference(Object instance) {
        return destroyTransientReference(null, instance);
    }

    private static boolean destroyTransientReference(String ownerBeanManagerId, Object instance) {
        return BeanManagerRegistry.destroyTransientReference(ownerBeanManagerId, instance);
    }

    private static <T> void registerTransientReference(String ownerBeanManagerId,
                                                       Bean<T> bean,
                                                       T instance,
                                                       CreationalContext<T> creationalContext) {
        BeanManagerRegistry.registerTransientReference(ownerBeanManagerId, bean, instance, creationalContext);
    }

    private static void clearTransientReferencesForOwner(String ownerBeanManagerId) {
        BeanManagerRegistry.clearTransientReferencesForOwner(ownerBeanManagerId);
    }

    public String getBeanManagerId() {
        return beanManagerId;
    }

    private Object writeReplace() throws ObjectStreamException {
        return BeanManagerRegistry.createSerializedReference(beanManagerId);
    }

    /**
     * Returns the KnowledgeBase used by this BeanManager.
     * This is used internally by extension events to propagate definition errors.
     *
     * @return the knowledge base
     */
    public KnowledgeBase getKnowledgeBase() {
        return knowledgeBase;
    }

    public BeanResolver getBeanResolver() {
        return beanResolver;
    }

    // ==================== BeanContainer Methods ====================

    /**
     * Gets a contextual reference for a bean.
     *
     * <p>For normal scopes (@ApplicationScoped, @RequestScoped, etc.), this returns a client proxy
     * that delegates to the current contextual instance. For pseudo-scopes (@Dependent), this
     * returns the actual bean instance.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * Set<Bean<?>> beans = beanManager.getBeans(UserService.class);
     * Bean<?> bean = beanManager.resolve(beans);
     * CreationalContext<?> ctx = beanManager.createCreationalContext(bean);
     *
     * // Returns a proxy for @ApplicationScoped beans
     * UserService service = (UserService) beanManager.getReference(bean, UserService.class, ctx);
     * }</pre>
     *
     * @param bean the bean to get a reference for
     * @param beanType the type of the bean (must be in bean.getTypes())
     * @param ctx the creational context
     * @return a contextual reference (proxy for normal scopes, instance for pseudo-scopes)
     * @throws IllegalArgumentException if bean or beanType is null
     */
    @Override
    public Object getReference(Bean<?> bean, Type beanType, CreationalContext<?> ctx) {
        requireAfterDeploymentValidation("getReference(Bean, Type, CreationalContext)");
        if (bean == null) {
            throw new IllegalArgumentException("bean cannot be null");
        }
        if (beanType == null) {
            throw new IllegalArgumentException("beanType cannot be null");
        }
        if (!isBeanTypeInBeanTypes(beanType, bean.getTypes())) {
            throw new IllegalArgumentException(
                "beanType " + beanType + " is not a bean type of bean " + bean.getBeanClass().getName());
        }

        // For normal scopes, return a client proxy
        if (contextManager.isNormalScope(bean.getScope())) {
            Object proxy = contextManager.createClientProxy(bean);
            if (proxy != null) {
                return proxy;
            }
        }

        if (DEPENDENT.matches(bean.getScope()) && ctx instanceof CreationalContextImpl) {
            @SuppressWarnings("unchecked")
            Bean<Object> dependentBean = (Bean<Object>) bean;
            CreationalContext<Object> childContext = createCreationalContext(dependentBean);
            Object instance = dependentBean.create(childContext);
            @SuppressWarnings("unchecked")
            CreationalContextImpl<Object> parentContext = (CreationalContextImpl<Object>) ctx;
            parentContext.addDependentInstance(dependentBean, instance, childContext);
            return maybeDecorateBuiltInInstance(bean, childContext, instance);
        }

        // For pseudo-scopes (e.g., @Dependent), get from context
        Context context = getContext(bean.getScope());
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object instance = context.get((Contextual) bean, ctx);
        return maybeDecorateBuiltInInstance(bean, ctx, instance);
    }

    private Object maybeDecorateBuiltInInstance(Bean<?> bean, CreationalContext<?> ctx, Object instance) {
        if (instance == null || bean == null) {
            return instance;
        }

        // Built-in Instance<T> already applies decorators through BeanResolver.
        if (bean instanceof BuiltInInstanceBean) {
            return instance;
        }

        // Managed beans already apply decorators during BeanImpl lifecycle.
        if (bean instanceof BeanImpl<?>) {
            return instance;
        }
        // Producer values are not automatically decorated.
        if (bean instanceof ProducerBean<?>) {
            return instance;
        }
        // Interceptors/decorators are not decoratable targets.
        if (bean instanceof Interceptor || bean instanceof Decorator) {
            return instance;
        }
        // Built-in BeanManager with @Default must not be decorated.
        if (BeanManager.class.equals(bean.getBeanClass())
                && bean.getQualifiers().contains(jakarta.enterprise.inject.Default.Literal.INSTANCE)) {
            return instance;
        }

        DecoratorSupport localDecoratorSupport = decoratorSupport != null
                ? decoratorSupport
                : new NoOpDecoratorSupport();
        List<DecoratorInfo> decorators = localDecoratorSupport.resolve(bean.getTypes(), bean.getQualifiers());
        if (decorators.isEmpty()) {
            return instance;
        }

        CreationalContext<?> creationalContext = ctx != null ? ctx : createCreationalContext(null);
        Object wrapped = localDecoratorSupport.applyDecoratorChain(instance, decorators, this, creationalContext);
        return wrapped != null ? wrapped : instance;
    }

    private boolean isBeanTypeInBeanTypes(Type requestedType, Set<Type> beanTypes) {
        if (beanTypes.contains(requestedType)) {
            return true;
        }
        for (Type beanType : beanTypes) {
            try {
                if (notSameRawType(requestedType, beanType)) {
                    continue;
                }
                if (typesHelper.isAssignable(requestedType, beanType)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // Fall through to primitive/wrapper exact checks.
            }
        }
        if (!(requestedType instanceof Class<?>)) {
            return false;
        }

        Class<?> requestedClass = (Class<?>) requestedType;
        Class<?> alternate = requestedClass.isPrimitive() ? boxPrimitive(requestedClass) : unboxWrapper(requestedClass);
        return alternate != null && beanTypes.contains(alternate);
    }

    private boolean notSameRawType(Type requiredType, Type beanType) {
        if (requiredType == null || beanType == null) {
            return true;
        }
        if (requiredType instanceof TypeVariable || requiredType instanceof WildcardType) {
            return false;
        }

        Class<?> requiredRaw;
        Class<?> beanRaw;
        try {
            requiredRaw = normalizePrimitiveType(getRawType(requiredType));
            beanRaw = normalizePrimitiveType(getRawType(beanType));
        } catch (RuntimeException e) {
            return false;
        }

        if (requiredRaw == null || beanRaw == null) {
            return false;
        }
        return !requiredRaw.equals(beanRaw);
    }

    private Class<?> normalizePrimitiveType(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == char.class) return Character.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == void.class) return Void.class;
        return type;
    }

    private Class<?> boxPrimitive(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == char.class) return Character.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == void.class) return Void.class;
        return null;
    }

    private Class<?> unboxWrapper(Class<?> type) {
        if (type == Integer.class) return int.class;
        if (type == Long.class) return long.class;
        if (type == Double.class) return double.class;
        if (type == Float.class) return float.class;
        if (type == Boolean.class) return boolean.class;
        if (type == Character.class) return char.class;
        if (type == Byte.class) return byte.class;
        if (type == Short.class) return short.class;
        if (type == Void.class) return void.class;
        return null;
    }

    /**
     * Creates a new creational context for managing dependent objects.
     *
     * <p>The creational context tracks dependent objects created during bean instantiation
     * so they can be destroyed when the parent bean is destroyed.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * Bean<MyService> bean = ...;
     * CreationalContext<MyService> ctx = beanManager.createCreationalContext(bean);
     * MyService instance = bean.create(ctx);
     *
     * // Later, destroy the bean and its dependents
     * bean.destroy(instance, ctx);
     * ctx.release();
     * }</pre>
     *
     * @param contextual the contextual type (usually a Bean)
     * @param <T> the type of the contextual instance
     * @return a new creational context
     */
    @Override
    public <T> CreationalContext<T> createCreationalContext(Contextual<T> contextual) {
        return new CreationalContextImpl<>();
    }

    public static <T> CreationalContext<T> resolveDependentCreationalContext(
            CreationalContext<T> creationalContext,
            Bean<?> bean,
            Object instance) {
        if (!(creationalContext instanceof CreationalContextImpl) || bean == null || instance == null) {
            return creationalContext;
        }
        CreationalContextImpl<?> parentContext = (CreationalContextImpl<?>) creationalContext;
        CreationalContext<?> dependentContext = parentContext.findDependentCreationalContext(bean, instance);
        if (dependentContext == null) {
            return creationalContext;
        }
        @SuppressWarnings("unchecked")
        CreationalContext<T> typedContext = (CreationalContext<T>) dependentContext;
        return typedContext;
    }

    /**
     * Returns all beans matching the given type and qualifiers.
     *
     * <p>This method performs type-safe resolution by checking:
     * <ul>
     *   <li>Type assignability (using CDI type rules)</li>
     *   <li>Qualifier matching (all required qualifiers must be present)</li>
     * </ul>
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * // Find all PaymentProcessor beans
     * Set<Bean<?>> beans = beanManager.getBeans(PaymentProcessor.class);
     *
     * // Find PaymentProcessor with @CreditCard qualifier
     * Set<Bean<?>> creditCardProcessors = beanManager.getBeans(
     *     PaymentProcessor.class,
     *     new CreditCardLiteral()
     * );
     *
     * // Find by name
     * Set<Bean<?>> namedBeans = beanManager.getBeans(
     *     PaymentProcessor.class,
     *     new NamedLiteral("stripe")
     * );
     * }</pre>
     *
     * @param beanType the required type
     * @param qualifiers the required qualifiers (empty = @Default)
     * @return set of matching beans (can be empty, never null)
     * @throws IllegalArgumentException if beanType is null
     */
    @Override
    public Set<Bean<?>> getBeans(Type beanType, Annotation... qualifiers) {
        return getBeansInternal(beanType, qualifiers, true);
    }

    /**
     * Internal container lookup used by invoker construction during BCE registration.
     *
     * <p>This bypasses lifecycle gating for {@link #getBeans(Type, Annotation...)} while
     * preserving the same type/qualifier resolution semantics.
     */
    public Set<Bean<?>> getBeansForInvokerLookup(Type beanType, Annotation... qualifiers) {
        return getBeansInternal(beanType, qualifiers, false);
    }

    private Set<Bean<?>> getBeansInternal(Type beanType,
                                          Annotation[] qualifiers,
                                          boolean enforceAfterBeanDiscoveryGate) {
        if (enforceAfterBeanDiscoveryGate) {
            requireAfterBeanDiscovery("getBeans(Type, Annotation...)");
        }
        if (beanType == null) {
            throw new IllegalArgumentException("beanType cannot be null");
        }
        if (beanType instanceof TypeVariable<?>) {
            throw new IllegalArgumentException("beanType cannot be a type variable: " + beanType);
        }
        validateRequiredQualifiers(qualifiers);
        Set<Bean<?>> builtInLookupBeans = resolveBuiltInLookupBeans(beanType, qualifiers);
        if (!builtInLookupBeans.isEmpty()) {
            return builtInLookupBeans;
        }

        LegacyNewSupport.LegacyNewSelection legacyNewSelection =
                legacyNewSupport.resolveSelection(beanType, qualifiers);
        if (legacyNewSelection != null) {
            if (!legacyNewSupport.isEnabled()) {
                return Collections.emptySet();
            }
            return resolveLegacyNewBeans(beanType, legacyNewSelection);
        }

        Set<Annotation> requiredQualifiers = extractQualifiers(qualifiers);
        Set<Bean<?>> matchingBeans = new HashSet<>();

        for (Bean<?> bean : knowledgeBase.getValidBeans()) {
            if (!isBeanUsableForResolution(bean)) {
                continue;
            }

            // Check type compatibility
            boolean typeMatches = false;
            for (Type type : bean.getTypes()) {
                if (notSameRawType(beanType, type)) {
                    continue;
                }
                if (typesHelper.isLookupTypeAssignable(beanType, type)) {
                    typeMatches = true;
                    break;
                }
            }

            if (!typeMatches) {
                continue;
            }

            // Check qualifier match
            if (qualifiersMatchIncludingBeanName(requiredQualifiers, bean)) {
                if (isNotBeanAccessibleFromCurrentInjectionPoint(bean)) {
                    continue;
                }
                matchingBeans.add(bean);
            }
        }

        return applySpecializationFiltering(matchingBeans);
    }

    private Set<Bean<?>> resolveBuiltInLookupBeans(Type beanType, Annotation[] qualifiers) {
        if (!(beanType instanceof ParameterizedType)) {
            return Collections.emptySet();
        }
        ParameterizedType parameterizedType = (ParameterizedType) beanType;
        Type rawType = parameterizedType.getRawType();
        if (!(rawType instanceof Class<?>)) {
            return Collections.emptySet();
        }
        Class<?> rawClass = (Class<?>) rawType;
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        if (typeArguments.length != 1) {
            return Collections.emptySet();
        }
        Annotation[] requestedQualifiers = qualifiers == null ? new Annotation[0] : qualifiers;
        Set<Annotation> lookupBeanQualifiers = normalizeBeanQualifiers(Arrays.asList(requestedQualifiers));
        Set<Bean<?>> builtInBeans = new LinkedHashSet<>();

        if (Event.class.equals(rawClass)) {
            builtInBeans.add(new BuiltInEventBean(typeArguments[0], beanType, lookupBeanQualifiers, this));
            return builtInBeans;
        }
        if (Instance.class.equals(rawClass)) {
            Set<Annotation> resolutionQualifiers = extractQualifiers(requestedQualifiers);
            builtInBeans.add(new BuiltInInstanceBean(
                    beanType, lookupBeanQualifiers, resolutionQualifiers, this));
            return builtInBeans;
        }
        return Collections.emptySet();
    }

    private boolean qualifiersMatchIncludingBeanName(Set<Annotation> requiredQualifiers, Bean<?> bean) {
        Set<Annotation> beanQualifiers = bean.getQualifiers();
        if (qualifiersMatch(requiredQualifiers, beanQualifiers)) {
            return true;
        }

        String requiredNamed = extractNonEmptyRequiredNamedValue(requiredQualifiers);
        if (requiredNamed == null) {
            return false;
        }

        String beanName = bean.getName();
        if (beanName == null || beanName.isEmpty() || !beanName.equals(requiredNamed)) {
            return false;
        }

        Set<Annotation> requiredWithoutNamed = removeNamedQualifiers(requiredQualifiers);
        return qualifiersMatch(requiredWithoutNamed, beanQualifiers);
    }

    private String extractNonEmptyRequiredNamedValue(Set<Annotation> qualifiers) {
        if (qualifiers == null) {
            return null;
        }
        for (Annotation qualifier : qualifiers) {
            if (qualifier == null || !hasNamedAnnotation(qualifier.annotationType())) {
                continue;
            }
            String value = getNamedValue(qualifier);
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        return null;
    }

    private Set<Annotation> removeNamedQualifiers(Set<Annotation> qualifiers) {
        Set<Annotation> withoutNamed = new LinkedHashSet<>();
        if (qualifiers == null) {
            return withoutNamed;
        }
        for (Annotation qualifier : qualifiers) {
            if (qualifier == null || !hasNamedAnnotation(qualifier.annotationType())) {
                withoutNamed.add(qualifier);
            }
        }
        return withoutNamed;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Set<Bean<?>> resolveLegacyNewBeans(
            Type beanType,
            LegacyNewSupport.LegacyNewSelection selection) {
        Set<Bean<?>> matchingBeans = new HashSet<>();
        Class<?> targetClass = selection.getTargetClass();

        for (Bean<?> bean : knowledgeBase.getValidBeans()) {
            if (!isBeanUsableForResolution(bean)) {
                continue;
            }
            if (!targetClass.equals(bean.getBeanClass())) {
                continue;
            }

            boolean typeMatches = false;
            for (Type type : bean.getTypes()) {
                if (notSameRawType(beanType, type)) {
                    continue;
                }
                if (typesHelper.isLookupTypeAssignable(beanType, type)) {
                    typeMatches = true;
                    break;
                }
            }

            if (!typeMatches) {
                continue;
            }

            if (isNotBeanAccessibleFromCurrentInjectionPoint(bean)) {
                continue;
            }
            matchingBeans.add(legacyNewSupport.adaptLegacyNewBean((Bean) bean));
        }

        return applySpecializationFiltering(matchingBeans);
    }

    private void validateRequiredQualifiers(Annotation[] qualifiers) {
        if (qualifiers == null || qualifiers.length == 0) {
            return;
        }
        Set<Class<? extends Annotation>> seenNonRepeatable = new HashSet<>();
        for (Annotation qualifier : qualifiers) {
            if (qualifier == null) {
                continue;
            }
            Class<? extends Annotation> qualifierType = qualifier.annotationType();
            if (!isQualifier(qualifierType)) {
                throw new IllegalArgumentException("Annotation is not a qualifier type: " + qualifierType.getName());
            }
            if (!hasRepeatableAnnotation(qualifierType)) {
                if (!seenNonRepeatable.add(qualifierType)) {
                    throw new IllegalArgumentException("Duplicate non-repeating qualifier: " + qualifierType.getName());
                }
            }
        }
    }

    /**
     * Returns all beans with the given EL name.
     *
     * <p>Beans can be named using {@code @Named} annotation:
     * <pre>{@code
     * @Named("userService")
     * @ApplicationScoped
     * public class UserService { ... }
     * }</pre>
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * // Find bean by name
     * Set<Bean<?>> beans = beanManager.getBeans("userService");
     * if (!beans.isEmpty()) {
     *     Bean<?> bean = beanManager.resolve(beans);
     *     // Use bean...
     * }
     * }</pre>
     *
     * @param name the EL name
     * @return set of beans with the given name (can be empty, never null)
     * @throws IllegalArgumentException if the name is null or empty
     */
    @Override
    public Set<Bean<?>> getBeans(String name) {
        requireAfterBeanDiscovery("getBeans(String)");
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name cannot be null or empty");
        }

        Set<Bean<?>> namedBeans = new HashSet<>();

        for (Bean<?> bean : knowledgeBase.getValidBeans()) {
            if (!isBeanUsableForResolution(bean)) {
                continue;
            }
            if (name.equals(bean.getName())) {
                namedBeans.add(bean);
            }
        }

        return applySpecializationFiltering(namedBeans);
    }

    /**
     * Resolves ambiguous bean dependencies using CDI 4.1 resolution rules.
     *
     * <p>Resolution algorithm:
     * <ol>
     *   <li>If only one bean, return it</li>
     *   <li>If multiple alternatives exist, return the highest priority alternative</li>
     *   <li>If multiple alternatives with the same priority, return null (ambiguous)</li>
     *   <li>If multiple non-alternatives exist, return null (ambiguous)</li>
     * </ol>
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * // Multiple implementations exist
     * Set<Bean<?>> beans = beanManager.getBeans(PaymentProcessor.class);
     *
     * // Resolve using priority
     * Bean<?> bean = beanManager.resolve(beans);
     * if (bean == null) {
     *     throw new AmbiguousResolutionException("Cannot resolve PaymentProcessor");
     * }
     *
     * // Alternative beans with @Priority
     * @Alternative
     * @Priority(100)
     * public class StripePaymentProcessor implements PaymentProcessor { ... }
     *
     * @Alternative
     * @Priority(200)  // Higher priority wins
     * public class PayPalPaymentProcessor implements PaymentProcessor { ... }
     * }</pre>
     *
     * @param beans the set of beans to resolve
     * @param <X> the bean type
     * @return the resolved bean, or null if ambiguous or empty
     */
    @Override
    public <X> Bean<? extends X> resolve(Set<Bean<? extends X>> beans) {
        return resolveInternal(beans, true);
    }

    /**
     * Internal container resolution used by invoker construction during BCE registration.
     *
     * <p>This bypasses lifecycle gating for {@link #resolve(Set)} while preserving
     * standard ambiguity handling and alternative-priority resolution.
     */
    public <X> Bean<? extends X> resolveForInvokerLookup(Set<Bean<? extends X>> beans) {
        return resolveInternal(beans, false);
    }

    private <X> Bean<? extends X> resolveInternal(Set<Bean<? extends X>> beans,
                                                  boolean enforceAfterBeanDiscoveryGate) {
        if (enforceAfterBeanDiscoveryGate) {
            requireAfterBeanDiscovery("resolve(Set)");
        }
        if (beans == null || beans.isEmpty()) {
            return null;
        }

        Set<Bean<? extends X>> filteredBeans = applySpecializationFiltering(beans);
        if (filteredBeans.isEmpty()) {
            return null;
        }

        List<Bean<? extends X>> enabledBeans = new ArrayList<>();
        for (Bean<? extends X> bean : filteredBeans) {
            if (isBeanUsableForResolution(bean)) {
                enabledBeans.add(bean);
            }
        }

        if (enabledBeans.isEmpty()) {
            return null;
        }

        if (enabledBeans.size() == 1) {
            return enabledBeans.get(0);
        }

        // Apply CDI 4.1 resolution rules:
        // 1. Filter enabled beans (non-alternatives or enabled alternatives)
        // 2. Select the highest priority alternative if any exist
        // 3. If multiple alternatives with the same priority, return null (ambiguous)

        List<Bean<? extends X>> alternatives = new ArrayList<>();
        List<Bean<? extends X>> nonAlternatives = new ArrayList<>();

        for (Bean<? extends X> bean : enabledBeans) {
            if (isEffectivelyAlternative(bean)) {
                alternatives.add(bean);
            } else {
                nonAlternatives.add(bean);
            }
        }

        // If alternatives exist, use highest priority alternative
        if (!alternatives.isEmpty()) {
            // Sort by priority (higher priority value = higher precedence)
            alternatives.sort((b1, b2) -> {
                int p1 = getPriority(b1);
                int p2 = getPriority(b2);
                return Integer.compare(p2, p1); // Descending order
            });

            Bean<? extends X> highest = alternatives.get(0);

            // Check for ambiguity (multiple alternatives with same priority)
            if (alternatives.size() > 1) {
                int highestPriority = getPriority(highest);
                if (getPriority(alternatives.get(1)) == highestPriority) {
                    throw new jakarta.enterprise.inject.AmbiguousResolutionException(
                        "Ambiguous dependency: multiple alternatives with same highest priority " + highestPriority);
                }
            }

            return highest;
        }

        // No alternatives, check non-alternatives
        if (nonAlternatives.size() == 1) {
            return nonAlternatives.get(0);
        }

        // Multiple non-alternatives = ambiguous
        throw new jakarta.enterprise.inject.AmbiguousResolutionException(
            "Ambiguous dependency: multiple beans match and no alternative can resolve ambiguity");
    }

    /**
     * Basic specialization filtering: if a candidate bean specializes its direct superclass,
     * the specialized superclass bean is removed from candidate sets.
     */
    private <X> Set<Bean<? extends X>> applySpecializationFiltering(Set<Bean<? extends X>> candidates) {
        if (candidates == null || candidates.size() < 2) {
            return candidates;
        }

        Set<Class<?>> specializedSuperclasses = new HashSet<>();
        for (Bean<? extends X> candidate : candidates) {
            Class<?> beanClass = candidate.getBeanClass();
            if (hasSpecializesAnnotation(beanClass)) {
                specializedSuperclasses.addAll(collectSpecializedSuperclasses(beanClass));
            }
        }

        if (specializedSuperclasses.isEmpty()) {
            return candidates;
        }

        Set<Bean<? extends X>> filtered = new HashSet<>();
        for (Bean<? extends X> candidate : candidates) {
            if (!specializedSuperclasses.contains(candidate.getBeanClass())) {
                filtered.add(candidate);
            }
        }
        return filtered;
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

    private boolean isBeanEnabledForResolution(Bean<?> bean) {
        if (bean == null) {
            return false;
        }
        if (bean instanceof ProducerBean) {
            ProducerBean<?> producerBean = (ProducerBean<?>) bean;
            Bean<?> declaringBean = findDeclaringBean(producerBean.getDeclaringClass());
            if (declaringBean == null) {
                return false;
            }
            if (!isBeanEnabledForResolution(declaringBean)) {
                return false;
            }
            return producerBean.isAlternativeEnabled();
        }
        if (bean instanceof SyntheticProducerBeanMarker) {
            Bean<?> originalBean = findOriginalProducerBean(bean);
            if (originalBean instanceof ProducerBean) {
                return isBeanEnabledForResolution(originalBean);
            }
        }
        if (!bean.isAlternative()) {
            return true;
        }
        if (bean instanceof SyntheticBeanPriority) {
            return ((SyntheticBeanPriority) bean).getPriority() != null || isAlternativeSelectedByClassOrStereotype(bean);
        }
        if (bean instanceof BeanImpl) {
            return ((BeanImpl<?>) bean).isAlternativeEnabled();
        }
        return isAlternativeSelectedByClassOrStereotype(bean);
    }

    private boolean isBeanUsableForResolution(Bean<?> bean) {
        if (!isBeanEnabledForResolution(bean)) {
            return false;
        }
        if (bean instanceof BeanImpl<?>) {
            BeanImpl<?> managedBean = (BeanImpl<?>) bean;
            return !managedBean.hasValidationErrors() && !managedBean.isVetoed();
        }
        if (bean instanceof ProducerBean<?>) {
            ProducerBean<?> producerBean = (ProducerBean<?>) bean;
            return !producerBean.hasValidationErrors() && !producerBean.isVetoed();
        }
        return true;
    }

    private Bean<?> findDeclaringBean(Class<?> declaringClass) {
        if (declaringClass == null) {
            return null;
        }
        for (Bean<?> candidate : knowledgeBase.getValidBeans()) {
            if (candidate instanceof ProducerBean) {
                continue;
            }
            if (declaringClass.equals(candidate.getBeanClass())) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isEffectivelyAlternative(Bean<?> bean) {
        if (bean == null) {
            return false;
        }
        if (bean.isAlternative()) {
            return true;
        }
        if (bean instanceof ProducerBean) {
            ProducerBean<?> producerBean = (ProducerBean<?>) bean;
            Bean<?> declaringBean = findDeclaringBean(producerBean.getDeclaringClass());
            return declaringBean != null && declaringBean.isAlternative();
        }
        if (bean instanceof SyntheticProducerBeanMarker) {
            Bean<?> originalBean = findOriginalProducerBean(bean);
            return isEffectivelyAlternative(originalBean);
        }
        return false;
    }

    private boolean isAlternativeSelectedByClassOrStereotype(Bean<?> bean) {
        if (bean instanceof SyntheticProducerBeanMarker) {
            Bean<?> originalBean = findOriginalProducerBean(bean);
            if (originalBean instanceof ProducerBean) {
                ProducerBean<?> producerBean = (ProducerBean<?>) originalBean;

                if (extractPriorityFromProducerMember(producerBean) != null) {
                    return true;
                }
                if (producerBean.getPriority() != null) {
                    return true;
                }

                Class<?> declaringClass = producerBean.getDeclaringClass();
                if (declaringClass != null) {
                    Integer declaringPriority = extractPriorityFromClass(declaringClass);
                    if (declaringPriority != null) {
                        return true;
                    }

                    String declaringClassName = declaringClass.getName();
                    if (knowledgeBase.isAlternativeEnabledProgrammatically(declaringClassName) ||
                            knowledgeBase.isAlternativeEnabledInBeansXml(declaringClassName)) {
                        return true;
                    }

                    for (Annotation annotation : declaringClass.getAnnotations()) {
                        Class<? extends Annotation> annotationType = annotation.annotationType();
                        if (hasStereotypeAnnotation(annotationType)) {
                            String stereotypeName = annotationType.getName();
                            if (knowledgeBase.isAlternativeEnabledProgrammatically(stereotypeName) ||
                                    knowledgeBase.isAlternativeEnabledInBeansXml(stereotypeName)) {
                                return true;
                            }
                        }
                    }
                }

                Set<Class<? extends Annotation>> producerStereotypes = producerBean.getStereotypes();
                if (producerStereotypes != null) {
                    for (Class<? extends Annotation> stereotype : producerStereotypes) {
                        String stereotypeName = stereotype.getName();
                        if (knowledgeBase.isAlternativeEnabledProgrammatically(stereotypeName) ||
                                knowledgeBase.isAlternativeEnabledInBeansXml(stereotypeName)) {
                            return true;
                        }
                    }
                }
            }
        }

        Class<?> beanClass = bean.getBeanClass();
        if (beanClass == null) {
            return false;
        }

        if (knowledgeBase.getApplicationAlternativeOrder(beanClass) >= 0) {
            return true;
        }

        Integer classPriority = extractPriorityFromClass(beanClass);
        if (classPriority != null) {
            return true;
        }

        String className = beanClass.getName();
        if (knowledgeBase.isAlternativeEnabledProgrammatically(className) ||
                knowledgeBase.isAlternativeEnabledInBeansXml(className)) {
            return true;
        }

        Set<Class<? extends Annotation>> stereotypes = bean.getStereotypes();
        if (stereotypes != null) {
            for (Class<? extends Annotation> stereotype : stereotypes) {
                String stereotypeName = stereotype.getName();
                if (knowledgeBase.isAlternativeEnabledProgrammatically(stereotypeName) ||
                        knowledgeBase.isAlternativeEnabledInBeansXml(stereotypeName)) {
                    return true;
                }
            }
        }

        for (Annotation annotation : beanClass.getAnnotations()) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (hasStereotypeAnnotation(type)) {
                String stereotypeName = type.getName();
                if (knowledgeBase.isAlternativeEnabledProgrammatically(stereotypeName) ||
                        knowledgeBase.isAlternativeEnabledInBeansXml(stereotypeName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNotBeanAccessibleFromCurrentInjectionPoint(Bean<?> bean) {
        if (bean == null) {
            return true;
        }
        Class<?> beanClass = bean.getBeanClass();
        if (beanClass == null) {
            return false;
        }

        InjectionPoint injectionPoint = beanResolver != null ? beanResolver.getCurrentInjectionPoint() : null;
        if (injectionPoint == null) {
            return Modifier.isPrivate(beanClass.getModifiers());
        }

        Member member = injectionPoint.getMember();
        Class<?> consumerClass = member != null ? member.getDeclaringClass() : null;
        if (consumerClass == null && injectionPoint.getBean() != null) {
            consumerClass = injectionPoint.getBean().getBeanClass();
        }

        return !isClassAccessibleTo(beanClass, consumerClass);
    }

    private boolean isClassAccessibleTo(Class<?> beanClass, Class<?> consumerClass) {
        if (beanClass == null || consumerClass == null) {
            return true;
        }
        if (beanClass.equals(consumerClass)) {
            return true;
        }

        int modifiers = beanClass.getModifiers();
        if (Modifier.isPublic(modifiers)) {
            return enclosingClassesAccessible(beanClass, consumerClass);
        }
        if (Modifier.isPrivate(modifiers)) {
            Class<?> owner = beanClass.getEnclosingClass();
            return owner != null && (owner.equals(consumerClass) || isEnclosedWithin(consumerClass, owner));
        }
        if (Modifier.isProtected(modifiers)) {
            return inSamePackage(beanClass, consumerClass) || beanClass.isAssignableFrom(consumerClass);
        }
        return inSamePackage(beanClass, consumerClass);
    }

    private boolean enclosingClassesAccessible(Class<?> beanClass, Class<?> consumerClass) {
        Class<?> enclosing = beanClass.getEnclosingClass();
        while (enclosing != null) {
            int modifiers = enclosing.getModifiers();
            if (Modifier.isPrivate(modifiers)) {
                return enclosing.equals(consumerClass) || isEnclosedWithin(consumerClass, enclosing);
            }
            if (!Modifier.isPublic(modifiers) && !inSamePackage(enclosing, consumerClass)) {
                return false;
            }
            enclosing = enclosing.getEnclosingClass();
        }
        return true;
    }

    private boolean isEnclosedWithin(Class<?> nestedClass, Class<?> enclosingClass) {
        Class<?> current = nestedClass;
        while (current != null) {
            if (current.equals(enclosingClass)) {
                return true;
            }
            current = current.getEnclosingClass();
        }
        return false;
    }

    private boolean inSamePackage(Class<?> a, Class<?> b) {
        return packageName(a).equals(packageName(b));
    }

    private String packageName(Class<?> type) {
        Package pkg = type.getPackage();
        return pkg != null ? pkg.getName() : "";
    }

    /**
     * Finds all observer methods matching the event and qualifiers.
     *
     * <p>Observer methods are matched by:
     * <ul>
     *   <li>Event type assignability</li>
     *   <li>Qualifier matching (all observer qualifiers must be in event qualifiers)</li>
     *   <li>Synchronous and asynchronous observer methods are both included</li>
     * </ul>
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * // Observer method
     * public class OrderService {
     *     void onOrderCreated(@Observes @Created Order order) {
     *         // Process order...
     *     }
     * }
     *
     * // Find matching observers
     * Order order = new Order();
     * Set<ObserverMethod<? super Order>> observers =
     *     beanManager.resolveObserverMethods(order, new CreatedLiteral());
     *
     * // Notify observers
     * for (ObserverMethod<? super Order> observer : observers) {
     *     observer.notify(order);
     * }
     * }</pre>
     *
     * @param event the event object
     * @param qualifiers the event qualifiers
     * @param <T> the event type
     * @return set of matching observer methods (can be empty, never null)
     * @throws IllegalArgumentException if the event is null
     */
    @Override
    public <T> Set<ObserverMethod<? super T>> resolveObserverMethods(T event, Annotation... qualifiers) {
        requireAfterBeanDiscovery("resolveObserverMethods(Object, Annotation...)");
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }
        if (event.getClass().getTypeParameters().length > 0) {
            throw new IllegalArgumentException(
                "Runtime event type contains unresolvable type variables: " + event.getClass().getName());
        }
        validateRequiredQualifiers(qualifiers);

        Type eventType = event.getClass();
        Set<Annotation> eventQualifiers = new HashSet<>(Arrays.asList(qualifiers));
        Set<Annotation> normalizedEventQualifiers = normalizeEventQualifiers(eventQualifiers);

        List<ObserverMethodMetadata> matchingObserverInfos = new ArrayList<>();

        for (ObserverMethodMetadata observerInfo : knowledgeBase.getObserverMethodInfos()) {
            // Check type compatibility
            if (!typesHelper.isEventTypeAssignable(observerInfo.getEventType(), eventType)) {
                continue;
            }

            // Check qualifier match
            Set<Annotation> observedQualifiers = normalizeObservedEventQualifiers(observerInfo.getQualifiers());
            if (observerQualifiersMatch(normalizedEventQualifiers, observedQualifiers)) {
                matchingObserverInfos.add(observerInfo);
            }
        }

        for (ObserverMethod<?> syntheticObserver : knowledgeBase.getSyntheticObserverMethods()) {
            if (syntheticObserver == null) {
                continue;
            }
            if (!typesHelper.isEventTypeAssignable(syntheticObserver.getObservedType(), eventType)) {
                continue;
            }

            Set<Annotation> observedQualifiers = syntheticObserver.getObservedQualifiers();
            Set<Annotation> normalizedObservedQualifiers = normalizeObservedEventQualifiers(
                    observedQualifiers == null ? Collections.emptySet() : observedQualifiers);
            if (observerQualifiersMatch(normalizedEventQualifiers, normalizedObservedQualifiers)) {
                matchingObserverInfos.add(createSyntheticObserverInfo(syntheticObserver));
            }
        }

        List<ObserverMethodMetadata> dedupedObserverInfos = new ArrayList<>();
        Set<String> seenObserverIdentities = new HashSet<>();
        for (ObserverMethodMetadata observerInfo : matchingObserverInfos) {
            String identityKey = observerMethodIdentityKey(observerInfo);
            if (seenObserverIdentities.add(identityKey)) {
                dedupedObserverInfos.add(observerInfo);
            }
        }

        dedupedObserverInfos.sort(
                Comparator.comparingInt(ObserverMethodMetadata::getPriority)
                        .thenComparing(info -> {
                            Method observerMethod = info.getObserverMethod();
                            if (observerMethod != null) {
                                return observerMethod.toGenericString();
                            }
                            Bean<?> declaringBean = info.getDeclaringBean();
                            if (declaringBean != null && declaringBean.getBeanClass() != null) {
                                return declaringBean.getBeanClass().getName();
                            }
                            return "";
                        })
        );

        Set<ObserverMethod<? super T>> observerMethods = new LinkedHashSet<>();
        for (ObserverMethodMetadata observerInfo : dedupedObserverInfos) {
            observerMethods.add(createObserverMethod(observerInfo));
        }
        return observerMethods;
    }

    /**
     * Resolves interceptors for the given interception type and bindings.
     *
     * <p>Interceptors are matched by:
     * <ul>
     *   <li>Interception type support (AROUND_INVOKE, POST_CONSTRUCT, etc.)</li>
     *   <li>Interceptor binding annotations</li>
     *   <li>Sorted by priority (lower = earlier execution)</li>
     * </ul>
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * // Interceptor binding
     * @InterceptorBinding
     * @Target({TYPE, METHOD ])
     * @Retention(RUNTIME)
     * public @interface Transactional { }
     *
     * // Interceptor
     * @Transactional
     * @Interceptor
     * @Priority(100)
     * public class TransactionInterceptor {
     *     @AroundInvoke
     *     public Object intercept(InvocationContext ctx) throws Exception {
     *         // Begin transaction...
     *         return ctx.proceed();
     *     }
     * }
     *
     * // Resolve interceptors
     * List<Interceptor<?>> interceptors = beanManager.resolveInterceptors(
     *     InterceptionType.AROUND_INVOKE,
     *     new TransactionalLiteral()
     * );
     * }</pre>
     *
     * @param type the interception type
     * @param interceptorBindings the required interceptor bindings
     * @return list of interceptors sorted by priority (can be empty, never null)
     * @throws IllegalArgumentException if the type is null
     */
    @Override
    public List<Interceptor<?>> resolveInterceptors(InterceptionType type, Annotation... interceptorBindings) {
        requireAfterBeanDiscovery("resolveInterceptors(InterceptionType, Annotation...)");
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        validateInterceptorBindings(interceptorBindings);

        // Convert varargs to Set for query
        Set<Annotation> requiredBindings = new HashSet<>(Arrays.asList(interceptorBindings));

        // Use KnowledgeBase query method - already filters by type and bindings, and sorts by priority
        List<InterceptorInfo> matchingInfos = requiredBindings.isEmpty()
                ? knowledgeBase.getInterceptorsByType(type)
                : knowledgeBase.getInterceptorsByBindingsAndType(type, requiredBindings);

        // Convert InterceptorInfo to Interceptor<?> beans
        List<Interceptor<?>> resolved = matchingInfos.stream()
                .map(this::createInterceptor)
                .filter(this::isInterceptorEnabled)
                .collect(Collectors.toList());

        // CDI Full extension: include custom Interceptor implementations registered as beans.
        Set<Class<?>> seenInterceptorClasses = new HashSet<>();
        for (Interceptor<?> interceptor : resolved) {
            seenInterceptorClasses.add(interceptor.getBeanClass());
        }
        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (!(bean instanceof Interceptor)) {
                continue;
            }
            Interceptor<?> interceptor = (Interceptor<?>) bean;
            if (seenInterceptorClasses.contains(interceptor.getBeanClass())) {
                continue;
            }
            if (!isInterceptorEnabled(interceptor)) {
                continue;
            }
            if (!interceptor.intercepts(type)) {
                continue;
            }
            if (!matchesInterceptorBindings(requiredBindings, interceptor.getInterceptorBindings())) {
                continue;
            }
            resolved.add(interceptor);
            seenInterceptorClasses.add(interceptor.getBeanClass());
        }

        resolved.sort((left, right) -> {
            int leftPriority = getInterceptorPriority(left);
            int rightPriority = getInterceptorPriority(right);

            boolean leftHasPriority = leftPriority != Integer.MAX_VALUE;
            boolean rightHasPriority = rightPriority != Integer.MAX_VALUE;

            if (leftHasPriority && rightHasPriority) {
                int byPriority = Integer.compare(leftPriority, rightPriority);
                if (byPriority != 0) {
                    return byPriority;
                }
            } else if (leftHasPriority != rightHasPriority) {
                return leftHasPriority ? -1 : 1;
            }

            int leftOrder = knowledgeBase.getApplicationInterceptorOrder(left.getBeanClass());
            int rightOrder = knowledgeBase.getApplicationInterceptorOrder(right.getBeanClass());
            if (leftOrder >= 0 && rightOrder >= 0) {
                int byOrder = Integer.compare(leftOrder, rightOrder);
                if (byOrder != 0) {
                    return byOrder;
                }
            } else if (leftOrder >= 0 || rightOrder >= 0) {
                return leftOrder >= 0 ? -1 : 1;
            }

            return left.getBeanClass().getName().compareTo(right.getBeanClass().getName());
        });

        return resolved;
    }

    private boolean matchesInterceptorBindings(Set<Annotation> required, Set<Annotation> candidate) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        Set<Class<? extends Annotation>> candidateTypes = new HashSet<>();
        for (Annotation annotation : candidate) {
            if (annotation != null) {
                candidateTypes.add(annotation.annotationType());
            }
        }
        for (Annotation requiredAnnotation : required) {
            if (requiredAnnotation == null || !candidateTypes.contains(requiredAnnotation.annotationType())) {
                return false;
            }
        }
        return true;
    }

    private void validateInterceptorBindings(Annotation[] interceptorBindings) {
        if (interceptorBindings == null || interceptorBindings.length == 0) {
            throw new IllegalArgumentException("At least one interceptor binding is required");
        }
        Set<Class<? extends Annotation>> seenNonRepeatable = new HashSet<>();
        for (Annotation binding : interceptorBindings) {
            if (binding == null) {
                throw new IllegalArgumentException("Interceptor binding cannot be null");
            }
            Class<? extends Annotation> bindingType = binding.annotationType();
            if (!isInterceptorBinding(bindingType)) {
                throw new IllegalArgumentException("Annotation is not an interceptor binding type: " + bindingType.getName());
            }
            if (!hasRepeatableAnnotation(bindingType)) {
                if (!seenNonRepeatable.add(bindingType)) {
                    throw new IllegalArgumentException("Duplicate non-repeating interceptor binding: " + bindingType.getName());
                }
            }
        }
    }

    /**
     * Checks if an annotation is a scope annotation.
     *
     * <p>Recognizes both {@code @Scope} (pseudo-scopes) and {@code @NormalScope} annotations.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * beanManager.isScope(ApplicationScoped.class);  // true
     * beanManager.isScope(Dependent.class);         // true (pseudo-scope)
     * beanManager.isScope(RequestScoped.class);     // true
     * beanManager.isScope(Inject.class);            // false
     * }</pre>
     *
     * @param annotationType the annotation type to check
     * @return true if it's a scope annotation
     */
    @Override
    public boolean isScope(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            return false;
        }
        // Check both annotation-based and programmatically registered scopes
        return hasScopeAnnotation(annotationType) ||
               hasNormalScopeAnnotation(annotationType) ||
               knowledgeBase.isRegisteredScope(annotationType);
    }

    /**
     * Checks if an annotation is a normal scope.
     *
     * <p>Normal scopes require client proxies:
     * <ul>
     *   <li>@ApplicationScoped</li>
     *   <li>@RequestScoped</li>
     *   <li>@SessionScoped</li>
     *   <li>@ConversationScoped</li>
     * </ul>
     *
     * <p>Pseudo-scopes like @Dependent do not require proxies.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * beanManager.isNormalScope(ApplicationScoped.class);  // true
     * beanManager.isNormalScope(Dependent.class);         // false (pseudo-scope)
     * }</pre>
     *
     * @param annotationType the annotation type to check
     * @return true if it's a normal scope requiring proxies
     */
    @Override
    public boolean isNormalScope(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            return false;
        }
        return contextManager.isNormalScope(annotationType);
    }

    /**
     * Checks if an annotation is a qualifier.
     *
     * <p>Qualifiers are meta-annotated with {@code @Qualifier}.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * @Qualifier
     * @Retention(RUNTIME)
     * @Target({FIELD, METHOD, PARAMETER, TYPE})
     * public @interface CreditCard { }
     *
     * beanManager.isQualifier(CreditCard.class);  // true
     * beanManager.isQualifier(Default.class);     // true
     * beanManager.isQualifier(Inject.class);      // false
     * }</pre>
     *
     * @param annotationType the annotation type to check
     * @return true if it's a qualifier
     */
    @Override
    public boolean isQualifier(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            return false;
        }
        // Check both annotation-based and programmatically registered qualifiers
        return hasQualifierAnnotation(annotationType) ||
               knowledgeBase.isRegisteredQualifier(annotationType);
    }

    /**
     * Checks if an annotation is a stereotype.
     *
     * <p>Stereotypes bundle multiple annotations together and are meta-annotated
     * with {@code @Stereotype}.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * @Stereotype
     * @ApplicationScoped
     * @Transactional
     * @Retention(RUNTIME)
     * @Target(TYPE)
     * public @interface Service { }
     *
     * beanManager.isStereotype(Service.class);  // true
     * }</pre>
     *
     * @param annotationType the annotation type to check
     * @return true if it's a stereotype
     */
    @Override
    public boolean isStereotype(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            return false;
        }
        // Check both @Stereotype annotation and programmatically registered stereotypes
        return hasStereotypeAnnotation(annotationType) ||
               knowledgeBase.isRegisteredStereotype(annotationType);
    }

    /**
     * Checks if an annotation is an interceptor binding.
     *
     * <p>Interceptor bindings are meta-annotated with {@code @InterceptorBinding}.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * @InterceptorBinding
     * @Retention(RUNTIME)
     * @Target({TYPE, METHOD ])
     * public @interface Transactional { }
     *
     * beanManager.isInterceptorBinding(Transactional.class); // true
     * }</pre>
     *
     * @param annotationType the annotation type to check
     * @return true if it's an interceptor binding
     */
    @Override
    public boolean isInterceptorBinding(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            return false;
        }
        // Check both annotation-based and programmatically registered interceptor bindings
        return hasActivateRequestContextAnnotation(annotationType) ||
               hasInterceptorBindingAnnotation(annotationType) ||
               knowledgeBase.isRegisteredInterceptorBinding(annotationType);
    }

    /**
     * Returns the context for the given scope type.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * // Get application scope context
     * Context appContext = beanManager.getContext(ApplicationScoped.class);
     * if (appContext.isActive()) {
     *     // Context is active
     * }
     *
     * // Get request scope context
     * try {
     *     Context reqContext = beanManager.getContext(RequestScoped.class);
     * } catch (ContextNotActiveException e) {
     *     // Request scope not active
     * }
     * }</pre>
     *
     * @param scopeType the scope annotation class
     * @return the context for the scope
     * @throws IllegalArgumentException if scopeType is null
     * @throws jakarta.enterprise.context.ContextNotActiveException if context not active
     */
    @Override
    public Context getContext(Class<? extends Annotation> scopeType) {
        if (scopeType == null) {
            throw new IllegalArgumentException("scopeType cannot be null");
        }

        Collection<com.threeamigos.common.util.implementations.injection.scopes.ScopeContext> activeContexts;
        try {
            activeContexts = contextManager.getActiveContexts(scopeType);
        } catch (IllegalArgumentException e) {
            Context bceContext = findActiveBceContext(scopeType);
            if (bceContext != null) {
                return bceContext;
            }
            throw new jakarta.enterprise.context.ContextNotActiveException(
                    "Context not active for scope: " + scopeType.getName());
        }

        if (activeContexts.size() > 1) {
            throw new IllegalStateException("More than one active context for scope: " + scopeType.getName());
        }
        com.threeamigos.common.util.implementations.injection.scopes.ScopeContext scopeContext;
        if (activeContexts.isEmpty()) {
            if (requireActiveContextForGetContext) {
                Context bceContext = findActiveBceContext(scopeType);
                if (bceContext != null) {
                    return bceContext;
                }
                throw new jakarta.enterprise.context.ContextNotActiveException(
                        "Context not active for scope: " + scopeType.getName());
            }

            Collection<com.threeamigos.common.util.implementations.injection.scopes.ScopeContext> registered =
                    contextManager.getRegisteredContexts(scopeType);
            if (registered.isEmpty()) {
                Context bceContext = findActiveBceContext(scopeType);
                if (bceContext != null) {
                    return bceContext;
                }
                throw new jakarta.enterprise.context.ContextNotActiveException(
                        "Context not active for scope: " + scopeType.getName());
            }
            scopeContext = registered.iterator().next();
        } else {
            scopeContext = activeContexts.iterator().next();
        }
        if (scopeContext instanceof CustomContextAdapter) {
            return ((CustomContextAdapter) scopeContext).getWrappedContext();
        }
        return new ScopeContextAdapter(scopeContext, scopeType);
    }

    /**
     * Returns all contexts for the given scope type.
     *
     * <p>CDI allows multiple contexts per scope (e.g., for propagation).
     * This implementation returns a singleton collection.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * Collection<Context> contexts = beanManager.getContexts(RequestScoped.class);
     * for (Context ctx : contexts) {
     *     if (ctx.isActive()) {
     *         // Use context...
     *     }
     * }
     * }</pre>
     *
     * @param scopeType the scope annotation class
     * @return collection of contexts (can be empty, never null)
     * @throws IllegalArgumentException if scopeType is null
     */
    @Override
    public Collection<Context> getContexts(Class<? extends Annotation> scopeType) {
        if (scopeType == null) {
            throw new IllegalArgumentException("scopeType cannot be null");
        }

        Set<Context> contexts = new LinkedHashSet<>();

        Class<? extends Annotation> effectiveScope = scopeType;
        if (Singleton.class.equals(scopeType)) {
            effectiveScope = ApplicationScoped.class;
        }

        try {
            Collection<com.threeamigos.common.util.implementations.injection.scopes.ScopeContext> registeredContexts =
                    contextManager.getRegisteredContexts(effectiveScope);
            for (com.threeamigos.common.util.implementations.injection.scopes.ScopeContext registered : registeredContexts) {
                if (registered instanceof CustomContextAdapter) {
                    contexts.add(((CustomContextAdapter) registered).getWrappedContext());
                } else {
                    contexts.add(new ScopeContextAdapter(registered, effectiveScope));
                }
            }
        } catch (Exception ignored) {
            // no runtime context for this scope
        }

        for (Context bceContext : getOrCreateBceContexts(scopeType)) {
            if (bceContext != null) {
                contexts.add(bceContext);
            }
        }

        return contexts;
    }

    private Context findActiveBceContext(Class<? extends Annotation> scopeType) {
        List<Context> contexts = getOrCreateBceContexts(scopeType);
        if (contexts.isEmpty()) {
            return null;
        }

        Context activeContext = null;
        for (Context context : contexts) {
            if (context == null || !context.isActive()) {
                continue;
            }
            if (activeContext != null) {
                throw new IllegalStateException("More than one active context for scope: " + scopeType.getName());
            }
            activeContext = context;
        }
        return activeContext;
    }

    private List<Context> getOrCreateBceContexts(Class<? extends Annotation> scopeType) {
        List<Context> existing = bceContextInstances.get(scopeType);
        if (existing != null) {
            return existing;
        }

        List<Class<? extends AlterableContext>> implementations = knowledgeBase.getContextImplementations(scopeType);
        if (implementations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Context> created = new ArrayList<>();
        for (Class<? extends AlterableContext> contextImplementation : implementations) {
            try {
                created.add(contextImplementation.getDeclaredConstructor().newInstance());
            } catch (Exception ignored) {
                // ignore unusable custom context implementations and continue with the rest
            }
        }
        if (created.isEmpty()) {
            return Collections.emptyList();
        }

        List<Context> previous = bceContextInstances.putIfAbsent(
                scopeType, Collections.unmodifiableList(created));
        return previous != null ? previous : bceContextInstances.get(scopeType);
    }

    /**
     * Returns an Event object for firing events.
     *
     * <p>The returned Event has type Object with @Default qualifier.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * // Get event object
     * Event<Object> event = beanManager.getEvent();
     *
     * // Fire specific event type
     * event.select(UserCreatedEvent.class)
     *      .fire(new UserCreatedEvent(user));
     *
     * // Fire with qualifier
     * event.select(OrderEvent.class, new CompletedLiteral())
     *      .fire(new OrderEvent(order));
     * }</pre>
     *
     * @return an Event<Object> instance for firing events
     */
    @Override
    public Event<Object> getEvent() {
        if (beanResolver != null) {
            Type eventObjectType = new ParameterizedType() {
                @Override
                public @Nonnull Type[] getActualTypeArguments() {
                    return new Type[]{Object.class};
                }

                @Override
                public @Nonnull Type getRawType() {
                    return Event.class;
                }

                @Override
                public Type getOwnerType() {
                    return null;
                }
            };
            Object resolved = beanResolver.resolve(
                    eventObjectType,
                    new Annotation[]{jakarta.enterprise.inject.Default.Literal.INSTANCE}
            );
            if (resolved instanceof Event) {
                @SuppressWarnings("unchecked")
                Event<Object> event = (Event<Object>) resolved;
                return event;
            }
        }

        // Fallback to ObserverSupport when resolver integration is unavailable.
        if (observerSupport != null) {
            return observerSupport.getRootEvent();
        }
        throw new IllegalStateException("ObserverSupport is not initialized; cannot create Event");
    }

    /**
     * Creates a programmatic Instance for dynamic bean lookup.
     *
     * <p>Instance allows runtime lookup of beans without injection.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * // Get instance handle
     * Instance<Object> instance = beanManager.createInstance();
     *
     * // Lookup specific bean type
     * Instance<UserService> userServiceInstance = instance.select(UserService.class);
     * if (userServiceInstance.isResolvable()) {
     *     UserService service = userServiceInstance.get();
     *     // Use service...
     * }
     *
     * // Lookup with qualifier
     * Instance<PaymentProcessor> creditCardProcessor = instance.select(
     *     PaymentProcessor.class,
     *     new CreditCardLiteral()
     * );
     * }</pre>
     *
     * @return an Instance<Object> for programmatic bean lookup
     */
    @Override
    public Instance<Object> createInstance() {
        requireAfterDeploymentValidation("createInstance()");
        // Create an Instance<Object> with @Default specified qualifier
        Set<Annotation> qualifiers = new HashSet<>();
        qualifiers.add(jakarta.enterprise.inject.Default.Literal.INSTANCE);
        qualifiers.add(new AnyLiteral());

        // Create resolution strategy
        InstanceImpl.ResolutionStrategy<Object> strategy = new InstanceImpl.ResolutionStrategy<Object>() {
            @Override
            public Object resolveInstance(Class<Object> type, Collection<Annotation> quals) {
                Annotation[] qualArray = quals.toArray(new Annotation[0]);
                return beanResolver.resolve(type, qualArray);
            }

            @Override
            public Object resolveInstance(Type type, Collection<Annotation> quals) {
                Annotation[] qualArray = quals.toArray(new Annotation[0]);
                return beanResolver.resolve(type, qualArray);
            }

            @Override
            public Collection<Class<?>> resolveImplementations(Class<Object> type, Collection<Annotation> quals) {
                Annotation[] qualArray = quals.toArray(new Annotation[0]);
                Set<Bean<?>> beans = getBeans(type, qualArray);
                return beans.stream()
                    .map(bean -> (Class<?>) bean.getBeanClass())
                    .collect(Collectors.toList());
            }

            @Override
            public Collection<Class<?>> resolveImplementations(Type type, Collection<Annotation> quals) {
                Annotation[] qualArray = quals.toArray(new Annotation[0]);
                Set<Bean<?>> beans = getBeans(type, qualArray);
                return beans.stream()
                        .map(bean -> (Class<?>) bean.getBeanClass())
                        .collect(Collectors.toList());
            }

            @Override
            public void invokePreDestroy(Object instance) throws java.lang.reflect.InvocationTargetException, IllegalAccessException {
                LifecycleMethodHelper.invokeLifecycleMethod(instance, PRE_DESTROY);
            }
        };

        java.util.function.Function<Class<?>, Bean<?>> beanLookup = beanClass -> {
            for (Bean<?> bean : knowledgeBase.getValidBeans()) {
                if (bean.getBeanClass().equals(beanClass)) {
                    return (Bean<?>) bean;
                }
            }
            return null;
        };

        return new InstanceImpl<>(Object.class, qualifiers, strategy, beanLookup, this);
    }

    /**
     * Checks if a bean matches an injection point.
     *
     * <p>Performs type and qualifier matching without actually resolving the bean.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * Bean<?> bean = ...;
     * Set<Type> beanTypes = bean.getTypes();
     * Set<Annotation> beanQualifiers = bean.getQualifiers();
     *
     * boolean matches = beanManager.isMatchingBean(
     *     beanTypes,
     *     beanQualifiers,
     *     UserService.class,
     *     Set.of(new DefaultLiteral())
     * );
     * }</pre>
     *
     * @param beanTypes the bean's types
     * @param beanQualifiers the bean's qualifiers
     * @param requiredType the required injection type
     * @param requiredQualifiers the required qualifiers
     * @return true if the bean matches
     */
    @Override
    public boolean isMatchingBean(Set<Type> beanTypes, Set<Annotation> beanQualifiers,
                                   Type requiredType, Set<Annotation> requiredQualifiers) {
        if (beanTypes == null) {
            throw new IllegalArgumentException("beanTypes cannot be null");
        }
        if (beanQualifiers == null) {
            throw new IllegalArgumentException("beanQualifiers cannot be null");
        }
        if (requiredType == null) {
            throw new IllegalArgumentException("requiredType cannot be null");
        }
        if (requiredQualifiers == null) {
            throw new IllegalArgumentException("requiredQualifiers cannot be null");
        }

        validateQualifierSet(beanQualifiers, "beanQualifiers");
        validateQualifierSet(requiredQualifiers, "requiredQualifiers");

        // Check type compatibility
        boolean typeMatches = false;
        boolean requiredTypeIsObject = requiredType instanceof Class<?> && Object.class.equals(requiredType);
        for (Type beanType : beanTypes) {
            if (beanType == null || !isLegalBeanType(beanType)) {
                continue;
            }
            if (requiredTypeIsObject) {
                typeMatches = true;
                break;
            }
            if (notSameRawType(requiredType, beanType)) {
                continue;
            }
            if (typesHelper.isAssignable(requiredType, beanType)) {
                typeMatches = true;
                break;
            }
        }

        if (!typeMatches) {
            return false;
        }

        Set<Annotation> normalizedBeanQualifiers = normalizeBeanQualifiers(beanQualifiers);
        Set<Annotation> normalizedRequiredQualifiers = requiredQualifiers.isEmpty() ?
                Collections.singleton(jakarta.enterprise.inject.Default.Literal.INSTANCE) :
                requiredQualifiers;

        return qualifiersMatch(normalizedRequiredQualifiers, normalizedBeanQualifiers);
    }

    /**
     * Checks if an event matches an observer method.
     *
     * <p>Performs type and qualifier matching for event-observer compatibility.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * boolean matches = beanManager.isMatchingEvent(
     *     UserCreatedEvent.class,
     *     Set.of(new CreatedLiteral()),
     *     Object.class,
     *     Set.of()
     * );
     * }</pre>
     *
     * @param eventType the event type
     * @param eventQualifiers the event qualifiers
     * @param observedType the observer's observed type
     * @param observedQualifiers the observer's qualifiers
     * @return true if the event matches the observer
     */
    @Override
    public boolean isMatchingEvent(Type eventType, Set<Annotation> eventQualifiers,
                                    Type observedType, Set<Annotation> observedQualifiers) {
        if (eventType == null) {
            throw new IllegalArgumentException("eventType cannot be null");
        }
        if (eventQualifiers == null) {
            throw new IllegalArgumentException("eventQualifiers cannot be null");
        }
        if (observedType == null) {
            throw new IllegalArgumentException("observedType cannot be null");
        }
        if (observedQualifiers == null) {
            throw new IllegalArgumentException("observedQualifiers cannot be null");
        }
        if (containsTypeVariable(eventType)) {
            throw new IllegalArgumentException("eventType cannot contain type variables: " + eventType.getTypeName());
        }

        validateQualifierSet(eventQualifiers, "eventQualifiers");
        validateQualifierSet(observedQualifiers, "observedQualifiers");

        // Check type compatibility
        if (!typesHelper.isEventTypeAssignable(observedType, eventType)) {
            return false;
        }

        Set<Annotation> normalizedEventQualifiers = normalizeEventQualifiers(eventQualifiers);
        Set<Annotation> normalizedObservedQualifiers = normalizeObservedEventQualifiers(observedQualifiers);

        return observerQualifiersMatch(normalizedEventQualifiers, normalizedObservedQualifiers);
    }

    // ==================== BeanManager Methods ====================

    /**
     * Gets an injectable reference for an injection point.
     *
     * <p>Performs full resolution including type and qualifier matching.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * InjectionPoint injectionPoint = ...; // from @Inject field
     * CreationalContext<?> ctx = beanManager.createCreationalContext(null);
     *
     * Object reference = beanManager.getInjectableReference(injectionPoint, ctx);
     * }</pre>
     *
     * @param injectionPoint the injection point
     * @param ctx the creational context
     * @return the injectable reference (can be a proxy)
     * @throws IllegalArgumentException if injectionPoint is null
     * @throws jakarta.enterprise.inject.UnsatisfiedResolutionException if no bean found
     */
    @Override
    public Object getInjectableReference(InjectionPoint injectionPoint, CreationalContext<?> ctx) {
        requireAfterDeploymentValidation("getInjectableReference(InjectionPoint, CreationalContext)");
        if (injectionPoint == null) {
            throw new IllegalArgumentException("injectionPoint cannot be null");
        }

        Type requiredType = injectionPoint.getType();
        Set<Annotation> qualifiers = injectionPoint.getQualifiers();
        Annotation[] qualifierArray = qualifiers.toArray(new Annotation[0]);

        if (beanResolver != null) {
            beanResolver.setCurrentInjectionPoint(injectionPoint);
        }
        try {
            // Special case for InjectionPoint built-in bean: resolve contextually, so nested
            // @Inject InjectionPoint inside dependent beans sees the owning outer injection site.
            if (requiredType instanceof Class &&
                    InjectionPoint.class.equals(requiredType)) {
                if (isDefaultQualified(qualifiers)) {
                    Bean<?> owningBean = injectionPoint.getBean();
                    if (owningBean == null) {
                        if (beanResolver != null) {
                            Object contextualInjectionPoint = beanResolver.resolve(requiredType, qualifierArray);
                            if (contextualInjectionPoint instanceof InjectionPoint &&
                                    ((InjectionPoint) contextualInjectionPoint).getBean() != null) {
                                return contextualInjectionPoint;
                            }
                        }
                        throw new DefinitionException(
                                "InjectionPoint with qualifier @Default is not allowed for non-bean injection targets");
                    }

                    Class<? extends Annotation> owningScope = owningBean.getScope();
                    if (owningScope != null && !hasDependentAnnotation(owningScope)) {
                        throw new DefinitionException(
                                "Bean " + owningBean.getBeanClass().getName() +
                                        " declares scope @" + owningScope.getSimpleName() +
                                        " and may not inject InjectionPoint with qualifier @Default");
                    }
                }
                if (beanResolver != null) {
                    return beanResolver.resolve(requiredType, qualifierArray);
                }
                return injectionPoint;
            }

            // Special case: inject Instance<T> handles for lazy/programmatic lookup.
            if (requiredType instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) requiredType;
                Type rawType = parameterizedType.getRawType();
                if (rawType instanceof Class && (
                        Event.class.isAssignableFrom((Class<?>) rawType) ||
                        Instance.class.isAssignableFrom((Class<?>) rawType) ||
                        jakarta.inject.Provider.class.isAssignableFrom((Class<?>) rawType) ||
                        Decorator.class.equals(rawType) ||
                        Bean.class.equals(rawType) ||
                        Interceptor.class.equals(rawType))) {
                    return beanResolver != null
                            ? beanResolver.resolve(requiredType, qualifierArray)
                            : createInstance();
                }
            }

            Set<Bean<?>> beans = getBeans(requiredType, qualifierArray);
            Bean<?> bean = resolve(beans);

            if (bean == null) {
                throw new jakarta.enterprise.inject.UnsatisfiedResolutionException(
                        "No bean found for injection point: " + injectionPoint);
            }

            if (DEPENDENT.matches(bean.getScope()) && ctx instanceof CreationalContextImpl) {
                if (isUnconstructibleManagedBean(bean)) {
                    throw new jakarta.enterprise.inject.UnsatisfiedResolutionException(
                            "No constructible bean found for injection point: " + injectionPoint);
                }
                @SuppressWarnings("unchecked")
                Bean<Object> dependentBean = (Bean<Object>) bean;
                CreationalContext<Object> childContext = createCreationalContext(dependentBean);
                Object instance;
                try {
                    instance = dependentBean.create(childContext);
                } catch (jakarta.enterprise.inject.CreationException creationException) {
                    if (isUnconstructibleManagedBean(bean) || hasCause(creationException)) {
                        throw new jakarta.enterprise.inject.UnsatisfiedResolutionException(
                                "No constructible bean found for injection point: " + injectionPoint);
                    }
                    throw creationException;
                }
                if (isTransientReferenceInjectionPoint(injectionPoint)) {
                    registerTransientReference(beanManagerId, dependentBean, instance, childContext);
                } else {
                    @SuppressWarnings("unchecked")
                    CreationalContextImpl<Object> parentContext = (CreationalContextImpl<Object>) ctx;
                    parentContext.addDependentInstance(dependentBean, instance, childContext);
                }
                return instance;
            }

            return getReference(bean, requiredType, ctx);
        } finally {
            if (beanResolver != null) {
                beanResolver.clearCurrentInjectionPoint();
            }
        }
    }

    private boolean isUnconstructibleManagedBean(Bean<?> bean) {
        if (!(bean instanceof BeanImpl<?>)) {
            return false;
        }
        BeanImpl<?> managedBean = (BeanImpl<?>) bean;
        if (managedBean.getInjectConstructor() != null) {
            return false;
        }
        Class<?> beanClass = managedBean.getBeanClass();
        if (beanClass == null) {
            return true;
        }
        for (Constructor<?> constructor : beanClass.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0) {
                return false;
            }
        }
        return true;
    }

    private boolean hasCause(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NoSuchMethodException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isDefaultQualified(Set<Annotation> qualifiers) {
        if (qualifiers == null || qualifiers.isEmpty()) {
            return true;
        }
        for (Annotation qualifier : qualifiers) {
            if (hasDefaultAnnotation(qualifier.annotationType())) {
                return true;
            }
        }
        return false;
    }

    private boolean isTransientReferenceInjectionPoint(InjectionPoint injectionPoint) {
        if (injectionPoint == null || injectionPoint.getAnnotated() == null ||
                injectionPoint.getAnnotated().getAnnotations() == null) {
            return false;
        }
        for (Annotation annotation : injectionPoint.getAnnotated().getAnnotations()) {
            if (annotation != null && hasTransientReferenceAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets a passivation-capable bean by its ID.
     *
     * <p>Passivation-capable beans can be serialized/deserialized.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * // During serialization
     * if (bean instanceof PassivationCapable) {
     *     String id = ((PassivationCapable) bean).getId();
     *     // Store ID...
     * }
     *
     * // During deserialization
     * Bean<?> bean = beanManager.getPassivationCapableBean(id);
     * }</pre>
     *
     * @param id the passivation ID
     * @return the bean with the given ID, or null if not found
     * @throws IllegalArgumentException if id is null
     */
    @Override
    public Bean<?> getPassivationCapableBean(String id) {
        requireAfterBeanDiscovery("getPassivationCapableBean(String)");
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }

        // Search through all beans for one with matching ID
        for (Bean<?> bean : knowledgeBase.getValidBeans()) {
            if (bean instanceof PassivationCapable) {
                PassivationCapable pc =
                    (PassivationCapable) bean;
                if (id.equals(pc.getId())) {
                    return bean;
                }
            }
        }

        return null;
    }

    /**
     * Validates an injection point at deployment time.
     *
     * <p>Checks that the injection point can be satisfied and is not ambiguous.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * InjectionPoint injectionPoint = ...;
     *
     * try {
     *     beanManager.validate(injectionPoint);
     *     // Valid injection point
     * } catch (UnsatisfiedResolutionException e) {
     *     // No matching bean
     * } catch (AmbiguousResolutionException e) {
     *     // Multiple matching beans
     * }
     * }</pre>
     *
     * @param injectionPoint the injection point to validate
     * @throws IllegalArgumentException if injectionPoint is null
     * @throws jakarta.enterprise.inject.UnsatisfiedResolutionException if no bean found
     * @throws jakarta.enterprise.inject.AmbiguousResolutionException if ambiguous
     */
    @Override
    public void validate(InjectionPoint injectionPoint) {
        requireAfterBeanDiscovery("validate(InjectionPoint)");
        if (injectionPoint == null) {
            throw new IllegalArgumentException("injectionPoint cannot be null");
        }

        Type requiredType = injectionPoint.getType();
        Set<Annotation> qualifiers = injectionPoint.getQualifiers();

        // Built-in bean types such as Event<T> are not represented by discoverable beans and
        // are resolved contextually at injection time.
        if (isBuiltInInjectionType(requiredType)) {
            if (beanResolver != null) {
                beanResolver.resolve(requiredType, qualifiers.toArray(new Annotation[0]));
            }
            return;
        }

        Set<Bean<?>> beans = getBeans(requiredType, qualifiers.toArray(new Annotation[0]));

        if (beans.isEmpty()) {
            throw new jakarta.enterprise.inject.UnsatisfiedResolutionException(
                "No bean found for injection point: " + injectionPoint);
        }

        Bean<?> resolved = resolve(beans);
        if (resolved == null && beans.size() > 1) {
            throw new jakarta.enterprise.inject.AmbiguousResolutionException(
                "Ambiguous dependency at injection point: " + injectionPoint +
                ". Matching beans: " + beans.stream()
                    .map(b -> b.getBeanClass().getName())
                    .collect(Collectors.joining(", ")));
        }
    }

    private boolean isBuiltInInjectionType(Type requiredType) {
        if (!(requiredType instanceof ParameterizedType)) {
            return false;
        }
        Type rawType = ((ParameterizedType) requiredType).getRawType();
        if (!(rawType instanceof Class<?>)) {
            return false;
        }
        Class<?> rawClass = (Class<?>) rawType;
        return Event.class.isAssignableFrom(rawClass)
                || Instance.class.isAssignableFrom(rawClass)
                || jakarta.inject.Provider.class.isAssignableFrom(rawClass)
                || Decorator.class.equals(rawClass)
                || Bean.class.equals(rawClass)
                || Interceptor.class.equals(rawClass);
    }

    /**
     * Resolves decorators for a set of types.
     *
     * <p>Decorators intercept method calls on beans by implementing the same interface.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * // Decorator
     * @Decorator
     * @Priority(100)
     * public class CachingPaymentProcessor implements PaymentProcessor {
     *     @Inject @Delegate PaymentProcessor delegate;
     *
     *     public void process(Payment payment) {
     *         // Check cache...
     *         delegate.process(payment);
     *     }
     * }
     *
     * // Resolve decorators
     * Set<Type> types = Set.of(PaymentProcessor.class);
     * List<Decorator<?>> decorators = beanManager.resolveDecorators(types);
     * }</pre>
     *
     * @param types the decorated types
     * @param qualifiers the qualifiers (usually empty for decorators)
     * @return list of decorators sorted by priority (can be empty, never null)
     * @throws IllegalArgumentException if the types' collection is null or empty
     */
    @Override
    public List<Decorator<?>> resolveDecorators(Set<Type> types, Annotation... qualifiers) {
        requireAfterBeanDiscovery("resolveDecorators(Set, Annotation...)");
        if (types == null || types.isEmpty()) {
            throw new IllegalArgumentException("types cannot be null or empty");
        }
        validateRequiredQualifiers(qualifiers);

        Set<Annotation> requiredQualifiers = extractQualifiers(qualifiers);
        List<Decorator<?>> matchingDecorators = new ArrayList<>();
        Set<Class<?>> seenDecoratorClasses = new HashSet<>();

        for (DecoratorInfo decoratorInfo : knowledgeBase.getDecoratorInfos()) {
            Decorator<?> decorator = createDecorator(decoratorInfo);
            if (!isDecoratorEnabled(decorator)) {
                continue;
            }
            if (doesNotMatchDecoratorTypes(types, decorator.getDecoratedTypes(), decorator.getDelegateType())) {
                continue;
            }
            if (!qualifiersMatch(decorator.getDelegateQualifiers(), requiredQualifiers)) {
                continue;
            }
            matchingDecorators.add(decorator);
            seenDecoratorClasses.add(decorator.getBeanClass());
        }

        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (!(bean instanceof Decorator<?>)) {
                continue;
            }
            Decorator<?> decorator = (Decorator<?>) bean;
            if (!seenDecoratorClasses.add(decorator.getBeanClass())) {
                continue;
            }
            if (doesNotMatchDecoratorTypes(types, decorator.getDecoratedTypes(), decorator.getDelegateType())) {
                continue;
            }
            if (!qualifiersMatch(decorator.getDelegateQualifiers(), requiredQualifiers)) {
                continue;
            }
            matchingDecorators.add(decorator);
        }

        // CDI 4.1: @Priority-enabled decorators are called before beans.xml-enabled decorators.
        matchingDecorators.sort(
                Comparator.comparingInt((Decorator<?> d) -> getDecoratorPriority(d) != Integer.MAX_VALUE ? 0 : 1)
                        .thenComparingInt(this::getDecoratorPriority)
                        .thenComparingInt(d -> {
                            int order = knowledgeBase.getDecoratorBeansXmlOrder(d.getBeanClass());
                            return order >= 0 ? order : Integer.MAX_VALUE;
                        })
                        .thenComparing(d -> d.getBeanClass().getName())
        );

        return matchingDecorators;
    }

    private boolean doesNotMatchDecoratorTypes(Set<Type> requestedTypes, Set<Type> decoratedTypes, Type delegateType) {
        return !matchesDecoratorTypes(requestedTypes, decoratedTypes, delegateType);
    }

    private boolean matchesDecoratorTypes(Set<Type> requestedTypes, Set<Type> decoratedTypes, Type delegateType) {
        if (decoratedTypes != null) {
            for (Type decoratedType : decoratedTypes) {
                for (Type requestedType : requestedTypes) {
                    if (isObjectType(requestedType)) {
                        continue;
                    }
                    if (typesHelper.isLookupTypeAssignable(requestedType, decoratedType)) {
                        return true;
                    }
                }
            }
        }

        if (delegateType != null) {
            for (Type requestedType : requestedTypes) {
                if (isObjectType(requestedType)) {
                    continue;
                }
                if (typesHelper.isLookupTypeAssignable(requestedType, delegateType)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isObjectType(Type type) {
        return type instanceof Class<?> && Object.class.equals(type);
    }

    private boolean isDecoratorEnabled(Decorator<?> decorator) {
        if (decorator == null) {
            return false;
        }
        int beansXmlOrder = knowledgeBase.getDecoratorBeansXmlOrder(decorator.getBeanClass());
        return beansXmlOrder >= 0 || getDecoratorPriority(decorator) != Integer.MAX_VALUE;
    }

    private boolean isInterceptorEnabled(Interceptor<?> interceptor) {
        if (interceptor == null) {
            return false;
        }
        // Programmatic custom Interceptor beans added by extensions are considered enabled.
        // Only annotation-based interceptor classes require explicit enablement (@Priority or app order).
        if (!INTERCEPTOR.isPresent(interceptor.getBeanClass())) {
            return true;
        }
        int applicationOrder = knowledgeBase.getApplicationInterceptorOrder(interceptor.getBeanClass());
        int beansXmlOrder = knowledgeBase.getInterceptorBeansXmlOrder(interceptor.getBeanClass());
        return applicationOrder >= 0 || beansXmlOrder >= 0 || getInterceptorPriority(interceptor) != Integer.MAX_VALUE;
    }

    /**
     * Checks if a scope is passivating.
     *
     * <p>Passivating scopes (@SessionScoped, @ConversationScoped) require
     * beans to be Serializable.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * beanManager.isPassivatingScope(SessionScoped.class);       // true
     * beanManager.isPassivatingScope(ConversationScoped.class);  // true
     * beanManager.isPassivatingScope(ApplicationScoped.class);   // false
     * beanManager.isPassivatingScope(RequestScoped.class);       // false
     * }</pre>
     *
     * @param annotationType the scope annotation
     * @return true if the scope is passivating
     */
    @Override
    public boolean isPassivatingScope(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            return false;
        }

        // Check if the annotation has @NormalScope(passivating=true)
        if (hasNormalScopeAnnotation(annotationType)) {
            Boolean passivating = getNormalScopePassivatingValue(annotationType);
            return passivating != null && passivating;
        }

        return false;
    }

    /**
     * Returns the full definition of an interceptor binding.
     *
     * <p>Includes all meta-annotations (for transitive bindings).
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * @InterceptorBinding
     * @Inherited
     * @Retention(RUNTIME)
     * @Target({TYPE, METHOD ])
     * public @interface Transactional {
     *     TransactionType value() default TransactionType.REQUIRED;
     * }
     *
     * Set<Annotation> definition = beanManager.getInterceptorBindingDefinition(Transactional.class);
     * // Returns: @InterceptorBinding, @Inherited, @Retention, @Target
     * }</pre>
     *
     * @param bindingType the interceptor binding annotation
     * @return set of annotations comprising the binding definition
     * @throws IllegalArgumentException if not an interceptor binding
     */
    @Override
    public Set<Annotation> getInterceptorBindingDefinition(Class<? extends Annotation> bindingType) {
        if (bindingType == null || !isInterceptorBinding(bindingType)) {
            throw new IllegalArgumentException("Not an interceptor binding: " + bindingType);
        }

        // Return all annotations on the interceptor binding, including transitives

        return new HashSet<>(Arrays.asList(bindingType.getAnnotations()));
    }

    /**
     * Returns the full definition of a stereotype.
     *
     * <p>Includes all annotations bundled by the stereotype.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * @Stereotype
     * @ApplicationScoped
     * @Transactional
     * @Named
     * @Retention(RUNTIME)
     * @Target(TYPE)
     * public @interface Service { }
     *
     * Set<Annotation> definition = beanManager.getStereotypeDefinition(Service.class);
     * // Returns: @Stereotype, @ApplicationScoped, @Transactional, @Named, @Retention, @Target
     * }</pre>
     *
     * @param stereotype the stereotype annotation
     * @return set of annotations comprising the stereotype definition
     * @throws IllegalArgumentException if not a stereotype
     */
    @Override
    public Set<Annotation> getStereotypeDefinition(Class<? extends Annotation> stereotype) {
        if (stereotype == null || !isStereotype(stereotype)) {
            throw new IllegalArgumentException("Not a stereotype: " + stereotype);
        }

        // Check if this is a programmatically registered stereotype
        Set<Annotation> registeredDef = knowledgeBase.getStereotypeDefinition(stereotype);
        if (registeredDef != null) {
            // Return the programmatically registered definition
            return new HashSet<>(registeredDef);
        }

        // Otherwise, return all annotations on the stereotype (for @Stereotype-annotated classes)
        return new HashSet<>(Arrays.asList(stereotype.getAnnotations()));
    }

    /**
     * Checks if two qualifiers are equivalent.
     *
     * <p>Qualifiers are equivalent if they have the same type and member values.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * Annotation q1 = new NamedLiteral("myBean");
     * Annotation q2 = new NamedLiteral("myBean");
     * Annotation q3 = new NamedLiteral("otherBean");
     *
     * beanManager.areQualifiersEquivalent(q1, q2);  // true
     * beanManager.areQualifiersEquivalent(q1, q3);  // false
     * }</pre>
     *
     * @param qualifier1 first qualifier
     * @param qualifier2 second qualifier
     * @return true if equivalent
     */
    @Override
    public boolean areQualifiersEquivalent(Annotation qualifier1, Annotation qualifier2) {
        if (qualifier1 == null || qualifier2 == null) {
            return false;
        }

        if (!qualifier1.annotationType().equals(qualifier2.annotationType())) {
            return false;
        }

        return AnnotationComparator.equals(qualifier1, qualifier2);
    }

    /**
     * Checks if two interceptor bindings are equivalent.
     *
     * <p>Similar to qualifier equivalence but for interceptor bindings.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * Annotation b1 = new TransactionalLiteral(TransactionType.REQUIRED);
     * Annotation b2 = new TransactionalLiteral(TransactionType.REQUIRED);
     *
     * beanManager.areInterceptorBindingsEquivalent(b1, b2);  // true
     * }</pre>
     *
     * @param binding1 first binding
     * @param binding2 second binding
     * @return true if equivalent
     */
    @Override
    public boolean areInterceptorBindingsEquivalent(Annotation binding1, Annotation binding2) {
        if (binding1 == null || binding2 == null) {
            return false;
        }

        if (!binding1.annotationType().equals(binding2.annotationType())) {
            return false;
        }

        return AnnotationComparator.equals(binding1, binding2);
    }

    /**
     * Returns the hash code for a qualifier.
     *
     * <p>Used for storing qualifiers in hash-based collections.
     *
     * @param qualifier the qualifier
     * @return hash code
     */
    @Override
    public int getQualifierHashCode(Annotation qualifier) {
        if (qualifier == null) {
            return 0;
        }
        return AnnotationComparator.hashCode(qualifier);
    }

    /**
     * Returns the hash code for an interceptor binding.
     *
     * <p>Used for storing bindings in hash-based collections.
     *
     * @param binding the binding
     * @return hash code
     */
    @Override
    public int getInterceptorBindingHashCode(Annotation binding) {
        if (binding == null) {
            return 0;
        }
        return AnnotationComparator.hashCode(binding);
    }

    /**
     * Returns an ELResolver for CDI beans.
     *
     * @return EL resolver
     * @deprecated EL integration deprecated in CDI 4.1
     */
    @Override
    public ELResolver getELResolver() {
        ELResolver resolver = beanManagerELResolver;
        if (resolver != null) {
            return resolver;
        }
        synchronized (this) {
            if (beanManagerELResolver == null) {
                beanManagerELResolver = ELSupport.createELResolver(this);
            }
            return beanManagerELResolver;
        }
    }

    /**
     * Wraps an expression factory for CDI integration.
     *
     * <p><b>Note:</b> EL integration not yet implemented.
     *
     * @param expressionFactory the expression factory
     * @return wrapped expression factory
     * @deprecated EL integration deprecated in CDI 4.1
     */
    @Override
    public ExpressionFactory wrapExpressionFactory(ExpressionFactory expressionFactory) {
        return ELSupport.wrapExpressionFactory(this, expressionFactory);
    }

    /**
     * Creates an AnnotatedType for a class.
     *
     * <p><b>Note:</b> Portable extension support not yet implemented.
     *
     * @param type the class
     * @param <T> the class type
     * @return annotated type
     * @throws IllegalArgumentException if the type is null
     * @throws UnsupportedOperationException always
     */
    @Override
    public <T> AnnotatedType<T> createAnnotatedType(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }

        return new SimpleAnnotatedType<>(type);
    }

    /**
     * Gets an injection target factory for an annotated type.
     *
     * <p><b>Note:</b> Portable extension support not yet implemented.
     *
     * @param annotatedType the annotated type
     * @param <T> the type
     * @return injection target factory
     * @throws IllegalArgumentException if annotatedType is null
     * @throws UnsupportedOperationException always
     */
    @Override
    public <T> InjectionTargetFactory<T> getInjectionTargetFactory(AnnotatedType<T> annotatedType) {
        if (annotatedType == null) {
            throw new IllegalArgumentException("annotatedType cannot be null");
        }

        // CDI 4.1: reject creation if any injection point on the type has a definition/deployment problem.
        try {
            for (AnnotatedField<? super T> field : annotatedType.getFields()) {
                if (INJECT.isPresent(field.getJavaMember())) {
                    validate(createInjectionPoint(field));
                }
            }
            for (AnnotatedConstructor<T> constructor : annotatedType.getConstructors()) {
                if (!INJECT.isPresent(constructor.getJavaMember())) {
                    continue;
                }
                for (AnnotatedParameter<? super T> parameter : constructor.getParameters()) {
                    validate(createInjectionPoint(parameter));
                }
            }
            for (AnnotatedMethod<? super T> method : annotatedType.getMethods()) {
                if (!INJECT.isPresent(method.getJavaMember())) {
                    continue;
                }
                for (AnnotatedParameter<? super T> parameter : method.getParameters()) {
                    validate(createInjectionPoint(parameter));
                }
            }
        } catch (jakarta.enterprise.inject.InjectionException e) {
            throw new IllegalArgumentException("Definition error associated with injection point of type: " +
                    annotatedType.getJavaClass().getName(), e);
        }

        return new InjectionTargetFactoryImpl<>(annotatedType, this);
    }

    /**
     * Gets a producer factory for a field.
     *
     * <p>The factory can be used to create Producer instances that handle
     * producer field invocation and lifecycle.
     *
     * @param field the producer field
     * @param declaringBean the declaring bean
     * @param <X> the produced type
     * @return producer factory
     * @throws IllegalArgumentException if the field is null
     */
    @Override
    public <X> ProducerFactory<X> getProducerFactory(AnnotatedField<? super X> field, Bean<X> declaringBean) {
        if (field == null) {
            throw new IllegalArgumentException("field cannot be null");
        }

        Field javaField = field.getJavaMember();
        if (javaField == null) {
            throw new IllegalArgumentException("field.getJavaMember() cannot be null");
        }

        if (!Modifier.isStatic(javaField.getModifiers()) && declaringBean == null) {
            throw new IllegalArgumentException("declaringBean cannot be null");
        }
        if (INJECT.isPresent(javaField)) {
            throw new IllegalArgumentException("field is annotated @Inject: " + javaField.getName());
        }
        if (containsWildcardOrTypeVariable(javaField.getGenericType())) {
            throw new IllegalArgumentException("Producer field type contains illegal type: " + javaField.getGenericType());
        }

        return new ProducerFactoryImpl<>(field, this);
    }

    /**
     * Gets a producer factory for a method.
     *
     * <p>The factory can be used to create Producer instances that handle
     * producer method invocation and lifecycle.
     *
     * @param method the producer method
     * @param declaringBean the declaring bean
     * @param <X> the produced type
     * @return producer factory
     * @throws IllegalArgumentException if the method is null
     */
    @Override
    public <X> ProducerFactory<X> getProducerFactory(AnnotatedMethod<? super X> method, Bean<X> declaringBean) {
        if (method == null) {
            throw new IllegalArgumentException("method cannot be null");
        }

        Method javaMethod = method.getJavaMember();
        if (javaMethod == null) {
            throw new IllegalArgumentException("method.getJavaMember() cannot be null");
        }

        if (!Modifier.isStatic(javaMethod.getModifiers()) && declaringBean == null) {
            throw new IllegalArgumentException("declaringBean cannot be null");
        }
        if (containsWildcardOrTypeVariable(javaMethod.getGenericReturnType())) {
            throw new IllegalArgumentException("Producer method return type contains illegal type: " +
                    javaMethod.getGenericReturnType());
        }
        try {
            for (AnnotatedParameter<? super X> parameter : method.getParameters()) {
                validate(createInjectionPoint(parameter));
            }
        } catch (jakarta.enterprise.inject.InjectionException e) {
            throw new IllegalArgumentException("Definition error associated with producer method: " +
                    method.getJavaMember().getName(), e);
        }

        return new ProducerFactoryImpl<>(method, this);
    }

    private boolean containsWildcardOrTypeVariable(Type type) {
        if (type instanceof TypeVariable || type instanceof WildcardType) {
            return true;
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                if (containsWildcardOrTypeVariable(argument)) {
                    return true;
                }
            }
            Type ownerType = parameterizedType.getOwnerType();
            return containsWildcardOrTypeVariable(ownerType);
        }
        if (type instanceof GenericArrayType) {
            return containsWildcardOrTypeVariable(((GenericArrayType) type).getGenericComponentType());
        }
        if (type instanceof Class<?>) {
            Class<?> klass = (Class<?>) type;
            return klass.isArray() && containsWildcardOrTypeVariable(klass.getComponentType());
        }
        return false;
    }

    /**
     * Creates bean attributes from an annotated type.
     *
     * <p><b>Note:</b> Portable extension support not yet implemented.
     *
     * @param type the annotated type
     * @param <T> the type
     * @return bean attributes
     * @throws IllegalArgumentException if the type is null
     * @throws UnsupportedOperationException always
     */
    @Override
    public <T> BeanAttributes<T> createBeanAttributes(AnnotatedType<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        validateDeclaredBeanAttributes(type);

        String name = extractName(type);
        Set<Annotation> qualifiers = normalizeBeanQualifiers(extractBeanQualifiers(type));
        Class<? extends Annotation> scope = extractScopeFromAnnotated(type);
        Set<Class<? extends Annotation>> stereotypes = extractStereotypesFromAnnotated(type);
        Set<Type> types = extractTypesFromAnnotatedType(type);
        boolean alternative = isAlternativeDeclared(type);

        return new BeanAttributesImpl<>(name, qualifiers, scope, stereotypes, types, alternative);
    }

    /**
     * Creates bean attributes from an annotated member.
     *
     * <p><b>Note:</b> Portable extension support not yet implemented.
     *
     * @param member the annotated member
     * @return bean attributes
     * @throws IllegalArgumentException if the member is null
     * @throws UnsupportedOperationException always
     */
    @Override
    public BeanAttributes<?> createBeanAttributes(AnnotatedMember<?> member) {
        if (member == null) {
            throw new IllegalArgumentException("member cannot be null");
        }
        if (member instanceof AnnotatedConstructor<?>) {
            throw new IllegalArgumentException("member must not be a constructor");
        }
        validateDeclaredBeanAttributes(member);

        String name = extractName(member);
        Set<Annotation> qualifiers = normalizeBeanQualifiers(extractBeanQualifiers(member));
        Class<? extends Annotation> scope = extractScopeFromAnnotated(member);
        Set<Class<? extends Annotation>> stereotypes = extractStereotypesFromAnnotated(member);
        Set<Type> types = extractTypesFromMember(member);
        boolean alternative = isAlternativeDeclared(member);

        return new BeanAttributesImpl<>(name, qualifiers, scope, stereotypes, types, alternative);
    }

    /**
     * Creates a bean from attributes and injection target factory.
     *
     * <p><b>Note:</b> Portable extension support not yet implemented.
     *
     * @param attributes the bean attributes
     * @param beanClass the bean class
     * @param injectionTargetFactory the injection target factory
     * @param <T> the bean type
     * @return created bean
     * @throws IllegalArgumentException if any parameter is null
     * @throws UnsupportedOperationException always
     */
    @Override
    public <T> Bean<T> createBean(BeanAttributes<T> attributes, Class<T> beanClass,
                                   InjectionTargetFactory<T> injectionTargetFactory) {
        if (attributes == null) {
            throw new IllegalArgumentException("attributes cannot be null");
        }
        if (beanClass == null) {
            throw new IllegalArgumentException("beanClass cannot be null");
        }
        if (injectionTargetFactory == null) {
            throw new IllegalArgumentException("injectionTargetFactory cannot be null");
        }

        // Create the injection target from the factory
        InjectionTarget<T> injectionTarget = injectionTargetFactory.createInjectionTarget(null);

        if (isDecoratorSyntheticBean(attributes, beanClass)) {
            InjectionPoint delegateInjectionPoint = findDecoratorDelegateInjectionPoint(beanClass);
            Set<Type> decoratedTypes = determineDecoratedTypes(attributes, beanClass, delegateInjectionPoint);
            return spiSupport.createSyntheticDecoratorBean(
                    attributes,
                    beanClass,
                    injectionTarget,
                    delegateInjectionPoint.getType(),
                    delegateInjectionPoint.getQualifiers(),
                    decoratedTypes
            );
        }

        // Create a synthetic bean that uses the injection target for lifecycle management
        return spiSupport.createSyntheticBean(attributes, beanClass, injectionTarget);
    }

    /**
     * Creates a bean from attributes and producer factory.
     *
     * <p><b>Note:</b> Portable extension support not yet implemented.
     *
     * @param attributes the bean attributes
     * @param beanClass the bean class
     * @param producerFactory the producer factory
     * @param <T> the bean type
     * @param <X> the producer type
     * @return created bean
     * @throws IllegalArgumentException if any parameter is null
     * @throws UnsupportedOperationException always
     */
    @Override
    public <T, X> Bean<T> createBean(BeanAttributes<T> attributes, Class<X> beanClass,
                                      ProducerFactory<X> producerFactory) {
        if (attributes == null) {
            throw new IllegalArgumentException("attributes cannot be null");
        }
        if (beanClass == null) {
            throw new IllegalArgumentException("beanClass cannot be null");
        }
        if (producerFactory == null) {
            throw new IllegalArgumentException("producerFactory cannot be null");
        }

        // Create the producer from the factory
        Producer<T> producer = producerFactory.createProducer(null);

        // Create a synthetic producer bean that uses the producer for instance creation
        return spiSupport.createSyntheticProducerBean(attributes, beanClass, producer);
    }

    /**
     * Creates an injection point from an annotated field.
     *
     * <p><b>Note:</b> Portable extension support not yet implemented.
     *
     * @param field the annotated field
     * @return injection point
     * @throws IllegalArgumentException if the field is null
     * @throws UnsupportedOperationException always
     */
    @Override
    public InjectionPoint createInjectionPoint(AnnotatedField<?> field) {
        if (field == null) {
            throw new IllegalArgumentException("field cannot be null");
        }
        Field javaField = field.getJavaMember();
        if (javaField == null) {
            throw new IllegalArgumentException("field.getJavaMember() cannot be null");
        }

        try {
            InjectionPoint injectionPoint = new InjectionPointImpl<>(javaField, null);
            validateInjectionPointDefinitionOnly(injectionPoint);
            return injectionPoint;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Definition error associated with injection point field: " +
                    javaField.getName(), e);
        }
    }

    /**
     * Creates an injection point from an annotated parameter.
     *
     * <p><b>Note:</b> Portable extension support not yet implemented.
     *
     * @param parameter the annotated parameter
     * @return injection point
     * @throws IllegalArgumentException if parameter is null
     * @throws UnsupportedOperationException always
     */
    @Override
    public InjectionPoint createInjectionPoint(AnnotatedParameter<?> parameter) {
        if (parameter == null) {
            throw new IllegalArgumentException("parameter cannot be null");
        }
        Parameter javaParameter = parameter.getJavaParameter();
        if (javaParameter == null) {
            throw new IllegalArgumentException("parameter.getJavaParameter() cannot be null");
        }

        try {
            InjectionPoint injectionPoint = new InjectionPointImpl<>(javaParameter, null);
            validateInjectionPointDefinitionOnly(injectionPoint);
            return injectionPoint;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Definition error associated with injection point parameter: " +
                    javaParameter.getName(), e);
        }
    }

    private void validateInjectionPointDefinitionOnly(InjectionPoint injectionPoint) {
        for (Annotation qualifier : injectionPoint.getQualifiers()) {
            if (qualifier == null || qualifier.annotationType() == null) {
                continue;
            }
            if (!hasNamedAnnotation(qualifier.annotationType())) {
                continue;
            }
            String namedValue = getNamedValue(qualifier);
            if (namedValue != null && !namedValue.trim().isEmpty()) {
                continue;
            }
            if (!(injectionPoint.getMember() instanceof Field)) {
                throw new IllegalArgumentException(
                        "Empty @Named value is only valid for field injection points");
            }
        }
    }

    private String getNamedValue(Annotation qualifier) {
        try {
            Method valueMethod = qualifier.annotationType().getMethod("value");
            Object value = valueMethod.invoke(qualifier);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private <T> boolean isDecoratorSyntheticBean(BeanAttributes<T> attributes, Class<T> beanClass) {
        if (hasDecoratorAnnotation(beanClass)) {
            return true;
        }
        if (attributes == null || attributes.getStereotypes() == null) {
            return false;
        }
        for (Class<? extends Annotation> stereotype : attributes.getStereotypes()) {
            if (stereotype == null) {
                continue;
            }
            if (hasDecoratorAnnotation(stereotype)) {
                return true;
            }
        }
        return false;
    }

    private InjectionPoint findDecoratorDelegateInjectionPoint(Class<?> beanClass) {
        List<InjectionPoint> delegatePoints = new ArrayList<>();

        for (Field field : beanClass.getDeclaredFields()) {
            if (hasDelegateMarker(field.getAnnotations())) {
                delegatePoints.add(new InjectionPointImpl<>(field, null));
            }
        }
        for (Constructor<?> constructor : beanClass.getDeclaredConstructors()) {
            Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
            Parameter[] parameters = constructor.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                if (hasDelegateMarker(parameterAnnotations[i])) {
                    delegatePoints.add(new InjectionPointImpl<>(parameters[i], null));
                }
            }
        }
        for (Method method : beanClass.getDeclaredMethods()) {
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                if (hasDelegateMarker(parameterAnnotations[i])) {
                    delegatePoints.add(new InjectionPointImpl<>(parameters[i], null));
                }
            }
        }

        if (delegatePoints.size() != 1) {
            throw new IllegalArgumentException("Decorator synthetic bean " + beanClass.getName() +
                    " must declare exactly one @Delegate injection point");
        }
        return delegatePoints.get(0);
    }

    private boolean hasDelegateMarker(Annotation[] annotations) {
        if (annotations == null) {
            return false;
        }
        for (Annotation annotation : annotations) {
            if (annotation == null || annotation.annotationType() == null) {
                continue;
            }
            if (hasDelegateAnnotation(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    private Set<Type> determineDecoratedTypes(BeanAttributes<?> attributes,
                                              Class<?> beanClass,
                                              InjectionPoint delegateInjectionPoint) {
        Set<Type> decoratedTypes = new HashSet<>();
        if (attributes != null && attributes.getTypes() != null) {
            for (Type type : attributes.getTypes()) {
                if (type == null || Object.class.equals(type) || beanClass.equals(type)) {
                    continue;
                }
                decoratedTypes.add(type);
            }
        }
        if (decoratedTypes.isEmpty()) {
            Type delegateType = delegateInjectionPoint != null ? delegateInjectionPoint.getType() : null;
            if (delegateType != null) {
                decoratedTypes.add(delegateType);
            }
        }
        return decoratedTypes;
    }

    /**
     * Gets a CDI extension instance.
     *
     * <p><b>Note:</b> Portable extensions not yet implemented.
     *
     * @param extensionClass the extension class
     * @param <T> the extension type
     * @return extension instance, or null if not found
     * @throws IllegalArgumentException if extensionClass is null
     */
    @Override
    public <T extends Extension> T getExtension(Class<T> extensionClass) {
        if (extensionClass == null) {
            throw new IllegalArgumentException("extensionClass cannot be null");
        }
        for (Extension extension : registeredExtensions) {
            if (extensionClass.isInstance(extension)) {
                return extensionClass.cast(extension);
            }
        }
        throw new IllegalArgumentException("No extension instance available for class: " + extensionClass.getName());
    }

    /**
     * Creates an interception factory for programmatic interceptor binding.
     *
     * <p><b>Note:</b> Not yet implemented.
     *
     * @param ctx the creational context
     * @param clazz the class to intercept
     * @param <T> the class type
     * @return interception factory
     * @throws IllegalArgumentException if any parameter is null
     * @throws UnsupportedOperationException always
     */
    @Override
    public <T> InterceptionFactory<T> createInterceptionFactory(CreationalContext<T> ctx, Class<T> clazz) {
        if (ctx == null) {
            throw new IllegalArgumentException("ctx cannot be null");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("clazz cannot be null");
        }
        if (clazz.isInterface() || clazz.isAnnotation() || clazz.isArray() || clazz.isPrimitive()) {
            throw new NonPortableBehaviourException("Non-portable behavior: InterceptionFactory requires a Java class type, got " + clazz.getName());
        }

        InterceptorSupport localInterceptorSupport = interceptorSupport != null
                ? interceptorSupport
                : new NoOpInterceptorSupport();
        return localInterceptorSupport.createInterceptionFactory(ctx, clazz, this);
    }

    // ==================== Helper Methods ====================

    /**
     * Checks if event qualifiers match observer qualifiers.
     * All observer qualifiers must be present in event qualifiers.
     */
    private boolean observerQualifiersMatch(Set<Annotation> eventQualifiers, Set<Annotation> observerQualifiers) {
        // All observer qualifiers must be present in event qualifiers
        for (Annotation observerQual : observerQualifiers) {
            // @Any matches everything
            if (hasAnyAnnotation(observerQual.annotationType())) {
                continue;
            }

            boolean found = false;
            for (Annotation eventQual : eventQualifiers) {
                if (areQualifiersEquivalent(observerQual, eventQual)) {
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

    private void validateQualifierSet(Set<Annotation> qualifiers, String paramName) {
        for (Annotation qualifier : qualifiers) {
            if (qualifier == null) {
                throw new IllegalArgumentException(paramName + " cannot contain null qualifiers");
            }
            if (!isQualifier(qualifier.annotationType())) {
                throw new IllegalArgumentException("Annotation is not a qualifier type: " +
                        qualifier.annotationType().getName());
            }
        }
    }

    private boolean isLegalBeanType(Type beanType) {
        if (beanType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) beanType;
            for (Type arg : parameterizedType.getActualTypeArguments()) {
                if (arg instanceof WildcardType || arg instanceof TypeVariable) {
                    return false;
                }
            }
        }
        return true;
    }

    private Set<Annotation> normalizeEventQualifiers(Set<Annotation> eventQualifiers) {
        Set<Annotation> normalized = new HashSet<>(eventQualifiers);
        if (normalized.isEmpty()) {
            normalized.add(jakarta.enterprise.inject.Default.Literal.INSTANCE);
        }
        normalized.add(Any.Literal.INSTANCE);
        return normalized;
    }

    private Set<Annotation> normalizeObservedEventQualifiers(Set<Annotation> observedQualifiers) {
        if (observedQualifiers.isEmpty()) {
            return Collections.singleton(Any.Literal.INSTANCE);
        }
        return observedQualifiers;
    }

    private boolean containsTypeVariable(Type type) {
        if (type instanceof TypeVariable) {
            return true;
        }
        if (type instanceof ParameterizedType) {
            for (Type arg : ((ParameterizedType) type).getActualTypeArguments()) {
                if (containsTypeVariable(arg)) {
                    return true;
                }
            }
            Type ownerType = ((ParameterizedType) type).getOwnerType();
            return containsTypeVariable(ownerType);
        }
        if (type instanceof GenericArrayType) {
            return containsTypeVariable(((GenericArrayType) type).getGenericComponentType());
        }
        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            for (Type upperBound : wildcardType.getUpperBounds()) {
                if (containsTypeVariable(upperBound)) {
                    return true;
                }
            }
            for (Type lowerBound : wildcardType.getLowerBounds()) {
                if (containsTypeVariable(lowerBound)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the priority value from @Priority annotation.
     * Returns APPLICATION priority if not specified.
     */
    private int getPriority(Object obj) {
        Integer applicationOrderPriority = getAfterTypeDiscoveryAlternativePriority(obj);
        if (applicationOrderPriority != null) {
            return applicationOrderPriority;
        }

        Class<?> clazz = obj instanceof Bean ? ((Bean<?>) obj).getBeanClass() : obj.getClass();

        if (obj instanceof SyntheticProducerBeanMarker) {
            Bean<?> originalProducerBean = findOriginalProducerBean((Bean<?>) obj);
            if (originalProducerBean instanceof ProducerBean) {
                ProducerBean<?> producerBean = (ProducerBean<?>) originalProducerBean;
                Integer memberPriority = extractPriorityFromProducerMember(producerBean);
                if (memberPriority != null) {
                    return memberPriority;
                }

                Integer producerPriority = producerBean.getPriority();
                if (producerPriority != null) {
                    return producerPriority;
                }

                Integer declaringPriority = extractPriorityFromClass(producerBean.getDeclaringClass());
                if (declaringPriority != null) {
                    return declaringPriority;
                }
            }
        }

        if (obj instanceof ProducerBean) {
            ProducerBean<?> producerBean = (ProducerBean<?>) obj;
            Integer memberPriority = extractPriorityFromProducerMember(producerBean);
            if (memberPriority != null) {
                return memberPriority;
            }

            Integer producerPriority = producerBean.getPriority();
            if (producerPriority != null) {
                return producerPriority;
            }

            Integer declaringPriority = extractPriorityFromClass(producerBean.getDeclaringClass());
            if (declaringPriority != null) {
                return declaringPriority;
            }
        }

        if (obj instanceof BeanImpl) {
            Integer beanPriority = ((BeanImpl<?>) obj).getPriority();
            if (beanPriority != null) {
                return beanPriority;
            }
        }

        if (obj instanceof SyntheticBeanPriority) {
            Integer syntheticPriority = ((SyntheticBeanPriority) obj).getPriority();
            if (syntheticPriority != null) {
                return syntheticPriority;
            }
        }

        Integer classPriority = extractPriorityFromClass(clazz);
        if (classPriority != null) {
            return classPriority;
        }

        return jakarta.interceptor.Interceptor.Priority.APPLICATION;
    }

    private Integer getAfterTypeDiscoveryAlternativePriority(Object obj) {
        if (!knowledgeBase.hasAfterTypeDiscoveryAlternativesCustomized()) {
            return null;
        }
        if (!(obj instanceof Bean)) {
            return null;
        }
        Class<?> beanClass = ((Bean<?>) obj).getBeanClass();
        if (beanClass == null) {
            return null;
        }
        int applicationOrder = knowledgeBase.getApplicationAlternativeOrder(beanClass);
        if (applicationOrder < 0) {
            return null;
        }
        return Integer.MAX_VALUE - applicationOrder;
    }

    private int getDecoratorPriority(Decorator<?> decorator) {
        if (decorator == null) {
            return Integer.MAX_VALUE;
        }
        if (decorator instanceof Prioritized) {
            return ((Prioritized) decorator).getPriority();
        }
        Integer classPriority = extractPriorityFromClass(decorator.getBeanClass());
        return classPriority != null ? classPriority : Integer.MAX_VALUE;
    }

    private int getInterceptorPriority(Interceptor<?> interceptor) {
        if (interceptor == null) {
            return Integer.MAX_VALUE;
        }
        if (interceptor instanceof Prioritized) {
            return ((Prioritized) interceptor).getPriority();
        }
        Integer classPriority = extractPriorityFromClass(interceptor.getBeanClass());
        return classPriority != null ? classPriority : Integer.MAX_VALUE;
    }

    private Bean<?> findOriginalProducerBean(Bean<?> syntheticBean) {
        for (ProducerBean<?> producerBean : knowledgeBase.getProducerBeans()) {
            if (!Objects.equals(producerBean.getBeanClass(), syntheticBean.getBeanClass())) {
                continue;
            }
            if (!Objects.equals(producerBean.getTypes(), syntheticBean.getTypes())) {
                continue;
            }
            if (!Objects.equals(producerBean.getQualifiers(), syntheticBean.getQualifiers())) {
                continue;
            }
            return producerBean;
        }
        return null;
    }

    private Integer extractPriorityFromProducerMember(ProducerBean<?> producerBean) {
        if (producerBean.getProducerMethod() != null) {
            return extractPriorityFromAnnotations(producerBean.getProducerMethod().getAnnotations());
        }
        if (producerBean.getProducerField() != null) {
            return extractPriorityFromAnnotations(producerBean.getProducerField().getAnnotations());
        }
        return null;
    }

    private Integer extractPriorityFromClass(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        AnnotatedType<?> override = knowledgeBase.getAnnotatedTypeOverride(clazz);
        Annotation[] annotations = override != null
                ? override.getAnnotations().toArray(new Annotation[0])
                : clazz.getAnnotations();
        return extractPriorityFromAnnotations(annotations);
    }

    private Integer extractPriorityFromAnnotations(Annotation[] annotations) {
        return extractPriorityFromAnnotations(annotations, new HashSet<>());
    }

    private Integer extractPriorityFromAnnotations(Annotation[] annotations,
                                                   Set<Class<? extends Annotation>> visitedStereotypes) {
        if (annotations == null) {
            return null;
        }
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (PRIORITY.matches(annotationType)) {
                try {
                    Method valueMethod = annotationType.getMethod("value");
                    Object value = valueMethod.invoke(annotation);
                    if (value instanceof Integer) {
                        return (Integer) value;
                    }
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }

            if (!hasStereotypeAnnotation(annotationType)) {
                continue;
            }
            if (!visitedStereotypes.add(annotationType)) {
                continue;
            }

            Integer nestedPriority = extractPriorityFromAnnotations(annotationType.getAnnotations(), visitedStereotypes);
            if (nestedPriority != null) {
                return nestedPriority;
            }
        }
        return null;
    }

    /**
     * Creates an ObserverMethod wrapper from ObserverMethodInfo.
     */
    private ObserverMethodMetadata createSyntheticObserverInfo(final ObserverMethod<?> syntheticObserver) {
        final Set<Annotation> observedQualifiers = syntheticObserver.getObservedQualifiers() == null
                ? Collections.<Annotation>emptySet()
                : syntheticObserver.getObservedQualifiers();
        return new ObserverMethodMetadata() {
            @Override
            public Method getObserverMethod() {
                return null;
            }

            @Override
            public Type getEventType() {
                return syntheticObserver.getObservedType();
            }

            @Override
            public Set<Annotation> getQualifiers() {
                return observedQualifiers;
            }

            @Override
            public jakarta.enterprise.event.Reception getReception() {
                return syntheticObserver.getReception();
            }

            @Override
            public jakarta.enterprise.event.TransactionPhase getTransactionPhase() {
                return syntheticObserver.getTransactionPhase();
            }

            @Override
            public boolean isAsync() {
                return syntheticObserver.isAsync();
            }

            @Override
            public Bean<?> getDeclaringBean() {
                return null;
            }

            @Override
            public int getPriority() {
                return syntheticObserver.getPriority();
            }

            @Override
            public int getObservedParameterPosition() {
                return -1;
            }

            @Override
            public ObserverMethod<?> getSyntheticObserver() {
                return syntheticObserver;
            }
        };
    }

    private <T> ObserverMethod<T> createObserverMethod(ObserverMethodMetadata info) {
        if (info.isSynthetic() && info.getSyntheticObserver() != null) {
            @SuppressWarnings("unchecked")
            ObserverMethod<T> synthetic = (ObserverMethod<T>) info.getSyntheticObserver();
            return synthetic;
        }

        final String identityKey = observerMethodIdentityKey(info);

        // Create a wrapper that implements ObserverMethod
        return new ObserverMethod<T>() {
            @Override
            public Class<?> getBeanClass() {
                return info.getDeclaringBean() != null ?
                    info.getDeclaringBean().getBeanClass() :
                    info.getObserverMethod().getDeclaringClass();
            }

            @Override
            public Bean<?> getDeclaringBean() {
                return info.getDeclaringBean();
            }

            @Override
            public Type getObservedType() {
                return info.getEventType();
            }

            @Override
            public Set<Annotation> getObservedQualifiers() {
                return info.getQualifiers();
            }

            @Override
            public jakarta.enterprise.event.Reception getReception() {
                return info.getReception();
            }

            @Override
            public jakarta.enterprise.event.TransactionPhase getTransactionPhase() {
                return info.getTransactionPhase();
            }

            @Override
            public int getPriority() {
                return info.getPriority();
            }

            @Override
            public void notify(T event) {
                Method observerMethod = info.getObserverMethod();
                if (observerMethod == null) {
                    throw new RuntimeException("Observer method metadata does not contain a reflective Method");
                }
                // Resolve the bean instance and jakarta.enterprise.invoke the observer method
                try {
                    Class<?> declaringClass = info.getDeclaringBean() != null ?
                        info.getDeclaringBean().getBeanClass() :
                        observerMethod.getDeclaringClass();
                    Object instance = beanResolver.resolveDeclaringBeanInstance(declaringClass);
                    if (!observerMethod.isAccessible()) {
                        observerMethod.setAccessible(true);
                    }
                    observerMethod.invoke(instance, event);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to jakarta.enterprise.invoke observer method", e);
                }
            }

            @Override
            public boolean isAsync() {
                return info.isAsync();
            }

            @Override
            public int hashCode() {
                return identityKey.hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (!(obj instanceof ObserverMethod<?>)) {
                    return false;
                }
                return identityKey.equals(observerMethodIdentityKey((ObserverMethod<?>) obj));
            }
        };
    }

    private static String observerMethodIdentityKey(ObserverMethodMetadata info) {
        if (info == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        Method observerMethod = info.getObserverMethod();
        if (observerMethod != null) {
            sb.append(observerMethod.getDeclaringClass().getName())
                    .append('#')
                    .append(observerMethod.getName())
                    .append('(');
            Class<?>[] parameterTypes = observerMethod.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(parameterTypes[i].getName());
            }
            sb.append(')');
        } else if (info.getSyntheticObserver() != null) {
            sb.append("synthetic:")
                    .append(info.getSyntheticObserver().getClass().getName());
        } else {
            sb.append("unknown");
        }

        sb.append('|').append(info.getEventType() != null ? info.getEventType().getTypeName() : "");
        sb.append('|').append(sortedQualifierIdentity(info.getQualifiers()));
        sb.append('|').append(info.getReception());
        sb.append('|').append(info.getTransactionPhase());
        sb.append('|').append(info.isAsync());
        sb.append('|').append(info.getPriority());
        return sb.toString();
    }

    private static String observerMethodIdentityKey(ObserverMethod<?> observerMethod) {
        ObserverMethodMetadata info = extractObserverMethodMetadata(observerMethod);
        if (info != null) {
            return observerMethodIdentityKey(info);
        }

        StringBuilder sb = new StringBuilder();
        if (observerMethod.getBeanClass() != null) {
            sb.append(observerMethod.getBeanClass().getName());
        } else {
            sb.append("unknown");
        }
        sb.append('|').append(observerMethod.getObservedType() != null
                ? observerMethod.getObservedType().getTypeName()
                : "");
        sb.append('|').append(sortedQualifierIdentity(observerMethod.getObservedQualifiers()));
        sb.append('|').append(observerMethod.getReception());
        sb.append('|').append(observerMethod.getTransactionPhase());
        sb.append('|').append(observerMethod.isAsync());
        sb.append('|').append(observerMethod.getPriority());
        return sb.toString();
    }

    private static ObserverMethodMetadata extractObserverMethodMetadata(ObserverMethod<?> observerMethod) {
        if (observerMethod == null) {
            return null;
        }

        Class<?> current = observerMethod.getClass();
        while (current != null && !Object.class.equals(current)) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                if (!ObserverMethodMetadata.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(observerMethod);
                    if (value instanceof ObserverMethodMetadata) {
                        return (ObserverMethodMetadata) value;
                    }
                } catch (IllegalAccessException ignored) {
                    // try next field/superclass
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static String sortedQualifierIdentity(Set<Annotation> qualifiers) {
        if (qualifiers == null || qualifiers.isEmpty()) {
            return "";
        }

        List<String> entries = new ArrayList<>();
        for (Annotation qualifier : qualifiers) {
            if (qualifier == null) {
                continue;
            }
            entries.add(qualifier.annotationType().getName() + ":" + qualifier);
        }
        Collections.sort(entries);
        return String.join(",", entries);
    }

    /**
     * Creates an Interceptor wrapper from InterceptorInfo.
     */
    private <T> Interceptor<T> createInterceptor(InterceptorInfo info) {
        final Class<?> interceptorClass = info.getInterceptorClass();
        final Set<Annotation> bindings = new HashSet<>(info.getInterceptorBindings());
        return new Interceptor<T>() {
            @Override
            public Set<Annotation> getInterceptorBindings() {
                return Collections.unmodifiableSet(bindings);
            }

            @Override
            public boolean intercepts(InterceptionType type) {
                if (type == null) {
                    return false;
                }
                switch (type) {
                    case AROUND_INVOKE:
                        return info.getAroundInvokeMethod() != null;
                    case AROUND_CONSTRUCT:
                        return info.getAroundConstructMethod() != null;
                    case POST_CONSTRUCT:
                        return info.getPostConstructMethod() != null;
                    case PRE_DESTROY:
                        return info.getPreDestroyMethod() != null;
                    default:
                        return false;
                }
            }

            @Override
            public Object intercept(InterceptionType type, T instance, jakarta.interceptor.InvocationContext ctx) throws Exception {
                Method interceptorMethod;
                switch (type) {
                    case AROUND_INVOKE:
                        interceptorMethod = info.getAroundInvokeMethod();
                        break;
                    case AROUND_CONSTRUCT:
                        interceptorMethod = info.getAroundConstructMethod();
                        break;
                    case POST_CONSTRUCT:
                        interceptorMethod = info.getPostConstructMethod();
                        break;
                    case PRE_DESTROY:
                        interceptorMethod = info.getPreDestroyMethod();
                        break;
                    default:
                        interceptorMethod = null;
                }
                if (interceptorMethod == null) {
                    return ctx != null ? ctx.proceed() : null;
                }
                if (!interceptorMethod.isAccessible()) {
                    interceptorMethod.setAccessible(true);
                }
                try {
                    return interceptorMethod.invoke(instance, ctx);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    Throwable target = e.getTargetException();
                    if (target instanceof Exception) {
                        throw (Exception) target;
                    }
                    throw new RuntimeException(target);
                }
            }

            @Override
            public Class<?> getBeanClass() {
                return interceptorClass;
            }

            @Override
            public Set<InjectionPoint> getInjectionPoints() {
                return Collections.emptySet();
            }

            @Override
            public T create(CreationalContext<T> context) {
                try {
                    @SuppressWarnings("unchecked")
                    T created = (T) interceptorClass.getDeclaredConstructor().newInstance();
                    return created;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create interceptor instance: " + interceptorClass.getName(), e);
                }
            }

            @Override
            public void destroy(T instance, CreationalContext<T> context) {
                if (context != null) {
                    context.release();
                }
            }

            @Override
            public Set<Type> getTypes() {
                Set<Type> types = new HashSet<>();
                types.add(interceptorClass);
                types.add(Object.class);
                return Collections.unmodifiableSet(types);
            }

            @Override
            public Set<Annotation> getQualifiers() {
                Set<Annotation> qualifiers = new HashSet<>();
                qualifiers.add(jakarta.enterprise.inject.Default.Literal.INSTANCE);
                qualifiers.add(Any.Literal.INSTANCE);
                return qualifiers;
            }

            @Override
            public Class<? extends Annotation> getScope() {
                return Dependent.class;
            }

            @Override
            public String getName() {
                return null;
            }

            @Override
            public Set<Class<? extends Annotation>> getStereotypes() {
                return Collections.emptySet();
            }

            @Override
            public boolean isAlternative() {
                return false;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (!(obj instanceof Bean<?>)) {
                    return false;
                }
                Bean<?> other = (Bean<?>) obj;
                return Objects.equals(interceptorClass, other.getBeanClass());
            }

            @Override
            public int hashCode() {
                return Objects.hashCode(interceptorClass);
            }
        };
    }

    /**
     * Creates a Decorator wrapper from DecoratorInfo.
     */
    private <T> Decorator<T> createDecorator(DecoratorInfo info) {
        return new Decorator<T>() {
            @Override
            public Type getDelegateType() {
                return info.getDelegateInjectionPoint().getType();
            }

            @Override
            public Set<Annotation> getDelegateQualifiers() {
                return info.getDelegateInjectionPoint().getQualifiers();
            }

            @Override
            public Set<Type> getDecoratedTypes() {
                return info.getDecoratedTypes();
            }

            @Override
            public Class<?> getBeanClass() {
                return info.getDecoratorClass();
            }

            @Override
            public Set<InjectionPoint> getInjectionPoints() {
                return resolveDecoratorInjectionPoints(info, this);
            }

            @Override
            public String getName() {
                return null;
            }

            @Override
            public Set<Annotation> getQualifiers() {
                return Collections.singleton(jakarta.enterprise.inject.Default.Literal.INSTANCE);
            }

            @Override
            public Class<? extends Annotation> getScope() {
                return Dependent.class;
            }

            @Override
            public Set<Class<? extends Annotation>> getStereotypes() {
                return Collections.emptySet();
            }

            @Override
            public Set<Type> getTypes() {
                return new HashSet<>(Arrays.<Type>asList(
                        info.getDecoratorClass(),
                        Decorator.class,
                        Object.class));
            }

            @Override
            public boolean isAlternative() {
                return false;
            }

            @Override
            public T create(CreationalContext<T> creationalContext) {
                throw new UnsupportedOperationException(
                        "Decorator metadata cannot create contextual instance for " + info.getDecoratorClass().getName());
            }

            @Override
            public void destroy(T instance, CreationalContext<T> creationalContext) {
                if (creationalContext != null) {
                    creationalContext.release();
                }
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (!(obj instanceof Bean<?>)) {
                    return false;
                }
                Bean<?> other = (Bean<?>) obj;
                return Objects.equals(info.getDecoratorClass(), other.getBeanClass());
            }

            @Override
            public int hashCode() {
                return Objects.hashCode(info.getDecoratorClass());
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Set<InjectionPoint> resolveDecoratorInjectionPoints(DecoratorInfo info, Bean<?> metadataBean) {
        Set<InjectionPoint> resolved = new LinkedHashSet<>();
        if (info == null) {
            return Collections.emptySet();
        }

        Bean<Object> decoratorMetadataBean = (Bean<Object>) metadataBean;
        InjectionPoint delegateInjectionPoint = info.getDelegateInjectionPoint();
        Member delegateMember = delegateInjectionPoint != null ? delegateInjectionPoint.getMember() : null;

        Class<?> decoratorClass = info.getDecoratorClass();
        if (decoratorClass != null) {
            for (Bean<?> bean : knowledgeBase.getBeans()) {
                if (!(bean instanceof Decorator<?>)) {
                    continue;
                }
                if (!decoratorClass.equals(bean.getBeanClass())) {
                    continue;
                }
                Set<InjectionPoint> candidateInjectionPoints = bean.getInjectionPoints();
                if (candidateInjectionPoints != null) {
                    resolved.addAll(candidateInjectionPoints);
                }
            }

            // Fallback for metadata-only decorators: derive injection points from class members.
            for (Class<?> current = decoratorClass;
                 current != null && !Object.class.equals(current);
                 current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || hasNotInjectMarker(field.getAnnotations())) {
                        continue;
                    }
                    if (delegateMember instanceof Field && delegateMember.equals(field)) {
                        resolved.add(delegateInjectionPoint);
                        continue;
                    }
                    resolved.add(new InjectionPointImpl<>(field, decoratorMetadataBean));
                }

                for (Method method : current.getDeclaredMethods()) {
                    if (hasNotInjectMarker(method.getAnnotations())) {
                        continue;
                    }
                    Parameter[] parameters = method.getParameters();
                    for (int i = 0; i < parameters.length; i++) {
                        Parameter parameter = parameters[i];
                        if (matchesDelegateParameter(delegateInjectionPoint, parameter, i) ||
                                hasDelegateMarker(parameter.getAnnotations())) {
                            if (delegateInjectionPoint != null) {
                                resolved.add(delegateInjectionPoint);
                            }
                            continue;
                        }
                        resolved.add(new InjectionPointImpl<>(parameter, decoratorMetadataBean));
                    }
                }
            }

            for (Constructor<?> constructor : decoratorClass.getDeclaredConstructors()) {
                if (hasNotInjectMarker(constructor.getAnnotations())) {
                    continue;
                }
                Parameter[] parameters = constructor.getParameters();
                for (int i = 0; i < parameters.length; i++) {
                    Parameter parameter = parameters[i];
                    if (matchesDelegateParameter(delegateInjectionPoint, parameter, i) ||
                            hasDelegateMarker(parameter.getAnnotations())) {
                        if (delegateInjectionPoint != null) {
                            resolved.add(delegateInjectionPoint);
                        }
                        continue;
                    }
                    resolved.add(new InjectionPointImpl<>(parameter, decoratorMetadataBean));
                }
            }
        }
        if (resolved.isEmpty() && delegateInjectionPoint != null) {
            resolved.add(delegateInjectionPoint);
        }
        return Collections.unmodifiableSet(resolved);
    }

    private boolean matchesDelegateParameter(InjectionPoint delegateInjectionPoint,
                                             Parameter parameter,
                                             int parameterIndex) {
        if (delegateInjectionPoint == null || parameter == null) {
            return false;
        }

        Member delegateMember = delegateInjectionPoint.getMember();
        if (!(delegateMember instanceof java.lang.reflect.Executable)) {
            return false;
        }

        java.lang.reflect.Executable executable = parameter.getDeclaringExecutable();
        if (!delegateMember.equals(executable)) {
            return false;
        }

        Annotated annotated = delegateInjectionPoint.getAnnotated();
        if (annotated instanceof AnnotatedParameter<?>) {
            return ((AnnotatedParameter<?>) annotated).getPosition() == parameterIndex;
        }

        return executable.getParameterCount() == 1;
    }

    private boolean hasNotInjectMarker(Annotation[] annotations) {
        if (annotations == null) {
            return true;
        }
        for (Annotation annotation : annotations) {
            if (annotation == null) {
                continue;
            }
            if (hasInjectAnnotation(annotation.annotationType())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Simple implementation of CreationalContext.
     * Tracks dependent instances for cleanup.
     */
    private static class CreationalContextImpl<T> implements CreationalContext<T>, Serializable {
        private static final long serialVersionUID = 1L;
        private final List<DependentEntry> dependentInstances = new ArrayList<>();

        @Override
        public void push(T incompleteInstance) {
            // Not needed for basic implementation
        }

        @Override
        public void release() {
            // Destroy dependent instances in reverse creation order.
            for (int i = dependentInstances.size() - 1; i >= 0; i--) {
                DependentEntry entry = dependentInstances.get(i);
                try {
                    if (DestroyedInstanceTracker.isDestroyed(entry.instance)) {
                        if (entry.creationalContext != null) {
                            entry.creationalContext.release();
                        }
                        continue;
                    }
                    entry.bean.destroy(entry.instance, entry.creationalContext);
                } catch (Exception ignored) {
                    // Continue destroying remaining dependents.
                }
            }
            dependentInstances.clear();
        }

        public void addDependentInstance(Bean<Object> bean, Object instance, CreationalContext<Object> creationalContext) {
            if (bean != null && instance != null) {
                dependentInstances.add(new DependentEntry(bean, instance, creationalContext));
            }
        }

        public CreationalContext<Object> findDependentCreationalContext(Bean<?> bean, Object instance) {
            if (bean == null || instance == null) {
                return null;
            }
            for (int i = dependentInstances.size() - 1; i >= 0; i--) {
                DependentEntry entry = dependentInstances.get(i);
                if (entry.bean == bean && entry.instance == instance) {
                    return entry.creationalContext;
                }
            }
            return null;
        }

        private static class DependentEntry {
            private final Bean<Object> bean;
            private final Object instance;
            private final CreationalContext<Object> creationalContext;

            private DependentEntry(Bean<Object> bean, Object instance, CreationalContext<Object> creationalContext) {
                this.bean = bean;
                this.instance = instance;
                this.creationalContext = creationalContext;
            }
        }
    }

    /**
     * Adapter to wrap internal ScopeContext as Jakarta Context.
     * Bridges the gap between internal scope management and CDI SPI.
     */
    private static class ScopeContextAdapter implements AlterableContext {
        private final com.threeamigos.common.util.implementations.injection.scopes.ScopeContext scopeContext;
        private final Class<? extends Annotation> scope;

        public ScopeContextAdapter(com.threeamigos.common.util.implementations.injection.scopes.ScopeContext scopeContext,
                                   Class<? extends Annotation> scope) {
            this.scopeContext = scopeContext;
            this.scope = scope;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return scope;
        }

        @Override
        public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
            if (creationalContext == null) {
                return get(contextual);
            }
            if (!scopeContext.isActive()) {
                throw new jakarta.enterprise.context.ContextNotActiveException(
                    "Context not active for scope: " + scope.getName()
                );
            }
            if (!(contextual instanceof Bean)) {
                return null;
            }
            return scopeContext.get((Bean<T>) contextual, creationalContext);
        }

        @Override
        public <T> T get(Contextual<T> contextual) {
            if (!scopeContext.isActive()) {
                throw new jakarta.enterprise.context.ContextNotActiveException(
                    "Context not active for scope: " + scope.getName()
                );
            }
            if (!(contextual instanceof Bean)) {
                return null;
            }
            Bean<T> bean = (Bean<T>) contextual;
            if (hasDependentAnnotation(scope)) {
                // CDI dependent context contract expects null for managed dependent beans when
                // no CreationalContext is supplied. Producer beans are a special case used by
                // lifecycle TCK flows that jakarta.enterprise.invoke destroy() after Context#get(Contextual).
                if (bean instanceof ProducerBean<?> || bean instanceof SyntheticProducerBeanMarker) {
                    return scopeContext.get(bean, null);
                }
                return null;
            }
            return scopeContext.getIfExists(bean);
        }

        @Override
        public boolean isActive() {
            return scopeContext.isActive();
        }

        public void destroy(Contextual<?> contextual) {
            if (!scopeContext.isActive()) {
                throw new jakarta.enterprise.context.ContextNotActiveException(
                    "Context not active for scope: " + scope.getName()
                );
            }
            scopeContext.destroy(contextual);
        }
    }

    private static class ExtensionServiceProviderBean<T extends Extension> implements Bean<T>, PassivationCapable {
        private final T extension;
        private final Class<T> beanClass;
        private final Set<Type> types;
        private final Set<Annotation> qualifiers;
        private final Set<Class<? extends Annotation>> stereotypes;

        @SuppressWarnings("unchecked")
        private ExtensionServiceProviderBean(T extension) {
            this.extension = extension;
            this.beanClass = (Class<T>) extension.getClass();
            this.types = collectTypes(beanClass);
            this.qualifiers = new HashSet<>();
            this.qualifiers.add(jakarta.enterprise.inject.Default.Literal.INSTANCE);
            this.qualifiers.add(Any.Literal.INSTANCE);
            this.stereotypes = Collections.emptySet();
        }

        @Override
        public Class<?> getBeanClass() {
            return beanClass;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return Collections.unmodifiableSet(qualifiers);
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return ApplicationScoped.class;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return stereotypes;
        }

        @Override
        public Set<Type> getTypes() {
            return Collections.unmodifiableSet(types);
        }

        @Override
        public boolean isAlternative() {
            return false;
        }

        @Override
        public T create(CreationalContext<T> creationalContext) {
            return extension;
        }

        @Override
        public void destroy(T instance, CreationalContext<T> creationalContext) {
            // Extension lifecycle is container-managed.
        }

        @Override
        public String getId() {
            return "extension:" + beanClass.getName();
        }

        private static Set<Type> collectTypes(Class<?> clazz) {
            Set<Type> collected = new HashSet<>();
            Class<?> current = clazz;
            while (current != null && current != Object.class) {
                collected.add(current);
                collected.addAll(Arrays.asList(current.getInterfaces()));
                current = current.getSuperclass();
            }
            collected.add(Object.class);
            return collected;
        }
    }

    private static final class BuiltInEventBean implements Bean<Event<?>> {
        private final Type eventPayloadType;
        private final Set<Type> types;
        private final Set<Annotation> qualifiers;
        private final BeanManagerImpl beanManager;

        private BuiltInEventBean(Type eventPayloadType,
                                 Type requestedBeanType,
                                 Set<Annotation> qualifiers,
                                 BeanManagerImpl beanManager) {
            this.eventPayloadType = eventPayloadType;
            this.beanManager = beanManager;

            Set<Type> resolvedTypes = new LinkedHashSet<>();
            resolvedTypes.add(Event.class);
            resolvedTypes.add(Object.class);
            resolvedTypes.add(requestedBeanType);
            this.types = Collections.unmodifiableSet(resolvedTypes);

            Set<Annotation> resolvedQualifiers = new LinkedHashSet<>();
            if (qualifiers != null) {
                resolvedQualifiers.addAll(qualifiers);
            }
            resolvedQualifiers.add(Any.Literal.INSTANCE);
            this.qualifiers = Collections.unmodifiableSet(resolvedQualifiers);
        }

        @Override
        public Class<?> getBeanClass() {
            return Event.class;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public Event<?> create(CreationalContext<Event<?>> context) {
            return beanManager.observerSupport.createEvent(eventPayloadType, new LinkedHashSet<>(qualifiers));
        }

        @Override
        public void destroy(Event<?> instance, CreationalContext<Event<?>> context) {
            if (context != null) {
                context.release();
            }
        }

        @Override
        public Set<Type> getTypes() {
            return types;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return qualifiers;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return Dependent.class;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public boolean isAlternative() {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof BuiltInEventBean)) {
                return false;
            }
            BuiltInEventBean other = (BuiltInEventBean) obj;
            return beanManager == other.beanManager;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(beanManager) + BuiltInEventBean.class.hashCode();
        }
    }

    private static final class BuiltInInstanceBean implements Bean<Instance<?>> {
        private final Type requestedBeanType;
        private final Set<Type> types;
        private final Set<Annotation> qualifiers;
        private final Set<Annotation> resolutionQualifiers;
        private final BeanManagerImpl beanManager;

        private BuiltInInstanceBean(Type requestedBeanType,
                                    Set<Annotation> qualifiers,
                                    Set<Annotation> resolutionQualifiers,
                                    BeanManagerImpl beanManager) {
            this.requestedBeanType = requestedBeanType;
            this.beanManager = beanManager;

            Set<Type> resolvedTypes = new LinkedHashSet<>();
            resolvedTypes.add(Instance.class);
            resolvedTypes.add(Object.class);
            resolvedTypes.add(requestedBeanType);
            this.types = Collections.unmodifiableSet(resolvedTypes);

            Set<Annotation> resolvedQualifiers = new LinkedHashSet<>();
            if (qualifiers != null) {
                resolvedQualifiers.addAll(qualifiers);
            }
            resolvedQualifiers.add(Any.Literal.INSTANCE);
            this.qualifiers = Collections.unmodifiableSet(resolvedQualifiers);

            Set<Annotation> requestedResolutionQualifiers = new LinkedHashSet<>();
            if (resolutionQualifiers != null) {
                requestedResolutionQualifiers.addAll(resolutionQualifiers);
            }
            this.resolutionQualifiers = Collections.unmodifiableSet(requestedResolutionQualifiers);
        }

        @Override
        public Class<?> getBeanClass() {
            return Instance.class;
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }

        @Override
        public Instance<?> create(CreationalContext<Instance<?>> context) {
            Annotation[] qualifierArray = resolutionQualifiers.toArray(new Annotation[0]);
            Object resolved = beanManager.beanResolver.resolve(requestedBeanType, qualifierArray);
            if (!(resolved instanceof Instance<?>)) {
                throw new DefinitionException(
                        "Built-in Instance lookup did not resolve Instance for type " + requestedBeanType);
            }
            return (Instance<?>) resolved;
        }

        @Override
        public void destroy(Instance<?> instance, CreationalContext<Instance<?>> context) {
            if (instance != null) {
                if (instance instanceof InstanceImpl<?>) {
                    ((InstanceImpl<?>) instance).destroyTrackedDependentInstances();
                } else {
                    invokeCloseIfPresent(instance);
                }
            }
            if (context != null) {
                context.release();
            }
        }

        @Override
        public Set<Type> getTypes() {
            return types;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return qualifiers;
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return Dependent.class;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Set<Class<? extends Annotation>> getStereotypes() {
            return Collections.emptySet();
        }

        @Override
        public boolean isAlternative() {
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof BuiltInInstanceBean)) {
                return false;
            }
            BuiltInInstanceBean other = (BuiltInInstanceBean) obj;
            return beanManager == other.beanManager;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(beanManager) + BuiltInInstanceBean.class.hashCode();
        }

        private void invokeCloseIfPresent(Instance<?> instance) {
            try {
                Method closeMethod = instance.getClass().getMethod("close");
                closeMethod.invoke(instance);
            } catch (NoSuchMethodException ignored) {
                // Best-effort cleanup for unknown Instance implementations.
            } catch (Exception ignored) {
                // Destroy should not fail teardown paths for built-in handles.
            }
        }
    }

    // ==================== Metadata Extraction Helper Methods ====================

    /**
     * Extracts the bean name from an Annotated element.
     * Returns null if no bean name is declared.
     */
    private String extractName(Annotated annotated) {
        if (annotated instanceof AnnotatedType<?>) {
            return extractNameFromType((AnnotatedType<?>) annotated);
        }
        if (annotated instanceof AnnotatedMember<?>) {
            return extractNameFromMember((AnnotatedMember<?>) annotated);
        }
        return null;
    }

    private String extractNameFromType(AnnotatedType<?> type) {
        Annotation named = findNamedAnnotation(type.getAnnotations());
        if (named != null) {
            return defaultedTypeName(readNamedValue(named), type.getJavaClass());
        }

        Set<Class<? extends Annotation>> visited = new HashSet<>();
        for (Class<? extends Annotation> stereotype : extractStereotypesFromAnnotated(type)) {
            String fromStereotype = extractNameFromStereotype(stereotype, type.getJavaClass(), visited);
            if (fromStereotype != null) {
                return fromStereotype;
            }
        }
        return null;
    }

    private String extractNameFromMember(AnnotatedMember<?> member) {
        Annotation named = findNamedAnnotation(member.getAnnotations());
        if (named == null) {
            return null;
        }

        String value = readNamedValue(named);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }

        Member javaMember = member.getJavaMember();
        if (javaMember instanceof Field) {
            return ((Field) javaMember).getName();
        }
        if (javaMember instanceof Method) {
            String methodName = ((Method) javaMember).getName();
            if (methodName.startsWith("get") && methodName.length() > 3) {
                return decapitalize(methodName.substring(3));
            }
            return methodName;
        }
        return null;
    }

    private String extractNameFromStereotype(Class<? extends Annotation> stereotype,
                                             Class<?> beanClass,
                                             Set<Class<? extends Annotation>> visited) {
        if (stereotype == null || !visited.add(stereotype)) {
            return null;
        }

        Set<Annotation> registeredDefinition = knowledgeBase.getStereotypeDefinition(stereotype);
        if (registeredDefinition != null && !registeredDefinition.isEmpty()) {
            Annotation named = findNamedAnnotation(registeredDefinition);
            if (named != null) {
                return defaultedTypeName(readNamedValue(named), beanClass);
            }
        }

        Annotation named = findNamedAnnotation(Arrays.asList(stereotype.getAnnotations()));
        if (named != null) {
            return defaultedTypeName(readNamedValue(named), beanClass);
        }

        for (Annotation meta : stereotype.getAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (!isStereotype(metaType)) {
                continue;
            }
            String nested = extractNameFromStereotype(metaType, beanClass, visited);
            if (nested != null) {
                return nested;
            }
        }

        return null;
    }

    private String defaultedTypeName(String rawName, Class<?> beanClass) {
        if (rawName != null && !rawName.trim().isEmpty()) {
            return rawName.trim();
        }
        return beanClass != null ? decapitalize(beanClass.getSimpleName()) : null;
    }

    /**
     * Extracts scope from an Annotated element.
     * Returns @Dependent if no scope is present.
     */
    private Class<? extends Annotation> extractScopeFromAnnotated(Annotated annotated) {
        Class<? extends Annotation> directScope = null;
        for (Annotation annotation : annotated.getAnnotations()) {
            if (!isScopeAnnotationType(annotation.annotationType())) {
                continue;
            }
            Class<? extends Annotation> normalized = normalizeSingletonToApplicationScoped(annotation.annotationType());
            if (directScope == null) {
                directScope = normalized;
            } else if (!directScope.equals(normalized)) {
                throw new IllegalArgumentException("Definition error: multiple scope annotations declared on " +
                        describeAnnotatedElement(annotated) + " (" +
                        directScope.getName() + ", " + normalized.getName() + ")");
            }
        }
        if (directScope != null) {
            return directScope;
        }

        Class<? extends Annotation> stereotypeScope = null;
        for (Class<? extends Annotation> stereotype : extractStereotypesFromAnnotated(annotated)) {
            Class<? extends Annotation> inheritedScope =
                    extractScopeFromStereotype(stereotype, new HashSet<>());
            if (inheritedScope == null) {
                continue;
            }
            if (stereotypeScope == null) {
                stereotypeScope = inheritedScope;
            } else if (!stereotypeScope.equals(inheritedScope)) {
                throw new IllegalArgumentException("Definition error: conflicting scopes inherited from stereotypes on " +
                        describeAnnotatedElement(annotated) + " (" +
                        stereotypeScope.getName() + ", " + inheritedScope.getName() + ")");
            }
        }
        return stereotypeScope != null ? stereotypeScope : Dependent.class;
    }

    private Class<? extends Annotation> extractScopeFromStereotype(Class<? extends Annotation> stereotype,
                                                                   Set<Class<? extends Annotation>> visited) {
        if (stereotype == null || !visited.add(stereotype)) {
            return null;
        }

        Class<? extends Annotation> discovered = null;

        Set<Annotation> registeredDefinition = knowledgeBase.getStereotypeDefinition(stereotype);
        if (registeredDefinition != null && !registeredDefinition.isEmpty()) {
            for (Annotation annotation : registeredDefinition) {
                if (annotation == null || !isScopeAnnotationType(annotation.annotationType())) {
                    continue;
                }
                Class<? extends Annotation> normalized = normalizeSingletonToApplicationScoped(annotation.annotationType());
                if (discovered == null) {
                    discovered = normalized;
                } else if (!discovered.equals(normalized)) {
                    throw new IllegalArgumentException("Definition error: conflicting scopes declared by stereotype " +
                            stereotype.getName() + " (" + discovered.getName() + ", " + normalized.getName() + ")");
                }
            }
        }

        for (Annotation meta : stereotype.getAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (isScopeAnnotationType(metaType)) {
                Class<? extends Annotation> normalized = normalizeSingletonToApplicationScoped(metaType);
                if (discovered == null) {
                    discovered = normalized;
                } else if (!discovered.equals(normalized)) {
                    throw new IllegalArgumentException("Definition error: conflicting scopes declared by stereotype " +
                            stereotype.getName() + " (" + discovered.getName() + ", " + normalized.getName() + ")");
                }
                continue;
            }
            if (!isStereotype(metaType)) {
                continue;
            }
            Class<? extends Annotation> nested = extractScopeFromStereotype(metaType, visited);
            if (nested == null) {
                continue;
            }
            if (discovered == null) {
                discovered = nested;
            } else if (!discovered.equals(nested)) {
                throw new IllegalArgumentException("Definition error: conflicting scopes inherited from stereotype chain " +
                        stereotype.getName() + " (" + discovered.getName() + ", " + nested.getName() + ")");
            }
        }

        return discovered;
    }

    /**
     * Extracts stereotypes from an Annotated element.
     */
    private Set<Class<? extends Annotation>> extractStereotypesFromAnnotated(Annotated annotated) {
        Set<Class<? extends Annotation>> stereotypes = new HashSet<>();
        for (Annotation ann : annotated.getAnnotations()) {
            if (isStereotype(ann.annotationType())) {
                stereotypes.add(ann.annotationType());
            }
        }
        return stereotypes;
    }

    private Set<Annotation> extractBeanQualifiers(Annotated annotated) {
        Set<Annotation> qualifiers = new LinkedHashSet<>(
                extractQualifierAnnotations(annotated.getAnnotations().toArray(new Annotation[0])));
        for (Class<? extends Annotation> stereotype : extractStereotypesFromAnnotated(annotated)) {
            qualifiers.addAll(extractQualifiersFromStereotype(stereotype, new HashSet<>()));
        }
        return qualifiers;
    }

    private Set<Annotation> extractQualifiersFromStereotype(Class<? extends Annotation> stereotype,
                                                            Set<Class<? extends Annotation>> visited) {
        if (stereotype == null || !visited.add(stereotype)) {
            return Collections.emptySet();
        }

        Set<Annotation> qualifiers = new LinkedHashSet<>();

        Set<Annotation> registeredDefinition = knowledgeBase.getStereotypeDefinition(stereotype);
        if (registeredDefinition != null && !registeredDefinition.isEmpty()) {
            for (Annotation annotation : registeredDefinition) {
                if (annotation == null) {
                    continue;
                }
                Class<? extends Annotation> annotationType = annotation.annotationType();
                if (isQualifier(annotationType) && !hasNamedAnnotation(annotationType)) {
                    qualifiers.add(annotation);
                }
            }
        }

        for (Annotation annotation : stereotype.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (isQualifier(annotationType) && !hasNamedAnnotation(annotationType)) {
                qualifiers.add(annotation);
            }
            if (!isStereotype(annotationType)) {
                continue;
            }
            qualifiers.addAll(extractQualifiersFromStereotype(annotationType, visited));
        }

        return qualifiers;
    }

    private Set<Type> extractTypesFromAnnotatedType(AnnotatedType<?> type) {
        Set<Type> unrestrictedTypes = new LinkedHashSet<>(type.getTypeClosure());
        if (unrestrictedTypes.isEmpty()) {
            unrestrictedTypes.addAll(extractTypesFromClass(type.getJavaClass()));
        }

        Annotation typedAnnotation = findTypedAnnotation(type.getAnnotations());
        Set<Type> resultingTypes = typedAnnotation != null
                ? applyTypedRestriction(type.getJavaClass(), typedAnnotation, unrestrictedTypes)
                : unrestrictedTypes;

        return keepLegalBeanTypes(resultingTypes);
    }

    /**
     * Extracts bean types from an AnnotatedMember (producer field or method).
     */
    private Set<Type> extractTypesFromMember(AnnotatedMember<?> member) {
        Set<Type> unrestrictedTypes = extractTypesFromType(member.getBaseType());
        Annotation typedAnnotation = findTypedAnnotation(member.getAnnotations());
        Set<Type> resultingTypes = unrestrictedTypes;
        if (typedAnnotation != null) {
            Class<?> memberRawType = getRawType(member.getBaseType());
            resultingTypes = applyTypedRestriction(memberRawType, typedAnnotation, unrestrictedTypes);
        }
        return keepLegalBeanTypes(resultingTypes);
    }

    private Annotation findTypedAnnotation(Collection<Annotation> annotations) {
        if (annotations == null) {
            return null;
        }
        for (Annotation annotation : annotations) {
            if (annotation != null && TYPED.matches(annotation.annotationType())) {
                return annotation;
            }
        }
        return null;
    }

    private Set<Type> applyTypedRestriction(Class<?> beanClass,
                                            Annotation typedAnnotation,
                                            Set<Type> unrestrictedTypes) {
        if (beanClass == null) {
            throw new IllegalArgumentException("Definition error: @Typed may only be used when raw bean type can be determined");
        }

        Set<Type> types = new LinkedHashSet<>();

        try {
            Method valueMethod = typedAnnotation.annotationType().getMethod("value");
            Object rawValue = valueMethod.invoke(typedAnnotation);
            if (!(rawValue instanceof Class<?>[])) {
                throw new IllegalArgumentException("Definition error: invalid @Typed declaration on " + beanClass.getName());
            }

            Class<?>[] typedClasses = (Class<?>[]) rawValue;
            if (typedClasses.length == 0) {
                types.add(Object.class);
                return types;
            }

            for (Class<?> typedClass : typedClasses) {
                if (typedClass == null || !typedClass.isAssignableFrom(beanClass)) {
                    throw new IllegalArgumentException("Definition error: @Typed specifies type " +
                            (typedClass == null ? "<null>" : typedClass.getName()) +
                            " which is not assignable from bean class " + beanClass.getName());
                }
                boolean matched = false;
                for (Type unrestricted : unrestrictedTypes) {
                    Class<?> rawType = getRawType(unrestricted);
                    if (typedClass.equals(rawType)) {
                        types.add(unrestricted);
                        matched = true;
                    }
                }
                if (!matched) {
                    types.add(typedClass);
                }
            }
            types.add(Object.class);
            return types;
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Definition error: failed to read @Typed values for " +
                    beanClass.getName(), e);
        }
    }

    private Set<Type> keepLegalBeanTypes(Set<Type> candidateTypes) {
        Set<Type> legalTypes = new LinkedHashSet<>();
        if (candidateTypes == null) {
            legalTypes.add(Object.class);
            return legalTypes;
        }
        for (Type candidate : candidateTypes) {
            if (isLegalBeanTypeForAttributes(candidate)) {
                legalTypes.add(candidate);
            }
        }
        if (legalTypes.isEmpty()) {
            legalTypes.add(Object.class);
        }
        return legalTypes;
    }

    private boolean isLegalBeanTypeForAttributes(Type type) {
        if (type instanceof TypeVariable || type instanceof WildcardType) {
            return false;
        }
        if (type instanceof GenericArrayType) {
            return isLegalBeanTypeForAttributes(((GenericArrayType) type).getGenericComponentType());
        }
        if (type instanceof Class<?>) {
            Class<?> klass = (Class<?>) type;
            return !klass.isArray() || isLegalBeanTypeForAttributes(klass.getComponentType());
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                if (argument instanceof WildcardType) {
                    return false;
                }
                if (argument instanceof TypeVariable) {
                    continue;
                }
                if (!isLegalBeanTypeForAttributes(argument)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean isAlternativeDeclared(Annotated annotated) {
        if (annotated == null) {
            return false;
        }

        for (Annotation annotation : annotated.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (hasAlternativeAnnotation(annotationType)) {
                return true;
            }
            if (isStereotype(annotationType) &&
                    stereotypeDeclaresAlternative(annotationType, new HashSet<>())) {
                return true;
            }
        }
        return false;
    }

    private boolean stereotypeDeclaresAlternative(Class<? extends Annotation> stereotype,
                                                  Set<Class<? extends Annotation>> visited) {
        if (stereotype == null || !visited.add(stereotype)) {
            return false;
        }

        if (hasAlternativeAnnotation(stereotype)) {
            return true;
        }

        Set<Annotation> registeredDefinition = knowledgeBase.getStereotypeDefinition(stereotype);
        if (registeredDefinition != null && !registeredDefinition.isEmpty()) {
            for (Annotation annotation : registeredDefinition) {
                if (annotation != null && hasAlternativeAnnotation(annotation.annotationType())) {
                    return true;
                }
            }
        }

        for (Annotation meta : stereotype.getAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (hasAlternativeAnnotation(metaType)) {
                return true;
            }
            if (isStereotype(metaType) && stereotypeDeclaresAlternative(metaType, visited)) {
                return true;
            }
        }
        return false;
    }

    private Annotation findNamedAnnotation(Collection<Annotation> annotations) {
        if (annotations == null) {
            return null;
        }
        for (Annotation annotation : annotations) {
            if (annotation != null && hasNamedAnnotation(annotation.annotationType())) {
                return annotation;
            }
        }
        return null;
    }

    private String readNamedValue(Annotation namedAnnotation) {
        if (namedAnnotation == null) {
            return "";
        }
        try {
            Method valueMethod = namedAnnotation.annotationType().getMethod("value");
            Object rawValue = valueMethod.invoke(namedAnnotation);
            return rawValue == null ? "" : rawValue.toString();
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private void validateDeclaredBeanAttributes(Annotated annotated) {
        int scopeCount = 0;
        Class<? extends Annotation> firstScope = null;
        Class<? extends Annotation> secondScope = null;

        for (Annotation annotation : annotated.getAnnotations()) {
            if (!isScopeAnnotationType(annotation.annotationType())) {
                continue;
            }
            scopeCount++;
            if (firstScope == null) {
                firstScope = annotation.annotationType();
            } else if (secondScope == null && !firstScope.equals(annotation.annotationType())) {
                secondScope = annotation.annotationType();
            }
        }

        if (secondScope != null || scopeCount > 1) {
            throw new IllegalArgumentException("Definition error: multiple scope annotations declared on " +
                    describeAnnotatedElement(annotated) + " (" +
                    (firstScope != null ? firstScope.getName() : "unknown") + ", " +
                    (secondScope != null ? secondScope.getName() : "unknown") + ")");
        }
    }

    private String describeAnnotatedElement(Annotated annotated) {
        if (annotated instanceof AnnotatedType) {
            return ((AnnotatedType<?>) annotated).getJavaClass().getName();
        }
        if (annotated instanceof AnnotatedMember) {
            Member member = ((AnnotatedMember<?>) annotated).getJavaMember();
            return member.getDeclaringClass().getName() + "#" + member.getName();
        }
        return annotated.toString();
    }

    /**
     * Decapitalizes a string following CDI conventions.
     */
    private String decapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Checks if an annotation type is a scope annotation.
     */
    private boolean isScopeAnnotationType(Class<? extends Annotation> annotationType) {
        return hasScopeAnnotation(annotationType) ||
               hasNormalScopeAnnotation(annotationType) ||
               knowledgeBase.isRegisteredScope(annotationType);
    }

    // ==================== Container Internal Methods ====================

    /**
     * Returns the ContextManager for internal container use.
     * <p>
     * This method is used by extension SPI implementations (e.g., AfterBeanDiscovery)
     * to register custom contexts programmatically.
     * <p>
     * <b>Note:</b> This is an internal API and should not be used by application code.
     * Applications should register custom contexts via portable extensions using
     * {@link AfterBeanDiscovery#addContext(Context)}.
     *
     * @return the context manager
     */
    public ContextManager getContextManager() {
        return contextManager;
    }

    /**
     * Marks that AfterBeanDiscovery was fired.
     */
    public void markAfterBeanDiscoveryFired() {
        this.afterBeanDiscoveryFired = true;
    }

    /**
     * Marks that AfterDeploymentValidation was fired.
     */
    public void markAfterDeploymentValidationFired() {
        this.afterDeploymentValidationFired = true;
    }

    private void requireAfterBeanDiscovery(String operationName) {
        if (!afterBeanDiscoveryFired) {
            throw new IllegalStateException(operationName +
                    " cannot be called before the AfterBeanDiscovery event is fired");
        }
    }

    private void requireAfterDeploymentValidation(String operationName) {
        if (!afterDeploymentValidationFired) {
            throw new IllegalStateException(operationName +
                    " cannot be called before the AfterDeploymentValidation event is fired");
        }
    }
}
