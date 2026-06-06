package com.threeamigos.common.util.implementations.injection.knowledgebase;

import com.threeamigos.common.util.implementations.injection.annotations.DynamicAnnotationRegistry;

import com.threeamigos.common.util.implementations.injection.annotations.AlternativesHelper;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlOrderingHelper;
import com.threeamigos.common.util.implementations.injection.events.ObserverMethodInfo;
import com.threeamigos.common.util.implementations.injection.interceptors.InterceptorsHelper;
import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.Phase;
import com.threeamigos.common.util.implementations.injection.annotations.AnnotationsHelper;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.inject.spi.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.*;

import static com.threeamigos.common.util.implementations.injection.annotations.AlternativesHelper.isAlternativeViaAnnotationOrStereotype;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotatedMetadataHelper.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationExtractors.getPriorityValue;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.hasBuiltInNormalScopeAnnotation;
import static com.threeamigos.common.util.implementations.injection.spi.SPIUtils.extractPrioritizedInterfacePriority;

public class KnowledgeBase {

    public final Set<String> processedSyntheticAnnotatedTypeIds = new HashSet<>();

    public final Set<Class<?>> syntheticAnnotatedTypeClasses = new HashSet<>();

    /**
     * Classes explicitly supplied via addDiscoveredClass(...).
     * These classes apply strict archive-mode filtering already at ProcessAnnotatedType time.
     */
    public final Set<Class<?>> explicitlyAddedDiscoveredClasses = new HashSet<>();

    public final Map<String, AnnotatedType<?>> additionalAnnotatedTypesForDiscoveredClasses = new LinkedHashMap<>();

    /**
     * Optional forced bean archive mode used during validation/discovery processing.
     * When set, this mode overrides detected archive mode for all classes that are
     * already discovered and present in the KnowledgeBase.
     *
     * <p>Important limitation: this does not alter scanner-time archive detection.
     * If an archive is skipped by the scanner because it is detected as
     * bean-discovery-mode="none", those classes are never added and therefore cannot
     * be affected by this override.
     */
    private BeanArchiveMode forcedBeanArchiveMode;

    private final MessageHandler messageHandler;
    private final KnowledgeBaseDiscoveryStore discoveryStore = new KnowledgeBaseDiscoveryStore();
    private final KnowledgeBaseBeanRegistryStore beanRegistryStore = new KnowledgeBaseBeanRegistryStore();
    private final KnowledgeBaseExtensionRegistrationStore extensionRegistrationStore =
            new KnowledgeBaseExtensionRegistrationStore();
    private final KnowledgeBaseEnablementStore enablementStore = new KnowledgeBaseEnablementStore();
    private final KnowledgeBaseProblemCollector problemCollector = new KnowledgeBaseProblemCollector();
    private final BeansXmlOrderingHelper beansXmlOrderingHelper;
    private final InterceptorsHelper interceptorsHelper;

    public KnowledgeBase(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
        this.beansXmlOrderingHelper = new BeansXmlOrderingHelper(discoveryStore.getBeansXmlConfigurations());
        this.interceptorsHelper = new InterceptorsHelper(
                beanRegistryStore.getInterceptorInfos(),
                this::getApplicationInterceptorOrder,
                this::getInterceptorBeansXmlOrder);
    }

    public void exclude(Class<?>... excludedClasses) {
        discoveryStore.exclude(excludedClasses);
    }

    public Collection<Class<?>> getExcludedClasses() {
        return discoveryStore.getExcludedClasses();
    }

    public void add(Class<?> clazz) {
        discoveryStore.addDiscoveredClass(clazz);
    }

    public void add(Class<?> clazz, BeanArchiveMode mode) {
        if (!discoveryStore.isExcluded(clazz) && !discoveryStore.containsClass(clazz)) {
            discoveryStore.addDiscoveredClass(clazz);
            if (mode != null) {
                discoveryStore.setBeanArchiveMode(clazz, mode);
            }
        } else if (!discoveryStore.isExcluded(clazz) && mode != null) {
            discoveryStore.mergeBeanArchiveMode(clazz, mode);
        }
    }

    /**
     * Adds a class programmatically, bypassing discovery exclusions.
     *
     * <p>This is used for types explicitly registered via lifecycle events such as
     * {@code BeforeBeanDiscovery.addAnnotatedType()} and
     * {@code AfterTypeDiscovery.addAnnotatedType()}.
     */
    public void addProgrammatic(Class<?> clazz, BeanArchiveMode mode) {
        if (clazz == null) {
            return;
        }
        discoveryStore.addProgrammaticClass(clazz);
        if (mode != null) {
            discoveryStore.setBeanArchiveMode(clazz, mode);
        }
    }

    public Collection<Class<?>> getClasses() {
        return discoveryStore.getClasses();
    }

    public void removeDiscoveredClass(Class<?> clazz) {
        if (clazz == null) {
            return;
        }
        discoveryStore.removeDiscoveredClass(clazz);
    }

    public boolean isImplicitBeanArchiveScanningDisabled() {
        return !discoveryStore.isImplicitBeanArchiveScanningEnabled();
    }

    public void setImplicitBeanArchiveScanningEnabled(boolean implicitBeanArchiveScanningEnabled) {
        discoveryStore.setImplicitBeanArchiveScanningEnabled(implicitBeanArchiveScanningEnabled);
    }

    public BeanArchiveMode getForcedBeanArchiveMode() {
        return forcedBeanArchiveMode;
    }

    public void setForcedBeanArchiveMode(BeanArchiveMode forcedBeanArchiveMode) {
        this.forcedBeanArchiveMode = forcedBeanArchiveMode;
    }

    public BeanArchiveMode getBeanArchiveMode(Class<?> clazz) {
        return discoveryStore.getBeanArchiveModeOrDefault(clazz);
    }

    public void setBeanArchiveMode(Class<?> clazz, BeanArchiveMode mode) {
        if (clazz == null || mode == null) {
            return;
        }
        discoveryStore.setBeanArchiveMode(clazz, mode);
    }

    public void setAnnotatedTypeOverride(Class<?> clazz, AnnotatedType<?> annotatedType) {
        if (clazz != null && annotatedType != null) {
            discoveryStore.setAnnotatedTypeOverride(clazz, annotatedType);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> AnnotatedType<T> getAnnotatedTypeOverride(Class<T> clazz) {
        return (AnnotatedType<T>) discoveryStore.getAnnotatedTypeOverride(clazz);
    }

    public void addBean(Bean<?> bean) {
        beanRegistryStore.addBean(bean);
    }

    public Collection<Bean<?>> getBeans() {
        return beanRegistryStore.getBeans();
    }

    public void markIgnoreFinalMethods(Bean<?> bean) {
        if (bean != null) {
            beanRegistryStore.markIgnoreFinalMethods(bean);
        }
    }

    public boolean shouldIgnoreFinalMethods(Bean<?> bean) {
        return bean != null && beanRegistryStore.shouldIgnoreFinalMethods(bean);
    }

    public <T> void addConstructor(Class<T> clazz, Constructor<T> constructor) {
        discoveryStore.setConstructor(clazz, constructor);
    }

    @SuppressWarnings("unchecked")
    public <T> Constructor<T> getConstructor(Class<T> clazz) {
        return (Constructor<T>)discoveryStore.getConstructor(clazz);
    }

    public void addWarning(String warning) {
        problemCollector.addWarning(warning);
    }

    public void addWarning(Phase phase, String warning) {
        problemCollector.addWarning("[" + phase.getDescription() + "] " + warning);
    }

    public List<String> getWarnings() {
        return problemCollector.getWarnings();
    }

    public void addError(String error) {
        problemCollector.addError(error);
    }

    public void addError(@Nonnull Phase phase, @Nonnull String error, Throwable t) {
        if (t != null) {
            problemCollector.addDefinitionError("[" + phase.getDescription() + "] " + error + ": " + t.getMessage());
        } else {
            problemCollector.addDefinitionError("[" + phase.getDescription() + "] " + error);
        }
    }

    public List<String> getErrors() {
        return problemCollector.getErrors();
    }

    public void addDefinitionError(String error) {
        problemCollector.addDefinitionError(error);
    }

    public void addDefinitionError(@Nonnull Phase phase, @Nonnull String error, Throwable t) {
        if (t != null) {
            problemCollector.addDefinitionError("[" + phase.getDescription() + "] " + error + ": " + t.getMessage());
        } else {
            problemCollector.addDefinitionError("[" + phase.getDescription() + "] " + error);
        }
    }

    public void addDefinitionError(String error, Throwable t) {
        if (t != null) {
            problemCollector.addDefinitionError(error + ": " + t.getMessage());
        } else {
            problemCollector.addDefinitionError(error);
        }
    }

    public List<String> getDefinitionErrors() {
        return problemCollector.getDefinitionErrors();
    }

    public void addDeploymentError(@Nonnull Phase phase, @Nonnull String error, Throwable t) {
        if (t != null) {
            problemCollector.addDefinitionError("[" + phase.getDescription() + "] " + error + ": " + t.getMessage());
        } else {
            problemCollector.addDefinitionError("[" + phase.getDescription() + "] " + error);
        }
    }

    public void addInjectionError(String error) {
        problemCollector.addInjectionError(error);
    }

    public List<String> getInjectionErrors() {
        return problemCollector.getInjectionErrors();
    }

    public void addIllegalProductError(String error) {
        problemCollector.addIllegalProductError(error);
    }

    public List<String> getIllegalProductErrors() {
        return problemCollector.getIllegalProductErrors();
    }

    /**
     * Checks if there are any critical errors that would prevent application startup.
     * This includes definition errors, injection errors, and general errors.
     *
     * @return true if there are any errors that should stop the application
     */
    public boolean hasErrors() {
        return problemCollector.hasErrors();
    }

    /**
     * Returns all beans that have validation errors.
     * These beans were discovered but failed validation.
     * The application should only fail if these beans are actually needed for injection.
     *
     * @return collection of beans with validation errors
     */
    public Collection<Bean<?>> getBeansWithValidationErrors() {
        List<Bean<?>> beansWithErrors = new ArrayList<>();
        for (Bean<?> bean : beanRegistryStore.getBeans()) {
            if (bean instanceof BeanImpl && ((BeanImpl<?>) bean).hasValidationErrors()) {
                beansWithErrors.add(bean);
            }
        }
        return beansWithErrors;
    }

    /**
     * Returns all beans that are valid (no validation errors).
     * These beans are candidates for dependency injection.
     *
     * @return collection of valid beans
     */
    public Collection<Bean<?>> getValidBeans() {
        List<Bean<?>> validBeans = new ArrayList<>();
        for (Bean<?> bean : beanRegistryStore.getBeans()) {
            if (!(bean instanceof BeanImpl) || !((BeanImpl<?>) bean).hasValidationErrors()) {
                validBeans.add(bean);
            }
        }
        return validBeans;
    }

    // Producer/Disposer methods

    /**
     * Adds a ProducerBean to the knowledge base.
     * ProducerBeans are also added to the general beans' collection.
     */
    public void addProducerBean(ProducerBean<?> producerBean) {
        beanRegistryStore.addProducerBean(producerBean);
        beanRegistryStore.addBean(producerBean); // Also add to general bean collection
    }

    /**
     * Returns all producer beans (convenience method).
     */
    public Collection<ProducerBean<?>> getProducerBeans() {
        return beanRegistryStore.getProducerBeans();
    }

    // Interceptor/Decorator methods (legacy)

    public void addInterceptor(Class<?> interceptorClass) {
        beanRegistryStore.addInterceptorClass(interceptorClass);
    }

    public Collection<Class<?>> getInterceptors() {
        return beanRegistryStore.getInterceptorClasses();
    }

    public void addDecorator(Class<?> decoratorClass) {
        beanRegistryStore.addDecoratorClass(decoratorClass);
    }

    public Collection<Class<?>> getDecorators() {
        return beanRegistryStore.getDecoratorClasses();
    }

    // Enhanced Interceptor/Decorator/Observer methods

    /**
     * Adds fully validated interceptor metadata to the knowledge base.
     * This should be called after validating the interceptor class.
     *
     * @param interceptorInfo the validated interceptor metadata
     */
    public void addInterceptorInfo(InterceptorInfo interceptorInfo) {
        beanRegistryStore.addInterceptorInfo(interceptorInfo);
    }

    /**
     * Returns all validated interceptors with full metadata.
     * Use this for building interceptor chains.
     *
     * @return collection of interceptor metadata
     */
    public Collection<InterceptorInfo> getInterceptorInfos() {
        return beanRegistryStore.getInterceptorInfos();
    }

    /**
     * Adds fully validated decorator metadata to the knowledge base.
     * This should be called after validating the decorator class.
     *
     * @param decoratorInfo the validated decorator metadata
     */
    public void addDecoratorInfo(DecoratorInfo decoratorInfo) {
        beanRegistryStore.addDecoratorInfo(decoratorInfo);
    }

    /**
     * Returns all validated decorators with full metadata.
     * Use this for building decorator chains.
     *
     * @return collection of decorator metadata
     */
    public Collection<DecoratorInfo> getDecoratorInfos() {
        return beanRegistryStore.getDecoratorInfos();
    }

    /**
     * Returns the aggregate beans.xml decorator order index for a decorator class.
     *
     * <p>Decorators listed in any beans.xml are considered enabled, and their relative order
     * is determined by first-appearance across all beans.xml files (in scan order) and
     * by position within the list. Decorators not present return -1.</p>
     *
     * @param decoratorClass the decorator class
     * @return zero-based order, or -1 if not listed
     */
    public int getDecoratorBeansXmlOrder(Class<?> decoratorClass) {
        return beansXmlOrderingHelper.getDecoratorOrder(decoratorClass);
    }

    public int getInterceptorBeansXmlOrder(Class<?> interceptorClass) {
        return beansXmlOrderingHelper.getInterceptorOrder(interceptorClass);
    }

    /**
     * Adds fully validated observer method metadata to the knowledge base.
     * This should be called after validating the observer method.
     *
     * @param observerMethodInfo the validated observer method metadata
     */
    public void addObserverMethodInfo(ObserverMethodInfo observerMethodInfo) {
        beanRegistryStore.addObserverMethodInfo(observerMethodInfo);
    }

    /**
     * Returns all validated observer methods with full metadata.
     * Use this for event firing and observer invocation.
     *
     * @return collection of observer method metadata
     */
    public Collection<ObserverMethodInfo> getObserverMethodInfos() {
        return beanRegistryStore.getObserverMethodInfos();
    }

    public boolean isObserverMethodsDiscovered() {
        return beanRegistryStore.isObserverMethodsDiscovered();
    }

    public void setObserverMethodsDiscovered(boolean observerMethodsDiscovered) {
        beanRegistryStore.setObserverMethodsDiscovered(observerMethodsDiscovered);
    }

    // ============================================================
    // INTERCEPTOR QUERY METHODS
    // ============================================================

    /**
     * Queries interceptors by interceptor bindings and interception type, sorted by priority.
     *
     * <p>This is the primary method for resolving which interceptors should be applied to a target bean.
     * It performs the following:
     * <ul>
     *   <li>Filters interceptors that have matching interceptor bindings</li>
     *   <li>Filters interceptors that support the specified interception type</li>
     *   <li>Sorts by priority (lower priority value = higher precedence, executes first)</li>
     * </ul>
     *
     * <p><b>CDI 4.1 Interceptor Resolution Rules:</b>
     * <ul>
     *   <li>An interceptor matches if ALL of its bindings are present on the target</li>
     *   <li>The target may have additional bindings not present on the interceptor</li>
     *   <li>Priority determines invocation order (default is Integer.MAX_VALUE if not specified)</li>
     * </ul>
     *
     * @param interceptionType the type of interception (AROUND_INVOKE, AROUND_CONSTRUCT, POST_CONSTRUCT, PRE_DESTROY)
     * @param targetBindings the interceptor bindings present on the target bean/method
     * @return list of matching interceptors sorted by priority (ascending)
     */
    public List<InterceptorInfo> getInterceptorsByBindingsAndType(
            InterceptionType interceptionType,
            Set<Annotation> targetBindings) {
        return interceptorsHelper.getInterceptorsByBindingsAndType(interceptionType, targetBindings);
    }

    /**
     * Queries interceptors by a single interceptor binding annotation, sorted by priority.
     *
     * <p>Convenience method for when the target has only one interceptor binding.
     *
     * @param interceptionType the type of interception
     * @param binding the single interceptor binding annotation
     * @return list of matching interceptors sorted by priority
     */
    public List<InterceptorInfo> getInterceptorsByBindingAndType(
            InterceptionType interceptionType,
            Annotation binding) {
        return interceptorsHelper.getInterceptorsByBindingAndType(interceptionType, binding);
    }

    /**
     * Queries interceptors by interception type only (no binding filtering), sorted by priority.
     *
     * <p>Returns all interceptors that support the given interception type, regardless of bindings.
     * This is useful for diagnostic purposes or when you want to see all available interceptors.
     *
     * @param interceptionType the type of interception
     * @return list of interceptors that support this type, sorted by priority
     */
    public List<InterceptorInfo> getInterceptorsByType(
            InterceptionType interceptionType) {
        return interceptorsHelper.getInterceptorsByType(interceptionType);
    }

    /**
     * Queries interceptors by interceptor bindings only (no type filtering), sorted by priority.
     *
     * <p>Returns all interceptors that match the given bindings, regardless of what interception
     * types they support. Useful for seeing all interceptors that could potentially apply.
     *
     * @param targetBindings the interceptor bindings to match
     * @return list of matching interceptors sorted by priority
     */
    public List<InterceptorInfo> getInterceptorsByBindings(Set<Annotation> targetBindings) {
        return interceptorsHelper.getInterceptorsByBindings(targetBindings);
    }

    public void setApplicationInterceptorOrder(List<Class<?>> orderedInterceptors) {
        enablementStore.setApplicationInterceptorOrder(orderedInterceptors);
    }

    public int getApplicationInterceptorOrder(Class<?> interceptorClass) {
        return enablementStore.getApplicationInterceptorOrder(interceptorClass);
    }

    public void setApplicationAlternativeOrder(List<Class<?>> orderedAlternatives) {
        enablementStore.setApplicationAlternativeOrder(orderedAlternatives);
    }

    public int getApplicationAlternativeOrder(Class<?> alternativeClass) {
        return enablementStore.getApplicationAlternativeOrder(alternativeClass);
    }

    public boolean hasApplicationAlternativeSelection() {
        return enablementStore.hasApplicationAlternativeSelection();
    }

    public void setApplicationDecoratorOrder(List<Class<?>> orderedDecorators) {
        enablementStore.setApplicationDecoratorOrder(orderedDecorators);
    }

    public int getApplicationDecoratorOrder(Class<?> decoratorClass) {
        return enablementStore.getApplicationDecoratorOrder(decoratorClass);
    }

    public boolean hasApplicationDecoratorSelection() {
        return enablementStore.hasApplicationDecoratorSelection();
    }

    public boolean hasApplicationInterceptorSelection() {
        return enablementStore.hasApplicationInterceptorSelection();
    }

    public void setAfterTypeDiscoveryAlternativesCustomized(boolean customized) {
        enablementStore.setAfterTypeDiscoveryAlternativesCustomized(customized);
    }

    public boolean hasAfterTypeDiscoveryAlternativesCustomized() {
        return enablementStore.isAfterTypeDiscoveryAlternativesCustomized();
    }

    public void setAfterTypeDiscoveryInterceptorsCustomized(boolean customized) {
        enablementStore.setAfterTypeDiscoveryInterceptorsCustomized(customized);
    }

    public boolean hasAfterTypeDiscoveryInterceptorsCustomized() {
        return enablementStore.isAfterTypeDiscoveryInterceptorsCustomized();
    }

    public void setAfterTypeDiscoveryDecoratorsCustomized(boolean customized) {
        enablementStore.setAfterTypeDiscoveryDecoratorsCustomized(customized);
    }

    public boolean hasAfterTypeDiscoveryDecoratorsCustomized() {
        return enablementStore.isAfterTypeDiscoveryDecoratorsCustomized();
    }

    /**
     * Returns all interceptor bindings registered in the system.
     *
     * <p>This returns the unique set of all interceptor-binding annotation types
     * that are present on any registered interceptor.
     *
     * @return set of all interceptor binding annotation types
     */
    public Set<Class<? extends Annotation>> getAllInterceptorBindingTypes() {
        return interceptorsHelper.getAllInterceptorBindingTypes();
    }

    // === Programmatic Bean Registration (for InjectorImpl2) ===

    /**
     * Adds a programmatic bean binding for runtime bean registration.
     *
     * <p>This allows beans to be registered programmatically outside classpath scanning,
     * useful for testing, dynamic configuration, and third-party library integration.
     *
     * @param bean the bean implementation
     */
    public void addProgrammaticBean(Bean<?> bean) {
        beanRegistryStore.addBean(bean);
    }

    /**
     * Enables an alternative bean at runtime.
     *
     * <p>This activates an @Alternative bean programmatically, useful for feature flags
     * and runtime environment detection.
     *
     * @param alternativeClass the alternative bean class to enable
     */
    public void enableAlternative(Class<?> alternativeClass) {
        if (alternativeClass == null) {
            throw new IllegalArgumentException("alternativeClass cannot be null");
        }

        if (!isAlternativeViaAnnotationOrStereotype(alternativeClass)) {
            throw new IllegalArgumentException(
                    alternativeClass.getName() + " is not an @Alternative bean class (directly or via stereotype)");
        }

        enablementStore.enableAlternative(alternativeClass.getName());
        messageHandler.info("[KnowledgeBase] Programmatically enabled alternative: " + alternativeClass.getName());
    }

    public boolean isAlternativeEnabledProgrammatically(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        return enablementStore.isAlternativeEnabled(className);
    }

    /**
     * Registers a stereotype programmatically with its meta-annotations.
     *
     * <p>This is called by BeforeBeanDiscovery.addStereotype() to register stereotypes
     * that are not defined via @Stereotype annotation.
     *
     * @param stereotype the stereotype annotation class
     * @param stereotypeDef the meta-annotations that define the stereotype (scope, qualifiers, interceptor bindings, etc.)
     */
    public void addStereotype(Class<? extends Annotation> stereotype, Annotation... stereotypeDef) {
        if (stereotype == null) {
            throw new IllegalArgumentException("Stereotype cannot be null");
        }

        Set<Annotation> definitions = new HashSet<>();
        if (stereotypeDef != null) {
            definitions.addAll(Arrays.asList(stereotypeDef));
        }

        extensionRegistrationStore.registerStereotype(stereotype, definitions);
        DynamicAnnotationRegistry.registerDynamicStereotype(stereotype);

        messageHandler.info("[KnowledgeBase] Registered stereotype: " + stereotype.getSimpleName() +
                          " with meta-annotation(s) " + AnnotationsHelper.toList(stereotypeDef));
    }

    /**
     * Checks if a given annotation type is a registered stereotype.
     *
     * @param annotationType the annotation type to check
     * @return true if it's a programmatically registered stereotype
     */
    public boolean isRegisteredStereotype(Class<? extends Annotation> annotationType) {
        return extensionRegistrationStore.isStereotypeRegistered(annotationType);
    }

    /**
     * Gets the stereotype definition (meta-annotations) for a registered stereotype.
     *
     * @param stereotype the stereotype annotation class
     * @return set of meta-annotations, or null if not registered
     */
    public Set<Annotation> getStereotypeDefinition(Class<? extends Annotation> stereotype) {
        return extensionRegistrationStore.getStereotypeDefinition(stereotype);
    }

    /**
     * Gets all registered stereotypes.
     *
     * @return map of stereotype class to their definitions
     */
    public Map<Class<? extends Annotation>, Set<Annotation>> getRegisteredStereotypes() {
        return Collections.unmodifiableMap(extensionRegistrationStore.getRegisteredStereotypes());
    }

    /**
     * Registers a qualifier programmatically.
     *
     * <p>This is called by BeforeBeanDiscovery.addQualifier() to register qualifiers
     * that are not defined via @Qualifier annotation.
     *
     * @param qualifier the qualifier annotation class
     */
    public void addQualifier(Class<? extends Annotation> qualifier) {
        if (qualifier == null) {
            throw new IllegalArgumentException("Qualifier cannot be null");
        }

        extensionRegistrationStore.registerQualifier(qualifier);
        DynamicAnnotationRegistry.registerDynamicQualifier(qualifier);

        messageHandler.info("[KnowledgeBase] Registered qualifier: " + qualifier.getSimpleName());
    }

    /**
     * Checks if a given annotation type is a registered qualifier.
     *
     * @param annotationType the annotation type to check
     * @return true if it's a programmatically registered qualifier
     */
    public boolean isRegisteredQualifier(Class<? extends Annotation> annotationType) {
        return extensionRegistrationStore.isQualifierRegistered(annotationType);
    }

    /**
     * Gets all registered qualifiers.
     *
     * @return set of registered qualifier annotation classes
     */
    public Set<Class<? extends Annotation>> getRegisteredQualifiers() {
        return Collections.unmodifiableSet(extensionRegistrationStore.getRegisteredQualifiers());
    }

    /**
     * Registers a scope programmatically with its characteristics.
     *
     * <p>This is called by BeforeBeanDiscovery.addScope() to register scopes
     * that are not defined via @NormalScope or pseudo-scope annotations.
     *
     * @param scopeType the scope annotation class
     * @param normal whether it's a normal scope (true) or pseudo-scope (false)
     * @param passivating whether instances in this scope can be passivated (serialized)
     */
    public void addScope(Class<? extends Annotation> scopeType, boolean normal, boolean passivating) {
        if (scopeType == null) {
            throw new IllegalArgumentException("Scope type cannot be null");
        }

        ScopeMetadata metadata = new ScopeMetadata(scopeType, normal, passivating);
        extensionRegistrationStore.registerScope(scopeType, metadata);
        DynamicAnnotationRegistry.registerDynamicScope(scopeType);

        messageHandler.info("[KnowledgeBase] Registered scope: " + scopeType.getSimpleName() +
                          " (normal=" + normal + ", passivating=" + passivating + ")");
    }

    /**
     * Checks if a given annotation type is a registered scope.
     *
     * @param annotationType the annotation type to check
     * @return true if it's a programmatically registered scope
     */
    public boolean isRegisteredScope(Class<? extends Annotation> annotationType) {
        return extensionRegistrationStore.isScopeRegistered(annotationType);
    }

    /**
     * Gets the scope metadata for a registered scope.
     *
     * @param scopeType the scope annotation class
     * @return scope metadata, or null if not registered
     */
    public ScopeMetadata getScopeMetadata(Class<? extends Annotation> scopeType) {
        return extensionRegistrationStore.getScopeMetadata(scopeType);
    }

    /**
     * Gets all registered scopes.
     *
     * @return map of scope class to their metadata
     */
    public Map<Class<? extends Annotation>, ScopeMetadata> getRegisteredScopes() {
        return Collections.unmodifiableMap(extensionRegistrationStore.getRegisteredScopes());
    }

    /**
     * Registers a context implementation class for a scope.
     *
     * <p>This is used by BCE {@code MetaAnnotations.addContext()} to keep track of
     * all declared context implementations for a given scope.
     */
    public void addContextImplementation(Class<? extends Annotation> scopeType,
                                         Class<? extends AlterableContext> contextImplementation) {
        if (scopeType == null) {
            throw new IllegalArgumentException("Scope type cannot be null");
        }
        if (contextImplementation == null) {
            throw new IllegalArgumentException("Context implementation cannot be null");
        }

        extensionRegistrationStore.addContextImplementation(scopeType, contextImplementation);
    }

    /**
     * Gets all context implementations registered for the given scope.
     */
    public List<Class<? extends AlterableContext>> getContextImplementations(Class<? extends Annotation> scopeType) {
        return extensionRegistrationStore.getContextImplementations(scopeType);
    }

    /**
     * Registers an interceptor binding programmatically with its meta-annotations.
     *
     * <p>This is called by BeforeBeanDiscovery.addInterceptorBinding() to register
     * interceptor bindings that are not defined via @InterceptorBinding annotation.
     *
     * @param bindingType the interceptor binding annotation class
     * @param bindingTypeDef the meta-annotations that define the binding
     */
    public void addInterceptorBinding(Class<? extends Annotation> bindingType, Annotation... bindingTypeDef) {
        if (bindingType == null) {
            throw new IllegalArgumentException("Interceptor binding type cannot be null");
        }

        Set<Annotation> definitions = new HashSet<>();
        if (bindingTypeDef != null) {
            definitions.addAll(Arrays.asList(bindingTypeDef));
        }

        extensionRegistrationStore.registerInterceptorBinding(bindingType, definitions);
        DynamicAnnotationRegistry.registerDynamicInterceptorBinding(bindingType);

        messageHandler.info("[KnowledgeBase] Registered interceptor binding: " + bindingType.getSimpleName() +
                          " with  meta-annotation(s) " + AnnotationsHelper.toList(definitions));
    }

    /**
     * Checks if a given annotation type is a registered interceptor binding.
     *
     * @param annotationType the annotation type to check
     * @return true if it's a programmatically registered interceptor binding
     */
    public boolean isRegisteredInterceptorBinding(Class<? extends Annotation> annotationType) {
        return extensionRegistrationStore.isInterceptorBindingRegistered(annotationType);
    }

    /**
     * Gets the interceptor binding definition (meta-annotations) for a registered binding.
     *
     * @param bindingType the interceptor binding annotation class
     * @return set of meta-annotations, or null if not registered
     */
    public Set<Annotation> getInterceptorBindingDefinition(Class<? extends Annotation> bindingType) {
        return extensionRegistrationStore.getInterceptorBindingDefinition(bindingType);
    }

    /**
     * Gets all registered interceptor bindings.
     *
     * @return map of interceptor-binding class to their definitions
     */
    public Map<Class<? extends Annotation>, Set<Annotation>> getRegisteredInterceptorBindings() {
        return Collections.unmodifiableMap(extensionRegistrationStore.getRegisteredInterceptorBindings());
    }

    /**
     * Registers an annotated type programmatically.
     *
     * <p>This is called by BeforeBeanDiscovery.addAnnotatedType() to register synthetic
     * types added by extensions that should be processed during bean discovery.
     *
     * @param type the annotated type to register
     * @param id the unique identifier for this registration
     */
    public void addAnnotatedType(AnnotatedType<?> type, String id) {
        addAnnotatedType(type, id, null);
    }

    public void addAnnotatedType(AnnotatedType<?> type, String id, Extension source) {
        if (type == null) {
            throw new IllegalArgumentException("Annotated type cannot be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }

        if (extensionRegistrationStore.hasAnnotatedType(id)) {
            throw new IllegalArgumentException("Annotated type with ID '" + id + "' already registered");
        }

        extensionRegistrationStore.registerAnnotatedType(id, type);
        if (source != null) {
            extensionRegistrationStore.registerAnnotatedTypeSource(id, source);
        }

        messageHandler.info("[KnowledgeBase] Registered annotated type: " + type.getJavaClass().getName() +
                          " with ID: " + id);
    }

    /**
     * Gets a registered annotated type by ID.
     *
     * @param id the unique identifier
     * @return the annotated type, or null if not found
     */
    public AnnotatedType<?> getRegisteredAnnotatedType(String id) {
        return extensionRegistrationStore.getRegisteredAnnotatedType(id);
    }

    /**
     * Gets all registered annotated types.
     *
     * @return map of ID to annotated type
     */
    public Map<String, AnnotatedType<?>> getRegisteredAnnotatedTypes() {
        return Collections.unmodifiableMap(extensionRegistrationStore.getRegisteredAnnotatedTypes());
    }

    public Extension getRegisteredAnnotatedTypeSource(String id) {
        if (id == null) {
            return null;
        }
        return extensionRegistrationStore.getRegisteredAnnotatedTypeSource(id);
    }

    /**
     * Registers a synthetic observer method.
     *
     * <p>This is called by AfterBeanDiscovery.addObserverMethod() to register observer methods
     * created programmatically by extensions (not discovered from bean classes).
     *
     * @param observerMethod the synthetic observer method to register
     */
    public void addSyntheticObserverMethod(ObserverMethod<?> observerMethod) {
        if (observerMethod == null) {
            throw new IllegalArgumentException("Observer method cannot be null");
        }

        beanRegistryStore.addSyntheticObserverMethod(observerMethod);

        messageHandler.info("[KnowledgeBase] Registered synthetic observer method: " +
                          "observedType=" + observerMethod.getObservedType() +
                          ", async=" + observerMethod.isAsync());
    }

    /**
     * Gets all synthetic observer methods.
     *
     * @return collection of synthetic observer methods
     */
    public Collection<ObserverMethod<?>> getSyntheticObserverMethods() {
        return beanRegistryStore.getSyntheticObserverMethods();
    }

    /**
     * Adds a beans.xml configuration from a scanned archive.
     *
     * <p>This is called during bean discovery to collect all beans.xml files found
     * in the classpath. Each archive (JAR or directory) may have its own beans.xml
     * with different alternatives, interceptors, decorators, and scan exclusions.
     *
     * @param beansXml the parsed beans.xml configuration
     */
    public void addBeansXml(BeansXml beansXml) {
        if (beansXml == null) {
            throw new IllegalArgumentException("BeansXml cannot be null");
        }

        // Only add non-empty configurations to avoid clutter
        if (!beansXml.isEmpty()) {
            discoveryStore.addBeansXmlConfiguration(beansXml);
            messageHandler.info("[KnowledgeBase] Registered beans.xml configuration: " + beansXml);
        }
    }

    /**
     * Gets all beans.xml configurations from all scanned archives.
     *
     * @return collection of BeansXml objects
     */
    public Collection<BeansXml> getBeansXmlConfigurations() {
        return Collections.unmodifiableCollection(discoveryStore.getBeansXmlConfigurations());
    }

    /**
     * Checks if a class or stereotype is enabled as an alternative in any beans.xml.
     *
     * <p>CDI 4.1 Section 5.1.2: Alternatives can be enabled via:
     * <ul>
     *   <li>@Priority annotation on the class (preferred in CDI 4.1)</li>
     *   <li>beans.xml &lt;alternatives&gt; section (traditional method)</li>
     * </ul>
     *
     * @param className the fully qualified class name to check
     * @return true if the class/stereotype is declared in any beans.xml alternatives section
     */
    public boolean isAlternativeEnabledInBeansXml(String className) {
        return AlternativesHelper.isAlternativeEnabledInBeansXml(className, discoveryStore.getBeansXmlConfigurations());
    }

    // ==================== Vetoed Types Management ====================

    /**
     * Marks a type as vetoed by an extension during ProcessAnnotatedType.
     * Vetoed types should not become beans.
     *
     * @param clazz the class to veto
     */
    public void vetoType(Class<?> clazz) {
        discoveryStore.vetoType(clazz);
    }

    /**
     * Checks if a type was vetoed by an extension.
     *
     * @param clazz the class to check
     * @return true if the type was vetoed
     */
    public boolean isTypeVetoed(Class<?> clazz) {
        return discoveryStore.isTypeVetoed(clazz);
    }

    /**
     * Returns all vetoed types.
     *
     * @return set of vetoed types
     */
    public Set<Class<?>> getVetoedTypes() {
        return Collections.unmodifiableSet(discoveryStore.getVetoedTypes());
    }

    public List<String> getDeploymentErrors() {
        return problemCollector.getDeploymentErrors();
    }

    /**
     * Clears all mutable runtime state retained by the knowledge base.
     * Intended to be called during container shutdown to release references.
     */
    public void clearAllState() {
        discoveryStore.clear();
        beanRegistryStore.clear();
        problemCollector.clear();
        extensionRegistrationStore.clear();
        enablementStore.clear();
    }

    // Utility methods

    public Annotation[] getEffectiveClassAnnotations(Class<?> candidate) {
        AnnotatedType<?> override = getAnnotatedTypeOverride(candidate);
        if (override != null) {
            return override.getAnnotations().toArray(new Annotation[0]);
        }
        return candidate.getAnnotations();
    }

    public Set<Class<? extends Annotation>> getClassStereotypes(Class<?> beanClass) {
        Set<Class<? extends Annotation>> stereotypes = new LinkedHashSet<>();
        if (beanClass == null) {
            return stereotypes;
        }
        for (Annotation annotation : getEffectiveClassAnnotations(beanClass)) {
            if (annotation == null) {
                continue;
            }
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (hasStereotypeAnnotation(annotationType)) {
                stereotypes.add(annotationType);
            }
        }
        return stereotypes;
    }

    public void collectStereotypePriorityValues(Class<? extends Annotation> stereotypeType,
                                                 Set<Integer> priorities,
                                                 Set<Class<? extends Annotation>> visited) {
        if (stereotypeType == null || !visited.add(stereotypeType)) {
            return;
        }

        Integer declaredPriority = getPriorityValueFromAnnotations(stereotypeType.getAnnotations());
        if (declaredPriority == null && isRegisteredStereotype(stereotypeType)) {
            Set<Annotation> definition = getStereotypeDefinition(stereotypeType);
            if (definition != null) {
                declaredPriority = getPriorityValueFromAnnotations(definition.toArray(new Annotation[0]));
            }
        }
        if (declaredPriority != null) {
            priorities.add(declaredPriority);
        }

        for (Annotation meta : stereotypeType.getAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (hasStereotypeAnnotation(metaType)) {
                collectStereotypePriorityValues(metaType, priorities, visited);
            }
        }

        if (isRegisteredStereotype(stereotypeType)) {
            Set<Annotation> definition = getStereotypeDefinition(stereotypeType);
            if (definition != null) {
                for (Annotation meta : definition) {
                    if (meta == null) {
                        continue;
                    }
                    Class<? extends Annotation> metaType = meta.annotationType();
                    if (hasStereotypeAnnotation(metaType)) {
                        collectStereotypePriorityValues(metaType, priorities, visited);
                    }
                }
            }
        }
    }

    public Set<Integer> collectStereotypePriorityValues(Set<Class<? extends Annotation>> stereotypes) {
        Set<Integer> priorities = new LinkedHashSet<>();
        if (stereotypes == null || stereotypes.isEmpty()) {
            return priorities;
        }
        Set<Class<? extends Annotation>> visited = new HashSet<>();
        for (Class<? extends Annotation> stereotype : stereotypes) {
            if (stereotype == null) {
                continue;
            }
            collectStereotypePriorityValues(stereotype, priorities, visited);
        }
        return priorities;
    }

    public Set<Integer> collectStereotypePriorityValues(Class<?> candidate) {
        Set<Integer> priorities = new LinkedHashSet<>();
        if (candidate == null) {
            return priorities;
        }

        Set<Class<? extends Annotation>> visited = new HashSet<>();
        for (Annotation annotation : getEffectiveClassAnnotations(candidate)) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (hasStereotypeAnnotation(annotationType)) {
                collectStereotypePriorityValues(annotationType, priorities, visited);
            }
        }
        return priorities;
    }

    public Integer getDirectPriority(Class<?> candidate) {
        Integer directPriority = getPriorityValue(candidate);
        if (directPriority != null) {
            return directPriority;
        }

        AnnotatedType<?> override = getAnnotatedTypeOverride(candidate);
        if (override == null) {
            return null;
        }

        for (Annotation annotation : override.getAnnotations()) {
            Integer value = extractPriorityValue(annotation);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public Integer getEffectivePriority(Class<?> candidate) {
        if (candidate == null) {
            return null;
        }

        Integer directPriority = getDirectPriority(candidate);
        if (directPriority != null) {
            return directPriority;
        }

        Set<Integer> stereotypePriorities = collectStereotypePriorityValues(candidate);
        if (stereotypePriorities.size() == 1) {
            return stereotypePriorities.iterator().next();
        }

        return extractPrioritizedInterfacePriority(candidate);
    }

    public BeanArchiveMode getEffectiveBeanArchiveMode(BeanArchiveMode discoveredMode) {
        if (forcedBeanArchiveMode != null) {
            return forcedBeanArchiveMode;
        }
        return discoveredMode != null ? discoveredMode : BeanArchiveMode.IMPLICIT;
    }

    public boolean isScopeOrNormalScope(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            return false;
        }
        if (hasDependentAnnotation(annotationType)) {
            return true;
        }
        if (hasNormalScopeAnnotation(annotationType) || hasBuiltInNormalScopeAnnotation(annotationType)) {
            return true;
        }
        ScopeMetadata metadata = getScopeMetadata(annotationType);
        return metadata != null && metadata.isNormal();
    }

    public boolean hasBeanDefiningAnnotation(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        if (hasInterceptorAnnotation(clazz) || hasDecoratorAnnotation(clazz)) {
            return true;
        }
        for (Annotation annotation : clazz.getAnnotations()) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (isScopeOrNormalScope(type) || isStereotype(type)) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldIncludeTypeInDiscoveryForArchiveMode(Class<?> clazz) {
        BeanArchiveMode mode = getBeanArchiveMode(clazz);
        if (mode == null) {
            mode = BeanArchiveMode.IMPLICIT;
        }
        if (BeanArchiveMode.NONE.equals(mode)) {
            return false;
        }
        if (BeanArchiveMode.EXPLICIT.equals(mode)) {
            return true;
        }
        // TRIMMED mode behavior differs by bootstrap path:
        // - forced/manual-trimmed discovery is already narrowed to bean-defining types
        // - scanner-derived trimmed discovery keeps PAT visibility of discovered types
        boolean trimAtTypeDiscovery = BeanArchiveMode.TRIMMED.equals(forcedBeanArchiveMode)
                || explicitlyAddedDiscoveredClasses.contains(clazz);
        if (BeanArchiveMode.TRIMMED.equals(mode)) {
            if (!trimAtTypeDiscovery) {
                return true;
            }
            if (clazz.isInterface() || clazz.isEnum() || clazz.isAnnotation()) {
                return false;
            }
            return hasBeanDefiningAnnotation(clazz);
        }
        // IMPLICIT discovery includes only bean-defining classes.
        if (clazz.isInterface() || clazz.isEnum() || clazz.isAnnotation()) {
            return false;
        }
        return hasBeanDefiningAnnotation(clazz);
    }

}
