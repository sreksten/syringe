package com.threeamigos.common.util.implementations.injection;

import com.threeamigos.common.util.implementations.injection.annotations.DynamicAnnotationRegistry;

import com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates;

import com.threeamigos.common.util.implementations.concurrency.ParallelTaskExecutor;
import com.threeamigos.common.util.implementations.injection.bce.*;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.Alternatives;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.builtinbeans.BeanManagerBean;
import com.threeamigos.common.util.implementations.injection.builtinbeans.ConversationBean;
import com.threeamigos.common.util.implementations.injection.builtinbeans.InjectionPointBean;
import com.threeamigos.common.util.implementations.injection.builtinbeans.InterceptionFactoryBean;
import com.threeamigos.common.util.implementations.injection.builtinbeans.RequestContextControllerBean;
import com.threeamigos.common.util.implementations.injection.builtinbeans.ActivateRequestContextInterceptor;
import com.threeamigos.common.util.implementations.injection.discovery.validation.CDI41BeanValidator;
import com.threeamigos.common.util.implementations.injection.events.ObserverMethodInfoKey;
import com.threeamigos.common.util.implementations.injection.extensions.ExtensionsManager;
import com.threeamigos.common.util.implementations.injection.extensions.ExtensionsManagerImpl;
import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import com.threeamigos.common.util.implementations.injection.discovery.*;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.injection.knowledgebase.InterceptorInfo;
import com.threeamigos.common.util.implementations.injection.events.EventImpl;
import com.threeamigos.common.util.implementations.injection.events.ObserverMethodInfo;
import com.threeamigos.common.util.implementations.injection.events.propagation.ConversationPropagationRegistry;
import com.threeamigos.common.util.implementations.injection.interceptors.InterceptorAwareProxyGenerator;
import com.threeamigos.common.util.implementations.injection.interceptors.InterceptorResolver;
import com.threeamigos.common.util.implementations.injection.decorators.DecoratorAwareProxyGenerator;
import com.threeamigos.common.util.implementations.injection.decorators.DecoratorResolver;
import com.threeamigos.common.util.implementations.injection.resolution.BeanAttributesImpl;
import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import com.threeamigos.common.util.implementations.injection.resolution.BeanResolver;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import com.threeamigos.common.util.implementations.injection.resolution.DestroyedInstanceTracker;
import com.threeamigos.common.util.implementations.injection.scopes.ClientProxyGenerator;
import com.threeamigos.common.util.implementations.injection.scopes.ConversationImpl;
import com.threeamigos.common.util.implementations.injection.scopes.InjectionPointImpl;
import com.threeamigos.common.util.implementations.injection.spi.*;
import com.threeamigos.common.util.implementations.injection.spi.spievents.*;
import com.threeamigos.common.util.implementations.injection.annotations.AnnotatedMetadataHelper;
import com.threeamigos.common.util.implementations.injection.resolution.GenericTypeResolver;
import com.threeamigos.common.util.implementations.injection.types.TypeClosureHelper;
import com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler;
import com.threeamigos.common.util.interfaces.messagehandler.MessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Startup;
import jakarta.enterprise.event.Shutdown;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.IllegalProductException;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.threeamigos.common.util.implementations.injection.annotations.AnnotatedMetadataHelper.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationPredicates.*;
import static com.threeamigos.common.util.implementations.injection.annotations.AnnotationExtractors.*;
import static com.threeamigos.common.util.implementations.injection.spi.SPIUtils.extractPrioritizedInterfacePriority;
import static com.threeamigos.common.util.implementations.injection.types.RawTypeExtractor.extractRawClass;

/**
 * Syringe - CDI 4.1 compliant container implementation.
 *
 * <p>This class implements the complete CDI 4.1 container lifecycle, including:
 * <ul>
 *   <li>Extension loading via ServiceLoader (portable extensions)</li>
 *   <li>Bean discovery (classpath scanning with beans.xml detection)</li>
 *   <li>Extension event firing (BeforeBeanDiscovery, ProcessAnnotatedType, AfterBeanDiscovery, etc.)</li>
 *   <li>Bean validation and registration</li>
 *   <li>Deployment validation</li>
 *   <li>Application lifecycle management</li>
 * </ul>
 *
 * <p><b>CDI 4.1 Container Lifecycle:</b>
 * <pre>
 * 1. Application Initialization
 *    - Load extensions via ServiceLoader
 *    - Create BeanManager
 *
 * 2. Bean Discovery (Type Discovery)
 *    → Fire BeforeBeanDiscovery event
 *    - Scan classpath for classes
 *    - Detect bean archives (explicit/implicit)
 *    → Fire ProcessAnnotatedType for each discovered class
 *    → Fire ProcessInjectionPoint, ProcessInjectionTarget, ProcessBeanAttributes
 *    → Fire ProcessBean, ProcessProducer, ProcessObserverMethod
 *    → Fire AfterBeanDiscovery event
 *
 * 3. Validation
 *    → Fire AfterDeploymentValidation event
 *    - Validate all beans, injection points, decorators, interceptors
 *    - Detect ambiguous dependencies, unsatisfied dependencies
 *    - Check specialization, alternatives, stereotypes
 *
 * 4. Application Running
 *    - Container is ready for use
 *    - Beans can be resolved and created
 *
 * 5. Application Shutdown
 *    → Fire BeforeShutdown event
 *    - Destroy all context instances
 *    - Call @PreDestroy on all beans
 * </pre>
 *
 * @author Stefano Reksten
 */
public class Syringe {

    /**
     * A MessageHandler implementation to log messages
     */
    private final MessageHandler messageHandler;

    /**
     * Package names to scan for beans.
     */
    private final String[] packageNames;

    /**
     * If true, keep only classes declared directly in the requested packages.
     * This is used by class-based convenience bootstrap to avoid accidental
     * pickup from sibling subpackages that contain unrelated test fixtures.
     */
    private final boolean exactPackageMatchOnly;

    /**
     * Knowledge base containing all discovered beans, interceptors, decorators, observers.
     */
    private KnowledgeBase knowledgeBase;

    /**
     * The ContextManager, responsible for handling Scopes
     */
    private ContextManager contextManager;

    /**
     * The ExtensionsManager, responsible for handling extensions
     */
    private ExtensionsManager extensionsManager;

    /**
     * The BuildCompatibleExtensionsManager, responsible for handling build compatible extensions
     */
    private BuildCompatibleExtensionsManager buildCompatibleExtensionsManager;

    /**
     * The BeanManager - central interface for programmatic CDI access.
     */
    private BeanManagerImpl beanManager;

    /**
     * Whether the container has been initialized.
     */
    private boolean initialized = false;

    /**
     * Whether a container shutdown has started.
     */
    private boolean shutdownStarted = false;

    /**
     * ClassLoader used when retaining dynamic BCE metadata for this container lifecycle.
     */
    private ClassLoader dynamicAnnotationClassLoader;

    /**
     * Whether this container lifecycle retained dynamic BCE metadata.
     */
    private boolean dynamicAnnotationsRetained = false;

    /**
     * Whether BeforeBeanDiscovery has already been fired for this container lifecycle.
     */
    private boolean beforeBeanDiscoveryFired = false;

    /**
     * Whether BeforeShutdown has already been fired for this container lifecycle.
     */
    private boolean beforeShutdownFired = false;

    /**
     * If true, expose CDI Lite behavior for CDI#getBeanManager() where only BeanContainer
     * methods are portable.
     */
    private boolean cdiLiteMode = false;

    private boolean cdiFullLegacyInterceptionEnabled = true;

    private boolean legacyCdi10NewEnabled = false;

    private boolean allowNonPortableAsyncObserverEventParameterPriority = false;

    /**
     * Custom contexts to register programmatically before container initialization.
     * These will be registered during the AfterBeanDiscovery phase.
     * Map key: scope annotation class, Map value: context implementation
     */
    private final Map<Class<? extends Annotation>, Context> customContextsToRegister = new HashMap<>();

    private final Map<ProducerBean<?>, Producer<?>> deferredProducerReplacements = new IdentityHashMap<>();

    private InterceptorAwareProxyGenerator runtimeInterceptorAwareProxyGenerator;

    private DecoratorAwareProxyGenerator runtimeDecoratorAwareProxyGenerator;

    public Syringe() {
        this.messageHandler = new ConsoleMessageHandler();
        this.packageNames = new String[0];
        this.exactPackageMatchOnly = false;
        completeInitialization();
    }

    public Syringe(String... packageNames) {
        this.messageHandler = new ConsoleMessageHandler();
        this.packageNames = packageNames != null ? packageNames : new String[0];
        this.exactPackageMatchOnly = false;
        completeInitialization();
    }

    public Syringe(MessageHandler messageHandler, Class<?>... classes) {
        this.messageHandler = messageHandler;
        this.packageNames = new String[classes.length];
        for (int i = 0; i < classes.length; i++) {
            this.packageNames[i] = classes[i].getPackage().getName();
        }
        this.exactPackageMatchOnly = true;
        completeInitialization();
    }

    private void completeInitialization() {
        knowledgeBase = new KnowledgeBase(messageHandler);
        contextManager = new ContextManager(messageHandler);
        beanManager = new BeanManagerImpl(knowledgeBase, contextManager);

        extensionsManager = new ExtensionsManagerImpl();
        extensionsManager.setKnowledgeBase(knowledgeBase);
        extensionsManager.setMessageHandler(messageHandler);
        extensionsManager.setBeanManager(beanManager);

        buildCompatibleExtensionsManager = new BuildCompatibleExtensionsManagerImpl();
        buildCompatibleExtensionsManager.setKnowledgeBase(knowledgeBase);
        buildCompatibleExtensionsManager.setMessageHandler(messageHandler);
        buildCompatibleExtensionsManager.setExtensionsManager(extensionsManager);
        buildCompatibleExtensionsManager.setBeanManager(beanManager);
    }

    /**
     * Manually exclude one or more classes from scanning.
     * This is useful for excluding classes that are known to be problematic (e.g., when running tests) or unnecessary.
     *
     * @param classes the classes to exclude
     */
    public void exclude(Class<?> ... classes) {
        info("Programmatically excluded classes: " + Arrays.stream(classes)
                .map(Class::getName)
                .collect(Collectors.joining(", ")));
        knowledgeBase.exclude(classes);
    }

    /**
     * Registers a portable extension by class name.
     * Extensions will be loaded and initialized during {@link #setup()}.
     *
     * @param extensionClassName fully qualified class name of the extension
     */
    public void addExtension(String extensionClassName) {
        if (initialized) {
            throw new IllegalStateException("Cannot add extensions after container initialization");
        }
        extensionsManager.addExtension(extensionClassName);
    }

    /**
     * Registers a portable extension instance directly.
     *
     * @param extension extension instance
     */
    public void addExtension(Extension extension) {
        if (initialized) {
            throw new IllegalStateException("Cannot add extensions after container initialization");
        }
        extensionsManager.addExtension(extension);
    }

    /**
     * Registers a build-compatible extension by class name.
     * Build compatible extensions are loaded and invoked during {@link #setup()} BCE checkpoints.
     *
     * @param extensionClassName fully qualified class name of the build compatible extension
     */
    public void addBuildCompatibleExtension(String extensionClassName) {
        if (initialized) {
            throw new IllegalStateException("Cannot add build compatible extensions after container initialization");
        }
        buildCompatibleExtensionsManager.addBuildCompatibleExtension(extensionClassName);
    }

    /**
     * Forces CDI Lite mode semantics for CDI#getBeanManager() view.
     * In this mode, invoking BeanManager methods not inherited from BeanContainer
     * is treated as non-portable behavior.
     *
     * @param cdiLiteMode true to enable CDI Lite BeanManager surface restrictions
     */
    public void forceCdiLiteMode(boolean cdiLiteMode) {
        if (initialized) {
            throw new IllegalStateException("Cannot change CDI mode after container initialization");
        }
        this.cdiLiteMode = cdiLiteMode;
        // Keep interception behavior aligned with selected CDI mode by default:
        // CDI Lite -> strict non-portable checks; CDI Full -> allow legacy forms.
        this.cdiFullLegacyInterceptionEnabled = !cdiLiteMode;
        info("CDI Lite mode forced: " + cdiLiteMode);
    }

    /**
     * Enables CDI Full legacy interception forms such as {@code @jakarta.interceptor.Interceptors}.
     *
     * <p>When disabled (default), these forms are treated as non-portable behavior for CDI Lite compatibility.
     *
     * @param enabled true to enable legacy interception forms
     */
    public void enableCdiFullLegacyInterception(boolean enabled) {
        if (initialized) {
            throw new IllegalStateException("Cannot change CDI interception mode after container initialization");
        }
        this.cdiFullLegacyInterceptionEnabled = enabled;
        info("CDI Full legacy interception enabled: " + enabled);
    }

    /**
     * Enables legacy CDI 1.0 {@code @javax.enterprise.inject.New} compatibility.
     *
     * <p>When disabled (default), {@code @New} injection points remain unsatisfied.
     * When enabled, {@code @New} resolves to a dependent-style contextual instance of the
     * selected bean class.
     *
     * @param enabled true to enable legacy {@code @New} compatibility
     */
    public void enableLegacyCdi10New(boolean enabled) {
        if (initialized) {
            throw new IllegalStateException("Cannot change legacy @New mode after container initialization");
        }
        this.legacyCdi10NewEnabled = enabled;
        info("Legacy @New annotation enabled: " + enabled);
    }

    /**
     * Allows non-portable behavior where an asynchronous observer event parameter is annotated with {@code @Priority}.
     *
     * <p>By default, this remains disabled and such observer methods cause
     * {@link NonPortableBehaviourException}
     * during deployment validation.
     *
     * <p><b>CDI 4.1 note:</b> This switch should stay disabled for spec-conformant behavior.
     * It exists only for legacy compatibility tests that intentionally exercise older,
     * non-portable observer declarations.
     *
     * @param enabled true to allow this non-portable observer declaration
     */
    public void allowNonPortableAsyncObserverEventParameterPriority(boolean enabled) {
        if (initialized) {
            throw new IllegalStateException(
                    "Cannot change async observer @Priority non-portable mode after container initialization");
        }
        this.allowNonPortableAsyncObserverEventParameterPriority = enabled;
        info("Allow non-portable async observer @Priority: " + enabled);
    }

    /**
     * Programmatically enables an {@code @Alternative} bean class.
     *
     * <p>This is useful in tests and controlled bootstrap scenarios where alternatives
     * must be selected without beans.xml.
     *
     * @param alternativeClass alternative bean class to enable
     */
    public void enableAlternative(Class<?> alternativeClass) {
        if (initialized) {
            throw new IllegalStateException("Cannot enable alternatives after container initialization");
        }
        knowledgeBase.enableAlternative(alternativeClass);
        info("Programmatically enabled alternative: " + alternativeClass.getName());
    }

    /**
     * Forces a bean archive mode for all discovered classes.
     *
     * <p>This is primarily useful for tests that need deterministic discovery behavior
     * regardless of detected beans.xml metadata.
     *
     * <p>Scope of this override:
     * <ul>
     *   <li>It changes the mode used when validating/registering discovered classes.</li>
     *   <li>It does not change scanner-time decisions in {@code ParallelClasspathScanner}.</li>
     *   <li>Archives skipped as {@code BeanArchiveMode.NONE} remain skipped.</li>
     * </ul>
     *
     * @param beanArchiveMode the mode to force (for example {@link BeanArchiveMode#EXPLICIT} or
     *                        {@link BeanArchiveMode#IMPLICIT}); cannot be {@code null}
     */
    public void forceBeanArchiveMode(BeanArchiveMode beanArchiveMode) {
        if (initialized) {
            throw new IllegalStateException("Cannot force bean archive mode after container initialization");
        }
        if (beanArchiveMode == null) {
            throw new IllegalArgumentException("beanArchiveMode cannot be null");
        }

        knowledgeBase.setForcedBeanArchiveMode(beanArchiveMode);
        info("Forced bean archive mode: " + beanArchiveMode);
    }

    /**
     * Registers a custom scope and its context programmatically.
     * <p>
     * <b>Non-Standard API:</b> This is a convenience method for direct container configuration
     * and testing. Standard CDI applications should register custom contexts via portable
     * extensions using {@link AfterBeanDiscovery#addContext(Context)}.
     * <p>
     * This method allows you to register custom scopes before calling {@link #setup()}.
     * The contexts will be registered during the AfterBeanDiscovery phase of container
     * initialization.
     * <p>
     * <h3>Example Usage:</h3>
     * <pre>{@code
     * // Create container
     * Syringe syringe = new Syringe("com.myapp");
     *
     * // Register custom scope programmatically
     * syringe.registerCustomContext(MyCustomScope.class, new MyCustomScopeContext());
     *
     * // Initialize container (custom context will be registered during AfterBeanDiscovery)
     * syringe.setup();
     *
     * // Use beans with custom scope
     * BeanManager bm = syringe.getBeanManager();
     * MyBean bean = bm.getReference(...);
     * }</pre>
     * <p>
     * <h3>Requirements:</h3>
     * The scope annotation must be annotated with {@code @NormalScope} or {@code @Scope}
     * from the Jakarta CDI specification. The context must properly implement
     * {@link Context}.
     *
     * @param scopeAnnotation the scope annotation class (must be annotated with @NormalScope or @Scope)
     * @param context the context implementation for this scope
     * @throws IllegalStateException if the container is already initialized
     * @throws IllegalArgumentException if scopeAnnotation or context is null
     */
    public void registerCustomContext(Class<? extends Annotation> scopeAnnotation,
                                       Context context) {
        if (initialized) {
            throw new IllegalStateException("Cannot register custom contexts after container initialization.");
        }

        if (scopeAnnotation == null) {
            throw new IllegalArgumentException("scopeAnnotation cannot be null");
        }

        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }

        // Validate that the context's scope matches the provided scope annotation
        if (!context.getScope().equals(scopeAnnotation)) {
            throw new IllegalArgumentException(
                "Context scope mismatch: context.getScope() returns " +
                context.getScope().getName() + " but scopeAnnotation is " +
                scopeAnnotation.getName()
            );
        }

        customContextsToRegister.put(scopeAnnotation, context);
        info("Queued custom context for registration: @" + scopeAnnotation.getSimpleName());
    }

    /**
     * Initializes the CDI container following the complete CDI 4.1 lifecycle.
     *
     * <p><b>CDI 4.1 Container Lifecycle Steps:</b>
     * <ol>
     *   <li>Load portable extensions</li>
     *   <li>Create BeanManager and KnowledgeBase</li>
     *   <li>Fire BeforeBeanDiscovery event</li>
     *   <li>Perform bean discovery (classpath scanning)</li>
     *   <li>Fire ProcessAnnotatedType for each discovered type</li>
     *   <li>Process and validate beans</li>
     *   <li>Fire ProcessInjectionPoint, ProcessInjectionTarget events</li>
     *   <li>Fire ProcessBean events (ProcessManagedBean, ProcessProducerMethod, etc.)</li>
     *   <li>Fire ProcessObserverMethod events</li>
     *   <li>Fire AfterBeanDiscovery event</li>
     *   <li>Validate deployment (check for errors)</li>
     *   <li>Fire AfterDeploymentValidation event</li>
     * </ol>
     * <p>This method is used for standalone SE mode. It performs automatic bean
     * discovery based on the package names provided in the constructor.
     *
     * @throws DeploymentException if validation fails or extensions cause errors
     */
    public void setup() {
        initialize();
        discover();
        start();
    }

    /**
     * PHASE 1: CONTAINER INITIALIZATION.
     *
     * <p>Initializes core infrastructure:
     * <ul>
     *   <li>Creates {@link KnowledgeBase} and {@link ContextManager}</li>
     *   <li>Loads portable extensions via ServiceLoader</li>
     *   <li>Creates {@link BeanManagerImpl}</li>
     *   <li>Fires {@code BeforeBeanDiscovery} event</li>
     * </ul>
     *
     * @throws IllegalStateException if the container is already initialized
     */
    public void initialize() {
        if (initialized) {
            throw new IllegalStateException("Container already initialized");
        }
        shutdownStarted = false;
        beforeBeanDiscoveryFired = false;
        beforeShutdownFired = false;
        dynamicAnnotationClassLoader = null;
        dynamicAnnotationsRetained = false;
        knowledgeBase.processedSyntheticAnnotatedTypeIds.clear();
        knowledgeBase.syntheticAnnotatedTypeClasses.clear();
        knowledgeBase.explicitlyAddedDiscoveredClasses.clear();
        knowledgeBase.additionalAnnotatedTypesForDiscoveredClasses.clear();

        // ============================================================
        // PHASE 1: CONTAINER INITIALIZATION
        // ============================================================
        info("Phase 1: Container Initialization");

        // Step 1.1: Load portable extensions via ServiceLoader + explicitly registered
        extensionsManager.loadExtensions();
        buildCompatibleExtensionsManager.loadBuildCompatibleExtensions();

        // Step 1.2: Set up BeanManager
        dynamicAnnotationClassLoader = beanManager.getRegistrationClassLoader();
        DynamicAnnotationRegistry.retainDynamicAnnotationsForClassLoader(dynamicAnnotationClassLoader);
        dynamicAnnotationsRetained = true;
        beanManager.setLegacyCdi10NewEnabled(legacyCdi10NewEnabled);
        beanManager.registerExtensions(extensionsManager.getExtensions());
        extensionsManager.registerRuntimeExtensionObserverMethods();

        // Register CDI built-in beans before any processing/validation
        registerBuiltInBeans();

        // Step 2.1: Fire BeforeBeanDiscovery event
        // Extensions can:
        // - Add new qualifiers, scopes, stereotypes, interceptor bindings
        // - Register additional beans programmatically
        extensionsManager.fireBeforeBeanDiscovery();
        beforeBeanDiscoveryFired = true;
        buildCompatibleExtensionsManager.fireBuildCompatibleExtensionPhase(BceSupportedPhase.DISCOVERY);
    }

    /**
     * Performs classpath bean discovery between {@link #initialize()} and {@link #start()}.
     *
     * <p>Steps:
     * <ol>
     *   <li>Use ParallelClasspathScanner to find all classes in specified packages</li>
     *   <li>Use BeanArchiveDetector to determine EXPLICIT/IMPLICIT mode per archive</li>
     *   <li>Collect AnnotatedType<?> for each discovered class</li>
     * </ol>
     */
    public void discover() {
        // Step 2.2: Perform bean discovery (classpath scanning)
        // - Scan for classes in specified packages
        // - Detect bean archives (explicit/implicit via beans.xml)
        // - Discover annotated types
        // NOTE: If a scanner detects BeanArchiveMode.NONE for an archive, that archive is skipped
        // before class registration. forceBeanArchiveMode(...) cannot override this scanner step.
        info("Discovering beans in packages: " + Arrays.toString(packageNames));

        ParallelClasspathScanner scanner;
        Set<Class<?>> preexistingDiscoveredClasses = new HashSet<>(knowledgeBase.getClasses());
        try (ParallelTaskExecutor parallelTaskExecutor = ParallelTaskExecutor.createExecutor()) {
            ClassProcessor classProcessor = new ClassProcessor(parallelTaskExecutor, knowledgeBase);
            scanner = new ParallelClasspathScanner(
                    Thread.currentThread().getContextClassLoader(),
                    classProcessor,
                    knowledgeBase,
                    packageNames
            );
            parallelTaskExecutor.awaitCompletion();
            filterDiscoveredClassesToRequestedPackages(preexistingDiscoveredClasses);
        } catch (Exception e) {
            throw new DeploymentException("Bean discovery failed", e);
        }

        info("Discovered " + knowledgeBase.getClasses().size() + " classes");

        // Collect beans.xml configurations from all scanned archives
        for (BeansXml beansXml : scanner.getBeansXmlConfigurations()) {
            knowledgeBase.addBeansXml(beansXml);
        }

        // Process registered AnnotatedTypes (added programmatically via BeforeBeanDiscovery)
        extensionsManager.processRegisteredAnnotatedTypes();
    }

    private void filterDiscoveredClassesToRequestedPackages(Set<Class<?>> preexistingDiscoveredClasses) {
        if (!exactPackageMatchOnly || packageNames == null || packageNames.length == 0) {
            return;
        }
        Set<String> allowedPackages = new HashSet<>();
        for (String packageName : packageNames) {
            if (packageName != null && !packageName.isEmpty()) {
                allowedPackages.add(packageName);
            }
        }
        if (allowedPackages.isEmpty()) {
            return;
        }
        for (Class<?> discovered : new ArrayList<>(knowledgeBase.getClasses())) {
            if (preexistingDiscoveredClasses != null && preexistingDiscoveredClasses.contains(discovered)) {
                continue;
            }
            Package discoveredPackage = discovered.getPackage();
            String discoveredPackageName = discoveredPackage != null ? discoveredPackage.getName() : "";
            if (!allowedPackages.contains(discoveredPackageName)) {
                knowledgeBase.removeDiscoveredClass(discovered);
            }
        }
    }

    /**
     * Adds a class to the container for bean discovery.
     *
     * <p>This method should be called after {@link #initialize()} and before
     * {@link #start()}. It is intended for managed bootstrap environments
     * (like WildFly) where discovery is performed externally.
     *
     * @param clazz the class to add
     * @throws IllegalStateException if the container is already initialized or not yet initialized
     */
    public void addDiscoveredClass(Class<?> clazz) {
        addDiscoveredClass(clazz, BeanArchiveMode.IMPLICIT);
    }

    public void addDiscoveredClass(Class<?> clazz, BeanArchiveMode beanArchiveMode) {
        addDiscoveredClassInternal(clazz, beanArchiveMode, true);
    }

    /**
     * Adds a class discovered by an external scanner/host runtime.
     *
     * <p>Unlike {@link #addDiscoveredClass(Class, BeanArchiveMode)}, this path keeps
     * trimmed archive {@code ProcessAnnotatedType} visibility aligned with scanner-driven discovery:
     * non-bean-defining types still get PAT, but they are filtered before bean registration/PBA.
     *
     * @param clazz a discovered class to add
     * @param beanArchiveMode archive mode associated with the class
     */
    public void addExternallyDiscoveredClass(Class<?> clazz, BeanArchiveMode beanArchiveMode) {
        addDiscoveredClassInternal(clazz, beanArchiveMode, false);
    }

    private void addDiscoveredClassInternal(Class<?> clazz,
                                            BeanArchiveMode beanArchiveMode,
                                            boolean explicitlyAdded) {
        if (initialized) {
            throw new IllegalStateException("Container already initialized");
        }
        BeanArchiveMode mode = beanArchiveMode != null ? beanArchiveMode : BeanArchiveMode.IMPLICIT;
        BeanArchiveMode effectiveMode = knowledgeBase.getEffectiveBeanArchiveMode(mode);
        if (knowledgeBase.getClasses().contains(clazz)) {
            if (!explicitlyAdded) {
                // Managed runtimes can report classes already added during BCE discovery.
                // Keep this path idempotent. Do not downgrade already discovered classes to NONE,
                // because BCE ScannedClasses can intentionally include classes in non-bean archives.
                BeanArchiveMode currentMode = knowledgeBase.getBeanArchiveMode(clazz);
                if (!(BeanArchiveMode.NONE.equals(effectiveMode) && !BeanArchiveMode.NONE.equals(currentMode))) {
                    knowledgeBase.add(clazz, effectiveMode);
                }
                return;
            }
            throw new NonPortableBehaviourException(
                    "Non-portable behavior: bean class " + clazz.getName() +
                            " was already discovered in another bean archive");
        }
        knowledgeBase.add(clazz, effectiveMode);
        if (explicitlyAdded) {
            knowledgeBase.explicitlyAddedDiscoveredClasses.add(clazz);
        }
    }

    /**
     * Registers a parsed beans.xml configuration for managed bootstrap environments.
     *
     * <p>This is used when class discovery is delegated to an application server and
     * beans.xml parsing is performed externally.
     *
     * @param beansXml parsed beans.xml configuration
     */
    public void addBeansXmlConfiguration(BeansXml beansXml) {
        if (initialized) {
            throw new IllegalStateException("Container already initialized");
        }
        knowledgeBase.addBeansXml(beansXml);
    }

    /**
     * Registers CDI built-in beans required by the spec.
     */
    private void registerBuiltInBeans() {
        knowledgeBase.addBean(new BeanManagerBean(beanManager));
        knowledgeBase.addBean(new InjectionPointBean());
        knowledgeBase.addBean(new InterceptionFactoryBean());
        knowledgeBase.addBean(new ConversationBean(beanManager));
        knowledgeBase.addBean(new RequestContextControllerBean(contextManager));
        knowledgeBase.add(ActivateRequestContextInterceptor.class, BeanArchiveMode.IMPLICIT);
    }

    /**
     * PHASE 2-6: BEAN PROCESSING AND VALIDATION.
     *
     * <p>Completes the CDI 4.1 lifecycle:
     * <ul>
     *   <li>Fires {@code ProcessAnnotatedType} events</li>
     *   <li>Validates and registers beans</li>
     *   <li>Fires {@code ProcessInjectionPoint}, {@code ProcessInjectionTarget}, etc.</li>
     *   <li>Fires {@code AfterBeanDiscovery} and {@code AfterDeploymentValidation}</li>
     * </ul>
     *
     * @throws DeploymentException if validation fails
     */
    public void start() {
        if (initialized) {
            throw new IllegalStateException("Container already initialized");
        }

        try {
            applyForcedArchiveModeOverride();
            applyBeansXmlAllModeOverrideForExactPackageBootstrap();

            // ============================================================
            // PHASE 2 (CONT): PROCESS DISCOVERED TYPES
            // ============================================================
            info("Phase 2: Processing Discovered Types");

            // Step 2.3: Fire ProcessAnnotatedType<T> for each discovered type
            // Extensions can:
            // - Veto types from becoming beans
            // - Add/remove/modify annotations
            // - Wrap AnnotatedType to customize metadata
            processAnnotatedTypes();
            buildCompatibleExtensionsManager.fireBuildCompatibleExtensionPhase(BceSupportedPhase.ENHANCEMENT);
            extensionsManager.fireAfterTypeDiscovery();

            // ============================================================
            // PHASE 3: BEAN PROCESSING
            // ============================================================
            info("Phase 3: Bean Processing");

            // Step 3.1: Validate beans and build Bean<?> objects
            // - Check constructor eligibility
            // - Validate injection points
            // - Check scope, qualifiers, stereotypes
            validateAndRegisterBeans();
            initializeBeanDependencyResolvers();

            // Step 3.2: Fire ProcessInjectionPoint<T, X> events
            // Extensions can modify injection point metadata
            processInjectionPoints();

            // Step 3.3: Fire ProcessInjectionTarget<T> events
            // Extensions can wrap InjectionTarget to customize instantiation/injection
            processInjectionTargets();

            // Step 3.4: Fire ProcessProducer<T, X> events
            // Extensions can wrap Producer to customize production logic
            processProducerEvents();

            // Step 3.5: Fire ProcessBeanAttributes<T> events
            // Extensions can modify bean attributes (scope, qualifiers, stereotypes, name)
            processBeanAttributes();

            // Step 3.6: Fire ProcessBean events
            // - ProcessManagedBean<T> for managed beans
            // - ProcessProducerMethod<T, X> for producer methods
            // - ProcessProducerField<T, X> for producer fields
            processBean();

            // Step 3.7: Fire ProcessProducerMethod/Field events
            processProducers();

            // Step 3.8: Fire ProcessObserverMethod<T, X> events
            // Extensions can modify observer method metadata
            processObserverMethods();
            buildCompatibleExtensionsManager.fireBuildCompatibleExtensionPhase(BceSupportedPhase.REGISTRATION);

            // ============================================================
            // PHASE 4: AFTER BEAN DISCOVERY
            // ============================================================
            info("Phase 4: After Bean Discovery");

            // Step 4.1: Fire AfterBeanDiscovery event
            // Extensions can:
            // - Register additional beans programmatically
            // - Register custom contexts
            // - Add observer methods programmatically
            // - Register interceptors and decorators
            fireAfterBeanDiscovery();
            buildCompatibleExtensionsManager.fireBuildCompatibleExtensionPhase(BceSupportedPhase.SYNTHESIS);

            // Extensions may add beans programmatically during AfterBeanDiscovery.
            // Re-apply dependency resolver wiring to cover newly registered BeanImpl/ProducerBean instances.
            initializeBeanDependencyResolvers();
            // CDI 4.1 §13.5.2: after synthesis, run registration callbacks for newly registered synthetic components.
            buildCompatibleExtensionsManager.fireBuildCompatibleExtensionPhase(BceSupportedPhase.REGISTRATION);

            // ============================================================
            // PHASE 5: VALIDATION
            // ============================================================
            info("Phase 5: Deployment Validation");

            // Step 5.1: Perform deployment validation
            // - Check for unsatisfied dependencies
            // - Check for ambiguous dependencies
            // - Validate decorators and interceptors
            // - Validate specialization
            // - Validate alternatives
            validateDeployment();
            buildCompatibleExtensionsManager.fireBuildCompatibleExtensionPhase(BceSupportedPhase.VALIDATION);
            // Re-validate after BCE @Validation; calls to Messages.error(...) must become deployment problems.
            validateDeployment();

            // Step 5.2: Fire AfterDeploymentValidation event
            // Extensions can perform final validation checks
            // Any deployment problems detected here will prevent application startup
            fireAfterDeploymentValidation();

            // Fire built-in application context initialized event after deployment is fully validated
            // so all observers are discovered and ready to receive it.
            contextManager.fireApplicationContextInitialized();

            // CDI 4.1 9.6.1: fire Startup after application context initialization.
            Set<Annotation> startupQualifiers = new HashSet<>();
            startupQualifiers.add(Any.Literal.INSTANCE);
            new EventImpl<Startup>(Startup.class, startupQualifiers, knowledgeBase, beanManager.getBeanResolver(),
                    contextManager, beanManager.getBeanResolver().getTransactionServices(),
                    null, null, true).fire(new Startup());

            // ============================================================
            // PHASE 6: APPLICATION READY
            // ============================================================
            info("Phase 6: Application Ready");

            // Ensure CDI.current() can resolve this container in managed bootstrap paths
            // (e.g., WildFly integration) where no explicit global provider registration occurs.
            SyringeCDIProvider.ensureProviderConfigured();
            SyringeCDIProvider.registerGlobalCDI(getCDI());
            ClientProxyGenerator.registerContainer(beanManager.getRegistrationClassLoader(), beanManager, contextManager);

            initialized = true;
            info("Container initialization complete");

        } catch (DefinitionException e) {
            throw e;
        } catch (Exception e) {
            throw new DeploymentException("Container initialization failed", e);
        }
    }

    /**
     * Wires runtime dependency resolution into beans that perform reflective injection/production.
     *
     * <p>Both managed beans ({@link BeanImpl}) and producer beans ({@link ProducerBean})
     * delegate dependency lookup to {@link BeanResolver} during instance creation.
     */
    private void initializeBeanDependencyResolvers() {
        BeanResolver beanResolver = beanManager.getBeanResolver();
        InterceptorResolver interceptorResolver = new InterceptorResolver(knowledgeBase);
        InterceptorAwareProxyGenerator interceptorAwareProxyGenerator = new InterceptorAwareProxyGenerator();
        DecoratorResolver decoratorResolver = new DecoratorResolver(knowledgeBase);
        DecoratorAwareProxyGenerator decoratorAwareProxyGenerator = new DecoratorAwareProxyGenerator();
        runtimeInterceptorAwareProxyGenerator = interceptorAwareProxyGenerator;
        runtimeDecoratorAwareProxyGenerator = decoratorAwareProxyGenerator;

        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (bean instanceof BeanImpl<?>) {
                BeanImpl<?> beanImpl = (BeanImpl<?>) bean;
                beanImpl.setDependencyResolver(beanResolver);
                beanImpl.setInterceptorResolver(interceptorResolver);
                beanImpl.setKnowledgeBase(knowledgeBase);
                beanImpl.setInterceptorAwareProxyGenerator(interceptorAwareProxyGenerator);
                beanImpl.setDecoratorResolver(decoratorResolver);
                beanImpl.setDecoratorAwareProxyGenerator(decoratorAwareProxyGenerator);
                beanImpl.setBeanManager(beanManager);
                beanImpl.buildMethodInterceptorChains();
            } else if (bean instanceof ProducerBean<?>) {
                ((ProducerBean<?>) bean).setDependencyResolver(beanResolver);
            }
        }

        contextManager.setRequestContextLifecycleListener(new ContextManager.RequestContextLifecycleListener() {
            @Override
            public void onInitialized() {
                beanManager.getEvent()
                        .select(Object.class, Initialized.Literal.of(RequestScoped.class), Any.Literal.INSTANCE)
                        .fire(new Object());
            }

            @Override
            public void onBeforeDestroyed() {
                beanManager.getEvent()
                        .select(Object.class, BeforeDestroyed.Literal.of(RequestScoped.class), Any.Literal.INSTANCE)
                        .fire(new Object());
            }

            @Override
            public void onDestroyed() {
                beanManager.getEvent()
                        .select(Object.class, Destroyed.Literal.of(RequestScoped.class), Any.Literal.INSTANCE)
                        .fire(new Object());
            }
        });

        contextManager.setApplicationContextLifecycleListener(new ContextManager.ApplicationContextLifecycleListener() {
            @Override
            public void onInitialized() {
                beanManager.getEvent()
                        .select(Object.class, Initialized.Literal.of(ApplicationScoped.class), Any.Literal.INSTANCE)
                        .fire(new Object());
            }

            @Override
            public void onBeforeDestroyed() {
                beanManager.getEvent()
                        .select(Object.class, BeforeDestroyed.Literal.of(ApplicationScoped.class), Any.Literal.INSTANCE)
                        .fire(new Object());
            }

            @Override
            public void onDestroyed() {
                beanManager.getEvent()
                        .select(Object.class, Destroyed.Literal.of(ApplicationScoped.class), Any.Literal.INSTANCE)
                        .fire(new Object());
            }
        });
    }

    /**
     * Shuts down the CDI container and destroys all beans.
     *
     * <p><b>CDI 4.1 Shutdown Process:</b>
     * <ol>
     *   <li>Fire BeforeShutdown event to all extensions</li>
     *   <li>Destroy all context instances (call @PreDestroy on all beans)</li>
     *   <li>Clear all caches and references</li>
     * </ol>
     */
    public void shutdown() {
        if (shutdownStarted) {
            return;
        }
        shutdownStarted = true;
        try {
            if (!initialized) {
                return;
            }

            info("Shutting down container");

            // CDI 4.1 9.6.2: fire Shutdown during container shutdown and not later than
            // @BeforeDestroyed(ApplicationScoped.class).
            Set<Annotation> shutdownQualifiers = new HashSet<>();
            shutdownQualifiers.add(Any.Literal.INSTANCE);
            new EventImpl<Shutdown>(Shutdown.class, shutdownQualifiers, knowledgeBase, beanManager.getBeanResolver(),
                    contextManager, beanManager.getBeanResolver().getTransactionServices(),
                    null, null, true).fire(new Shutdown());

            // Destroy all beans (call @PreDestroy methods)
            destroyAllBeans();

            // Fire BeforeShutdown as the final lifecycle event after contexts are destroyed.
            fireBeforeShutdown();
        } finally {
            cleanupStaticState();
            cleanupInstanceState();
            // Clear state
            extensionsManager.clear();
            buildCompatibleExtensionsManager.clear();
            initialized = false;
            info("Container shutdown complete");
        }
    }

    private void cleanupStaticState() {
        ClassLoader classLoader = null;
        if (beanManager != null) {
            classLoader = beanManager.getRegistrationClassLoader();
            beanManager.unregisterFromGlobalRegistries();
        }
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        if (classLoader == null) {
            classLoader = Syringe.class.getClassLoader();
        }

        ClientProxyGenerator.unregisterContainer(classLoader, beanManager, contextManager);
        InterceptorAwareProxyGenerator.clearTargetAroundInvokeCache();
        InterceptorAwareProxyGenerator.clearTargetAroundInvokeCacheForClassLoader(classLoader);
        BeansXmlParser.clearJaxbContextCacheForClassLoader(classLoader);
        if (dynamicAnnotationsRetained && dynamicAnnotationClassLoader != null) {
            DynamicAnnotationRegistry.releaseDynamicAnnotationsForClassLoader(dynamicAnnotationClassLoader);
        }
        ConversationImpl.clearAllGlobalState();
        ConversationPropagationRegistry.clear();
        DestroyedInstanceTracker.clear();
        EventImpl.clearStaticState();
        SyringeCDIProvider.unregisterThreadLocalCDI();
        SyringeCDIProvider.unregisterGlobalCDI();
    }

    private void cleanupInstanceState() {
        // Release instance registries and BCE handles.
        extensionsManager.clear();
        buildCompatibleExtensionsManager.clear();
        customContextsToRegister.clear();
        knowledgeBase.processedSyntheticAnnotatedTypeIds.clear();
        knowledgeBase.syntheticAnnotatedTypeClasses.clear();
        knowledgeBase.explicitlyAddedDiscoveredClasses.clear();
        knowledgeBase.additionalAnnotatedTypesForDiscoveredClasses.clear();
        deferredProducerReplacements.clear();

        // Drop runtime metadata references eagerly.
        knowledgeBase.clearAllState();

        if (beanManager != null) {
            try {
                beanManager.clearRuntimeState();
            } catch (Exception ignored) {
                // Best-effort cleanup.
            }
        }
        if (runtimeInterceptorAwareProxyGenerator != null) {
            runtimeInterceptorAwareProxyGenerator.clearCache();
            runtimeInterceptorAwareProxyGenerator = null;
        }
        if (runtimeDecoratorAwareProxyGenerator != null) {
            runtimeDecoratorAwareProxyGenerator.clearCache();
            runtimeDecoratorAwareProxyGenerator = null;
        }
        dynamicAnnotationsRetained = false;
        dynamicAnnotationClassLoader = null;
        beanManager = null;
    }

    // ============================================================
    // PHASE 2: BEAN DISCOVERY EVENTS
    // ============================================================

    /**
     * Fires ProcessAnnotatedType<T> event for each discovered type.
     *
     * <p>Extensions can:
     * <ul>
     *   <li>Veto the type via veto()</li>
     *   <li>Modify the AnnotatedType via setAnnotatedType()</li>
     *   <li>Add/remove/change annotations</li>
     * </ul>
     */
    private void processAnnotatedTypes() {
        info("Processing annotated types");

        // Create exclude filter from all beans.xml configurations
        ExcludeFilter excludeFilter = new ExcludeFilter(knowledgeBase.getBeansXmlConfigurations());

        // For each discovered class:
        // 1. Check if excluded by beans.xml scan filters
        // 2. Create AnnotatedType<T> using BeanManager
        // 3. Create ProcessAnnotatedType<T> event
        // 4. Fire to all extensions
        // 5. If vetoed, mark in KnowledgeBase
        // 6. If modified, use modified AnnotatedType

        int excludedCount = 0;
        for (Class<?> clazz : new ArrayList<>(knowledgeBase.getClasses())) {
            try {
                if (knowledgeBase.syntheticAnnotatedTypeClasses.contains(clazz)) {
                    continue;
                }
                if (shouldBypassProcessAnnotatedTypeEvent(clazz)) {
                    continue;
                }
                // Step 1: Check if a class is excluded by beans.xml scan filters
                if (excludeFilter.isExcluded(clazz.getName())) {
                    knowledgeBase.vetoType(clazz);
                    excludedCount++;
                    continue;
                }
                if (shouldSkipProcessAnnotatedTypeEvent(clazz)) {
                    knowledgeBase.vetoType(clazz);
                    continue;
                }
                if (!knowledgeBase.shouldIncludeTypeInDiscoveryForArchiveMode(clazz)) {
                    continue;
                }

                // Step 2: Create AnnotatedType for the class using BeanManager
                AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(clazz);

                // Step 3: Create ProcessAnnotatedType event
                ProcessAnnotatedTypeImpl<?> event = new ProcessAnnotatedTypeImpl<>(messageHandler, annotatedType);

                // Step 4: Fire to all extensions
                extensionsManager.fireEventToExtensions(event);

                // Step 5: If vetoed, mark the type in KnowledgeBase
                if (event.isVetoed()) {
                    info("Type vetoed by extension: " + clazz.getName());
                    knowledgeBase.vetoType(clazz);
                }

                // Store AnnotatedType override only when extensions replace/configure metadata.
                // Keeping default BeanManager.createAnnotatedType() output as an override can
                // unintentionally change baseline annotation inheritance behavior during validation.
                AnnotatedType<?> finalAnnotatedType = event.getAnnotatedTypeInternal();
                if (finalAnnotatedType != null && finalAnnotatedType != annotatedType) {
                    knowledgeBase.setAnnotatedTypeOverride(clazz, finalAnnotatedType);
                }
            } catch (DefinitionException e) {
                throw e;
            } catch (Exception e) {
                throw new DefinitionException("Error processing annotated type: " + clazz.getName(), e);
            }
        }

        info("Excluded by beans.xml filters: " + excludedCount);
        info("Vetoed types (total): " + knowledgeBase.getVetoedTypes().size());
    }

    private boolean shouldBypassProcessAnnotatedTypeEvent(Class<?> clazz) {
        return ActivateRequestContextInterceptor.class.equals(clazz);
    }

    // ============================================================
    // PHASE 3: BEAN PROCESSING
    // ============================================================

    /**
     * Validates all discovered beans and registers them in the KnowledgeBase.
     *
     * <p>This uses CDI41BeanValidator to validate:
     * <ul>
     *   <li>Bean class eligibility</li>
     *   <li>Constructor requirements</li>
     *   <li>Injection point validity</li>
     *   <li>Scope correctness</li>
     *   <li>Producer method/field validity</li>
     * </ul>
     */
    private void validateAndRegisterBeans() {
        info("Validating and registering beans");

        CDI41BeanValidator validator = new CDI41BeanValidator(
                knowledgeBase,
                cdiFullLegacyInterceptionEnabled
        );
        int validated = 0;

        for (Class<?> clazz : knowledgeBase.getClasses()) {
            try {
                // Effective mode honors forced override when configured; otherwise uses
                // the mode detected during scanning and recorded in KnowledgeBase.
                BeanArchiveMode mode = knowledgeBase.getEffectiveBeanArchiveMode(knowledgeBase.getBeanArchiveMode(clazz));
                AnnotatedType<?> override = knowledgeBase.getAnnotatedTypeOverride(clazz);
                validator.validateAndRegisterRaw(clazz, mode, override);
                validated++;
            } catch (DefinitionException e) {
                throw e;
            } catch (Exception e) {
                log("Error validating bean class " + clazz.getName(), e);
            }
        }

        validated += registerAdditionalAnnotatedTypeBeans(validator);

        info("Validated " + validated + " class(es); registered " + knowledgeBase.getBeans().size() + " bean(s)");
    }

    private int registerAdditionalAnnotatedTypeBeans(CDI41BeanValidator validator) {
        if (knowledgeBase.additionalAnnotatedTypesForDiscoveredClasses.isEmpty()) {
            return 0;
        }

        int registered = 0;
        for (Map.Entry<String, AnnotatedType<?>> entry : knowledgeBase.additionalAnnotatedTypesForDiscoveredClasses.entrySet()) {
            String id = entry.getKey();
            AnnotatedType<?> annotatedType = entry.getValue();
            if (annotatedType == null) {
                continue;
            }

            Class<?> clazz = annotatedType.getJavaClass();
            if (clazz == null || knowledgeBase.isTypeVetoed(clazz)) {
                continue;
            }

            try {
                BeanArchiveMode mode = knowledgeBase.getEffectiveBeanArchiveMode(knowledgeBase.getBeanArchiveMode(clazz));
                BeanImpl<?> bean = validator.validateAndRegisterRaw(clazz, mode, annotatedType);
                if (bean != null) {
                    registered++;
                }
            } catch (DefinitionException e) {
                throw e;
            } catch (Exception e) {
                throw new DefinitionException(
                        "Error registering additional AnnotatedType bean " + clazz.getName() +
                                " (ID: " + id + ")", e);
            }
        }

        if (registered > 0) {
            info("Registered " + registered + " additional bean(s) from registered AnnotatedTypes");
        }

        return registered;
    }

    /**
     * Fires ProcessInjectionPoint<T, X> events for all discovered injection points.
     *
     * <p>Extensions can modify injection point metadata.
     */
    private void processInjectionPoints() {
        info("Processing injection points");

        Map<Class<?>, Bean<?>> lifecycleMethodDispatchBeans = resolveLifecycleMethodDispatchBeans();

        for (Bean<?> bean : knowledgeBase.getBeans()) {
            try {
                processInjectionPointsForBean(bean, bean.getInjectionPoints());
            } catch (DefinitionException e) {
                throw e;
            } catch (Exception e) {
                throw new DefinitionException("Error processing injection points for bean " +
                        bean.getBeanClass().getName(), e);
            }
        }

        for (Bean<?> bean : lifecycleMethodDispatchBeans.values()) {
            try {
                processLifecycleMethodInjectionPoints(bean);
            } catch (DefinitionException e) {
                throw e;
            } catch (Exception e) {
                throw new DefinitionException("Error processing lifecycle method injection points for bean " +
                        bean.getBeanClass().getName(), e);
            }
        }

        processInterceptorAndDecoratorInjectionPoints();
    }

    private Map<Class<?>, Bean<?>> resolveLifecycleMethodDispatchBeans() {
        Map<Class<?>, Bean<?>> lifecycleMethodDispatchBeans = new LinkedHashMap<>();
        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (bean == null || bean.getBeanClass() == null) {
                continue;
            }
            Class<?> beanClass = bean.getBeanClass();
            Bean<?> existing = lifecycleMethodDispatchBeans.get(beanClass);
            if (existing == null ||
                    (!(existing instanceof BeanImpl<?>) && bean instanceof BeanImpl<?>)) {
                lifecycleMethodDispatchBeans.put(beanClass, bean);
            }
        }
        return lifecycleMethodDispatchBeans;
    }

    private Set<InjectionPoint> processInjectionPointsForBean(Bean<?> bean, Set<InjectionPoint> injectionPoints) {
        if (injectionPoints == null || injectionPoints.isEmpty()) {
            return Collections.emptySet();
        }
        List<InjectionPoint> snapshot = new ArrayList<>(injectionPoints);
        Set<InjectionPoint> updatedPoints = new LinkedHashSet<>();
        for (InjectionPoint ip : snapshot) {
            ProcessInjectionPointImpl<?, ?> event =
                    new ProcessInjectionPointImpl<>(messageHandler, ip, knowledgeBase);

            extensionsManager.fireEventToExtensions(event);
            InjectionPoint updated = event.getInjectionPointInternal();
            if (updated == null) {
                updated = ip;
            }
            updatedPoints.add(updated);

            if (bean != null) {
                if (updated != ip) {
                    updateInjectionPoint(bean, ip, updated);
                }
            }
        }
        return updatedPoints;
    }

    private void processLifecycleMethodInjectionPoints(Bean<?> bean) {
        if (bean == null || bean.getBeanClass() == null) {
            return;
        }

        Map<String, Method> methodsBySignature = new LinkedHashMap<>();
        Class<?> current = bean.getBeanClass();
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                String signature = method.getName() + Arrays.toString(method.getParameterTypes());
                methodsBySignature.putIfAbsent(signature, method);
            }
            current = current.getSuperclass();
        }

        for (Method method : methodsBySignature.values()) {
            boolean observerMethod = false;
            for (Parameter parameter : method.getParameters()) {
                if (hasObservesAnnotation(parameter) || hasObservesAsyncAnnotation(parameter)) {
                    observerMethod = true;
                }
            }
            // Disposer method parameters are already exposed as ProducerBean injection points.
            // Only observer method non-event parameters need dedicated lifecycle dispatch here.
            if (!observerMethod) {
                continue;
            }

            for (Parameter parameter : method.getParameters()) {
                if (hasDisposesAnnotation(parameter)
                        || hasObservesAnnotation(parameter)
                        || hasObservesAsyncAnnotation(parameter)) {
                    continue;
                }
                InjectionPoint ip = new InjectionPointImpl<>(parameter, bean);
                ProcessInjectionPointImpl<?, ?> event =
                        new ProcessInjectionPointImpl<>(messageHandler, ip, knowledgeBase);
                extensionsManager.fireEventToExtensions(event);
            }
        }
    }

    private void processInterceptorAndDecoratorInjectionPoints() {
        Set<Class<?>> lifecycleTypes = new LinkedHashSet<>();
        lifecycleTypes.addAll(knowledgeBase.getInterceptors());
        lifecycleTypes.addAll(knowledgeBase.getDecorators());
        for (InterceptorInfo interceptorInfo : knowledgeBase.getInterceptorInfos()) {
            if (interceptorInfo != null && interceptorInfo.getInterceptorClass() != null) {
                lifecycleTypes.add(interceptorInfo.getInterceptorClass());
            }
        }
        for (DecoratorInfo decoratorInfo : knowledgeBase.getDecoratorInfos()) {
            if (decoratorInfo != null && decoratorInfo.getDecoratorClass() != null) {
                lifecycleTypes.add(decoratorInfo.getDecoratorClass());
            }
        }

        for (Class<?> lifecycleType : lifecycleTypes) {
            try {
                Bean<?> lifecycleBean = resolveLifecycleTypeInjectionPointBean(lifecycleType);
                AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(lifecycleType);
                InjectionTargetFactory<?> factory = new InjectionTargetFactoryImpl<>(annotatedType, beanManager);
                @SuppressWarnings({"rawtypes", "unchecked"})
                InjectionTarget<?> injectionTarget = factory.createInjectionTarget((Bean) lifecycleBean);
                Set<InjectionPoint> updated = processInjectionPointsForBean(lifecycleBean, injectionTarget.getInjectionPoints());
                applyLifecycleTypeInjectionPointUpdates(lifecycleType, updated);
            } catch (DefinitionException e) {
                throw e;
            } catch (Exception e) {
                throw new DefinitionException("Error processing injection points for lifecycle type " +
                        lifecycleType.getName(), e);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Bean<?> resolveLifecycleTypeInjectionPointBean(Class<?> lifecycleType) {
        if (lifecycleType == null) {
            return null;
        }
        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (bean != null && lifecycleType.equals(bean.getBeanClass())) {
                return bean;
            }
        }
        BeanImpl syntheticBean = new BeanImpl(lifecycleType, false);
        syntheticBean.setTypes(TypeClosureHelper.extractTypesFromClass(lifecycleType));
        return syntheticBean;
    }

    private void applyLifecycleTypeInjectionPointUpdates(Class<?> lifecycleType, Set<InjectionPoint> updatedInjectionPoints) {
        if (lifecycleType == null || updatedInjectionPoints == null || updatedInjectionPoints.isEmpty()) {
            return;
        }
        if (knowledgeBase.getDecorators().contains(lifecycleType)) {
            refreshDecoratorInfoFromInjectionPoints(lifecycleType, updatedInjectionPoints);
        }
    }

    private void refreshDecoratorInfoFromInjectionPoints(Class<?> decoratorClass, Set<InjectionPoint> updatedInjectionPoints) {
        DecoratorInfo existing = null;
        for (DecoratorInfo info : knowledgeBase.getDecoratorInfos()) {
            if (info != null && decoratorClass.equals(info.getDecoratorClass())) {
                existing = info;
                break;
            }
        }

        List<InjectionPoint> delegateInjectionPoints = new ArrayList<>();
        for (InjectionPoint injectionPoint : updatedInjectionPoints) {
            if (injectionPoint != null && injectionPoint.isDelegate()) {
                delegateInjectionPoints.add(injectionPoint);
            }
        }

        int delegateCount = delegateInjectionPoints.size();
        InjectionPoint delegateInjectionPoint = delegateCount > 0 ? delegateInjectionPoints.get(0) : null;
        if (delegateInjectionPoint == null && existing != null) {
            delegateInjectionPoint = existing.getDelegateInjectionPoint();
            if (delegateInjectionPoint != null) {
                // Preserve previous metadata when lifecycle injection-point extraction omits constructor parameters.
                delegateCount = 1;
            }
        }
        if (delegateInjectionPoint == null) {
            return;
        }

        Set<Type> decoratedTypes = existing != null
                ? existing.getDecoratedTypes()
                : extractDecoratorDecoratedTypes(decoratorClass);
        if (decoratedTypes == null || decoratedTypes.isEmpty()) {
            return;
        }

        int priority = existing != null ? existing.getPriority() : Integer.MAX_VALUE;
        Integer configuredPriority = knowledgeBase.getEffectivePriority(decoratorClass);
        if (configuredPriority != null) {
            priority = configuredPriority;
        }

        if (existing != null) {
            knowledgeBase.getDecoratorInfos().remove(existing);
        }
        knowledgeBase.addDecoratorInfo(new DecoratorInfo(decoratorClass, decoratedTypes, priority, delegateInjectionPoint));
        clearDecoratorDelegateValidationErrors(decoratorClass, delegateCount == 1);
        if (delegateCount == 1) {
            revalidateDecoratorDelegateTypeCompatibility(decoratorClass, decoratedTypes, delegateInjectionPoint);
        }
    }

    private void clearDecoratorDelegateValidationErrors(Class<?> decoratorClass, boolean clearMissingDelegateError) {
        if (decoratorClass == null) {
            return;
        }
        String delegateTypePrefix = decoratorClass.getName() + ": delegate type ";
        String missingDelegatePrefix = decoratorClass.getName() +
                ": Decorator must have exactly one @Delegate injection point (found 0).";
        knowledgeBase.getDefinitionErrors().removeIf(definitionError -> definitionError.startsWith(delegateTypePrefix) ||
                (clearMissingDelegateError && definitionError.startsWith(missingDelegatePrefix)));
    }

    private void revalidateDecoratorDelegateTypeCompatibility(Class<?> decoratorClass,
                                                              Set<Type> decoratedTypes,
                                                              InjectionPoint delegateInjectionPoint) {
        if (decoratorClass == null || delegateInjectionPoint == null ||
                decoratedTypes == null || decoratedTypes.isEmpty()) {
            return;
        }
        Type delegateType = delegateInjectionPoint.getType();
        if (delegateType == null) {
            return;
        }

        for (Type decoratedType : decoratedTypes) {
            if (!decoratorDelegateTypeCoversDecoratedType(delegateType, decoratedType)) {
                knowledgeBase.addDefinitionError(decoratorClass.getName() +
                        ": delegate type " + delegateType.getTypeName() +
                        " does not implement/extend decorated type " + decoratedType.getTypeName() +
                        " with matching type parameters");
            }
        }
    }

    private boolean decoratorDelegateTypeCoversDecoratedType(Type delegateType, Type decoratedType) {
        Class<?> delegateRaw = extractRawClass(delegateType);
        Class<?> decoratedRaw = extractRawClass(decoratedType);
        if (delegateRaw == null || decoratedRaw == null || !decoratedRaw.isAssignableFrom(delegateRaw)) {
            return false;
        }

        if (!(decoratedType instanceof ParameterizedType)) {
            return true;
        }

        Type viewOnDecoratedRaw = findDecoratorTypeInHierarchy(delegateType, decoratedRaw, new HashSet<>());
        if (viewOnDecoratedRaw == null) {
            return false;
        }

        return decoratorBeanTypeAssignableToDelegateType(decoratedType, viewOnDecoratedRaw);
    }

    private boolean decoratorBeanTypeAssignableToDelegateType(Type beanType, Type delegateType) {
        if (beanType == null || delegateType == null) {
            return false;
        }

        Class<?> beanRaw = extractRawClass(beanType);
        Class<?> delegateRaw = extractRawClass(delegateType);
        if (delegateRaw == null || !delegateRaw.equals(beanRaw)) {
            return false;
        }

        if (beanType instanceof Class && delegateType instanceof ParameterizedType) {
            for (Type delegateArg : ((ParameterizedType) delegateType).getActualTypeArguments()) {
                if (!Object.class.equals(delegateArg)) {
                    return false;
                }
            }
            return true;
        }

        if (beanType instanceof ParameterizedType && delegateType instanceof ParameterizedType) {
            Type[] beanArgs = ((ParameterizedType) beanType).getActualTypeArguments();
            Type[] delegateArgs = ((ParameterizedType) delegateType).getActualTypeArguments();
            if (beanArgs.length != delegateArgs.length) {
                return false;
            }
            for (int i = 0; i < beanArgs.length; i++) {
                if (!decoratorMatchesDelegateParameter(beanArgs[i], delegateArgs[i])) {
                    return false;
                }
            }
            return true;
        }

        if (beanType instanceof Class && delegateType instanceof Class) {
            return true;
        }

        return beanType.equals(delegateType);
    }

    private boolean decoratorMatchesDelegateParameter(Type beanParam, Type delegateParam) {
        if (decoratorIsActualType(beanParam) && decoratorIsActualType(delegateParam)) {
            Class<?> beanRaw = extractRawClass(beanParam);
            Class<?> delegateRaw = extractRawClass(delegateParam);
            if (beanRaw == null || !beanRaw.equals(delegateRaw)) {
                return false;
            }
            if (beanParam instanceof ParameterizedType && delegateParam instanceof ParameterizedType) {
                return decoratorBeanTypeAssignableToDelegateType(beanParam, delegateParam);
            }
            return true;
        }

        if (delegateParam instanceof WildcardType && decoratorIsActualType(beanParam)) {
            return decoratorWildcardMatches((WildcardType) delegateParam, beanParam);
        }

        if (delegateParam instanceof WildcardType && beanParam instanceof TypeVariable<?>) {
            Type beanUpperBound = decoratorFirstUpperBound((TypeVariable<?>) beanParam);
            return decoratorWildcardMatches((WildcardType) delegateParam, beanUpperBound);
        }

        if (delegateParam instanceof TypeVariable<?> && beanParam instanceof TypeVariable<?>) {
            Type delegateUpper = decoratorFirstUpperBound((TypeVariable<?>) delegateParam);
            Type beanUpper = decoratorFirstUpperBound((TypeVariable<?>) beanParam);
            return decoratorIsAssignable(beanUpper, delegateUpper);
        }

        if (delegateParam instanceof TypeVariable<?> && decoratorIsActualType(beanParam)) {
            Type delegateUpper = decoratorFirstUpperBound((TypeVariable<?>) delegateParam);
            return decoratorIsAssignable(beanParam, delegateUpper);
        }

        return false;
    }

    private boolean decoratorWildcardMatches(WildcardType wildcard, Type candidate) {
        for (Type upper : wildcard.getUpperBounds()) {
            if (!Object.class.equals(upper) && !decoratorIsAssignable(candidate, upper)) {
                return false;
            }
        }
        for (Type lower : wildcard.getLowerBounds()) {
            if (!decoratorIsAssignable(lower, candidate)) {
                return false;
            }
        }
        return true;
    }

    private boolean decoratorIsAssignable(Type from, Type to) {
        Class<?> fromRaw = extractRawClass(from);
        Class<?> toRaw = extractRawClass(to);
        return fromRaw != null && toRaw != null && toRaw.isAssignableFrom(fromRaw);
    }

    private Type decoratorFirstUpperBound(TypeVariable<?> variable) {
        Type[] bounds = variable.getBounds();
        return bounds.length == 0 ? Object.class : bounds[0];
    }

    private boolean decoratorIsActualType(Type type) {
        return type instanceof Class<?> || type instanceof ParameterizedType;
    }

    private Type findDecoratorTypeInHierarchy(Type source, Class<?> targetRaw, Set<Type> visited) {
        if (source == null || !visited.add(source)) {
            return null;
        }

        Class<?> raw = extractRawClass(source);
        if (raw == null) {
            return null;
        }
        if (raw.equals(targetRaw)) {
            return source;
        }

        for (Type type : raw.getGenericInterfaces()) {
            Type found = findDecoratorTypeInHierarchy(type, targetRaw, visited);
            if (found != null) {
                return found;
            }
        }

        return findDecoratorTypeInHierarchy(raw.getGenericSuperclass(), targetRaw, visited);
    }

    private Set<Type> extractDecoratorDecoratedTypes(Class<?> decoratorClass) {
        if (decoratorClass == null) {
            return Collections.emptySet();
        }
        Set<Type> decoratedTypes = new LinkedHashSet<>();
        for (Type type : TypeClosureHelper.extractTypesFromClass(decoratorClass)) {
            Class<?> raw = extractRawClass(type);
            if (raw == null || !raw.isInterface()) {
                continue;
            }
            if (Object.class.equals(raw)
                    || java.io.Serializable.class.equals(raw)
                    || Decorator.class.equals(raw)) {
                continue;
            }
            decoratedTypes.add(type);
        }
        return decoratedTypes;
    }

    /**
     * Fires ProcessInjectionTarget<T> events for all injection targets.
     *
     * <p>Extensions can wrap InjectionTarget to customize instantiation and injection.
     */
    private void processInjectionTargets() {
        info("Processing injection targets");

        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (bean instanceof BeanImpl<?>) {
                BeanImpl<?> managedBean = (BeanImpl<?>) bean;
                Class<?> beanClass = managedBean.getBeanClass();

                try {
                    AnnotatedType<?> annotatedType = managedBean.getAnnotatedTypeMetadata();
                    if (annotatedType == null) {
                        annotatedType = knowledgeBase.getAnnotatedTypeOverride(beanClass);
                    }
                    if (annotatedType == null) {
                        annotatedType = new SimpleAnnotatedType<>(beanClass);
                    }
                    InjectionTargetFactory<?> factory = new InjectionTargetFactoryImpl<>(annotatedType, beanManager);

                    @SuppressWarnings("unchecked")
                    InjectionTarget<Object> injectionTarget =
                            (InjectionTarget<Object>) factory.createInjectionTarget((Bean) managedBean);

                    @SuppressWarnings("unchecked")
                    ProcessInjectionTargetImpl<Object> event =
                        new ProcessInjectionTargetImpl<>(messageHandler, knowledgeBase,
                                (AnnotatedType<Object>) annotatedType, injectionTarget);

                    extensionsManager.fireEventToExtensions(event);

                    InjectionTarget<?> finalTarget = event.getInjectionTargetInternal();
                    if (finalTarget != null && finalTarget != injectionTarget) {
                        managedBean.setCustomInjectionTarget((InjectionTarget) finalTarget);
                    } else {
                        managedBean.setCustomInjectionTarget(null);
                    }
                } catch (DefinitionException e) {
                    throw e;
                } catch (Exception e) {
                    throw new DefinitionException("Error processing injection target for " +
                            beanClass.getName(), e);
                }
            }
        }

        processInterceptorAndDecoratorInjectionTargets();
    }

    private void processInterceptorAndDecoratorInjectionTargets() {
        Set<Class<?>> lifecycleTypes = new LinkedHashSet<>();
        for (InterceptorInfo interceptorInfo : knowledgeBase.getInterceptorInfos()) {
            if (interceptorInfo != null && interceptorInfo.getInterceptorClass() != null) {
                lifecycleTypes.add(interceptorInfo.getInterceptorClass());
            }
        }
        for (DecoratorInfo decoratorInfo : knowledgeBase.getDecoratorInfos()) {
            if (decoratorInfo != null && decoratorInfo.getDecoratorClass() != null) {
                lifecycleTypes.add(decoratorInfo.getDecoratorClass());
            }
        }

        for (Class<?> lifecycleType : lifecycleTypes) {
            try {
                AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(lifecycleType);
                InjectionTargetFactory<?> factory = new InjectionTargetFactoryImpl<>(annotatedType, beanManager);
                InjectionTarget<?> injectionTarget = factory.createInjectionTarget(null);
                @SuppressWarnings({"rawtypes", "unchecked"})
                ProcessInjectionTargetImpl<?> event = new ProcessInjectionTargetImpl(
                        messageHandler, knowledgeBase, annotatedType, injectionTarget);
                extensionsManager.fireEventToExtensions(event);
            } catch (DefinitionException e) {
                throw e;
            } catch (Exception e) {
                throw new DefinitionException("Error processing injection target for lifecycle type " +
                        lifecycleType.getName(), e);
            }
        }
    }

    /**
     * Fires ProcessBeanAttributes<T> events for all beans.
     *
     * <p>Extensions can modify bean attributes (scope, qualifiers, stereotypes, name).
     */
    private void processBeanAttributes() {
        info("Processing bean attributes");

        Set<Bean<?>> vetoed = new LinkedHashSet<>();
        Set<Class<?>> vetoedDeclaringBeanClasses = new HashSet<>();
        Set<Class<?>> processedBeanClasses = new HashSet<>();
        Set<Class<?>> specializationSuppressedBeanClasses = new HashSet<>();
        List<Bean<?>> orderedBeans = orderBeansForProcessBeanAttributes(knowledgeBase.getBeans());

        for (Bean<?> bean : orderedBeans) {
            if (!isProcessBeanAttributesCandidate(bean)) {
                continue;
            }
            Class<?> beanClass = bean.getBeanClass();
            Class<?> declaringClass = resolveProcessBeanAttributesDeclaringClass(bean);
            if (declaringClass != null) {
                if (vetoedDeclaringBeanClasses.contains(declaringClass)) {
                    vetoed.add(bean);
                    continue;
                }
                if (specializationSuppressedBeanClasses.contains(declaringClass)) {
                    vetoed.add(bean);
                    continue;
                }
            }
            if (beanClass != null) {
                processedBeanClasses.add(beanClass);
            }
            try {
                BeanAttributes<?> attrs = new BeanAttributesImpl<>(bean.getName(), bean.getQualifiers(),
                    bean.getScope(), bean.getStereotypes(), bean.getTypes(), bean.isAlternative());

                Annotated annotated = resolveProcessBeanAttributesAnnotated(bean);
                ProcessBeanAttributesImpl<?> event =
                    new ProcessBeanAttributesImpl<>(messageHandler, annotated, attrs, knowledgeBase);

                extensionsManager.fireEventToExtensions(event);

                if (event.isVetoed()) {
                    vetoed.add(bean);
                    if (bean instanceof BeanImpl<?>) {
                        Class<?> vetoedBeanClass = bean.getBeanClass();
                        if (vetoedBeanClass != null) {
                            vetoedDeclaringBeanClasses.add(vetoedBeanClass);
                        }
                    }
                    continue;
                }

                if (event.isIgnoreFinalMethods()) {
                    knowledgeBase.addWarning("ProcessBeanAttributes ignoreFinalMethods requested for " +
                                             bean.getBeanClass().getName());
                    knowledgeBase.markIgnoreFinalMethods(bean);
                    if (bean instanceof BeanImpl<?>) {
                        ((BeanImpl<?>) bean).setIgnoreFinalMethods(true);
                    }
                }

                BeanAttributes<?> finalAttrs = event.getBeanAttributesInternal();
                validateBeanAttributesFromProcessBeanAttributes(bean, finalAttrs);
                applyBeanAttributes(bean, finalAttrs);
                validateSpecializationTypeClosureAfterProcessBeanAttributes(bean, finalAttrs);

                if (bean instanceof BeanImpl<?>) {
                    Class<?> managedBeanClass = bean.getBeanClass();
                    if (hasSpecializesAnnotation(managedBeanClass) &&
                            isBeanEnabledForObserverLifecycle(bean)) {
                        specializationSuppressedBeanClasses.addAll(collectSpecializedSuperclasses(managedBeanClass));
                    }
                }

            } catch (DefinitionException e) {
                throw e;
            } catch (Exception e) {
                throw new DefinitionException("Error processing bean attributes for bean " +
                        bean.getBeanClass().getName(), e);
            }
        }

        processInterceptorAndDecoratorBeanAttributes(processedBeanClasses);

        if (!vetoedDeclaringBeanClasses.isEmpty()) {
            for (Bean<?> bean : knowledgeBase.getBeans()) {
                if (!(bean instanceof ProducerBean<?>)) {
                    continue;
                }
                Class<?> declaringClass = ((ProducerBean<?>) bean).getDeclaringClass();
                if (declaringClass != null && vetoedDeclaringBeanClasses.contains(declaringClass)) {
                    vetoed.add(bean);
                }
            }
        }

        // Remove vetoed beans
        if (!vetoed.isEmpty()) {
            knowledgeBase.getBeans().removeAll(vetoed);
            info("Vetoed " + vetoed.size() + " bean(s) via ProcessBeanAttributes");
        }
    }

    private List<Bean<?>> orderBeansForProcessBeanAttributes(Collection<Bean<?>> beans) {
        List<Bean<?>> ordered = new ArrayList<>();
        if (beans != null) {
            ordered.addAll(beans);
        }
        ordered.sort((left, right) -> {
            int leftDepth = processBeanAttributesClassDepth(left);
            int rightDepth = processBeanAttributesClassDepth(right);
            int depthCompare = Integer.compare(rightDepth, leftDepth);
            if (depthCompare != 0) {
                return depthCompare;
            }

            String leftClassName = processBeanAttributesClassName(left);
            String rightClassName = processBeanAttributesClassName(right);
            int classCompare = leftClassName.compareTo(rightClassName);
            if (classCompare != 0) {
                return classCompare;
            }

            int kindCompare = Integer.compare(processBeanAttributesBeanKindOrder(left),
                    processBeanAttributesBeanKindOrder(right));
            if (kindCompare != 0) {
                return kindCompare;
            }

            return Integer.compare(System.identityHashCode(left), System.identityHashCode(right));
        });
        return ordered;
    }

    private int processBeanAttributesClassDepth(Bean<?> bean) {
        Class<?> clazz = resolveProcessBeanAttributesDeclaringClass(bean);
        return processBeanAttributesClassDepth(clazz);
    }

    private int processBeanAttributesClassDepth(Class<?> clazz) {
        int depth = 0;
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            depth++;
            current = current.getSuperclass();
        }
        return depth;
    }

    private String processBeanAttributesClassName(Bean<?> bean) {
        Class<?> clazz = resolveProcessBeanAttributesDeclaringClass(bean);
        return clazz != null ? clazz.getName() : "";
    }

    private int processBeanAttributesBeanKindOrder(Bean<?> bean) {
        if (bean instanceof BeanImpl<?>) {
            return 0;
        }
        if (bean instanceof ProducerBean<?>) {
            return 1;
        }
        return 2;
    }

    private Class<?> resolveProcessBeanAttributesDeclaringClass(Bean<?> bean) {
        if (bean instanceof ProducerBean<?>) {
            return ((ProducerBean<?>) bean).getDeclaringClass();
        }
        return bean != null ? bean.getBeanClass() : null;
    }

    private void processInterceptorAndDecoratorBeanAttributes(Set<Class<?>> processedBeanClasses) {
        Set<Class<?>> lifecycleTypes = new LinkedHashSet<>();
        for (InterceptorInfo interceptorInfo : knowledgeBase.getInterceptorInfos()) {
            if (interceptorInfo != null && interceptorInfo.getInterceptorClass() != null) {
                lifecycleTypes.add(interceptorInfo.getInterceptorClass());
            }
        }
        for (DecoratorInfo decoratorInfo : knowledgeBase.getDecoratorInfos()) {
            if (decoratorInfo != null && decoratorInfo.getDecoratorClass() != null) {
                lifecycleTypes.add(decoratorInfo.getDecoratorClass());
            }
        }

        for (Class<?> lifecycleType : lifecycleTypes) {
            if (lifecycleType == null || processedBeanClasses.contains(lifecycleType)) {
                continue;
            }
            try {
                AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(lifecycleType);
                BeanAttributes<?> attrs = beanManager.createBeanAttributes((AnnotatedType) annotatedType);
                ProcessBeanAttributesImpl<?> event =
                        new ProcessBeanAttributesImpl<>(messageHandler, annotatedType, attrs, knowledgeBase);
                extensionsManager.fireEventToExtensions(event);
            } catch (DefinitionException e) {
                throw e;
            } catch (Exception e) {
                throw new DefinitionException("Error processing bean attributes for lifecycle type " +
                        lifecycleType.getName(), e);
            }
        }
    }

    private boolean isProcessBeanAttributesCandidate(Bean<?> bean) {
        return bean instanceof BeanImpl<?> || bean instanceof ProducerBean<?>;
    }

    private Annotated resolveProcessBeanAttributesAnnotated(Bean<?> bean) {
        if (bean instanceof ProducerBean<?>) {
            ProducerBean<?> producerBean = (ProducerBean<?>) bean;
            Class<?> declaringClass = producerBean.getDeclaringClass();
            AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(declaringClass);
            if (producerBean.isMethod()) {
                Method producerMethod = producerBean.getProducerMethod();
                AnnotatedMethod<?> annotatedMethod = findAnnotatedMethod(annotatedType, producerMethod);
                return annotatedMethod != null ? annotatedMethod : annotatedType;
            }
            if (producerBean.isField()) {
                Field producerField = producerBean.getProducerField();
                AnnotatedField<?> annotatedField = findAnnotatedField(annotatedType, producerField);
                return annotatedField != null ? annotatedField : annotatedType;
            }
            return annotatedType;
        }

        if (bean instanceof BeanImpl<?>) {
            BeanImpl<?> managedBean = (BeanImpl<?>) bean;
            AnnotatedType<?> annotatedType = managedBean.getAnnotatedTypeMetadata();
            if (annotatedType == null) {
                annotatedType = knowledgeBase.getAnnotatedTypeOverride(managedBean.getBeanClass());
            }
            if (annotatedType != null) {
                return annotatedType;
            }
        }

        return new SimpleAnnotatedType<>(bean.getBeanClass());
    }

    /**
     * Fires ProcessBean events (ProcessManagedBean, ProcessProducerMethod, ProcessProducerField, ProcessSyntheticBean).
     *
     * <p>Extensions can inspect final Bean<?> objects before deployment validation.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void processBean() {
        info("Processing beans");

        Collection<Bean<?>> allBeans = knowledgeBase.getBeans();
        info("Found " + allBeans.size() + " total bean(s)");

        Set<Class<?>> processedBeanClasses = new HashSet<>();
        int managedCount = 0;
        int producerMethodCount = 0;
        int producerFieldCount = 0;
        int syntheticCount = 0;

        for (Bean<?> bean : allBeans) {
            try {
                if (bean.getBeanClass() != null) {
                    processedBeanClasses.add(bean.getBeanClass());
                }
                // Determine the bean type and fire an appropriate event
                if (bean instanceof SyntheticBean) {
                    // Synthetic bean - registered via AfterBeanDiscovery.addBean()
                    ProcessSyntheticBeanImpl event = new ProcessSyntheticBeanImpl(messageHandler, knowledgeBase, bean,
                            null);
                    extensionsManager.fireEventToExtensions(event);
                    syntheticCount++;

                } else if (bean instanceof ProducerBean) {
                    // Producer beans are already handled by processProducers()
                    // which fires ProcessProducerMethod/ProcessProducerField
                    ProducerBean<?> producerBean = (ProducerBean<?>) bean;
                    if (producerBean.isMethod()) {
                        producerMethodCount++;
                    } else if (producerBean.isField()) {
                        producerFieldCount++;
                    }

                } else if (bean instanceof BeanImpl) {
                    // Managed bean - discovered via classpath scanning
                    BeanImpl<?> managedBean = (BeanImpl<?>) bean;
                    AnnotatedType<?> annotatedType = managedBean.getAnnotatedTypeMetadata();
                    if (annotatedType == null) {
                        annotatedType = knowledgeBase.getAnnotatedTypeOverride(managedBean.getBeanClass());
                    }
                    if (annotatedType == null) {
                        annotatedType = beanManager.createAnnotatedType(managedBean.getBeanClass());
                    }

                    @SuppressWarnings({"rawtypes", "unchecked"})
                    ProcessManagedBeanImpl<?> event = new ProcessManagedBeanImpl(messageHandler, knowledgeBase,
                         managedBean, annotatedType, beanManager);
                    extensionsManager.fireEventToExtensions(event);
                    managedCount++;

                } else {
                    // Built-in beans (BeanManager, InjectionPoint, etc.)
                    // These don't get ProcessBean events
                    info("Skipping built-in bean: " + bean.getBeanClass().getSimpleName());
                }
            } catch (DefinitionException e) {
                throw e;
            } catch (Exception e) {
                throw new DefinitionException("Error processing bean " + bean.getBeanClass().getName(), e);
            }
        }

        managedCount += processInterceptorAndDecoratorBeans(processedBeanClasses);

        info("Processed: " + managedCount + " managed, " + producerMethodCount + " producer methods, " +
                producerFieldCount + " producer fields, " + syntheticCount + " synthetic");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int processInterceptorAndDecoratorBeans(Set<Class<?>> processedBeanClasses) {
        int managedCount = 0;
        Set<Class<?>> lifecycleTypes = new LinkedHashSet<>();
        for (InterceptorInfo interceptorInfo : knowledgeBase.getInterceptorInfos()) {
            if (interceptorInfo != null && interceptorInfo.getInterceptorClass() != null) {
                lifecycleTypes.add(interceptorInfo.getInterceptorClass());
            }
        }
        for (DecoratorInfo decoratorInfo : knowledgeBase.getDecoratorInfos()) {
            if (decoratorInfo != null && decoratorInfo.getDecoratorClass() != null) {
                lifecycleTypes.add(decoratorInfo.getDecoratorClass());
            }
        }

        for (Class<?> lifecycleType : lifecycleTypes) {
            if (lifecycleType == null || processedBeanClasses.contains(lifecycleType)) {
                continue;
            }
            try {
                AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(lifecycleType);
                BeanAttributes<?> attrs = beanManager.createBeanAttributes((AnnotatedType) annotatedType);
                BeanImpl<?> syntheticManagedBean = new BeanImpl(lifecycleType, attrs.isAlternative());
                applyBeanAttributes(syntheticManagedBean, attrs);
                ProcessManagedBeanImpl<?> event = new ProcessManagedBeanImpl(
                        messageHandler, knowledgeBase, syntheticManagedBean, annotatedType, beanManager);
                extensionsManager.fireEventToExtensions(event);
                managedCount++;
            } catch (DeploymentException e) {
                throw e;
            } catch (DefinitionException e) {
                throw new DeploymentException(
                        "Error processing interceptor/decorator bean " + lifecycleType.getName(), e);
            } catch (Exception e) {
                throw new DefinitionException(
                        "Error processing interceptor/decorator bean " + lifecycleType.getName(), e);
            }
        }
        return managedCount;
    }

    /**
     * Fires ProcessProducer<T, X> events for all producers.
     *
     * <p>Extensions can wrap Producer to customize production logic.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void processProducerEvents() {
        info("Processing producer events");

        Collection<ProducerBean<?>> producers = knowledgeBase.getProducerBeans();
        info("Found " + producers.size() + " producers");

        for (ProducerBean<?> producerBean : producers) {
            if (producerBean == null || producerBean.isVetoed() || producerBean.hasValidationErrors()) {
                continue;
            }
            if (!knowledgeBase.getBeans().contains(producerBean)) {
                continue;
            }
            try {
                Class<?> declaringClass = producerBean.getDeclaringClass();
                AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(declaringClass);
                Producer producer = new ProducerBeanAdapter(producerBean);

                if (producerBean.isMethod()) {
                    Method method = producerBean.getProducerMethod();
                    AnnotatedMethod annotatedMethod = findAnnotatedMethod(annotatedType, method);
                    if (annotatedMethod == null) {
                        continue;
                    }

                    ProcessProducerImpl event = new ProcessProducerImpl(
                            messageHandler,
                            knowledgeBase,
                            Phase.PROCESS_PRODUCER_METHOD,
                            annotatedMethod,
                            producer
                    );
                    extensionsManager.fireEventToExtensions(event);

                    Producer finalProducer = event.getFinalProducer();
                    if (finalProducer != producer) {
                        info("Producer wrapped for method: " + declaringClass.getSimpleName() + "." +
                                method.getName());
                        registerDeferredProducerReplacement(producerBean, finalProducer);
                    }
                } else if (producerBean.isField()) {
                    Field field = producerBean.getProducerField();
                    AnnotatedField annotatedField = findAnnotatedField(annotatedType, field);
                    if (annotatedField == null) {
                        continue;
                    }

                    ProcessProducerImpl event = new ProcessProducerImpl(
                            messageHandler,
                            knowledgeBase,
                            Phase.PROCESS_PRODUCER_FIELD,
                            annotatedField,
                            producer
                    );
                    extensionsManager.fireEventToExtensions(event);

                    Producer finalProducer = event.getFinalProducer();
                    if (finalProducer != producer) {
                        info("Producer wrapped for field: " + declaringClass.getSimpleName() + "." +
                                field.getName());
                        registerDeferredProducerReplacement(producerBean, finalProducer);
                    }
                }
            } catch (DefinitionException e) {
                throw e;
            } catch (Exception e) {
                throw new DefinitionException("Error processing producer", e);
            }
        }
    }

    /**
     * Fires ProcessProducerMethod/ProcessProducerField events for all producers.
     *
     * <p>Extensions can inspect producer-bean metadata after ProcessBeanAttributes.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void processProducers() {
        info("Processing producer method/field events");

        Collection<ProducerBean<?>> producers = knowledgeBase.getProducerBeans();
        info("Found " + producers.size() + " producers");

        for (ProducerBean<?> producerBean : producers) {
            if (producerBean == null || producerBean.isVetoed() || producerBean.hasValidationErrors()) {
                continue;
            }
            if (!knowledgeBase.getBeans().contains(producerBean)) {
                // Skip producer beans previously removed from resolvable beans (e.g., PBA veto).
                continue;
            }
            try {
                // Get the declaring class
                Class<?> declaringClass = producerBean.getDeclaringClass();

                // Create AnnotatedType for the declaring class
                AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(declaringClass);

                if (producerBean.isMethod()) {
                    // Process producer method
                    Method method = producerBean.getProducerMethod();

                    // Find the matching AnnotatedMethod
                    AnnotatedMethod annotatedMethod = findAnnotatedMethod(annotatedType, method);

                    if (annotatedMethod != null) {
                        // Start with ProcessProducer-wrapped producer, if any.
                        Producer producer = resolveEffectiveProducerForLifecycleEvent(producerBean);
                        AnnotatedParameter<?> disposedParameter = findAnnotatedDisposedParameter(
                                annotatedType,
                                method.getGenericReturnType()
                        );

                        // Create and fire ProcessProducerMethod event
                        ProcessProducerMethodImpl event = new ProcessProducerMethodImpl(messageHandler,
                                knowledgeBase, producerBean, annotatedMethod, producer, disposedParameter);

                        extensionsManager.fireEventToExtensions(event);

                        Producer finalProducer = event.getFinalProducer();
                        if (finalProducer != producer) {
                            info("Producer wrapped for method: " + declaringClass.getSimpleName() + "." +
                                    method.getName());
                            registerDeferredProducerReplacement(producerBean, finalProducer);
                        }
                    }
                } else if (producerBean.isField()) {
                    // Process producer field
                    Field field = producerBean.getProducerField();

                    // Find the matching AnnotatedField
                    AnnotatedField annotatedField = findAnnotatedField(annotatedType, field);

                    if (annotatedField != null) {
                        // Start with ProcessProducer-wrapped producer, if any.
                        Producer producer = resolveEffectiveProducerForLifecycleEvent(producerBean);
                        AnnotatedParameter<?> disposedParameter = findAnnotatedDisposedParameter(
                                annotatedType,
                                field.getGenericType()
                        );

                        // Create and fire ProcessProducerField event
                        ProcessProducerFieldImpl event = new ProcessProducerFieldImpl(messageHandler, knowledgeBase,
                            producerBean, annotatedField, producer, disposedParameter);

                        extensionsManager.fireEventToExtensions(event);

                        Producer finalProducer = event.getFinalProducer();
                        if (finalProducer != producer) {
                            info("Producer wrapped for field: " + declaringClass.getSimpleName() + "." +
                                    field.getName());
                            registerDeferredProducerReplacement(producerBean, finalProducer);
                        }
                    }
                }
            } catch (DefinitionException e) {
                throw e;
            } catch (Exception e) {
                throw new DefinitionException("Error processing producer", e);
            }
        }

        applyDeferredProducerReplacements();
    }

    /**
     * Finds the AnnotatedMethod matching the given Method.
     */
    private AnnotatedMethod<?> findAnnotatedMethod(AnnotatedType<?> annotatedType, Method method) {
        for (AnnotatedMethod<?> am : annotatedType.getMethods()) {
            if (am.getJavaMember().equals(method)) {
                return am;
            }
        }
        return null;
    }

    /**
     * Finds the AnnotatedField matching the given Field.
     */
    private AnnotatedField<?> findAnnotatedField(AnnotatedType<?> annotatedType, Field field) {
        for (AnnotatedField<?> af : annotatedType.getFields()) {
            if (af.getJavaMember().equals(field)) {
                return af;
            }
        }
        return null;
    }

    private AnnotatedParameter<?> findAnnotatedDisposedParameter(AnnotatedType<?> annotatedType, Type producedType) {
        if (annotatedType == null || producedType == null) {
            return null;
        }
        for (AnnotatedMethod<?> method : annotatedType.getMethods()) {
            for (AnnotatedParameter<?> parameter : method.getParameters()) {
                if (!hasDisposesAnnotationInAnnotatedParameter(parameter)) {
                    continue;
                }
                if (isSameRawType(parameter.getBaseType(), producedType)) {
                    return parameter;
                }
            }
        }
        return null;
    }

    private boolean isSameRawType(Type left, Type right) {
        Class<?> leftRawType = extractRawType(left);
        Class<?> rightRawType = extractRawType(right);
        if (leftRawType != null && rightRawType != null) {
            return leftRawType.equals(rightRawType);
        }
        return Objects.equals(left, right);
    }

    private Class<?> extractRawType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class<?>) {
                return (Class<?>) rawType;
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Producer resolveEffectiveProducerForLifecycleEvent(ProducerBean<?> producerBean) {
        Producer<?> producer = deferredProducerReplacements.get(producerBean);
        if (producer != null) {
            return producer;
        }
        return new ProducerBeanAdapter(producerBean);
    }

    private void registerDeferredProducerReplacement(ProducerBean<?> producerBean, Producer<?> producer) {
        if (producerBean == null || producer == null) {
            return;
        }
        deferredProducerReplacements.put(producerBean, producer);
    }

    private void applyDeferredProducerReplacements() {
        if (deferredProducerReplacements.isEmpty()) {
            return;
        }
        for (Map.Entry<ProducerBean<?>, Producer<?>> entry :
                new ArrayList<>(deferredProducerReplacements.entrySet())) {
            replaceProducerBean(entry.getKey(), entry.getValue());
        }
        deferredProducerReplacements.clear();
    }

    /**
     * Replaces a discovered ProducerBean with a synthetic bean that delegates to the
     * final Producer selected by extensions via ProcessProducer events.
     * This ensures the container uses the wrapped/replaced Producer for lifecycle operations.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void replaceProducerBean(ProducerBean<?> original, Producer<?> finalProducer) {
        if (original == null || finalProducer == null) {
            return;
        }

        // Remove the original bean from the resolvable set
        knowledgeBase.getBeans().remove(original);

        // Create a synthetic bean that delegates create/destroy/injection points to the final producer
        Bean synthetic = new SyntheticProducerBeanImpl(original, original.getBeanClass(), finalProducer);
        knowledgeBase.addBean(synthetic);
    }

    /**
     * Simple Producer adapter that wraps a ProducerBean.
     * This allows extensions to observe and wrap producer logic.
     */
    private static class ProducerBeanAdapter<T> implements Producer<T> {
        private final ProducerBean<T> producerBean;

        ProducerBeanAdapter(ProducerBean<T> producerBean) {
            this.producerBean = producerBean;
        }

        @Override
        public T produce(CreationalContext<T> ctx) {
            return producerBean.create(ctx);
        }

        @Override
        public void dispose(T instance) {
            producerBean.destroy(instance, null);
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return producerBean.getInjectionPoints();
        }
    }

    /**
     * Fires ProcessObserverMethod<T, X> events for all observer methods.
     *
     * <p>Extensions can modify observer method metadata.
     */
    private void processObserverMethods() {
        info("Processing observer methods");

        Collection<ObserverMethodInfo> existing = new ArrayList<>(knowledgeBase.getObserverMethodInfos());
        if (existing.isEmpty()) {
            // Fallback discovery so ProcessObserverMethod can still be delivered at lifecycle time
            // even when runtime observer registration is deferred to deployment validation.
            existing = discoverObserverMethodsForLifecycleDispatch();
        }
        List<ObserverMethodInfo> updated = new ArrayList<>();

        for (ObserverMethodInfo info : existing) {
            try {
                ObserverMethod<?> observer;
                AnnotatedMethod<?> annotatedMethod = null;

                if (info.isSynthetic()) {
                    observer = info.getSyntheticObserver();
                } else {
                    Method method = info.getObserverMethod();
                    AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(method.getDeclaringClass());
                    annotatedMethod = findAnnotatedMethod(annotatedType, method);
                    observer = new ReflectiveObserverMethodAdapter<>(info,
                            new BeanResolver(knowledgeBase, contextManager),
                            contextManager);
                }

                if (info.isSynthetic()) {
                    ProcessSyntheticObserverMethodImpl<?, ?> event =
                            new ProcessSyntheticObserverMethodImpl<>(messageHandler, knowledgeBase, observer, null);
                    extensionsManager.fireEventToExtensions(event);
                    if (event.isVetoed()) {
                        continue; // remove this observer
                    }
                    ObserverMethod<?> finalObserver = event.getFinalObserverMethod();
                    if (finalObserver == observer) {
                        updated.add(info);
                    } else {
                        updated.add(toObserverMethodInfo(finalObserver, info.getDeclaringBean()));
                    }
                } else {
                    ProcessObserverMethodImpl<?, ?> event =
                            new ProcessObserverMethodImpl<>(messageHandler, knowledgeBase, observer, annotatedMethod);
                    extensionsManager.fireEventToExtensions(event);
                    if (event.isVetoed()) {
                        continue; // remove this observer
                    }
                    ObserverMethod<?> finalObserver = event.getFinalObserverMethod();
                    if (finalObserver == observer) {
                        updated.add(info);
                    } else {
                        updated.add(toObserverMethodInfo(finalObserver, info.getDeclaringBean()));
                    }
                }

            } catch (DefinitionException e) {
                throw e;
            } catch (Exception e) {
                throw new DefinitionException("Error processing observer method", e);
            }
        }

        knowledgeBase.getObserverMethodInfos().clear();
        List<ObserverMethodInfo> deduped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ObserverMethodInfo info : updated) {
            String key = observerInfoKey(info);
            if (seen.add(key)) {
                deduped.add(info);
            }
        }
        knowledgeBase.getObserverMethodInfos().addAll(deduped);
    }

    private Collection<ObserverMethodInfo> discoverObserverMethodsForLifecycleDispatch() {
        List<ObserverMethodInfo> out = new ArrayList<>();
        for (Bean<?> bean : filterObserverDeclaringBeansForLifecycleDispatch()) {
            Class<?> beanClass = bean.getBeanClass();
            for (Method method : collectObserverCandidateMethods(beanClass)) {
                ObserverMethodInfo info = toObserverInfoForLifecycleDispatch(method, bean);
                if (info != null) {
                    out.add(info);
                }
            }
        }
        return out;
    }

    private Set<Bean<?>> filterObserverDeclaringBeansForLifecycleDispatch() {
        Set<Class<?>> discoveredClasses = new HashSet<>(knowledgeBase.getClasses());
        Set<Bean<?>> candidates = new LinkedHashSet<>();

        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (!(bean instanceof BeanImpl<?>)) {
                continue;
            }
            Class<?> beanClass = bean.getBeanClass();
            if (beanClass == null || !discoveredClasses.contains(beanClass)) {
                continue;
            }
            if (beanClass.getName().startsWith(Syringe.class.getName() + "$")) {
                continue;
            }
            if (!isBeanEnabledForObserverLifecycle(bean)) {
                continue;
            }
            candidates.add(bean);
        }

        return applyObserverSpecializationFiltering(candidates);
    }

    private boolean isBeanEnabledForObserverLifecycle(Bean<?> bean) {
        if (bean == null) {
            return false;
        }
        if (!bean.isAlternative()) {
            return true;
        }
        if (bean instanceof BeanImpl<?>) {
            return ((BeanImpl<?>) bean).isAlternativeEnabled();
        }
        return true;
    }

    private Set<Bean<?>> applyObserverSpecializationFiltering(Set<Bean<?>> candidates) {
        if (candidates == null || candidates.size() < 2) {
            return candidates;
        }

        Set<Class<?>> specializedSuperclasses = new HashSet<>();
        for (Bean<?> candidate : candidates) {
            Class<?> beanClass = candidate.getBeanClass();
            if (hasSpecializesAnnotation(beanClass)) {
                specializedSuperclasses.addAll(collectSpecializedSuperclasses(beanClass));
            }
        }

        if (specializedSuperclasses.isEmpty()) {
            return candidates;
        }

        Set<Bean<?>> filtered = new LinkedHashSet<>();
        for (Bean<?> candidate : candidates) {
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

    private List<Method> collectObserverCandidateMethods(Class<?> beanClass) {
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = beanClass;
        while (current != null && !Object.class.equals(current)) {
            hierarchy.add(0, current);
            current = current.getSuperclass();
        }

        Map<String, Method> bySignature = new LinkedHashMap<>();
        for (Class<?> type : hierarchy) {
            for (Method method : type.getDeclaredMethods()) {
                String signature = observerMethodSignature(method);
                bySignature.put(signature, method);
            }
        }
        return new ArrayList<>(bySignature.values());
    }

    private String observerMethodSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getName()).append('(');
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(parameterTypes[i].getName());
        }
        sb.append(')');
        return sb.toString();
    }

    private void validateBeanAttributesFromProcessBeanAttributes(Bean<?> bean, BeanAttributes<?> attrs) {
        if (attrs == null) {
            knowledgeBase.addDefinitionError(Phase.PROCESS_BEAN_ATTRIBUTES,
                    "BeanAttributes returned from extension must not be null for " +
                            describeProcessBeanAttributesBean(bean), null);
            return;
        }

        String beanDescription = describeProcessBeanAttributesBean(bean);

        Set<Type> types = attrs.getTypes();
        if (types == null || types.isEmpty()) {
            knowledgeBase.addDefinitionError(Phase.PROCESS_BEAN_ATTRIBUTES,
                    "BeanAttributes types must not be empty for " + beanDescription, null);
        } else {
            for (Type type : types) {
                if (type == null) {
                    knowledgeBase.addDefinitionError(Phase.PROCESS_BEAN_ATTRIBUTES,
                            "BeanAttributes types must not contain null for " + beanDescription, null);
                    break;
                }
            }
        }

        Set<Annotation> qualifiers = attrs.getQualifiers();
        if (qualifiers != null) {
            for (Annotation qualifier : qualifiers) {
                Class<? extends Annotation> qualifierType =
                        qualifier != null ? qualifier.annotationType() : null;
                if (!isValidProcessBeanAttributesQualifier(qualifierType)) {
                    knowledgeBase.addDefinitionError(Phase.PROCESS_BEAN_ATTRIBUTES,
                            "BeanAttributes qualifier " + qualifierType +
                                    " is not a valid qualifier for " + beanDescription, null);
                }
            }
        }

        Class<? extends Annotation> scope = attrs.getScope();
        if (!isValidProcessBeanAttributesScope(scope)) {
            knowledgeBase.addDefinitionError(Phase.PROCESS_BEAN_ATTRIBUTES,
                    "BeanAttributes scope " + scope +
                            " is not a valid scope for " + beanDescription, null);
        }

        Set<Class<? extends Annotation>> stereotypes = attrs.getStereotypes();
        if (stereotypes != null) {
            for (Class<? extends Annotation> stereotype : stereotypes) {
                if (!isValidProcessBeanAttributesStereotype(stereotype)) {
                    knowledgeBase.addDefinitionError(Phase.PROCESS_BEAN_ATTRIBUTES,
                            "BeanAttributes stereotype " + stereotype +
                                    " is not a valid stereotype for " + beanDescription, null);
                }
            }
        }
    }

    private String describeProcessBeanAttributesBean(Bean<?> bean) {
        if (bean instanceof ProducerBean<?>) {
            ProducerBean<?> producerBean = (ProducerBean<?>) bean;
            Class<?> declaringClass = producerBean.getDeclaringClass();
            String declaringClassName = declaringClass != null ? declaringClass.getName() : "<unknown>";
            if (producerBean.getProducerMethod() != null) {
                return declaringClassName + "#" + producerBean.getProducerMethod().getName() + "()";
            }
            if (producerBean.getProducerField() != null) {
                return declaringClassName + "#" + producerBean.getProducerField().getName();
            }
            return declaringClassName;
        }
        Class<?> beanClass = bean != null ? bean.getBeanClass() : null;
        return beanClass != null ? beanClass.getName() : String.valueOf(bean);
    }

    private boolean isValidProcessBeanAttributesQualifier(Class<? extends Annotation> qualifierType) {
        if (qualifierType == null) {
            return false;
        }
        return hasQualifierAnnotation(qualifierType) || knowledgeBase.isRegisteredQualifier(qualifierType);
    }

    private boolean isValidProcessBeanAttributesScope(Class<? extends Annotation> scopeType) {
        if (scopeType == null) {
            return false;
        }
        if (hasDependentAnnotation(scopeType)) {
            return true;
        }
        if (hasScopeAnnotation(scopeType) || hasNormalScopeAnnotation(scopeType) ||
                hasBuiltInNormalScopeAnnotation(scopeType)) {
            return true;
        }
        return knowledgeBase.isRegisteredScope(scopeType);
    }

    private boolean isValidProcessBeanAttributesStereotype(Class<? extends Annotation> stereotypeType) {
        if (stereotypeType == null) {
            return false;
        }
        return hasStereotypeAnnotation(stereotypeType) || knowledgeBase.isRegisteredStereotype(stereotypeType);
    }

    private void validateSpecializationTypeClosureAfterProcessBeanAttributes(Bean<?> bean, BeanAttributes<?> attrs) {
        if (!(bean instanceof BeanImpl<?>)) {
            return;
        }
        Class<?> beanClass = bean.getBeanClass();
        if (!hasSpecializesAnnotation(beanClass) || !isBeanEnabledForObserverLifecycle(bean)) {
            return;
        }

        Set<Type> specializingBeanTypes = attrs != null ? attrs.getTypes() : bean.getTypes();
        if (specializingBeanTypes == null) {
            specializingBeanTypes = Collections.emptySet();
        }

        BeanTypesExtractor beanTypesExtractor = new BeanTypesExtractor();
        Class<?> current = beanClass.getSuperclass();
        while (current != null && current != Object.class) {
            BeanTypesExtractor.ExtractionResult extractionResult = beanTypesExtractor.extractManagedBeanTypes(current);
            Set<Type> missingTypes = new HashSet<>(extractionResult.getTypes());
            missingTypes.removeAll(specializingBeanTypes);
            if (!missingTypes.isEmpty()) {
                knowledgeBase.addDefinitionError(beanClass.getName() +
                        ": specializing bean does not have all bean types of specialized bean " +
                        current.getName() + ". Missing: " + missingTypes);
            }
            if (!hasSpecializesAnnotation(current)) {
                break;
            }
            current = current.getSuperclass();
        }
    }

    /**
     * Applies updated BeanAttributes back to the underlying bean implementation.
     */
    private void applyBeanAttributes(Bean<?> bean, BeanAttributes<?> attrs) {
        if (bean instanceof BeanImpl<?>) {
            BeanImpl<?> b = (BeanImpl<?>) bean;
            b.setName(attrs.getName());
            b.setQualifiers(attrs.getQualifiers());
            b.setScope(attrs.getScope());
            b.setStereotypes(attrs.getStereotypes());
            b.setTypes(attrs.getTypes());
            b.setAlternative(attrs.isAlternative());
            b.setAlternativeEnabled(!attrs.isAlternative() ||
                    isAlternativeEnabledAfterBeanAttributes(b.getBeanClass(), attrs.getStereotypes()));
        } else if (bean instanceof ProducerBean<?>) {
            ProducerBean<?> b = (ProducerBean<?>) bean;
            b.setName(attrs.getName());
            b.setQualifiers(attrs.getQualifiers());
            b.setScope(attrs.getScope());
            b.setStereotypes(attrs.getStereotypes());
            b.setTypes(attrs.getTypes());
            boolean memberAlternative = attrs.isAlternative();
            boolean classAlternative = isAlternativeDeclaration(b.getBeanClass());
            boolean producerAlternativeDeclared = memberAlternative || classAlternative;

            b.setAlternative(memberAlternative);
            b.setAlternativeEnabled(!producerAlternativeDeclared ||
                    isProducerAlternativeEnabledAfterBeanAttributes(b, attrs.getStereotypes()));
        } else {
            // Synthetic or built-in beans: no-op
        }
    }

    private boolean isProducerAlternativeEnabledAfterBeanAttributes(ProducerBean<?> producerBean,
                                                                    Set<Class<? extends Annotation>> stereotypes) {
        if (producerBean == null) {
            return false;
        }

        if (resolveProducerMemberPriority(producerBean) != null) {
            return true;
        }

        Set<Class<? extends Annotation>> memberStereotypes =
                (stereotypes == null || stereotypes.isEmpty())
                        ? resolveProducerMemberStereotypes(producerBean)
                        : stereotypes;

        if (resolveSingleStereotypePriority(memberStereotypes) != null) {
            return true;
        }

        for (Class<? extends Annotation> stereotype : memberStereotypes) {
            if (stereotype == null) {
                continue;
            }
            String stereotypeName = stereotype.getName();
            if (knowledgeBase.isAlternativeEnabledProgrammatically(stereotypeName) ||
                    knowledgeBase.isAlternativeEnabledInBeansXml(stereotypeName)) {
                return true;
            }
        }

        Class<?> declaringClass = producerBean.getBeanClass();
        return isAlternativeEnabledAfterBeanAttributes(declaringClass, knowledgeBase.getClassStereotypes(declaringClass));
    }

    private Integer resolveProducerMemberPriority(ProducerBean<?> producerBean) {
        if (producerBean == null) {
            return null;
        }
        if (producerBean.getProducerMethod() != null) {
            return getPriorityValue(producerBean.getProducerMethod());
        }
        if (producerBean.getProducerField() != null) {
            return getPriorityValue(producerBean.getProducerField());
        }
        return null;
    }

    private Set<Class<? extends Annotation>> resolveProducerMemberStereotypes(ProducerBean<?> producerBean) {
        Set<Class<? extends Annotation>> stereotypes = new LinkedHashSet<>();
        if (producerBean == null) {
            return stereotypes;
        }

        AnnotatedElement producerMember = producerBean.getProducerMethod() != null
                ? producerBean.getProducerMethod()
                : producerBean.getProducerField();
        if (producerMember == null) {
            return stereotypes;
        }

        for (Annotation annotation : producerMember.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (hasStereotypeAnnotation(annotationType)) {
                stereotypes.add(annotationType);
            }
        }
        return stereotypes;
    }

    private Integer resolveSingleStereotypePriority(Set<Class<? extends Annotation>> stereotypes) {
        Set<Integer> priorities = knowledgeBase.collectStereotypePriorityValues(stereotypes);
        if (priorities.size() == 1) {
            return priorities.iterator().next();
        }
        return null;
    }

    private boolean isAlternativeEnabledAfterBeanAttributes(Class<?> beanClass,
                                                            Set<Class<? extends Annotation>> stereotypes) {
        if (beanClass == null) {
            return false;
        }

        Integer effectivePriority = resolveEffectivePriorityAfterBeanAttributes(beanClass, stereotypes);

        if (knowledgeBase.hasAfterTypeDiscoveryAlternativesCustomized()) {
            if (knowledgeBase.getApplicationAlternativeOrder(beanClass) >= 0) {
                return true;
            }
            if (effectivePriority != null) {
                return false;
            }
        }

        if (effectivePriority != null) {
            return true;
        }
        if (knowledgeBase.isAlternativeEnabledProgrammatically(beanClass.getName())) {
            return true;
        }
        if (knowledgeBase.isAlternativeEnabledInBeansXml(beanClass.getName())) {
            return true;
        }
        if (stereotypes != null) {
            for (Class<? extends Annotation> stereotype : stereotypes) {
                if (stereotype == null) {
                    continue;
                }
                String stereotypeName = stereotype.getName();
                if (knowledgeBase.isAlternativeEnabledProgrammatically(stereotypeName) ||
                        knowledgeBase.isAlternativeEnabledInBeansXml(stereotypeName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Integer resolveEffectivePriorityAfterBeanAttributes(Class<?> beanClass,
                                                                Set<Class<? extends Annotation>> stereotypes) {
        Integer directPriority = knowledgeBase.getDirectPriority(beanClass);
        if (directPriority != null) {
            return directPriority;
        }

        Set<Integer> stereotypePriorities = (stereotypes == null || stereotypes.isEmpty())
                ? knowledgeBase.collectStereotypePriorityValues(beanClass)
                : knowledgeBase.collectStereotypePriorityValues(stereotypes);
        if (stereotypePriorities.size() == 1) {
            return stereotypePriorities.iterator().next();
        }

        return extractPrioritizedInterfacePriority(beanClass);
    }

    /**
     * Replaces an injection point inside a bean with an updated instance.
     */
    private void updateInjectionPoint(Bean<?> bean, InjectionPoint original, InjectionPoint updated) {
        if (bean instanceof BeanImpl<?>) {
            ((BeanImpl<?>) bean).replaceInjectionPoint(original, updated);
        } else if (bean instanceof ProducerBean<?>) {
            ProducerBean<?> pb = (ProducerBean<?>) bean;
            pb.replaceInjectionPoint(original, updated);
        } else if (bean instanceof SyntheticBean<?>) {
            try {
                SyntheticBean<?> sb = (SyntheticBean<?>) bean;
                Set<InjectionPoint> ips = new HashSet<>(sb.getInjectionPoints());
                if (original != null) {
                    ips.remove(original);
                }
                if (updated != null) {
                    ips.add(updated);
                }

                @SuppressWarnings("unchecked")
                Function<CreationalContext<Object>, Object> createCb =
                        (Function<CreationalContext<Object>, Object>) getPrivateField(sb, "createCallback");
                @SuppressWarnings("unchecked")
                BiConsumer<Object, CreationalContext<Object>> destroyCb =
                        (BiConsumer<Object, CreationalContext<Object>>) getPrivateField(sb, "destroyCallback");
                Integer priority = (Integer) getPrivateField(sb, "priority");

                SyntheticBean<?> replacement = new SyntheticBean<>(
                        sb.getBeanClass(),
                        sb.getTypes(),
                        sb.getQualifiers(),
                        sb.getScope(),
                        sb.getName(),
                        sb.getId(),
                        sb.getStereotypes(),
                        sb.isAlternative(),
                        priority,
                        createCb,
                        destroyCb,
                        ips
                );
                knowledgeBase.getBeans().remove(sb);
                knowledgeBase.addBean(replacement);
            } catch (Exception e) {
                log("Error updating injection point for synthetic bean", e);
            }
        } else if (bean instanceof SyntheticProducerBeanImpl<?>) {
            try {
                SyntheticProducerBeanImpl<?> sp = (SyntheticProducerBeanImpl<?>) bean;
                Set<InjectionPoint> ips = new HashSet<>(sp.getInjectionPoints());
                if (original != null) {
                    ips.remove(original);
                }
                if (updated != null) {
                    ips.add(updated);
                }

                @SuppressWarnings("unchecked")
                Producer<Object> originalProducer = (Producer<Object>) getPrivateField(sp, "producer");
                @SuppressWarnings("unchecked")
                BeanAttributes<Object> attributes = (BeanAttributes<Object>) getPrivateField(sp, "attributes");
                Class<?> beanClass = (Class<?>) getPrivateField(sp, "beanClass");

                Producer<Object> wrapper = new Producer<Object>() {
                    @Override
                    public Object produce(CreationalContext<Object> ctx) {
                        return originalProducer.produce(ctx);
                    }

                    @Override
                    public void dispose(Object instance) {
                        originalProducer.dispose(instance);
                    }

                    @Override
                    public Set<InjectionPoint> getInjectionPoints() {
                        return ips;
                    }
                };

                SyntheticProducerBeanImpl<Object> replacement =
                        new SyntheticProducerBeanImpl<>(attributes, beanClass, wrapper);
                knowledgeBase.getBeans().remove(sp);
                knowledgeBase.addBean(replacement);
            } catch (Exception e) {
                log("Error updating injection point for synthetic producer bean", e);
            }
        }
    }

    private Object getPrivateField(Object target, String fieldName) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(target);
    }

    private ObserverMethodInfo toObserverMethodInfo(ObserverMethod<?> observer, Bean<?> declaringBean) {
        return new ObserverMethodInfo(
                observer.getObservedType(),
                observer.getObservedQualifiers(),
                observer.getReception(),
                observer.getTransactionPhase(),
                observer.getPriority(),
                observer.isAsync(),
                declaringBean,
                observer
        );
    }

    /**
     * ObserverMethod adapter that invokes the original reflective observer method.
     * Used so ProcessObserverMethod can replace/keep reflective observers while allowing
     * extensions to wrap or veto them.
     */
    private static class ReflectiveObserverMethodAdapter<T> implements ObserverMethod<T> {
        private final ObserverMethodInfo info;
        private final BeanResolver beanResolver;
        private final ContextManager contextManager;
        private final String identityKey;

        ReflectiveObserverMethodAdapter(ObserverMethodInfo info,
                                        BeanResolver beanResolver,
                                        ContextManager contextManager) {
            this.info = info;
            this.beanResolver = beanResolver;
            this.contextManager = contextManager;
            this.identityKey = observerMethodIdentityKey(info);
        }

        @Override
        public Class<?> getBeanClass() {
            return info.getDeclaringBean() != null
                    ? info.getDeclaringBean().getBeanClass()
                    : info.getObserverMethod().getDeclaringClass();
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
        public Reception getReception() {
            return info.getReception();
        }

        @Override
        public TransactionPhase getTransactionPhase() {
            return info.getTransactionPhase();
        }

        @Override
        public void notify(T event) {
            try {
                // Honor IF_EXISTS reception
                if (info.getReception() == Reception.IF_EXISTS && info.getDeclaringBean() != null) {
                    Class<? extends Annotation> scope = info.getDeclaringBean().getScope();
                    try {
                        com.threeamigos.common.util.implementations.injection.scopes.ScopeContext ctx =
                                contextManager.getContext(scope);
                        Object existing = ctx.getIfExists(info.getDeclaringBean());
                        if (existing == null) {
                            return; // skip notification
                        }
                    } catch (IllegalArgumentException ignored) {
                        return;
                    }
                }

                Method method = info.getObserverMethod();
                Object beanInstance = info.getDeclaringBean() != null
                        ? beanResolver.resolveDeclaringBeanInstance(info.getDeclaringBean().getBeanClass())
                        : beanResolver.resolveDeclaringBeanInstance(method.getDeclaringClass());

                Parameter[] params = method.getParameters();
                Object[] args = new Object[params.length];
                for (int i = 0; i < params.length; i++) {
                    Parameter p = params[i];
                    if (hasObservesAnnotation(p) || hasObservesAsyncAnnotation(p)) {
                        args[i] = event;
                    } else {
                        args[i] = beanResolver.resolve(p.getParameterizedType(), p.getAnnotations());
                    }
                }

                method.setAccessible(true);
                method.invoke(beanInstance, args);
            } catch (Exception e) {
                throw new RuntimeException("Failed to notify observer " +
                        info.getObserverMethod().getName() + ": " + e.getMessage(), e);
            }
        }

        @Override
        public boolean isAsync() {
            return info.isAsync();
        }

        @Override
        public int getPriority() {
            return info.getPriority();
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

        private static String observerMethodIdentityKey(ObserverMethodInfo info) {
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
            ObserverMethodInfo info = extractObserverMethodInfo(observerMethod);
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

        private static ObserverMethodInfo extractObserverMethodInfo(ObserverMethod<?> observerMethod) {
            if (observerMethod == null) {
                return null;
            }

            Class<?> current = observerMethod.getClass();
            while (current != null && !Object.class.equals(current)) {
                Field[] fields = current.getDeclaredFields();
                for (Field field : fields) {
                    if (!ObserverMethodInfo.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        Object value = field.get(observerMethod);
                        if (value instanceof ObserverMethodInfo) {
                            return (ObserverMethodInfo) value;
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
    }

    // ============================================================
    // PHASE 4: AFTER BEAN DISCOVERY
    // ============================================================

    /**
     * Fires AfterBeanDiscovery event to all extensions.
     *
     * <p>Extensions can:
     * <ul>
     *   <li>Register additional beans via addBean()</li>
     *   <li>Register custom contexts via addContext()</li>
     *   <li>Add observer methods via addObserverMethod()</li>
     *   <li>Add interceptors/decorators</li>
     * </ul>
     * <p>
     * This method also registers any custom contexts added programmatically
     * via {@link #registerCustomContext(Class, Context)} before container initialization.
     */
    private void fireAfterBeanDiscovery() {
        info("Firing AfterBeanDiscovery event");
        beanManager.markAfterBeanDiscoveryFired();
        AfterBeanDiscovery event = new AfterBeanDiscoveryImpl(messageHandler, knowledgeBase, beanManager,
                ev -> extensionsManager.fireEventToExtensions(ev));

        // Register programmatically added custom contexts BEFORE firing to extensions
        // This allows extensions to see and potentially modify these contexts
        if (!customContextsToRegister.isEmpty()) {
            info("Registering " + customContextsToRegister.size() + " programmatically added custom contexts");

            try {
                ((ObserverInvocationLifecycle) event).beginObserverInvocation();
                for (Map.Entry<Class<? extends Annotation>, Context> entry : customContextsToRegister.entrySet()) {
                    try {
                        event.addContext(entry.getValue());
                        info("Registered custom context for @" +entry.getKey().getSimpleName());
                    } catch (Exception e) {
                        log("Failed to register custom context for @" + entry.getKey().getSimpleName(), e);
                        throw new DeploymentException("Failed to register custom context for @" + entry.getKey().getSimpleName(), e);
                    }
                }
            } finally {
                ((ObserverInvocationLifecycle) event).endObserverInvocation();
            }
        }

        extensionsManager.fireEventToExtensions(event);
    }

    // ============================================================
    // PHASE 5: VALIDATION
    // ============================================================

    /**
     * Performs deployment validation.
     *
     * <p>Checks for:
     * <ul>
     *   <li>Unsatisfied dependencies</li>
     *   <li>Ambiguous dependencies</li>
     *   <li>Invalid decorator/interceptor configurations</li>
     *   <li>Specialization errors</li>
     *   <li>Alternative priority conflicts</li>
     * </ul>
     *
     * @throws DeploymentException if validation fails
     */
    private void validateDeployment() {
        info("Validating deployment");

        Collection<Class<?>> excludedClasses = knowledgeBase.getExcludedClasses();
        if (!excludedClasses.isEmpty()) {
            info("Manually excluded classes:");
            for (Class<?> excludedClass : excludedClasses) {
                info("  - " + excludedClass.getName());
            }
        }

        // 1. Check for unsatisfied/ambiguous dependencies
        CDI41InjectionValidator injectionValidator =
                new CDI41InjectionValidator(
                        knowledgeBase,
                        legacyCdi10NewEnabled,
                        allowNonPortableAsyncObserverEventParameterPriority);
        injectionValidator.validateAllInjectionPoints();

        // 1.1 Validate beans.xml alternatives declarations (CDI Full modularity rules)
        validateBeansXmlAlternativesConfiguration();
        // 1.2 Validate beans.xml interceptor declarations
        validateBeansXmlInterceptorsConfiguration();
        // 1.3 Validate beans.xml decorator declarations
        validateBeansXmlDecoratorsConfiguration();
        // 1.4 Validate extension-provided custom decorator metadata
        validateProgrammaticDecoratorsConfiguration();

        // 2. Check definition errors
        if (knowledgeBase.hasErrors()) {
            error("Deployment validation failed:");
            knowledgeBase.getDefinitionErrors().forEach(error ->
                    error("  - Definition error: " + error));
            knowledgeBase.getInjectionErrors().forEach(error ->
                    error("  - Injection error: " + error));
            knowledgeBase.getIllegalProductErrors().forEach(error ->
                    error("  - Illegal product error: " + error));
            knowledgeBase.getErrors().forEach(error ->
                    error("  - Generic Error: " + error));


            if (!knowledgeBase.getDefinitionErrors().isEmpty()) {
                if (shouldReportDefinitionErrorsAsDeploymentProblem()) {
                    throw new DeploymentException("Deployment validation failed. See log for details.");
                }
                throw new DefinitionException("Deployment validation failed. See log for details.");
            } else if (!knowledgeBase.getIllegalProductErrors().isEmpty()) {
                throw new IllegalProductException("Deployment validation failed. See log for details.");
            } else {
                throw new DeploymentException("Deployment validation failed. See log for details.");
            }
        }

        info("Deployment validation passed");
    }

    private boolean shouldReportDefinitionErrorsAsDeploymentProblem() {
        Collection<String> definitionErrors = knowledgeBase.getDefinitionErrors();
        if (definitionErrors == null || definitionErrors.isEmpty()) {
            return false;
        }
        boolean hasDeploymentProblem = false;
        for (String definitionError : definitionErrors) {
            if (isDeploymentProblemDefinitionError(definitionError)) {
                hasDeploymentProblem = true;
            }
        }
        return hasDeploymentProblem;
    }

    private boolean isDeploymentProblemDefinitionError(String definitionError) {
        if (definitionError == null) {
            return false;
        }
        return definitionError.contains("matches a decorator but is unproxyable")
                || definitionError.startsWith("beans.xml <decorators><class>")
                || definitionError.startsWith("beans.xml decorator class ")
                || definitionError.startsWith("beans.xml <interceptors><class>")
                || definitionError.startsWith("beans.xml interceptor class ");
    }

    private void validateProgrammaticDecoratorsConfiguration() {
        Set<Class<?>> validatedDecoratorClasses = new HashSet<>();
        for (DecoratorInfo decoratorInfo : knowledgeBase.getDecoratorInfos()) {
            if (decoratorInfo != null && decoratorInfo.getDecoratorClass() != null) {
                validatedDecoratorClasses.add(decoratorInfo.getDecoratorClass());
                validateDecoratorInfoDelegateInjectionPoint(decoratorInfo);
            }
        }

        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (!(bean instanceof Decorator<?>)) {
                continue;
            }
            Decorator<?> decorator = (Decorator<?>) bean;
            Class<?> decoratorClass = decorator.getBeanClass();
            if (decoratorClass != null && validatedDecoratorClasses.contains(decoratorClass)) {
                continue;
            }
            validateProgrammaticDecoratorDecoratedTypes(decorator);
            validateProgrammaticDecoratorDelegateInjectionPoints(decorator);
        }
    }

    private void validateProgrammaticDecoratorDecoratedTypes(Decorator<?> decorator) {
        if (decorator == null) {
            return;
        }
        Set<Type> decoratedTypes = decorator.getDecoratedTypes();
        if (hasAtLeastOneValidDecoratedType(decoratedTypes)) {
            return;
        }
        Class<?> beanClass = decorator.getBeanClass();
        String decoratorName = beanClass != null ? beanClass.getName() : decorator.getClass().getName();
        knowledgeBase.addDefinitionError(
                "Custom decorator '" + decoratorName +
                        "' must declare at least one decorated type (interface bean type excluding java.io.Serializable)");
    }

    private boolean hasAtLeastOneValidDecoratedType(Set<Type> decoratedTypes) {
        if (decoratedTypes == null || decoratedTypes.isEmpty()) {
            return false;
        }
        for (Type decoratedType : decoratedTypes) {
            Class<?> rawType = extractRawClass(decoratedType);
            if (rawType == null) {
                continue;
            }
            if (Object.class.equals(rawType)
                    || java.io.Serializable.class.equals(rawType)
                    || Decorator.class.equals(rawType)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private void validateDecoratorInfoDelegateInjectionPoint(DecoratorInfo decoratorInfo) {
        if (decoratorInfo == null || decoratorInfo.getDecoratorClass() == null) {
            return;
        }
        InjectionPoint delegateInjectionPoint = decoratorInfo.getDelegateInjectionPoint();
        if (delegateInjectionPoint != null && delegateInjectionPoint.isDelegate()) {
            return;
        }
        knowledgeBase.addDefinitionError(
                decoratorInfo.getDecoratorClass().getName() +
                        ": Decorator must have exactly one @Delegate injection point (found 0). " +
                        "Add @Inject @Delegate to a field, method parameter, or constructor parameter.");
    }

    private void validateProgrammaticDecoratorDelegateInjectionPoints(Decorator<?> decorator) {
        Set<InjectionPoint> injectionPoints = decorator.getInjectionPoints();
        if (injectionPoints == null || injectionPoints.isEmpty()) {
            return;
        }
        Class<?> beanClass = decorator.getBeanClass();
        String decoratorName = beanClass != null ? beanClass.getName() : decorator.getClass().getName();

        int delegateInjectionPoints = 0;
        for (InjectionPoint injectionPoint : injectionPoints) {
            if (injectionPoint != null && injectionPoint.isDelegate()) {
                delegateInjectionPoints++;
            }
        }

        if (delegateInjectionPoints == 0) {
            knowledgeBase.addDefinitionError(
                    "Custom decorator '" + decoratorName +
                            "' must declare exactly one delegate injection point but declares none");
        } else if (delegateInjectionPoints > 1) {
            knowledgeBase.addDefinitionError(
                    "Custom decorator '" + decoratorName +
                            "' must declare exactly one delegate injection point but declares " + delegateInjectionPoints);
        }
    }

    private void validateBeansXmlAlternativesConfiguration() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = Syringe.class.getClassLoader();
        }

        for (BeansXml beansXml : knowledgeBase.getBeansXmlConfigurations()) {
            if (beansXml == null) {
                continue;
            }
            Alternatives alternatives = beansXml.getAlternatives();
            if (alternatives == null) {
                continue;
            }

            List<String> classes = alternatives.getClasses() != null ? alternatives.getClasses() : Collections.emptyList();
            List<String> stereotypes = alternatives.getStereotypes() != null ? alternatives.getStereotypes() : Collections.emptyList();

            validateNoDuplicateEntries(classes, "beans.xml <alternatives><class>");
            validateNoDuplicateEntries(stereotypes, "beans.xml <alternatives><stereotype>");

            for (String className : classes) {
                validateAlternativeClassEntry(className, classLoader);
            }
            for (String stereotypeName : stereotypes) {
                validateAlternativeStereotypeEntry(stereotypeName, classLoader);
            }
        }
    }

    private void validateBeansXmlInterceptorsConfiguration() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = Syringe.class.getClassLoader();
        }

        for (BeansXml beansXml : knowledgeBase.getBeansXmlConfigurations()) {
            if (beansXml == null) {
                continue;
            }

            com.threeamigos.common.util.implementations.injection.beansxml.Interceptors interceptors =
                    beansXml.getInterceptors();
            if (interceptors == null) {
                continue;
            }

            List<String> classes = interceptors.getClasses() != null
                    ? interceptors.getClasses()
                    : Collections.emptyList();

            validateNoDuplicateEntries(classes, "beans.xml <interceptors><class>");

            for (String className : classes) {
                validateInterceptorClassEntry(className, classLoader);
            }
        }
    }

    private void validateBeansXmlDecoratorsConfiguration() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = Syringe.class.getClassLoader();
        }

        for (BeansXml beansXml : knowledgeBase.getBeansXmlConfigurations()) {
            if (beansXml == null) {
                continue;
            }

            com.threeamigos.common.util.implementations.injection.beansxml.Decorators decorators =
                    beansXml.getDecorators();
            if (decorators == null) {
                continue;
            }

            List<String> classes = decorators.getClasses() != null
                    ? decorators.getClasses()
                    : Collections.emptyList();

            validateNoDuplicateEntries(classes, "beans.xml <decorators><class>");

            for (String className : classes) {
                validateDecoratorClassEntry(className, classLoader);
            }
        }
    }

    private void validateNoDuplicateEntries(List<String> entries, String location) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            if (!seen.add(entry)) {
                duplicates.add(entry);
            }
        }
        if (!duplicates.isEmpty()) {
            knowledgeBase.addDefinitionError(location + " contains duplicate entries: " + duplicates);
        }
    }

    private void validateAlternativeClassEntry(String className, ClassLoader classLoader) {
        if (className == null || className.trim().isEmpty()) {
            knowledgeBase.addDefinitionError("beans.xml <alternatives><class> must not be empty");
            return;
        }

        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            knowledgeBase.addDefinitionError("beans.xml alternative class not found: " + className);
            return;
        }

        if (isAlternativeDeclaration(clazz) ||
                declaresAlternativeProducerMember(clazz) ||
                hasAlternativeBeanWithBeanClassName(className)) {
            return;
        }

        knowledgeBase.addDefinitionError(
                "beans.xml alternative class '" + className + "' is invalid: " +
                        "not an @Alternative bean, not an alternative producer holder, and no matching alternative bean exists");
    }

    private void validateAlternativeStereotypeEntry(String stereotypeName, ClassLoader classLoader) {
        if (stereotypeName == null || stereotypeName.trim().isEmpty()) {
            knowledgeBase.addDefinitionError("beans.xml <alternatives><stereotype> must not be empty");
            return;
        }

        Class<?> loaded;
        try {
            loaded = Class.forName(stereotypeName, false, classLoader);
        } catch (ClassNotFoundException e) {
            knowledgeBase.addDefinitionError("beans.xml alternative stereotype not found: " + stereotypeName);
            return;
        }

        if (!loaded.isAnnotation()) {
            knowledgeBase.addDefinitionError(
                    "beans.xml alternative stereotype '" + stereotypeName + "' is not an annotation type");
            return;
        }

        @SuppressWarnings("unchecked")
        Class<? extends Annotation> annotationType = (Class<? extends Annotation>) loaded;
        if (!hasStereotypeAnnotation(annotationType) ||
                !declaresAlternativeViaStereotype(annotationType, new HashSet<>())) {
            knowledgeBase.addDefinitionError(
                    "beans.xml alternative stereotype '" + stereotypeName + "' is not an @Alternative stereotype");
        }
    }

    private void validateInterceptorClassEntry(String className, ClassLoader classLoader) {
        if (className == null || className.trim().isEmpty()) {
            knowledgeBase.addDefinitionError("beans.xml <interceptors><class> must not be empty");
            return;
        }

        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            knowledgeBase.addDefinitionError("beans.xml interceptor class not found: " + className);
            return;
        }

        if (hasInterceptorAnnotation(clazz) ||
                Interceptor.class.isAssignableFrom(clazz) ||
                hasInterceptorBeanWithBeanClassName(className)) {
            return;
        }

        knowledgeBase.addDefinitionError(
                "beans.xml interceptor class '" + className + "' is not an interceptor class");
    }

    private void validateDecoratorClassEntry(String className, ClassLoader classLoader) {
        if (className == null || className.trim().isEmpty()) {
            knowledgeBase.addDefinitionError("beans.xml <decorators><class> must not be empty");
            return;
        }

        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            knowledgeBase.addDefinitionError("beans.xml decorator class not found: " + className);
            return;
        }

        if (knowledgeBase.isTypeVetoed(clazz)) {
            return;
        }

        if (hasDecoratorAnnotation(clazz) ||
                Decorator.class.isAssignableFrom(clazz) ||
                declaresDelegateInjectionPoint(clazz)) {
            return;
        }

        knowledgeBase.addDefinitionError(
                "beans.xml decorator class '" + className + "' is not a decorator class");
    }

    private boolean declaresDelegateInjectionPoint(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }

        for (Field field : clazz.getDeclaredFields()) {
            for (Annotation annotation : field.getAnnotations()) {
                if (AnnotationPredicates.hasDelegateAnnotation(annotation.annotationType())) {
                    return true;
                }
            }
        }

        for (Method method : clazz.getDeclaredMethods()) {
            for (Parameter parameter : method.getParameters()) {
                for (Annotation annotation : parameter.getAnnotations()) {
                    if (AnnotationPredicates.hasDelegateAnnotation(annotation.annotationType())) {
                        return true;
                    }
                }
            }
        }

        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            for (Parameter parameter : constructor.getParameters()) {
                for (Annotation annotation : parameter.getAnnotations()) {
                    if (AnnotationPredicates.hasDelegateAnnotation(annotation.annotationType())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean hasAlternativeBeanWithBeanClassName(String className) {
        for (Bean<?> bean : knowledgeBase.getBeans()) {
            Class<?> beanClass = bean.getBeanClass();
            if (beanClass != null &&
                    className.equals(beanClass.getName()) &&
                    bean.isAlternative()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInterceptorBeanWithBeanClassName(String className) {
        for (Bean<?> bean : knowledgeBase.getBeans()) {
            if (!(bean instanceof Interceptor<?>)) {
                continue;
            }
            Class<?> beanClass = bean.getBeanClass();
            if (beanClass != null && className.equals(beanClass.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean declaresAlternativeProducerMember(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (hasProducesAnnotation(method) && isAlternativeDeclaration(method)) {
                return true;
            }
        }
        for (Field field : clazz.getDeclaredFields()) {
            if (hasProducesAnnotation(field) && isAlternativeDeclaration(field)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAlternativeDeclaration(AnnotatedElement element) {
        if (element == null) {
            return false;
        }
        if (hasAlternativeAnnotation(element)) {
            return true;
        }
        for (Annotation annotation : element.getAnnotations()) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (hasStereotypeAnnotation(type) &&
                    declaresAlternativeViaStereotype(type, new HashSet<>())) {
                return true;
            }
        }
        return false;
    }

    private boolean declaresAlternativeViaStereotype(Class<? extends Annotation> stereotypeType,
                                                     Set<Class<? extends Annotation>> visited) {
        if (stereotypeType == null || !visited.add(stereotypeType)) {
            return false;
        }
        if (hasAlternativeAnnotation(stereotypeType)) {
            return true;
        }
        for (Annotation meta : stereotypeType.getAnnotations()) {
            Class<? extends Annotation> metaType = meta.annotationType();
            if (hasStereotypeAnnotation(metaType) &&
                    declaresAlternativeViaStereotype(metaType, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fires AfterDeploymentValidation event to all extensions.
     *
     * <p>Extensions can perform final validation checks.
     * Any deployment problems detected here will prevent application startup.
     */
    private void fireAfterDeploymentValidation() {
        info("Firing AfterDeploymentValidation event");
        beanManager.markAfterDeploymentValidationFired();
        int deploymentProblemsBefore = knowledgeBase.getErrors().size();
        AfterDeploymentValidation event = new AfterDeploymentValidationImpl(knowledgeBase);
        extensionsManager.fireEventToExtensions(event);
        int deploymentProblemsAfter = knowledgeBase.getErrors().size();
        if (deploymentProblemsAfter > deploymentProblemsBefore) {
            throw new DeploymentException("Deployment validation failed due to AfterDeploymentValidation problems.");
        }
    }

    // ============================================================
    // SHUTDOWN
    // ============================================================

    /**
     * Fires BeforeShutdown event to all extensions.
     *
     * <p>Extensions can perform cleanup before the container shuts down.
     */
    private void fireBeforeShutdown() {
        info("Firing BeforeShutdown event");
        beforeShutdownFired = true;
        BeforeShutdown event = new BeforeShutdownImpl();
        extensionsManager.fireEventToExtensions(event);
    }

    /**
     * Destroys all beans by calling @PreDestroy methods.
     */
    private void destroyAllBeans() {
        info("Destroying all beans");

        if (contextManager != null) {
            try {
                contextManager.destroyAll();
            } catch (Exception e) {
                log("Error destroying contexts", e);
            }
        }

    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    /**
     * Lightweight wrapper capturing an extension observer method invocation.
     * Sorting happens on the priority value.
     */
    public static class ExtensionObserverInvocation {
        public final Extension extension;
        private final Method method;
        private final int observesIndex;
        public final int priority;
        private final BeanManager beanManager;
        private final MessageHandler messageHandler;
        private final Set<Class<? extends Annotation>> withAnnotationsFilter;

        public ExtensionObserverInvocation(Extension extension,
                                           Method method,
                                           int observesIndex,
                                           int priority,
                                           BeanManager beanManager,
                                           MessageHandler messageHandler,
                                           Set<Class<? extends Annotation>> withAnnotationsFilter) {
            this.extension = extension;
            this.method = method;
            this.observesIndex = observesIndex;
            this.priority = priority;
            this.beanManager = beanManager;
            this.messageHandler = messageHandler;
            this.withAnnotationsFilter = withAnnotationsFilter;
        }

        public void invoke(Object event) throws Exception {
            if (!matchesWithAnnotationsFilter(event)) {
                return;
            }
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];
            args[observesIndex] = event;

            for (int i = 0; i < parameters.length; i++) {
                if (i == observesIndex) continue;
                args[i] = resolveExtensionParameter(parameters[i]);
            }

            method.setAccessible(true);
            method.invoke(extension, args);

            messageHandler.info("[Syringe] Invoked extension observer: " +
                    extension.getClass().getSimpleName() + "." + method.getName() +
                    "(@Observes " + event.getClass().getSimpleName() +
                    ", priority=" + priority + ")");
        }

        private boolean matchesWithAnnotationsFilter(Object event) {
            if (withAnnotationsFilter == null) {
                return true;
            }
            if (!(event instanceof ProcessAnnotatedType<?>)) {
                return true;
            }
            AnnotatedType<?> annotatedType = ((ProcessAnnotatedType<?>) event).getAnnotatedType();
            if (annotatedType == null) {
                return false;
            }
            return hasAnyConfiguredAnnotation(annotatedType);
        }

        private boolean hasAnyConfiguredAnnotation(AnnotatedType<?> annotatedType) {
            if (matchesAnyAnnotation(annotatedType.getAnnotations())) {
                return true;
            }
            // @WithAnnotations parity: for class-level annotations, Java's @Inherited
            // semantics must be considered even when AnnotatedType suppresses generic
            // non-scope inheritance for metadata APIs.
            Set<Annotation> javaClassAnnotations = new HashSet<>();
            for (Annotation annotation : annotatedType.getJavaClass().getAnnotations()) {
                Class<? extends Annotation> type = annotation.annotationType();
                if (hasScopeAnnotation(type) || hasNormalScopeAnnotation(type)) {
                    continue;
                }
                javaClassAnnotations.add(annotation);
            }
            if (matchesAnyAnnotation(javaClassAnnotations)) {
                return true;
            }
            for (AnnotatedField<?> field : annotatedType.getFields()) {
                if (matchesAnyAnnotation(field.getAnnotations())) {
                    return true;
                }
            }
            for (AnnotatedMethod<?> method : annotatedType.getMethods()) {
                if (matchesAnyAnnotation(method.getAnnotations())) {
                    return true;
                }
                for (AnnotatedParameter<?> parameter : method.getParameters()) {
                    if (matchesAnyAnnotation(parameter.getAnnotations())) {
                        return true;
                    }
                }
            }
            for (AnnotatedConstructor<?> constructor : annotatedType.getConstructors()) {
                if (matchesAnyAnnotation(constructor.getAnnotations())) {
                    return true;
                }
                for (AnnotatedParameter<?> parameter : constructor.getParameters()) {
                    if (matchesAnyAnnotation(parameter.getAnnotations())) {
                        return true;
                    }
                }
            }

            // CDI TCK parity: constructor-level @WithAnnotations matching must also consider
            // superclass constructors while processing a subclass PAT event.
            Class<?> current = annotatedType.getJavaClass().getSuperclass();
            while (current != null && current != Object.class) {
                for (Constructor<?> constructor : current.getDeclaredConstructors()) {
                    if (matchesAnyAnnotation(new HashSet<>(Arrays.asList(constructor.getAnnotations())))) {
                        return true;
                    }
                    Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
                    for (Annotation[] parameterAnnotationArray : parameterAnnotations) {
                        if (matchesAnyAnnotation(new HashSet<>(Arrays.asList(parameterAnnotationArray)))) {
                            return true;
                        }
                    }
                }
                current = current.getSuperclass();
            }
            return false;
        }

        private boolean matchesAnyAnnotation(Set<Annotation> annotations) {
            for (Annotation annotation : annotations) {
                Class<? extends Annotation> presentType = annotation.annotationType();
                if (withAnnotationsFilter.contains(presentType)) {
                    return true;
                }
                for (Class<? extends Annotation> filter : withAnnotationsFilter) {
                    if (presentType.isAnnotationPresent(filter)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private Object resolveExtensionParameter(Parameter parameter) {
            Class<?> pType = parameter.getType();
            if (BeanManager.class.isAssignableFrom(pType)) {
                return beanManager;
            }
            throw new NonPortableBehaviourException("Injecting " + pType.getName() +
                    " into extension observer method parameter is non-portable; only BeanManager is supported");
        }
    }

    /**
     * Returns the BeanManager for programmatic CDI access.
     *
     * @return the BeanManager
     */
    public BeanManager getBeanManager() {
        if (!initialized) {
            throw new IllegalStateException("Container not initialized. Call setup() first.");
        }
        return beanManager;
    }

    /**
     * Activates the request context if currently inactive.
     *
     * @return true when this call activated the request context, false if it was already active
     */
    public boolean activateRequestContextIfNeeded() {
        if (!initialized) {
            throw new IllegalStateException("Container not initialized. Call setup() first.");
        }
        if (contextManager.getContext(RequestScoped.class).isActive()) {
            return false;
        }
        contextManager.activateRequest();
        return true;
    }

    /**
     * Deactivates request context if currently active.
     */
    public void deactivateRequestContextIfActive() {
        if (!initialized) {
            throw new IllegalStateException("Container not initialized. Call setup() first.");
        }
        if (contextManager.getContext(RequestScoped.class).isActive()) {
            contextManager.deactivateRequest();
        }
    }

    /**
     * Activates a synthetic session context when no session is currently associated
     * with the current thread.
     *
     * @return synthetic session id if activated by this call, otherwise null
     */
    public String activateSyntheticSessionContextIfNeeded() {
        if (!initialized) {
            throw new IllegalStateException("Container not initialized. Call setup() first.");
        }
        String currentSessionId = contextManager.getCurrentSessionId();
        if (currentSessionId != null) {
            return null;
        }
        String syntheticSessionId = "syringe-auto-session-" + UUID.randomUUID();
        contextManager.activateSession(syntheticSessionId);
        return syntheticSessionId;
    }

    /**
     * Deactivates session context for the current thread, when active.
     */
    public void deactivateSessionContextIfActive() {
        if (!initialized) {
            throw new IllegalStateException("Container not initialized. Call setup() first.");
        }
        if (contextManager.getCurrentSessionId() != null) {
            contextManager.deactivateSession();
        }
    }

    /**
     * Invalidates and destroys a specific session context.
     *
     * @param sessionId id of the session to invalidate
     */
    public void invalidateSessionContext(String sessionId) {
        if (!initialized) {
            throw new IllegalStateException("Container not initialized. Call setup() first.");
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }
        contextManager.invalidateSession(sessionId);
    }

    /**
     * Programmatically get an injected instance of the given bean class.
     * Uses default qualifiers unless explicit qualifiers are provided.
     */
    public <T> T inject(Class<T> beanClass, Annotation... qualifiers) {
        if (!initialized) {
            throw new IllegalStateException("Container not initialized. Call setup() first.");
        }
        BeanManager bm = getBeanManager();
        Set<Bean<?>> beans = (qualifiers != null && qualifiers.length > 0)
                ? bm.getBeans(beanClass, qualifiers)
                : bm.getBeans(beanClass);
        if (beans == null || beans.isEmpty()) {
            String message = "No bean found for type " + beanClass.getName();
            knowledgeBase.addInjectionError("Programmatic lookup unsatisfied dependency: " + message);
            throw new UnsatisfiedResolutionException(message);
        }
        Bean<?> bean;
        try {
            bean = bm.resolve(beans);
        } catch (AmbiguousResolutionException e) {
            knowledgeBase.addInjectionError("Programmatic lookup ambiguous dependency for type " +
                    beanClass.getName() + " with qualifiers " + formatQualifiers(qualifiers) + ": " + e.getMessage());
            throw e;
        }
        if (bean == null) {
            String candidates = beans.stream()
                    .map(candidate -> candidate.getBeanClass().getName())
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(", "));
            String message = "Ambiguous dependency for type " + beanClass.getName() +
                    " with qualifiers " + formatQualifiers(qualifiers) +
                    ". Matching beans: [" + candidates + "]";
            knowledgeBase.addInjectionError("Programmatic lookup " + message);
            throw new AmbiguousResolutionException(message);
        }
        CreationalContext<?> ctx = bm.createCreationalContext(bean);
        @SuppressWarnings("unchecked")
        T instance = (T) bm.getReference(bean, beanClass, ctx);
        return instance;
    }

    private String formatQualifiers(Annotation... qualifiers) {
        if (qualifiers == null || qualifiers.length == 0) {
            return "[@Default]";
        }
        return Arrays.stream(qualifiers)
                .filter(Objects::nonNull)
                .map(annotation -> "@" + annotation.annotationType().getSimpleName())
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }

    /**
     * Returns the CDI instance for the container.
     *
     * <p>This enables static container access via {@code CDI.current()} when registered
     * with {@link SyringeCDIProvider}.
     *
     * @return the CDI instance
     */
    public CDI<Object> getCDI() {
        if (!beforeBeanDiscoveryFired) {
            if (isInvokedThroughCdiCurrentLookup()) {
                BeanManagerImpl provisionalBeanManager = beanManager != null
                        ? beanManager
                        : new BeanManagerImpl(knowledgeBase, contextManager);
                return new com.threeamigos.common.util.implementations.injection.spi.CDIImpl(
                        provisionalBeanManager,
                        cdiLiteMode,
                        this::isCdiPortableAccessWindow);
            }
            throw new NonPortableBehaviourException(
                    "CDI.current() access is non-portable before BeforeBeanDiscovery is fired");
        }
        return new com.threeamigos.common.util.implementations.injection.spi.CDIImpl(
                beanManager,
                cdiLiteMode,
                this::isCdiPortableAccessWindow);
    }

    private boolean isCdiPortableAccessWindow() {
        return beforeBeanDiscoveryFired && !beforeShutdownFired;
    }

    private boolean isInvokedThroughCdiCurrentLookup() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            if ("jakarta.enterprise.inject.spi.CDI".equals(element.getClassName())) {
                String method = element.getMethodName();
                if ("current".equals(method) || "getCDIProvider".equals(method)) {
                    return true;
                }
            }
        }
        return false;
    }

    public KnowledgeBase getKnowledgeBase() {
        return knowledgeBase;
    }

    private void applyForcedArchiveModeOverride() {
        BeanArchiveMode forcedBeanArchiveMode = knowledgeBase.getForcedBeanArchiveMode();

        if (forcedBeanArchiveMode == null) {
            return;
        }

        // Rewrites class-level archive mode entries for already discovered classes.
        // This does not discover additional classes; it only changes how existing
        // entries will be validated/registered.
        int updated = 0;
        for (Class<?> clazz : knowledgeBase.getClasses()) {
            knowledgeBase.setBeanArchiveMode(clazz, forcedBeanArchiveMode);
            updated++;
        }
        info("Applied forced bean archive mode " + forcedBeanArchiveMode + " to " + updated + " class(es)");
    }

    private void applyBeansXmlAllModeOverrideForExactPackageBootstrap() {
        if (!exactPackageMatchOnly || knowledgeBase.getForcedBeanArchiveMode() != null) {
            return;
        }

        BeanArchiveMode beansXmlArchiveMode = null;
        for (BeansXml beansXml : knowledgeBase.getBeansXmlConfigurations()) {
            if (beansXml == null) {
                continue;
            }
            String discoveryMode = beansXml.getBeanDiscoveryMode();
            if (discoveryMode == null) {
                continue;
            }
            if ("all".equalsIgnoreCase(discoveryMode.trim())) {
                beansXmlArchiveMode = beansXml.isTrimEnabled()
                        ? BeanArchiveMode.TRIMMED
                        : BeanArchiveMode.EXPLICIT;
                break;
            }
        }

        if (beansXmlArchiveMode == null) {
            return;
        }

        int updated = 0;
        for (Class<?> clazz : knowledgeBase.getClasses()) {
            knowledgeBase.setBeanArchiveMode(clazz, beansXmlArchiveMode);
            updated++;
        }

        info("Applied beans.xml archive mode " + beansXmlArchiveMode + " to " + updated +
                " class(es) for exact-package bootstrap");
    }

    private void info(String message) {
        messageHandler.info("[Syringe] " + message);
    }

    private void warn(String message) {
        messageHandler.warn("[Syringe] " + message);
    }

    private void error(String message) {
        messageHandler.error("[Syringe] " + message);
    }

    private void log(String error, Exception t) {
        messageHandler.exception("[Syringe] " + error, t);
    }

    private String observerInfoKey(ObserverMethodInfo info) {
        return ObserverMethodInfoKey.of(info);
    }

    private ObserverMethodInfo toObserverInfoForLifecycleDispatch(Method method, Bean<?> declaringBean) {
        int observesCount = 0;
        int observesAsyncCount = 0;
        Parameter observedParameter = null;
        Annotation[] observedParameterAnnotations = null;
        Type observedParameterBaseType = null;
        int observedParameterPosition = -1;
        AnnotatedMethod<?> annotatedMethod = null;
        AnnotatedType<?> override = declaringBean != null
                ? knowledgeBase.getAnnotatedTypeOverride(declaringBean.getBeanClass())
                : null;
        if (override != null) {
            annotatedMethod = AnnotatedMetadataHelper.findAnnotatedMethod(override, method);
        }

        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            AnnotatedParameter<?> annotatedParameter = annotatedMethod != null
                    ? findAnnotatedParameter(annotatedMethod, i)
                    : null;
            Annotation[] parameterAnnotations = annotatedParameter != null
                    ? annotatedParameter.getAnnotations().toArray(new Annotation[0])
                    : parameter.getAnnotations();
            Type parameterBaseType = annotatedParameter != null
                    ? annotatedParameter.getBaseType()
                    : parameter.getParameterizedType();

            if (hasObservesAnnotationIn(parameterAnnotations)) {
                observesCount++;
                observedParameter = parameter;
                observedParameterAnnotations = parameterAnnotations;
                observedParameterBaseType = parameterBaseType;
                observedParameterPosition = i;
            }
            if (hasObservesAsyncAnnotationIn(parameterAnnotations)) {
                observesAsyncCount++;
                observedParameter = parameter;
                observedParameterAnnotations = parameterAnnotations;
                observedParameterBaseType = parameterBaseType;
                observedParameterPosition = i;
            }
        }

        if (observesCount == 0 && observesAsyncCount == 0) {
            return null;
        }
        if (observesCount + observesAsyncCount != 1 || observedParameter == null) {
            return null;
        }

        if (resolveWithAnnotationsFilter(observedParameter) != null) {
            throw new DefinitionException("@WithAnnotations is only valid on extension observer parameters " +
                    "observing ProcessAnnotatedType: " +
                    method.getDeclaringClass().getName() + "." + method.getName());
        }

        boolean async = observesAsyncCount > 0;
        Type eventType = GenericTypeResolver.resolve(
                observedParameterBaseType != null ? observedParameterBaseType : observedParameter.getParameterizedType(),
                declaringBean.getBeanClass(),
                method.getDeclaringClass()
        );
        Set<Annotation> qualifiers = extractObserverQualifiers(
                observedParameterAnnotations != null ? observedParameterAnnotations : observedParameter.getAnnotations());
        Reception reception = Reception.ALWAYS;
        TransactionPhase transactionPhase = TransactionPhase.IN_PROGRESS;
        int priority = jakarta.interceptor.Interceptor.Priority.APPLICATION + 500;

        if (async) {
            jakarta.enterprise.event.ObservesAsync observesAsync = getObservesAsyncAnnotationFrom(
                    observedParameterAnnotations != null ? observedParameterAnnotations : observedParameter.getAnnotations());
            if (observesAsync != null) {
                reception = observesAsync.notifyObserver();
            }
        } else {
            jakarta.enterprise.event.Observes observes = getObservesAnnotationFrom(
                    observedParameterAnnotations != null ? observedParameterAnnotations : observedParameter.getAnnotations());
            if (observes != null) {
                reception = observes.notifyObserver();
                transactionPhase = observes.during();
            }
            Integer paramPriority = getPriorityValueFromAnnotations(
                    observedParameterAnnotations != null ? observedParameterAnnotations : observedParameter.getAnnotations());
            if (paramPriority != null) {
                priority = paramPriority;
            } else {
                Integer methodPriority = getPriorityValue(method);
                if (methodPriority != null) {
                    priority = methodPriority;
                }
            }
        }

        return new ObserverMethodInfo(
                method,
                eventType,
                qualifiers,
                reception,
                transactionPhase,
                async,
                declaringBean,
                priority,
                observedParameterPosition
        );
    }

}
