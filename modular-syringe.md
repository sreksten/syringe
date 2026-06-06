# Refactoring Task: Make Syringe Fully Modular

## Project location

`/Users/stefano.reksten/IdeaProjects/syringe`

Currently a single Maven module, Java 8, CDI 4.1 compliant container.

---

## Primary goal

Make Syringe a true Maven multi-module project and make every optional feature a separately
deployable plug-in.

- If a feature module is **absent** and the feature is **not used**, Syringe must initialise and
  run correctly without it.
- If a feature module is **absent** and the feature is **used** (e.g., a `@Decorator` class is
  on the classpath, `addExtension()` is called, a `@Produces` method is found), Syringe must
  throw `NotEnabledFeatureException` with a clear message telling the user which jar to add.
  It must never silently ignore the usage.
- No behaviour must change when all feature modules are present.

**Final-state target:** `syringe-core` must be as small as possible. It must contain only what is truly
indispensable to a working injection container: the `Syringe` orchestrator, the `KnowledgeBase`
central registry, `ContextManager` with the `@Dependent` pseudo-scope, `BeanManagerImpl`, and
the minimal type system. Every optional feature — including interceptors, decorators, events,
extensions, build-compatible extensions, legacy `@New` support, SE container support, bean
discovery scanning, and normal scopes — will move to its own Maven module.

**Starting-point rule for this task:** create the module split first, keep implementation code in
`syringe-core` initially, and progressively move feature code to the appropriate module while
refactoring.

---

## `NotEnabledFeatureException`

Create this exception class in `syringe-core` (package
`com.threeamigos.common.util.implementations.injection`):

```java
public class NotEnabledFeatureException extends RuntimeException {
    public NotEnabledFeatureException(String message) {
        super(message);
    }
}
```

**When to throw it vs. when to warn:**

| Situation | Action |
|-----------|--------|
| User explicitly calls a feature-enabling API (e.g., `addExtension(...)`, `enableLegacyCdi10New(true)`) and the module is absent | Throw `NotEnabledFeatureException` with a message that names the missing jar (e.g., `"Extension support is not available. Add syringe-extensions to your classpath."`) |
| Container discovers actual usage of a disabled feature during initialisation (e.g., a `@Decorator`-annotated class is found, an `@Interceptor` class is found, an `@Observes` method is found, a `@Produces` method is found, a `@New` qualifier is found) | Throw `NotEnabledFeatureException` with the class/method name and the name of the missing jar |
| The feature module is absent and no usage is detected | Silent (no warning, no exception) — the no-op is transparent |

The no-op implementations are therefore **not fully silent**: they must inspect the
`KnowledgeBase` (or the class being processed) at the right lifecycle point and throw
`NotEnabledFeatureException` when they find evidence that the feature is actually used.
Each no-op implementation receives the `KnowledgeBase` via `setKnowledgeBase()` so it can
perform this check.

**Message convention** — always include:
- What was found (annotation name + class or method where it appears).
- Which jar to add (`syringe-<feature>`).

Example:
```
@Decorator found on class com.example.MyDecorator but decorator support is not available.
Add syringe-decorators to your classpath to enable decorator support.
```

---

## Target Maven module structure (primary goal of this task)

```
syringe-core
    Syringe.java, KnowledgeBase, ContextManager (@Dependent only), BeanManagerImpl
    NotEnabledFeatureException
    All manager interfaces (see below) and their NoOp implementations
    Programmatic lookup API:
        jakarta.enterprise.inject.Instance (InstanceImpl / BeanManager#createInstance())
        CDI.select(...) root instance semantics
    Minimal type/annotation utilities
    Supported annotations (ownership in core):
        @jakarta.inject.Inject
        @jakarta.inject.Named
        @jakarta.inject.Qualifier
        @jakarta.inject.Scope (meta-annotation)
        @jakarta.inject.Singleton (mapped to @Dependent in CDI Lite mode)
        @jakarta.enterprise.context.Dependent
        @jakarta.enterprise.inject.Default
        @jakarta.enterprise.inject.Any
        @jakarta.enterprise.inject.Typed
        @jakarta.enterprise.inject.Vetoed
        @jakarta.enterprise.inject.Specializes (managed-bean specialization resolution)
        @jakarta.enterprise.util.Nonbinding (qualifier/interceptor-binding member matching)
        @jakarta.enterprise.inject.TransientReference
        @jakarta.annotation.PostConstruct
        @jakarta.annotation.PreDestroy
        @jakarta.annotation.Priority (base ordering metadata used across features)
        @java.lang.annotation.Inherited
        @java.lang.annotation.Target
        @java.lang.annotation.Repeatable

syringe-interceptors        depends on: syringe-core
    InterceptorSupportImpl, InterceptorResolver, InterceptorAwareProxyGenerator,
    InterceptorChain, InvocationContextImpl, InterceptionFactoryImpl,
    knowledgebase/InterceptorInfo
    META-INF/services/...InterceptorSupport
    Supported annotations:
        @jakarta.interceptor.Interceptor
        @jakarta.interceptor.InterceptorBinding (meta-annotation)
        @jakarta.interceptor.Interceptors (legacy class-level binding)
        @jakarta.interceptor.ExcludeClassInterceptors
        @jakarta.interceptor.ExcludeDefaultInterceptors
        @jakarta.interceptor.AroundInvoke
        @jakarta.interceptor.AroundConstruct
        @jakarta.enterprise.inject.Intercepted
        @jakarta.annotation.Priority (interceptor ordering; metadata owned by core)
        @jakarta.enterprise.inject.spi.InterceptionType (enum, not annotation, but handled here)

syringe-decorators          depends on: syringe-core
    DecoratorSupportImpl, DecoratorResolver, DecoratorAwareProxyGenerator,
    DecoratorChain, DecoratorChainBuilder, DecoratorInstance,
    knowledgebase/DecoratorInfo
    META-INF/services/...DecoratorSupport
    Supported annotations:
        @jakarta.decorator.Decorator
        @jakarta.decorator.Delegate
        @jakarta.enterprise.inject.Decorated
        @jakarta.annotation.Priority (decorator ordering; metadata owned by core)

syringe-scopes              depends on: syringe-core
    ApplicationScopedContext, RequestScopedContext, SessionScopedContext,
    ConversationScopedContext, ConversationImpl, ClientProxyGenerator
    META-INF/services/...ScopeSupport
    Supported annotations:
        @jakarta.enterprise.context.ApplicationScoped
        @jakarta.enterprise.context.RequestScoped
        @jakarta.enterprise.context.SessionScoped
        @jakarta.enterprise.context.ConversationScoped
        @jakarta.enterprise.context.NormalScope (meta-annotation for custom normal scopes)
        @jakarta.enterprise.context.Initialized
        @jakarta.enterprise.context.BeforeDestroyed
        @jakarta.enterprise.context.Destroyed
        @jakarta.enterprise.context.control.ActivateRequestContext
        @jakarta.ejb.PrePassivate
        @jakarta.ejb.PostActivate

syringe-events              depends on: syringe-core
    EventImpl, ObserverMethodInfo, ConversationPropagationRegistry,
    propagation/*
    META-INF/services/...ObserverSupport
    Supported annotations:
        @jakarta.enterprise.event.Observes
        @jakarta.enterprise.event.ObservesAsync
        jakarta.enterprise.event.Startup (container lifecycle event payload type)
        jakarta.enterprise.event.Shutdown (container lifecycle event payload type)
        @jakarta.enterprise.event.Reception (enum used with @Observes)
        @jakarta.enterprise.event.TransactionPhase (enum used with @Observes)

syringe-discovery           depends on: syringe-core
    ParallelClasspathScanner, BeanArchiveDetector, ClassProcessor,
    beansxml/*
    META-INF/services/...DiscoverySupport
    Supported annotations / configuration:
        beans.xml (EXPLICIT, IMPLICIT, NONE archive modes)
        @jakarta.enterprise.inject.Alternative (activation via beans.xml <alternatives>)
        @jakarta.enterprise.inject.Stereotype (meta-annotation, resolved during scanning)
        @jakarta.enterprise.inject.Model (built-in stereotype resolved during scanning)
        @jakarta.enterprise.inject.LookupIfProperty
        @jakarta.enterprise.inject.LookupUnlessProperty
        beans.xml <scan><exclude> filters
        beans.xml <interceptors>, <decorators>, <alternatives> stanzas

syringe-producers           depends on: syringe-core
    ProducerBean, ProducerFactoryImpl
    META-INF/services/...ProducerSupport
    Supported annotations:
        @jakarta.enterprise.inject.Produces
        @jakarta.enterprise.inject.Disposes
    Notes:
        Producer and disposer processing is optional and lives in this module.
        Instance<X> remains in syringe-core and continues to resolve non-producer beans
        when syringe-producers is absent.

syringe-extensions          depends on: syringe-core
    ExtensionsManagerImpl, extensions/*
    META-INF/services/...ExtensionsManager
    Supported annotations / SPI:
        jakarta.enterprise.inject.spi.Extension (marker interface, discovered via ServiceLoader)
        All portable extension SPI observer method parameter types:
            ProcessAnnotatedType, ProcessInjectionPoint, ProcessInjectionTarget,
            ProcessBeanAttributes, ProcessBean, ProcessProducer,
            ProcessProducerMethod, ProcessProducerField,
            ProcessObserverMethod, AfterTypeDiscovery,
            AfterBeanDiscovery, AfterDeploymentValidation, BeforeShutdown
        BeforeBeanDiscovery (allows addAnnotatedType, addQualifier, addScope, etc.)
        @jakarta.enterprise.inject.spi.WithAnnotations (extension observer parameter filter)

syringe-bce                 depends on: syringe-core, syringe-extensions
    BuildCompatibleExtensionsManagerImpl, bce/*
    META-INF/services/...BuildCompatibleExtensionsManager
    Supported annotations / SPI:
        jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension (marker interface)
        @jakarta.enterprise.inject.build.compatible.spi.Discovery
        @jakarta.enterprise.inject.build.compatible.spi.Enhancement
        @jakarta.enterprise.inject.build.compatible.spi.Registration
        @jakarta.enterprise.inject.build.compatible.spi.Synthesis
        @jakarta.enterprise.inject.build.compatible.spi.Validation
        @jakarta.enterprise.inject.build.compatible.spi.SkipIfPortableExtensionPresent
        jakarta.enterprise.invoke.Invoker (Invoker API, wired in REGISTRATION phase)

syringe-legacy-new          depends on: syringe-core
    LegacyNewQualifierHelper, LegacyNewBeanAdapter
    META-INF/services/...LegacyNewSupport
    Supported annotations:
        @javax.enterprise.inject.New (deprecated CDI 1.0 qualifier; legacy only)

syringe-se                  depends on: syringe-core
    SyringeSeContainer, SyringeSeContainerInitializer, SyringeCDIProvider
    META-INF/services/...SeSupport
    META-INF/services/jakarta.enterprise.inject.se.SeContainerInitializer
    META-INF/services/jakarta.enterprise.inject.spi.CDIProvider
    Supported annotations / SPI:
        SeContainerInitializer (programmatic bootstrap API)
        SeContainer (programmatic container handle)
        CDI.current() provider registration

syringe-full                aggregate + integration-test module
    Declares dependencies on every optional module above
    Represents a full CDI 4.1-compliant container equivalent to the current single module
    Temporary testing rule for this task:
        Move and run the complete test suite here
        Keep tests centralized here until feature extraction stabilizes
```

All entries currently present in `AnnotationsEnum` must be owned by one of the modules above.

---

## Multi-module transition strategy for this task

1. Perform the Maven split now: create parent/aggregator setup and all modules listed above.
2. Move the full test suite to `syringe-full` immediately; tests must continue to pass there.
3. For each feature step, move the implementation code and service file from `syringe-core` into
   the appropriate feature module as part of that same step — do not defer code migration.
   After each move the feature module must compile and all tests in `syringe-full` must be green.
4. Test redistribution to feature modules is intentionally deferred; keep tests centralized in
   `syringe-full` for now and split tests later when boundaries stabilize.

---

## The universal pattern (apply to every feature area)

For each feature area:

1. **Define a manager interface** in `syringe-core` (package
   `com.threeamigos.common.util.implementations.injection`) that abstracts the feature boundary.
2. **Create a `NoOp*` implementation** of that interface. Every method is either a silent no-op
   or a usage-detecting check that throws `NotEnabledFeatureException` (see table above).
   Store the `MessageHandler` (for logging) and the `KnowledgeBase` (for usage detection).
3. **Add a private static `discover*()` helper** in `Syringe.java` that uses
   `ServiceLoader.load(Interface.class).iterator()` (Java 8 compatible — do not use
   `findFirst()`, which is Java 9+) and falls back to the no-op when nothing is found.
4. **Rewrite `Syringe.reset()` / relevant lifecycle methods** to call the helper instead of
   hard-instantiating the `*Impl` class.
5. **Remove all direct imports** of `*Impl` classes from `Syringe.java` so that `syringe-core`
   has zero compile-time dependency on any optional class.
6. **Register the real implementation** in
   `src/main/resources/META-INF/services/<fully-qualified-interface-name>` so that the
   `ServiceLoader` finds it when the implementation is on the classpath.

All interface setter methods must be callable unconditionally: no-op implementations silently
absorb them (except that they store `MessageHandler` and `KnowledgeBase` for later use).

---

## Feature areas — ordered by implementation priority (easiest first)

Read the relevant source files before touching any of them.

---

### FEATURE 1 — Portable Extensions (`ExtensionsManager`)

**Interface (already exists)**:
`extensions/ExtensionsManager.java`

**Implementation (already exists)**:
`extensions/ExtensionsManagerImpl.java`

**Steps**:
1. Create `extensions/NoOpExtensionsManager.java`.
   - `addExtension(String)` / `addExtension(Extension)` → throw `NotEnabledFeatureException`:
     `"Extension support is not available. Add syringe-extensions to your classpath."`
   - `getExtensions()` → `Collections.emptyList()`.
   - `getExtensionClassNames()` → `Collections.emptyList()`.
   - All lifecycle methods → silent no-ops (no extensions registered, nothing to fire).
2. Create service file:
   `src/main/resources/META-INF/services/com.threeamigos.common.util.implementations.injection.extensions.ExtensionsManager`
   containing:
   `com.threeamigos.common.util.implementations.injection.extensions.ExtensionsManagerImpl`
3. Add `private static ExtensionsManager discoverExtensionsManager()` to `Syringe.java`.
4. In `Syringe.reset()`, replace `extensionsManager = new ExtensionsManagerImpl()` with
   `extensionsManager = discoverExtensionsManager()`.
5. Remove the `ExtensionsManagerImpl` import from `Syringe.java`.

---

### FEATURE 2 — Build-Compatible Extensions (`BuildCompatibleExtensionsManager`)

**Interface (already exists)**:
`bce/BuildCompatibleExtensionsManager.java`

**Implementation (already exists)**:
`bce/BuildCompatibleExtensionsManagerImpl.java`

**Steps**:
1. Create `bce/NoOpBuildCompatibleExtensionsManager.java`.
   - `addBuildCompatibleExtension(String)` → throw `NotEnabledFeatureException`:
     `"Build-compatible extension support is not available. Add syringe-bce to your classpath."`
   - All lifecycle methods → silent no-ops.
2. Create service file:
   `src/main/resources/META-INF/services/com.threeamigos.common.util.implementations.injection.bce.BuildCompatibleExtensionsManager`
   containing:
   `com.threeamigos.common.util.implementations.injection.bce.BuildCompatibleExtensionsManagerImpl`
3. Add `private static BuildCompatibleExtensionsManager discoverBuildCompatibleExtensionsManager()`
   to `Syringe.java`.
4. In `Syringe.reset()`, replace the hard-wired instantiation with the discovery helper.
5. Remove the `BuildCompatibleExtensionsManagerImpl` import from `Syringe.java`.

---

### ~~FEATURE 3 — Interceptors (`InterceptorSupport`)~~

**Interface to create**:
`interceptors/InterceptorSupport.java`

Study `InterceptorResolver`, `InterceptorAwareProxyGenerator`, and all `Syringe.java` private
methods that reference interceptors before writing the interface.

```java
public interface InterceptorSupport {
    void setMessageHandler(MessageHandler messageHandler);
    void setKnowledgeBase(KnowledgeBase knowledgeBase);
    void setBeanManager(BeanManager beanManager);

    /** Called during the start() phase to process interceptor metadata. */
    void processInterceptorBeans();

    /** Called during validateDeployment() to validate beans.xml interceptor stanzas. */
    void validateBeansXmlInterceptorConfiguration();

    /** Returns the InterceptorAwareProxyGenerator for wiring into bean creation. */
    InterceptorAwareProxyGenerator getProxyGenerator();

    /** Returns the InterceptorResolver for wiring into bean creation. */
    InterceptorResolver getResolver();

    void clear();
}
```

**No-op `processInterceptorBeans()`** must inspect the `KnowledgeBase` for any class annotated
with `@jakarta.interceptor.Interceptor`. If any are found, throw `NotEnabledFeatureException`:
```
@Interceptor found on class com.example.MyInterceptor but interceptor support is not available.
Add syringe-interceptors to your classpath to enable interceptor support.
```

**No-op `validateBeansXmlInterceptorConfiguration()`** must inspect the `KnowledgeBase` for
any interceptor entries declared in `beans.xml`. If any are found, throw
`NotEnabledFeatureException` with the beans.xml interceptor class name and the missing jar.

**No-op `getProxyGenerator()`** returns a proxy generator that returns the target object
unchanged (no proxy wrapping).

**No-op `getResolver()`** returns a resolver that always returns an empty list of interceptors.

**Steps**:
1. Create the `InterceptorSupport` interface.
2. Create `InterceptorSupportImpl` — wraps the existing `InterceptorResolver` and
   `InterceptorAwareProxyGenerator` that are currently direct fields in `Syringe`.
   Move the interceptor-related private methods from `Syringe.java` into this class.
3. Create `NoOpInterceptorSupport` with usage detection as described above.
4. Create service file for the interface.
5. Add `private static InterceptorSupport discoverInterceptorSupport()` to `Syringe.java`.
6. Replace all direct interceptor field/method usage in `Syringe` with calls through the
   `InterceptorSupport` instance obtained from discovery.
7. Remove all direct `interceptors.*Impl` imports from `Syringe.java`.

---

### FEATURE 4 — Decorators (`DecoratorSupport`)

**Interface to create**:
`decorators/DecoratorSupport.java`

Study `DecoratorResolver`, `DecoratorAwareProxyGenerator`, and all decorator-related private
methods in `Syringe.java` before writing the interface.

```java
public interface DecoratorSupport {
    void setMessageHandler(MessageHandler messageHandler);
    void setKnowledgeBase(KnowledgeBase knowledgeBase);
    void setBeanManager(BeanManager beanManager);

    /** Called during the start() phase to process decorator metadata. */
    void processDecoratorBeans();

    /** Called during validateDeployment() to validate beans.xml decorator stanzas. */
    void validateBeansXmlDecoratorConfiguration();

    /** Called during validateDeployment() to validate programmatically registered decorators. */
    void validateProgrammaticDecoratorConfiguration();

    /** Returns the DecoratorAwareProxyGenerator for wiring into bean creation. */
    DecoratorAwareProxyGenerator getProxyGenerator();

    /** Returns the DecoratorResolver for wiring into bean creation. */
    DecoratorResolver getResolver();

    void clear();
}
```

**No-op `processDecoratorBeans()`** must inspect the `KnowledgeBase` for any class annotated
with `@jakarta.decorator.Decorator`. If any are found, throw `NotEnabledFeatureException`:
```
@Decorator found on class com.example.MyDecorator but decorator support is not available.
Add syringe-decorators to your classpath to enable decorator support.
```

**No-op `validateBeansXmlDecoratorConfiguration()`** must inspect the `KnowledgeBase` for any
decorator entries in `beans.xml`. If any are found, throw `NotEnabledFeatureException` with the
beans.xml decorator class name and the missing jar.

**No-op `validateProgrammaticDecoratorConfiguration()`** must inspect the `KnowledgeBase` for
any programmatically registered decorators. If any are found, throw
`NotEnabledFeatureException`.

**No-op `getProxyGenerator()`** returns a proxy generator that returns the target unchanged.

**No-op `getResolver()`** returns a resolver that always returns an empty list of decorators.

Follow the same steps as for `InterceptorSupport`.

---

### FEATURE 5 — Legacy `@New` annotation support (`LegacyNewSupport`)

**Interface to create**:
`annotations/legacy/LegacyNewSupport.java`

Study `LegacyNewQualifierHelper`, `LegacyNewBeanAdapter`, and how `legacyCdi10NewEnabled` is
used in `Syringe.java` and `BeanManagerImpl` before writing the interface.

```java
public interface LegacyNewSupport {
    void setMessageHandler(MessageHandler messageHandler);
    void setKnowledgeBase(KnowledgeBase knowledgeBase);
    void setBeanManager(BeanManager beanManager);

    /** Returns true if @New qualifier resolution is active. */
    boolean isEnabled();

    /** Resolves a @New injection point to the appropriate bean, or null if not applicable. */
    Bean<?> resolveNewInjectionPoint(InjectionPoint injectionPoint);

    /** Validates @New injection points during deployment validation. */
    void validateNewInjectionPoints();
}
```

**No-op `validateNewInjectionPoints()`** must inspect the `KnowledgeBase` for any injection
point carrying the `@javax.enterprise.inject.New` qualifier. If any are found, throw
`NotEnabledFeatureException`:
```
@New qualifier found on injection point [field/parameter description] but legacy @New support
is not available. Add syringe-legacy-new to your classpath to enable @New support.
```

**No-op `isEnabled()`** returns `false`.

**No-op `resolveNewInjectionPoint()`** returns `null`.

**Steps**:
1. Create the `LegacyNewSupport` interface.
2. Create `LegacyNewSupportImpl` that wraps `LegacyNewQualifierHelper` and
   `LegacyNewBeanAdapter`; `isEnabled()` returns `true`.
3. Create `NoOpLegacyNewSupport` with usage detection as described above.
4. Create service file.
5. Add `private static LegacyNewSupport discoverLegacyNewSupport()` to `Syringe.java`.
6. Replace the `legacyCdi10NewEnabled` boolean field and all references to
   `LegacyNewQualifierHelper` / `LegacyNewBeanAdapter` in `Syringe.java` with calls through
   the discovered `LegacyNewSupport` instance.
   — The public method `enableLegacyCdi10New(boolean)` on `Syringe` must remain. If called
     when the module is absent, it must throw `NotEnabledFeatureException`:
     `"Legacy @New support is not available. Add syringe-legacy-new to your classpath."`
7. Pass the `LegacyNewSupport` instance (or just `isEnabled()`) to `BeanManagerImpl` and the
   injection validators that currently receive the `legacyCdi10NewEnabled` boolean.
8. Remove all direct imports of legacy-new classes from `Syringe.java`.

---

### FEATURE 6 — SE container support (`SeSupport`)

**Interface to create**:
`se/SeSupport.java`

Study `SyringeSeContainer`, `SyringeSeContainerInitializer`, `SyringeCDIProvider`, and the SE
helper methods on `Syringe.java` before writing the interface.

```java
public interface SeSupport {
    void setMessageHandler(MessageHandler messageHandler);
    void setKnowledgeBase(KnowledgeBase knowledgeBase);
    void setBeanManager(BeanManager beanManager);
    void setContextManager(ContextManager contextManager);

    /** Called at the end of start() to register the CDI provider (CDI.current()). */
    void registerCdiProvider(Syringe syringe);

    /** Called at the start of shutdown() to unregister the CDI provider. */
    void unregisterCdiProvider();

    /** Activates request context for the current thread if not already active. */
    void activateRequestContextIfNeeded();

    /** Deactivates request context for the current thread if active. */
    void deactivateRequestContextIfActive();

    /** Activates a synthetic session context if SE session management is needed. */
    void activateSyntheticSessionContextIfNeeded();

    /** Deactivates session context if active. */
    void deactivateSessionContextIfActive();

    /** Invalidates and destroys session context beans. */
    void invalidateSessionContext();
}
```

**No-op**: All methods are silent no-ops. SE support is an infrastructure feature, not a
bean-model feature, so there are no in-container annotations to detect: absence of this module
simply means `CDI.current()` will not be registered and SE context management methods will do
nothing. No `NotEnabledFeatureException` is thrown by the no-op — the container still functions
as a programmatically driven container.

**Steps**:
1. Create the `SeSupport` interface.
2. Create `SeSupportImpl` that delegates to the existing SE classes.
3. Create `NoOpSeSupport` (fully silent).
4. Create service file.
5. Add `private static SeSupport discoverSeSupport()` to `Syringe.java`.
6. Replace all direct SE method calls and CDI provider registrations in `Syringe.java` with
   calls through the discovered `SeSupport` instance.
7. Remove all direct `se.*` and `SyringeCDIProvider` imports from `Syringe.java`.

---

### FEATURES 7–10 — Future scope (define now, implement later)

The following feature areas follow the same pattern but have higher internal coupling. Define
their interfaces now as stubs so that the boundaries are clear, but defer full implementation.

**`ObserverSupport`** (events & observers — `events/ObserverSupport.java`):
- `discoverObserverMethods(Class<?> beanClass)`, `fireEvent(Object, Annotation...)`,
  `fireEventAsync(Object, Annotation...)`, `processObserverMethodSpiEvent(...)`, `clear()`.
- No-op usage detection: if `@Observes` or `@ObservesAsync` methods are found in any
  discovered class, throw `NotEnabledFeatureException` naming `syringe-events`.
- No-op fire methods: silently drop events.

**`DiscoverySupport`** (classpath scanning — `discovery/DiscoverySupport.java`):
- `scanClasspath(String[] packages, boolean exact)` → `Collection<Class<?>>`,
  `parseBeansXml(URL)` → `BeansXml`, `clear()`.
- No-op: returns no classes; `parseBeansXml` returns empty config.
  Core still supports programmatic registration via `addDiscoveredClass()` — no exception thrown.

**`ProducerSupport`** (producers — `resolution/ProducerSupport.java`):
- `discoverProducers(Class<?> beanClass)`, `processProducerEvents()`, `clear()`.
- No-op usage detection: if `@Produces` or `@Disposes` methods are found in any discovered
  class, throw `NotEnabledFeatureException` naming `syringe-producers`.

**`ScopeSupport`** (normal scopes — `scopes/ScopeSupport.java`):
- `registerNormalScopes(ContextManager)`, `createClientProxy(Class<?> beanClass, Bean<?> bean)`.
- No-op usage detection: if a bean is annotated with `@ApplicationScoped`, `@RequestScoped`,
  `@SessionScoped`, `@ConversationScoped`, or any custom `@NormalScope`, throw
  `NotEnabledFeatureException` naming `syringe-scopes`.
- No-op `createClientProxy`: returns `null` (core falls back to direct instantiation for
  `@Dependent`-only containers).

---

## Implementation order

Implement in this exact order. Each step must be fully tested before starting the next.

| Order | Feature | Reason |
|-------|---------|--------|
| ~~-1~~ | ~~Maven multi-module split baseline (`syringe-core` + optional modules + `syringe-full`)~~ | ~~Establishes build/runtime boundaries before feature extraction~~ |
| ~~0~~ | ~~`NotEnabledFeatureException`~~ | ~~Required by all subsequent steps~~ |
| ~~1~~ | ~~Portable Extensions~~ | ~~Already has interface; lowest risk~~ |
| ~~2~~ | ~~Build-Compatible Extensions~~ | ~~Already has interface; depends on #1~~ |
| ~~3~~ | ~~Interceptors~~ | ~~Higher coupling but self-contained proxy system~~ |
| 4 | Decorators | Symmetric with interceptors |
| 5 | Legacy `@New` support | Tiny feature surface, simple coupling |
| 6 | SE container support | Already largely self-contained; no-op is fully silent |
| 7+ | Events, Discovery, Producers, Scopes | Future work, higher coupling |

---

## What to do for each step (checklist)

For every feature area implemented:

- [ ] Read all referenced source files before writing any code
- [ ] Create `NotEnabledFeatureException` (step 0 only; reuse afterwards)
- [ ] Define the manager interface in the feature sub-package of `syringe-core`
- [ ] Create the `NoOp*` implementation in `syringe-core`:
      — stores `MessageHandler` and `KnowledgeBase`
      — throws `NotEnabledFeatureException` on explicit API calls and on usage detection
      — is fully silent only where no usage is detectable (SE support, pure lifecycle hooks)
- [ ] Add `private static <Interface> discover<Feature>()` to `Syringe.java`
- [ ] Rewrite `reset()` / lifecycle methods in `Syringe.java` to use the discovered instance
- [ ] Remove all direct `*Impl` imports from `Syringe.java`
- [ ] Move the `*Impl` class(es) and all feature-specific source files from `syringe-core`
      to the target feature module (e.g. `syringe-extensions/src/main/java/...`)
- [ ] Move the `META-INF/services/<interface-fqn>` file from `syringe-core` to the feature module
- [ ] Confirm the feature module compiles and all tests in `syringe-full` are green

---

## Testing the no-op path (template for each feature)

Add a test class for each feature. The test must:

1. Instantiate `Syringe` normally.
2. Inject the `NoOp*` implementation via the package-private setter added to `Syringe`.
3. Call `setup()` and `shutdown()`.
4. Assert no exception is thrown (assuming no usage of the missing feature in the test beans).
5. Assert that at least one normal `@Dependent` bean can be looked up and instantiated.
6. **Usage-detection test**: Register a bean class that uses the disabled feature
   (e.g., annotated with `@Decorator`). Assert that `setup()` throws
   `NotEnabledFeatureException` with a message that mentions the missing jar name.
7. **Explicit API test**: Call the feature-enabling API directly (e.g., `addExtension(...)`,
   `enableLegacyCdi10New(true)`). Assert that `NotEnabledFeatureException` is thrown
   immediately, before `setup()` is called.

Until module boundaries are finalized, keep these tests under `syringe-full`.

---

## Utility class splitting

Scattered across the packages are utility classes containing static helper methods. During the
module migration, check each utility class and identify which methods are used exclusively by a
single feature module. When that is the case:

- Move those methods to a dedicated utility class **inside the target feature module** rather than
  keeping them in `syringe-core`.
- If a utility class ends up with no methods remaining in `syringe-core`, delete it entirely from
  core and keep only the feature-module copy.
- If a utility class is genuinely shared (methods used by two or more modules), keep the shared
  methods in `syringe-core` and move the feature-specific ones out.

The goal is to keep `syringe-core` as small as possible: every static helper that is only needed
by an optional feature is a candidate for removal from core.

---

## Constraints

- Do not alter existing `ExtensionsManager` or `BuildCompatibleExtensionsManager` interfaces.
- Do not alter `ExtensionsManagerImpl` or `BuildCompatibleExtensionsManagerImpl`.
- Do not change any existing public method signature on `Syringe`.
- Java 8 compatibility must be maintained throughout. Use `ServiceLoader.load(X.class).iterator()`
  with `.hasNext()` — do not use `findFirst()` (Java 9+), `stream()` (Java 9+), or `var`.
- New public API additions to `Syringe` must be limited to what is listed above (the public
  `enableLegacyCdi10New(boolean)` method already exists and must stay; it now throws instead
  of silently accepting when the module is absent).
- Package-private setters on `Syringe` for each discovered manager are permitted and required
  for testing the no-op path.
- Perform the Maven multi-module split in this task, starting from the module layout above.
- Keep tests in `syringe-full` for now; move tests to feature modules later as a separate step.
- Temporary bootstrap rule: implementation code may remain in `syringe-core` initially and then
  be moved incrementally to feature modules during the refactoring.
