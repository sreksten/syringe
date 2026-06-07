# Refactoring Task: Make Syringe Fully Modular

## Project location

`/Users/stefano.reksten/IdeaProjects/syringe`

Already a Maven multi-module project, Java 8, CDI 4.1 compliant container.

---

## Primary goal

Complete feature modularization within the existing Maven multi-module project, so optional
runtime capabilities are separately deployable plug-ins.

**Product intent (standalone-first):**
- Syringe should run with a very small baseline for small standalone applications.
- Out-of-the-box baseline must include classpath scanning/discovery, simple injection,
  `@Dependent` lifecycle, and programmatic lookup (`Instance`, `CDI.select` root semantics).
- Request/session/conversation scopes, extension processing, BCE processing, observers,
  interceptors, decorators, and proxy generation are optional features.

- If a feature module is **absent** and the feature is **not used**, Syringe must initialise and
  run correctly without it.
- If a feature module is **absent** and the feature is **used** (e.g., a `@Decorator` class is
  on the classpath, `addExtension()` is called), Syringe must
  throw `NotEnabledFeatureException` with a clear message telling the user which jar to add.
  It must never silently ignore the usage.
- No behaviour must change when all feature modules are present.

**Final-state target:** `syringe-core` must be as small as possible. It must contain only what is truly
indispensable to a working injection container: the `Syringe` orchestrator, the `KnowledgeBase`
central registry, `ContextManager` with the `@Dependent` pseudo-scope, `BeanManagerImpl`, and
the minimal type system. Every optional feature — including interceptors, decorators, events,
extensions, build-compatible extensions, legacy `@New` support, SE container support, and normal
scopes — will move to its own Maven module.

This plan only covers the modules and feature boundaries explicitly listed below.

**Starting-point rule for this task:** the module split was created, implementation code in
`syringe-core` should be progressively moved to the appropriate module while refactoring.

### Runtime profiles to enforce

1. **Standalone core profile (default small runtime):**
   - Dependency set: `syringe-core` only.
   - Must support: scanning/discovery, managed bean resolution, injection, `@Dependent` lifecycle,
     `Instance`/`CDI.select`, qualifiers, producer method/field handling already owned by core.
   - Must not require proxies.
2. **Full CDI profile:**
   - Dependency set: `syringe-full` aggregate (all modules).
   - Must preserve current CDI 4.1 behavior.

Modularization is successful only when both profiles pass their dedicated test suites.

### Documentation status convention

- Completed checklist items, completed step entries, and completed implementation-order rows must
  be marked with Markdown strikethrough: `~~...~~`.
- Pending or partially completed items must remain unstruck.
- Feature headers may use `[Done]` as a summary label, but item-level completion still requires
  `~~...~~`.

---

## `NotEnabledFeatureException`

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
| User explicitly calls a feature-enabling API (e.g., `addExtension(...)`, `enableLegacyCdi10New(true)`) and the module is absent | Throw `NotEnabledFeatureException` in canonical 2-line format with usage=`API call`, API method signature as location, and required module/feature tokens |
| Container discovers actual usage of a disabled feature during initialisation (e.g., a `@Decorator`-annotated class is found, an `@Interceptor` class is found, an `@Observes` method is found, a `@New` qualifier is found) | Throw `NotEnabledFeatureException` in canonical 2-line format with annotation/stanza as usage and concrete class/method/config location plus required module/feature tokens |
| The feature module is absent and no usage is detected | Silent (no warning, no exception) — the no-op is transparent |

The no-op implementations are therefore **not fully silent**: they must inspect the
`KnowledgeBase` (or the class being processed) at the right lifecycle point and throw
`NotEnabledFeatureException` when they find evidence that the feature is actually used.
Each no-op implementation receives the `KnowledgeBase` via `setKnowledgeBase()` so it can
perform this check.

**Message convention (strict, mandatory, test-enforced):**
- Every `NotEnabledFeatureException` message must follow this 2-line template:
  1) `<usage> found at <location> but <feature-label> support is not available.`
  2) `Add <module-artifact-id> to your classpath to enable <feature-label> support.`
- Required fields:
  - `<usage>`: concrete trigger (`@Decorator`, `@Interceptor`, `@Observes`, `beans.xml <decorators>`, `API call`, etc.)
  - `<location>`: concrete site (fully-qualified class, method signature, injection point, beans.xml class name, or API method signature)
  - `<module-artifact-id>`: exact module name (for example `syringe-decorators`)
  - `<feature-label>`: human-readable feature name (`decorator`, `interceptor`, `event/observer`, `legacy @New`, etc.)
- Generic messages without location are not acceptable for usage-detection paths.

Example:
```
@Decorator found at class com.example.MyDecorator but decorator support is not available.
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
    Discovery/scanning and base bean registration pipeline
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

syringe-proxy               depends on: syringe-core
    Shared proxy/runtime-bytecode infrastructure used by optional features
    Owns ByteBuddy dependency and reusable proxy helper APIs/utilities
    No CDI feature semantics by itself; used by feature modules that generate proxies

syringe-interceptors        depends on: syringe-core, syringe-proxy
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

syringe-decorators          depends on: syringe-core, syringe-proxy
    DecoratorSupportImpl, DecoratorResolver, DecoratorAwareProxyGenerator,
    DecoratorChain, DecoratorChainBuilder, DecoratorInstance,
    knowledgebase/DecoratorInfo
    META-INF/services/...DecoratorSupport
    Supported annotations:
        @jakarta.decorator.Decorator
        @jakarta.decorator.Delegate
        @jakarta.enterprise.inject.Decorated
        @jakarta.annotation.Priority (decorator ordering; metadata owned by core)

syringe-scopes              depends on: syringe-core, syringe-proxy
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
    Declares dependencies on every optional feature module above plus `syringe-proxy`
    Represents a full CDI 4.1-compliant runtime configuration
    Temporary testing rule for this task:
        Move and run the complete test suite here
        Keep tests centralized here until feature extraction stabilizes
```

All entries currently present in `AnnotationsEnum` must remain owned by either `syringe-core`
or one of the active feature modules above.

---

## Multi-module transition strategy for this task

1. The full test suite is in `syringe-full`; tests must continue to pass there.
2. Introduce `syringe-proxy` as the shared ByteBuddy/proxy module, then migrate ByteBuddy-backed
   proxy code out of `syringe-core` into either `syringe-proxy` or feature modules that depend on it.
3. For each feature step, move the implementation code and service file from `syringe-core` into
   the appropriate feature module as part of that same step — do not defer code migration.
   After each move the feature module must compile and all tests in `syringe-full` must be green.
4. Test redistribution to feature modules is intentionally deferred; keep tests centralized in
   `syringe-full` for now and split tests later when boundaries stabilize.
5. ~~Add and keep a **core-only profile gate** (`syringe-core` alone on classpath) to guarantee the
   standalone baseline does not regress while optional features move out.~~

### Minimum required changes to satisfy standalone-first goal

These are the concrete deltas still required for the desired “small by default” runtime:

1. ~~Remove remaining direct optional-feature instantiations from core classes (`Syringe`,
   `BeanResolver`, `BeanImpl`, `BeanManagerImpl`) and route through SPI interfaces only.~~
2. ~~Complete feature extraction so `syringe-core` no longer owns runtime semantics for:
   events/observers, extensions, BCE, interceptors, decorators, normal scopes.~~
3. ~~Keep proxy generation behind optional modules only (`syringe-proxy` + consumers); core must not
   assume proxy availability for baseline execution paths.~~
4. ~~Ensure absent optional modules fail only on real usage (annotation discovery or explicit API
   call), with canonical `NotEnabledFeatureException` messages.~~
5. ~~Ensure extension/BCE phases degrade to safe no-op behavior when their modules are absent and no
   extension/BCE usage is requested.~~
6. ~~Add CI test matrix for:~~
   - ~~core-only standalone profile~~
   - ~~full profile (`syringe-full`)~~
   - ~~negative tests proving missing-feature usage throws clear guidance.~~

**Status update (2026-06-06):**
- ~~`syringe-full/pom.xml` now has a real core-only gate:~~
  - ~~`full-runtime` profile activates when `syringe.core.only` is not set.~~
  - ~~`core-only-tests` profile activates when `-Dsyringe.core.only=true` and compiles/runs only
    `coreonly` tests with `syringe-core` on classpath.~~
- ~~Added dedicated standalone/core-only tests under
  `syringe-full/src/test/java/.../coreonly/...` for smoke + missing-feature negative guidance.~~
- ~~Added CI workflow `.github/workflows/runtime-matrix.yml` with two lanes:~~
  - ~~core-only: `mvn -B -ntp -pl syringe-full -am -Dsyringe.core.only=true test`~~
  - ~~full: `mvn -B -ntp -pl syringe-full -am test`~~

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

## Phase 2 — Actually moving the bulk of feature code

### Why classes are stuck in core

Phase 1 introduced ServiceLoader-backed `*Support` interfaces, but a large amount of optional
feature code is still directly referenced inside core classes. The blockers below reflect the
**actual codebase status**:

| Core class (must stay in core) | Direct instantiation | Blocks moving |
|---|---|---|
| `Syringe.java` | `new ProcessAnnotatedTypeImpl(...)`, `new ProcessInjectionPointImpl(...)`, `new AfterBeanDiscoveryImpl(...)`, etc. | `spi/spievents/*` classes cannot move to `syringe-extensions` yet |
| `BeanResolver` | `new EventImpl<>(...)` | `EventImpl` and entire event model |
| `BeanResolver` | `new RegistryContextTokenProvider()` | entire `propagation.*` package |
| `BeanResolver` | `new LegacyNewBeanAdapter(...)` | `LegacyNewBeanAdapter` |
| `BeanResolver` | `new DecoratorResolver(...)`, `new DecoratorAwareProxyGenerator()` | decorator resolver/proxy classes cannot move to `syringe-decorators` yet |
| `BeanManagerImpl` | `new LegacyNewBeanAdapter(...)` | `LegacyNewBeanAdapter` |
| `BeanManagerImpl` | `new DecoratorResolver(...)`, `new DecoratorAwareProxyGenerator()`, `new InterceptorAwareProxyGenerator()` | decorator/interceptor resolver + chain runtime still anchored in core |
| `BeanImpl` | `InterceptorChain`, `InvocationContextImpl`, `InterceptorAwareProxyGenerator`, `DecoratorResolver`, `DecoratorAwareProxyGenerator`, `DecoratorChain` | bulk of interceptor/decorator runtime still anchored in core |
| `ClientProxyGenerator` | core facade delegates to `syringe-proxy` ByteBuddy implementation | ByteBuddy ownership already moved out of core; remaining work is to move scope semantics |

**Status update (2026-06-06):**
- `syringe-core` no longer declares a direct `net.bytebuddy:byte-buddy` dependency.
- ByteBuddy-backed implementations now live under `syringe-proxy`:
  - `ByteBuddyClientProxyGenerator`
  - `ByteBuddyInterceptorAwareProxyGenerator`
  - `ByteBuddyDecoratorAwareProxyGenerator`
- Core keeps stable facade types (`ClientProxyGenerator`, `InterceptorAwareProxyGenerator`,
  `DecoratorAwareProxyGenerator`) to avoid breaking internal contracts while preserving modular ownership.

The class **can** be in a feature module even when it is used by a core class — as long as the
core class **never directly mentions its concrete type**. The fix for every blocking `new` call
is to route through the SPI interface.

### The factory-method rule

**No core class may call `new ConcreteFeatureClass()`.** Every such instantiation must be
replaced with a call through the feature's SPI interface. The SPI interface method may return a
core-defined interface, an opaque `Object`, or a standard JDK type — never the concrete type.

```java
// BEFORE (BeanImpl, stays in core):
InvocationContextImpl invocationContext = new InvocationContextImpl(instance, lifecycleChain, targetInvocation);
invocationContext.proceed();

// AFTER — InvocationContextImpl lives in syringe-interceptors; core only sees support SPI:
interceptorSupport.invokeLifecycle(instance, lifecycleChain, targetInvocation);
```

```java
// BEFORE (BeanResolver, stays in core):
return new EventImpl<>(payloadType, qualifiers, beanManager, ...);

// AFTER — EventImpl lives in syringe-events; core only sees Event<T>:
return observerSupport.createEvent(payloadType, qualifiers);
```

```java
// BEFORE (BeanResolver):
private EventImpl.ContextTokenProvider contextTokenProvider = new RegistryContextTokenProvider();

// AFTER — RegistryContextTokenProvider lives in syringe-events:
private ContextTokenProvider contextTokenProvider = observerSupport.getContextTokenProvider();
// ContextTokenProvider is extracted from EventImpl into a standalone core interface
```

### Moving SPI event implementation classes (Feature 1 / Extensions)

`ProcessAnnotatedTypeImpl`, `AfterBeanDiscoveryImpl`, and siblings are currently instantiated in
`Syringe.java` and then passed through `ExtensionsManager.fireEventToExtensions(...)`. Since
`ExtensionsManagerImpl` is already in `syringe-extensions`, construction must move there by
routing event dispatch through raw-data methods on the interface (or an equivalent callback API)
instead of passing pre-built implementation objects from core.

```java
// BEFORE — Syringe.java creates the event object:
extensionsManager.fireEventToExtensions(new ProcessAnnotatedTypeImpl<>(messageHandler, annotatedType));

// AFTER — ExtensionsManagerImpl creates it internally:
extensionsManager.fireProcessAnnotatedType(annotatedType, additionalAnnotatedTypes);
```

In other words: core provides data, extensions module builds event implementation objects.

### Interface extraction for data classes stored in core structures

Some feature classes are stored inside `KnowledgeBase` or `BeanImpl`, which stay in core.
Where the class is substantive enough to be worth moving, extract a minimal interface in core
and have the class implement it from the feature module:

- `ObserverMethodInfo` is stored in `KnowledgeBase` (`Collection<ObserverMethodInfo>`). Extract
  `ObserverMethodMetadata` marker interface in core; `ObserverMethodInfo` implements it in
  `syringe-events`; change `KnowledgeBase` to `Collection<ObserverMethodMetadata>`.
- `ContextTokenProvider` is an inner interface of `EventImpl`. Extract it as a standalone
  interface in core so `BeanResolver` can use it without seeing `EventImpl`.
- `InterceptorInfo` and `DecoratorInfo` are plain data classes stored in `KnowledgeBase` and
  `BeanImpl`. They may stay in core if moving them adds only indirection without code reduction.

### BeanResolver decomposition (still required)

`BeanResolver` has the highest feature coupling in core. It must be modified (or split) so it
no longer directly references concrete feature classes. Required changes:

1. **Event wrappers**: add `setObserverSupport(ObserverSupport)` to `BeanResolver`; replace
   `new EventImpl<>(...)` with `observerSupport.createEvent(payloadType, qualifiers)`;
   replace `new RegistryContextTokenProvider()` with `observerSupport.getContextTokenProvider()`.

2. **Legacy @New**: add `setLegacyNewSupport(LegacyNewSupport)` to `BeanResolver` and
   `BeanManagerImpl`; replace direct `LegacyNewBeanAdapter` / `LegacyNewQualifierHelper` calls
   with `legacyNewSupport.resolveNewInjectionPoint(ip)`.

3. **Decorator resolution**: add `setDecoratorSupport(DecoratorSupport)` to `BeanResolver`;
   route `DecoratorResolver` calls through `decoratorSupport.resolve(types, qualifiers)`.

All three supports are wired into `BeanResolver` during `Syringe.reset()`.

### What is allowed

- **Classes may be modified**, not only moved. Changing method signatures on interfaces and core
  classes to break a direct dependency is the primary tool.
- **Core classes may be split** if they accumulate too much coupling. `BeanResolver` is the
  primary candidate.
- **New interfaces may be introduced** in core as pass-through types so concrete implementations
  can live in feature modules.
- **Data classes may stay in core** when moving them would add interface indirection with no
  meaningful code reduction (e.g. `InterceptorInfo` is 60 lines of POJOs; moving it saves
  nothing).

---

## Feature areas — ordered by implementation priority (easiest first)

Read the relevant source files before touching any of them.

---

### FEATURE 1 — Portable Extensions (`ExtensionsManager`) [Done]

**Interface (already exists)**:
`extensions/ExtensionsManager.java`

**Implementation (already exists)**:
`extensions/ExtensionsManagerImpl.java`

**Phase 1 steps** (done):
1. ~~Create `extensions/NoOpExtensionsManager.java`.~~
   - `addExtension(String)` / `addExtension(Extension)` → throw `NotEnabledFeatureException`:
     use the canonical template with:
     - usage: `API call`
     - location: `Syringe.addExtension(String)` or `Syringe.addExtension(Extension)`
     - module: `syringe-extensions`
     - feature-label: `extension`
   - `getExtensions()` → `Collections.emptyList()`.
   - `getExtensionClassNames()` → `Collections.emptyList()`.
   - All lifecycle methods → silent no-ops (no extensions registered, nothing to fire).
2. ~~Create service file:~~
   `src/main/resources/META-INF/services/com.threeamigos.common.util.implementations.injection.extensions.ExtensionsManager`
   containing:
   `com.threeamigos.common.util.implementations.injection.extensions.ExtensionsManagerImpl`
3. ~~Add `private static ExtensionsManager discoverExtensionsManager()` to `Syringe.java`.~~
4. ~~In `Syringe.reset()`, replace `extensionsManager = new ExtensionsManagerImpl()` with
   `extensionsManager = discoverExtensionsManager()`.~~
5. ~~Remove the `ExtensionsManagerImpl` import from `Syringe.java`.~~

**Status update (2026-06-06):**
6. ~~`ExtensionsManager` now exposes operation-oriented methods for extension lifecycle processing
   (PAT/PIP/PIT/PBA/ProcessBean/ProcessProducer variants + ABD/ADV/BeforeShutdown).
   `Syringe` passes raw data and no longer constructs SPI event implementation classes directly.~~
7. ~~SPI event implementation classes were moved from `syringe-core` to `syringe-extensions`
   (`spi/spievents/*`, except `SimpleAnnotatedType` which remains in core).~~
8. ~~`Syringe.java` no longer imports `spi.spievents.*`; extension event construction is owned by
   `ExtensionsManagerImpl`.~~
9. ~~Build wiring updated:~~
   - `syringe-events` now depends on `syringe-extensions` (for ProcessObserverMethod events).
   - Reactor module order updated so `syringe-extensions` builds before `syringe-events`.
10. ~~Validation:~~
   - `mvn -q -DskipTests clean validate` passed.
   - Focused extension tests passed:
     `ProcessSyntheticBeanImplUnitTest`, `ContainerLifecycleEventsTest`.

---

### FEATURE 2 — Build-Compatible Extensions (`BuildCompatibleExtensionsManager`) [Done]

**Interface (already exists)**:
`bce/BuildCompatibleExtensionsManager.java`

**Implementation (already exists)**:
`bce/BuildCompatibleExtensionsManagerImpl.java`

**Steps**:
1. ~~Create `bce/NoOpBuildCompatibleExtensionsManager.java`.~~
   - `addBuildCompatibleExtension(String)` → throw `NotEnabledFeatureException`:
     use the canonical template with:
     - usage: `API call`
     - location: `Syringe.addBuildCompatibleExtension(String)`
     - module: `syringe-bce`
     - feature-label: `build-compatible extension`
   - All lifecycle methods → silent no-ops.
2. ~~Create service file:~~
   `src/main/resources/META-INF/services/com.threeamigos.common.util.implementations.injection.bce.BuildCompatibleExtensionsManager`
   containing:
   `com.threeamigos.common.util.implementations.injection.bce.BuildCompatibleExtensionsManagerImpl`
3. ~~Add `private static BuildCompatibleExtensionsManager discoverBuildCompatibleExtensionsManager()`
   to `Syringe.java`.~~
4. ~~In `Syringe.reset()`, replace the hard-wired instantiation with the discovery helper.~~
5. ~~Remove the `BuildCompatibleExtensionsManagerImpl` import from `Syringe.java`.~~

**Status update (2026-06-06):**
6. ~~No-op fallback throws canonical API-call `NotEnabledFeatureException` for
   `Syringe.addBuildCompatibleExtension(String)`.~~
7. ~~Service discovery is wired through `discoverBuildCompatibleExtensionsManager()` in
   `Syringe.reset()`, with `NoOpBuildCompatibleExtensionsManager` fallback when module is absent.~~
8. ~~Build-compatible implementation classes are in `syringe-bce` and loaded through
   `META-INF/services/...BuildCompatibleExtensionsManager`.~~

---

### FEATURE 3 — Interceptors (`InterceptorSupport`) [Done]

**Interface file (exists)**:
`interceptors/InterceptorSupport.java`

**Current interface shape (operation-based)**:
```java
public interface InterceptorSupport {
    void setKnowledgeBase(KnowledgeBase knowledgeBase);
    void setMessageHandler(MessageHandler messageHandler);
    List<InterceptorInfo> resolve(...);
    List<InterceptorInfo> resolveForConstructor(...);
    Set<Annotation> resolveBindings(...);
    Set<Annotation> resolveBindingsForConstructor(...);
    InterceptorChainModel.Builder newChainBuilder();
    Object applyInterceptorProxy(...);
    Object invokeLifecycle(...);
    <T> InterceptionFactory<T> createInterceptionFactory(...);
    void clearTargetAroundInvokeCache();
    void clearTargetAroundInvokeCacheForClassLoader(ClassLoader classLoader);
    void validateBeansXmlInterceptorConfiguration();
    void clear();
}
```

**No-op `validateBeansXmlInterceptorConfiguration()`** must inspect the `KnowledgeBase` for
any interceptor entries declared in `beans.xml`. If any are found, throw
`NotEnabledFeatureException` with the beans.xml interceptor class name and the missing jar.

No-op support returns empty resolution results, no-op chain builder/proxy behavior, and throws
canonical `NotEnabledFeatureException` for explicit `BeanManager.createInterceptionFactory(...)`
calls and discovered interceptor usage.

**Phase 1 steps** (done):
1. ~~Service discovery + no-op fallback implemented in `Syringe.reset()`.~~
2. ~~`InterceptorSupportImpl` moved to `syringe-interceptors`.~~
3. ~~Service file moved to `syringe-interceptors`.~~

**Status update (2026-06-06):**
- ~~`InterceptorSupport` now provides operation-based APIs plus `newChainBuilder()`; core does not
  use `getResolver()` / `getProxyGenerator()` anymore.~~
- ~~`BeanImpl` was decoupled to use core contract type `InterceptorChainModel` and builds chains
  only through `InterceptorSupport.newChainBuilder()`.~~
- ~~`BeanManagerImpl.createInterceptionFactory(...)` delegates through `InterceptorSupport`.~~
- ~~`InjectionTargetFactoryImpl` no longer references interceptor proxy state types directly; it
  unwraps interceptor proxies reflectively via `$$_getTargetInstance` when present.~~
- ~~`KnowledgeBase` no longer depends on `InterceptorsHelper`; interceptor ordering/filtering logic
  is handled directly inside `KnowledgeBase`.~~
- ~~Moved from `syringe-core` to `syringe-interceptors`:
  `InterceptorResolver`, `InterceptorChain`, `InvocationContextImpl`,
  `InterceptorAwareProxyGenerator`, `InterceptionFactoryImpl`, `InterceptorsHelper`.~~
- ~~`syringe-core` keeps only interceptor SPI contracts/no-op:
  `InterceptorSupport`, `NoOpInterceptorSupport`, `InterceptorChainModel`.~~
- ~~Validation executed:~~
  - `mvn -q -DskipTests validate` (pass)
  - `mvn -q -pl syringe-full -am -Dtest=NoOpInterceptorSupportTest,InterceptionFactoryInterfaceTest,InterceptorResolutionRegressionTest,ContainerInvocationsAndInterceptorsTest,InvocationContextImplTest -Dsurefire.failIfNoSpecifiedTests=false test` (pass)

**What remains for Feature 3:**
- No open class-move items.
- `InterceptorInfo` and `InterceptorDecoratorDefinitionValidator` intentionally remain in
  `syringe-core` (shared metadata/validation used by core discovery/validation flow).

---

### FEATURE 4 — Decorators (`DecoratorSupport`) [Done]

**Interface file (exists)**:
`decorators/DecoratorSupport.java`

**Current interface shape (operation-based)**:
```java
public interface DecoratorSupport {
    void setKnowledgeBase(KnowledgeBase knowledgeBase);
    void setMessageHandler(MessageHandler messageHandler);
    List<DecoratorInfo> resolve(Set<Type> beanTypes, Set<Annotation> qualifiers);
    boolean hasDecorators(Set<Type> beanTypes, Set<Annotation> qualifiers);
    Object applyDecoratorChain(Object target, List<DecoratorInfo> decorators,
                               BeanManager beanManager, CreationalContext<?> creationalContext);
    void destroyDecoratorChain(Object outermostInstance);
    void validateBeansXmlDecoratorConfiguration();
    void validateProgrammaticDecoratorConfiguration();
    void clear();
}
```

**No-op `validateBeansXmlDecoratorConfiguration()`** must inspect the `KnowledgeBase` for any
decorator entries in `beans.xml`. If any are found, throw `NotEnabledFeatureException` with the
beans.xml decorator class name and the missing jar.

**No-op `validateProgrammaticDecoratorConfiguration()`** must inspect the `KnowledgeBase` for
any programmatically registered decorators. If any are found, throw
`NotEnabledFeatureException`.

No-op returns empty/identity behavior for operation methods and throws in validation when usage is
detected.

**Status update (2026-06-06):**
1. ~~`DecoratorSupport` was refactored to operation-based SPI methods; core no longer exposes
   `DecoratorResolver`/`DecoratorAwareProxyGenerator` through the interface.~~
2. ~~`BeanImpl`, `BeanResolver`, `BeanManagerImpl`, `ConversationBean`, and `Syringe` were
   decoupled from concrete decorator classes and now route through `DecoratorSupport`.~~
3. ~~`DecoratorSupport` is wired from `Syringe.reset()` into `BeanManagerImpl` and then into
   `BeanResolver` and `BeanImpl` runtime paths.~~
4. ~~Moved from `syringe-core` to `syringe-decorators`:
   `DecoratorChain`, `DecoratorChainBuilder`, `DecoratorInstance`,
   `DecoratorAwareProxyGenerator`, `DecoratorResolver`.~~
5. ~~`ByteBuddyDecoratorAwareProxyGenerator` in `syringe-proxy` was adjusted to avoid direct
   dependency on moved core decorator chain types and now manages runtime chain cleanup internally.~~
6. ~~Validation executed and passed:~~
   - `mvn -q -DskipTests validate`
   - `mvn -q -pl syringe-full -am -Dtest=DecoratorBeansTest,DecoratorEnablementAndOrderingTest,DecoratorResolutionTest,DecoratorInvocationTest,AdditionalDecoratorRulesTest,DecoratorTckParityTest,DecoratorDefinitionTckParityTest,BuiltinEventDecoratorTckParityTest,BuiltinConversationDecoratorTckParityTest,DependentPseudoScopeInCDIFullTest,DependencyInjectionInCDIFullTest -Dsurefire.failIfNoSpecifiedTests=false test`
   - `mvn -q -pl syringe-full -am -Dtest=NoOpInterceptorSupportTest,InterceptionFactoryInterfaceTest,InterceptorResolutionRegressionTest,ContainerInvocationsAndInterceptorsTest,InvocationContextImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

**What remains for Feature 4:**
- No open class-move or decoupling items.
- Optional hardening: keep/expand dedicated no-op decorator message tests in `syringe-full`.

---

### FEATURE 5 — Legacy `@New` annotation support (`LegacyNewSupport`) [Done]

**Interface file (exists)**:
`annotations/legacy/LegacyNewSupport.java`

**Current interface shape (operation-based + gating)**:
```java
public interface LegacyNewSupport {
    boolean isEnabled();
    void enable();
    LegacyNewSelection resolveSelection(Type requiredType, Annotation[] qualifiers);
    <T> Bean<T> adaptLegacyNewBean(Bean<T> bean);
    void validateNewInjectionPoints();
}
```

**No-op `isEnabled()`** returns `false`.

**No-op behavior:**
- `enable()` throws canonical API-call `NotEnabledFeatureException` for
  `Syringe.enableLegacyCdi10New(boolean)`.
- `resolveSelection(...)` detects `@New` qualifier usage and throws canonical
  missing-module `NotEnabledFeatureException`.

**Status update (2026-06-06):**
1. ~~`LegacyNewSupport` was extended with selection/adaptation operations used by core resolution:
   `resolveSelection(...)`, `adaptLegacyNewBean(...)`, and `validateNewInjectionPoints()`.~~
2. ~~`BeanResolver`, `BeanManagerImpl`, and `CDI41InjectionValidator` were decoupled from direct
   `LegacyNewQualifierHelper` / `LegacyNewBeanAdapter` references and now route through
   `LegacyNewSupport`.~~
3. ~~`Syringe.reset()` now wires `LegacyNewSupport` into `BeanManagerImpl`, which wires it into
   `BeanResolver`; the package-private test setter mirrors this wiring.~~
4. ~~Moved from `syringe-core` to `syringe-legacy-new`:
   `LegacyNewQualifierHelper` and `LegacyNewBeanAdapter`.~~
5. ~~`LegacyNewSupportImpl` now keeps explicit feature state (`isEnabled()==false` by default,
   enabled via `enable()`), preserving the explicit opt-in semantics.~~
6. ~~Added dedicated no-op tests in `syringe-full`:
   `NoOpLegacyNewSupportTest` (API-call and `@New`-usage missing-module guidance).~~
7. ~~Validation executed and passed:~~
   - `mvn -q -DskipTests validate`
   - `mvn -q -pl syringe-full -am -Dtest=NoOpLegacyNewSupportTest,LegacyAnnotationsTest,ProgrammaticLookupInCDIFullTest,TypesafeResolutionInCDIFullTest,DependencyInjectionInCDIFullTest,BeanManagerTest -Dsurefire.failIfNoSpecifiedTests=false test`

**What remains for Feature 5:**
- No open class-move or core-decoupling items.

---

### FEATURE 6 — SE container support (`SeSupport`) [Done]

**Interface file (exists)**:
`se/SeSupport.java`

**Current Phase 1 interface in codebase**:
```java
public interface SeSupport {
    void registerCdiProvider(Syringe syringe);
    void unregisterCdiProvider();
}
```

**No-op**: both methods are silent no-ops. SE support is an infrastructure feature, not a
bean-model feature, so there are no in-container annotations to detect: absence of this module
simply means `CDI.current()` will not be registered. No `NotEnabledFeatureException` is thrown.

**Phase 1 steps** (done):
1. ~~Service discovery + no-op fallback implemented in `Syringe.reset()`.~~
2. ~~`SeSupportImpl` moved to `syringe-se`.~~
3. ~~Service file moved to `syringe-se`.~~

**Phase 2 steps** (done):

4. ~~Check whether `SyringeCDIProvider` is referenced by name anywhere in `syringe-core`.
   It is registered via `META-INF/services/jakarta.enterprise.inject.spi.CDIProvider` and
   instantiated only by the JDK `ServiceLoader` — no core class should hold a direct import.
   If confirmed: move `SyringeCDIProvider` and its service file to `syringe-se`.
   Confirm the module compiles and all tests in `syringe-full` are green.~~

**Status update (2026-06-06):**
5. ~~`SyringeCDIProvider` moved from `syringe-core` to `syringe-se`
   (`spi/SyringeCDIProvider.java`).~~
6. ~~`META-INF/services/jakarta.enterprise.inject.spi.CDIProvider` moved from
   `syringe-core` to `syringe-se`.~~
7. ~~No remaining `SyringeCDIProvider` references in `syringe-core` sources/resources.~~
8. ~~Validation executed and passed:~~
   - `mvn -q -DskipTests validate`
   - `mvn -q -pl syringe-full -am -Dtest=CdiCurrentDynamicLookupTest,BeanContainerTest,BuildCompatibleExtensionInterfaceTest -Dsurefire.failIfNoSpecifiedTests=false test`

---

### FEATURE 7 — Events & Observers (`ObserverSupport`) [done]

**Interface file (exists)**:
`events/ObserverSupport.java`

**Current Phase 1 interface in codebase**:
```java
public interface ObserverSupport {
    void setMessageHandler(MessageHandler messageHandler);
    void setKnowledgeBase(KnowledgeBase knowledgeBase);
    void setBeanManager(BeanManager beanManager);
    void setContextManager(ContextManager contextManager);
    void discoverObserverMethods(Class<?> beanClass, Bean<?> declaringBean);
    void registerRuntimeExtensionObserverMethods(Collection<Extension> extensions);
    void processObserverMethodSpiEvents(ExtensionsManager extensionsManager);
    void fireEvent(Object event, Annotation... qualifiers);
    void fireEventAsync(Object event, Annotation... qualifiers);
    void fireStartupEvent();
    void fireShutdownEvent();
    void fireContextInitializedEvent(Class<? extends Annotation> scopeType);
    void fireContextBeforeDestroyedEvent(Class<? extends Annotation> scopeType);
    void fireContextDestroyedEvent(Class<? extends Annotation> scopeType);
    Event<Object> getRootEvent();
    <T> Event<T> createEvent(Type payloadType, Set<Annotation> qualifiers);
    ContextTokenProvider getContextTokenProvider();
    void clear();
}
```

**No-op `discoverObserverMethods()`** must reflectively inspect the class for any method whose
parameters carry `@jakarta.enterprise.event.Observes` or `@jakarta.enterprise.event.ObservesAsync`.
If any are found, throw `NotEnabledFeatureException`:
```
@Observes found on method com.example.MyHandler.onEvent(Object) but event support is not
available. Add syringe-events to your classpath to enable observer support.
```

**No-op `processObserverMethodSpiEvents()`**, **`fireEvent()`**, **`fireEventAsync()`**,
**`fireStartupEvent()`**, **`fireShutdownEvent()`**, and all `fireContext*Event()` methods:
silent no-ops (no observers registered, events are dropped).

**No-op `registerRuntimeExtensionObserverMethods(...)`**: silent no-op.

**No-op `getRootEvent()`**: returns a dummy `Event<Object>` whose `fire()` and `fireAsync()`
are no-ops and whose `select()` returns itself.

**Coupling note — FEATURE 8 depends on this**: ContextManager currently
wires scope lifecycle events through listener callbacks that call EventImpl directly. When
ScopeSupport is extracted, those callbacks will be replaced by calls to
`observerSupport.fireContextInitializedEvent()` etc. ObserverSupport must therefore exist
before ScopeSupport can be implemented.

**Phase 1 status (actual)**:
1. ~~Service discovery + no-op fallback implemented in `Syringe.reset()`.~~
2. ~~`ObserverSupportImpl` and service file moved to `syringe-events`.~~
3. ~~Phase 2 preparation identified `EventImpl`, `ObserverMethodInfo`, `ObserverMethodInfoKey`,
   and `events.propagation.*` as remaining core-held event runtime classes.~~

**Phase 2 steps** (move the bulk of the event model to `syringe-events`):

4. ~~Extend `ObserverSupport` with context-token indirection:
    ```java
    ContextTokenProvider getContextTokenProvider();
    ```
   Keep `createEvent(Type, Set<Annotation>)` as the wrapper factory for injected events.
   `ContextTokenProvider` is extracted from `EventImpl` as a standalone core interface.~~

5. ~~Add `setObserverSupport(ObserverSupport)` to `BeanResolver`; wire it from `Syringe.reset()`.
    In `BeanResolver`:
    - Replace `new EventImpl<>(payloadType, ...)` with
      `observerSupport.createEvent(payloadType, qualifiers)`.
    - Remove direct `RegistryContextTokenProvider` / `EventImpl` construction from core
      event-wrapper paths and route through `ObserverSupport` contracts.~~

6. ~~Extract `ObserverMethodMetadata` marker interface in core. Change
    `KnowledgeBase.getObserverMethodInfos()` to return `Collection<ObserverMethodMetadata>`.
    `ObserverMethodInfo` implements `ObserverMethodMetadata` from `syringe-events`.~~

7. ~~Move to `syringe-events`:~~
   - ~~`EventImpl`~~
   - ~~`ObserverMethodInfo`~~
   - ~~`ObserverMethodInfoKey`~~
   - `ObserverInvocationLifecycle` (n/a here; currently in `syringe-extensions` and shared by extension SPI events)
   - ~~`ConversationPropagationRegistry` and the entire `events.propagation.*` package
     (`ConversationCarrier`, `ConversationPropagationFilter`, `ConversationPropagationManager`,
     `HttpConversationCarrier`, `RegistryContextTokenProvider`)~~

8. ~~`SPI event impls` that reference `ObserverMethodInfo` (`ProcessObserverMethodImpl`,
    `ProcessSyntheticObserverMethodImpl`) move to `syringe-extensions` (see Feature 1 Phase 2).
    Confirm the module compiles and all tests in `syringe-full` are green.~~

**Status update (2026-06-06):**
9. ~~`ContextTokenProvider` + `ContextSnapshot` extracted to core as standalone contracts
   (`events/ContextTokenProvider.java`, `events/ContextSnapshot.java`).~~
10. ~~`ObserverSupport` now exposes `getContextTokenProvider()`, with implementations in
    `NoOpObserverSupport` and `ObserverSupportImpl`.~~
11. ~~`BeanResolver` no longer directly constructs `EventImpl`; event wrapper creation now routes
    through `observerSupport.createEvent(...)` and is wired via
    `BeanManagerImpl#setObserverSupport(...)` from `Syringe.reset()`.~~
12. ~~Moved from `syringe-core` to `syringe-events`:
    `EventImpl`, `ConversationPropagationRegistry`, and `events.propagation.*`
    (`ConversationCarrier`, `ConversationPropagationFilter`, `ConversationPropagationManager`,
    `HttpConversationCarrier`, `RegistryContextTokenProvider`).~~
13. ~~Core decoupling completed for observer metadata:~~
    - ~~`ObserverMethodMetadata` was introduced in `syringe-core` and
      `KnowledgeBase` now stores `Collection<ObserverMethodMetadata>`.~~
    - ~~`CDI41InjectionValidator`, `BeanManagerImpl`, and `syringe-bce` were updated to use
      `ObserverMethodMetadata` instead of `ObserverMethodInfo`.~~
    - ~~`ObserverMethodInfo` + `ObserverMethodInfoKey` moved from `syringe-core` to `syringe-events`.~~
    - ~~Extension runtime observer registration moved from `ExtensionsManager` to
      `ObserverSupport.registerRuntimeExtensionObserverMethods(...)`, removing the remaining
      concrete observer-metadata dependency from `syringe-extensions`.~~
14. ~~Validation executed and passed:~~
    - `mvn -q -DskipTests validate`
    - `mvn -q -pl syringe-core,syringe-events,syringe-extensions,syringe-bce,syringe-full -am -DskipTests compile`
    - `mvn -q -pl syringe-full -am -Dtest=ContainerLifecycleEventsTest,BeanInterfaceTest,PackagingAndDeploymentInCDIFullTest,BeanDiscoveryTest,EventsInCDIFullTest -Dsurefire.failIfNoSpecifiedTests=false test`
    - `mvn -q -pl syringe-full -am -Dtest=SyringeStressLoopMemoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

---

### FEATURE 8 — Normal Scopes (`ScopeSupport`)

**Interface to create**:
`scopes/ScopeSupport.java`

Study `ClientProxyGenerator`, `ContextManager` (focusing on the
`RequestContextLifecycleListener` and `ApplicationContextLifecycleListener` inner interfaces
and how listeners are wired), and all scope context classes (`ApplicationScopedContext`,
`RequestScopedContext`, `SessionScopedContext`, `ConversationScopedContext`, `ConversationImpl`)
before writing the interface. Also study `ActivateRequestContextInterceptor` (a built-in bean
that activates request context) and the public session/request management methods on `Syringe`.

**Implement AFTER FEATURE 7** (`ObserverSupport`): scope lifecycle event firing
(`@Initialized`, `@BeforeDestroyed`, `@Destroyed`) is wired through `ObserverSupport`.

**Key design constraints**:
- `ContextManager` stays in `syringe-core` because it manages the `@Dependent` pseudo-scope,
  which is core. Only the `ApplicationScopedContext`, `RequestScopedContext`, etc.
  implementations move to `syringe-scopes`.
- Proxy generation is optional feature code. `syringe-core` must not keep a direct ByteBuddy
  dependency after proxy generators are moved out. ByteBuddy is owned by shared module
  `syringe-proxy`, which is consumed by `syringe-scopes`/`syringe-interceptors`/
  `syringe-decorators` as needed.
- The `ContextManager.RequestContextLifecycleListener` and
  `ContextManager.ApplicationContextLifecycleListener` inner interfaces remain in core (they
  are part of `ContextManager`'s public API). Implementations move to `ScopeSupportImpl`.
- The public scope-management methods on `Syringe` (`activateRequestContextIfNeeded()`,
  `deactivateRequestContextIfActive()`, `activateSyntheticSessionContextIfNeeded()`,
  `deactivateSessionContextIfActive()`, `invalidateSessionContext(String)`) must remain on
  `Syringe`. They delegate to `ScopeSupport`.

```java
public interface ScopeSupport {
    void setMessageHandler(MessageHandler messageHandler);
    void setKnowledgeBase(KnowledgeBase knowledgeBase);
    void setBeanManager(BeanManager beanManager);
    void setObserverSupport(ObserverSupport observerSupport);

    /** Registers ApplicationScoped, RequestScoped, SessionScoped, ConversationScoped contexts
     *  with the ContextManager and wires scope lifecycle event firing via ObserverSupport.
     *  Called during reset(). No-op does nothing (only @Dependent context remains). */
    void registerNormalScopes(ContextManager contextManager);

    /** Creates a client proxy for the given normal-scoped bean.
     *  No-op returns null; core then falls back to direct instantiation (@Dependent only). */
    <T> T createClientProxy(Bean<T> bean);

    /** Called during validateDeployment() to verify no normal-scoped beans are present
     *  when this module is absent.
     *  Throws NotEnabledFeatureException if any @ApplicationScoped, @RequestScoped,
     *  @SessionScoped, @ConversationScoped, or custom @NormalScope-annotated bean is found. */
    void validateNormalScopeUsage();

    /** Activates the request context for the current thread if not already active.
     *  Returns true if the context was newly activated; false if it was already active. */
    boolean activateRequestContextIfNeeded();

    /** Deactivates the request context for the current thread if active. */
    void deactivateRequestContextIfActive();

    /** Activates a synthetic session context if session management is needed.
     *  Returns the new session ID, or null if already active. */
    String activateSyntheticSessionContextIfNeeded();

    /** Deactivates the session context for the current thread if active. */
    void deactivateSessionContextIfActive();

    /** Invalidates and destroys the session context for the given session ID. */
    void invalidateSessionContext(String sessionId);

    /** ContextManager raw scope activation/deactivation delegation methods. */
    void activateRequestContext();
    void deactivateRequestContext();
    void activateSessionContext(String sessionId);
    void deactivateSessionContext();
    String getCurrentSessionId();
    void beginConversation(String conversationId);
    void endConversation();
    void endConversation(String conversationId);
    String getCurrentConversationId();

    /** Built-in Conversation bean creation and global/static cleanup hooks. */
    Conversation createConversation();
    void registerContainer(ClassLoader classLoader, BeanManager beanManager, ContextManager contextManager);
    void unregisterContainer(ClassLoader classLoader, BeanManager beanManager, ContextManager contextManager);
    void clearGlobalState();

    void clear();
}
```

**No-op `registerNormalScopes()`**: does nothing.

**No-op `createClientProxy()`**: returns `null`. The caller (ContextManager or BeanImpl) must
check for `null` and fall back to direct instantiation. This fall-back already works for
`@Dependent` beans and must be preserved in core logic.

**No-op `validateNormalScopeUsage()`** must inspect the `KnowledgeBase` for any managed bean
whose scope annotation is `@ApplicationScoped`, `@RequestScoped`, `@SessionScoped`,
`@ConversationScoped`, or any annotation meta-annotated with
`@jakarta.enterprise.context.NormalScope`. If any are found, throw
`NotEnabledFeatureException`:
```
@ApplicationScoped found on class com.example.MyService but scope support is not available.
Add syringe-scopes to your classpath to enable normal scope support.
```

**No-op `activateRequestContextIfNeeded()`**: returns `false`. All other delegation methods:
silent no-ops.

**Steps**:
1. ~~Create the `ScopeSupport` interface.~~
2. ~~Create `scopes/ScopeSupportImpl.java`:~~
   - Wraps `ClientProxyGenerator`, `ApplicationScopedContext`, `RequestScopedContext`,
     `SessionScopedContext`, `ConversationScopedContext`.
   - `registerNormalScopes()` registers each context with the `ContextManager` and wires the
     `RequestContextLifecycleListener` and `ApplicationContextLifecycleListener` callbacks to
     call `observerSupport.fireContextInitializedEvent()`, `fireContextBeforeDestroyedEvent()`,
     `fireContextDestroyedEvent()`.
   - `createClientProxy()` delegates to `ClientProxyGenerator.createProxy(bean)`.
   - `validateNormalScopeUsage()` is a no-op in the Impl (scopes are registered and valid).
   - Delegation methods delegate to the relevant context objects on `ContextManager`.
3. ~~Create `scopes/NoOpScopeSupport.java` with usage detection in `validateNormalScopeUsage()`
   as described above; all other methods are silent no-ops.~~
4. ~~Create service file:
   `src/main/resources/META-INF/services/com.threeamigos.common.util.implementations.injection.scopes.ScopeSupport`
   containing `ScopeSupportImpl`.~~
5. ~~Add `private static ScopeSupport discoverScopeSupport()` to `Syringe.java`.~~
6. ~~Wire `ScopeSupport` into `Syringe.java`:~~
   - In `reset()`: after creating `contextManager`, call
     `scopeSupport.setObserverSupport(observerSupport)` then
     `scopeSupport.registerNormalScopes(contextManager)`. Remove the direct registrations of
     `ApplicationScopedContext`, `RequestScopedContext`, etc. and the direct wiring of lifecycle
     listeners that currently call `EventImpl`.
   - In `validateDeployment()`: call `scopeSupport.validateNormalScopeUsage()` before other
     checks.
   - Replace `contextManager.createClientProxy(bean)` (and equivalent calls in `BeanImpl` /
     `ContextManager`) with `scopeSupport.createClientProxy(bean)`, with a `null`-check
     fall-back to direct instantiation.
   - Delegate the public `activateRequestContextIfNeeded()`, `deactivateRequestContextIfActive()`,
     `activateSyntheticSessionContextIfNeeded()`, `deactivateSessionContextIfActive()`, and
     `invalidateSessionContext(String)` methods on `Syringe` to `scopeSupport.*`.
7. ~~Remove all direct `scopes.ClientProxyGenerator`, `scopes.ConversationImpl`, scope context
   class imports from `Syringe.java`. Retain `scopes.ContextManager` (stays in core).~~
8. ~~Move `ScopeSupportImpl`, `ClientProxyGenerator`, `ApplicationScopedContext`,
   `RequestScopedContext`, `SessionScopedContext`, `ConversationScopedContext`,
   `ConversationImpl`, and the listener implementations to the `syringe-scopes` module.~~
9. ~~Move the `META-INF/services/...ScopeSupport` file to `syringe-scopes`.
   Confirm the module compiles and all tests in `syringe-full` are green.~~

**Status update (2026-06-06):**
10. ~~`ContextManager` was refactored to keep only core context registry behavior (`@Dependent`
    built-in) and delegate scope activation/conversation/session operations to `ScopeSupport`.~~
11. ~~`BeanManagerImpl` now accepts/wires `ScopeSupport` and keeps proxy creation resilient by
    falling back when no proxy is returned.~~
12. ~~`BeanResolver#getInstanceFromScope()` now null-checks proxy creation and falls back to
    contextual instance retrieval.~~
13. ~~`ConversationBean` no longer directly instantiates `ConversationImpl`; it now delegates
    to `ScopeSupport#createConversation()`.~~
14. ~~Cross-module proxy coupling was updated: `ByteBuddyClientProxyGenerator` now uses its own
    nested `ProxyState` interface, and interceptor module checks that state type directly.~~
15. ~~`syringe-events` now explicitly depends on `syringe-scopes` because event propagation and
    async observer context handling rely on conversation/request/session scope classes.~~
16. ~~Validation executed and passed:~~
    - `mvn -q -DskipTests validate`
    - `mvn -q -pl syringe-core,syringe-scopes,syringe-proxy,syringe-full -am -DskipTests compile`
    - `mvn -q -pl syringe-full -am -Dtest=NoOpScopeSupportTest,ContextManagerCleanupTest,ContextManagementForBuiltinScopesInCDIFullTest,ClientProxiesInCDIFullTest,BuiltinConversationDecoratorTckParityTest -Dsurefire.failIfNoSpecifiedTests=false test`

---

## Implementation order

Phase 1 (SPI wiring) and Phase 2 (actual class movement) are tracked separately.
Phase 1 must complete before Phase 2 begins for each feature.
Phase 2 may run across features concurrently once BeanResolver is prepared.

| Order | Feature | Phase 1 | Phase 2 |
|-------|---------|---------|---------|
| ~~-1~~ | ~~Maven multi-module split baseline~~ | ~~done~~ | — |
| ~~0~~ | ~~`NotEnabledFeatureException`~~ | ~~done~~ | — |
| ~~0.5~~ | ~~Shared proxy module (`syringe-proxy`)~~ | ~~done~~ | ~~ByteBuddy ownership moved out of core~~ |
| ~~1~~ | ~~Portable Extensions~~ | ~~done~~ | ~~done (SPI event impl classes moved to syringe-extensions)~~ |
| ~~2~~ | ~~Build-Compatible Extensions~~ | ~~done (25 classes moved)~~ | ~~—~~ |
| ~~3~~ | ~~Interceptors~~ | ~~done~~ | ~~done (`InterceptorResolver`, `InterceptorChain`, `InvocationContextImpl`, `InterceptorAwareProxyGenerator`, `InterceptionFactoryImpl`, `InterceptorsHelper` moved; core decoupled via `InterceptorChainModel` + `InterceptorSupport`)~~ |
| ~~4~~ | ~~Decorators~~ | ~~done~~ | ~~done (`DecoratorChain`, `DecoratorChainBuilder`, `DecoratorInstance`, `DecoratorAwareProxyGenerator`, `DecoratorResolver` moved; core decoupled via operation-based `DecoratorSupport`)~~ |
| ~~5~~ | ~~Legacy `@New` support~~ | ~~done~~ | ~~done (`LegacyNewQualifierHelper`, `LegacyNewBeanAdapter` moved; BeanResolver + BeanManagerImpl + CDI41InjectionValidator routed through `LegacyNewSupport`)~~ |
| ~~6~~ | ~~SE container support~~ | ~~done~~ | ~~done (`SyringeCDIProvider` + CDIProvider service file moved to `syringe-se`; core decoupled)~~ |
| ~~7~~ | ~~Events & Observers~~ | ~~done~~ | ~~done (`EventImpl`, `ObserverMethodInfo`, `ObserverMethodInfoKey`, and `events.propagation.*` moved; core decoupled via `ObserverMethodMetadata`; extension runtime observer registration moved to `ObserverSupport`)~~ |
| ~~8~~ | ~~Normal Scopes (`ScopeSupport`)~~ | ~~done~~ | ~~done~~ |

**Before starting any Phase 2 work**: modify `BeanResolver` to accept `ObserverSupport`,
`LegacyNewSupport`, and `DecoratorSupport` via setters (wired from `Syringe.reset()`), and define
equivalent wiring points for `BeanManagerImpl`/`BeanImpl` where core still directly instantiates
optional feature classes.

---

## What to do for each step (checklist)

Template reference only for future feature splits. This is not an open action list for the current
state, so items below intentionally remain unstruck.

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
   `NotEnabledFeatureException` with a message that mentions both:
   - the usage location (annotation + class/method or beans.xml entry), and
   - the missing jar name.
7. **Explicit API test**: Call the feature-enabling API directly (e.g., `addExtension(...)`,
   `enableLegacyCdi10New(true)`). Assert that `NotEnabledFeatureException` is thrown
   immediately, before `setup()` is called, and assert canonical message fields:
   - usage = `API call`
   - location = called API method signature
   - module = expected `syringe-<feature>`
   - feature-label = expected feature label.
8. Add a shared assertion helper in tests that validates the 2-line canonical format and checks
   all required tokens (`usage`, `location`, `module`, `feature-label`). Every feature no-op
   test must use this helper.

Until module boundaries are finalized, keep these tests under `syringe-full`.

## Required test matrix (standalone-first)

In addition to existing feature tests, enforce these suites:

1. ~~**Core-only smoke suite** (`syringe-core` only on classpath):~~
   - ~~scanning + bean discovery~~
   - ~~constructor/field/method injection~~
   - ~~`@Dependent` lifecycle~~
   - ~~`Instance` and `CDI.select` usage~~
2. ~~**Core-only negative suite**:~~
   - ~~detecting `@Interceptor`, `@Decorator`, `@Observes`, normal scopes, extension API calls
     without corresponding modules must throw canonical `NotEnabledFeatureException`.~~
3. ~~**Full compatibility suite** (`syringe-full`):~~
   - ~~existing CDI 4.1 compliance/integration tests remain green.~~

**Status update (2026-06-06):**
- ~~Local verification executed:~~
  - ~~`mvn -q -pl syringe-full -am -Dsyringe.core.only=true test`~~
  - ~~`mvn -q -pl syringe-full -am -DskipTests compile`~~
  - ~~`mvn -q -pl syringe-full -am -Dtest=NoOpInterceptorSupportTest,NoOpDecoratorSupportTest,NoOpScopeSupportTest,NoOpBuildCompatibleExtensionsManagerTest,NoOpLegacyNewSupportTest -Dsurefire.failIfNoSpecifiedTests=false test`~~

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

- Java 8 compatibility must be maintained throughout. Use `ServiceLoader.load(X.class).iterator()`
  with `.hasNext()` — do not use `findFirst()` (Java 9+), `stream()` (Java 9+), or `var`.
- New public API additions to `Syringe` must be limited to what is listed above (the public
  `enableLegacyCdi10New(boolean)` method already exists and must stay; it now throws instead
  of silently accepting when the module is absent).
- Package-private setters on `Syringe` for each discovered manager are permitted and required
  for testing the no-op path.
- Keep tests in `syringe-full` for now; move tests to feature modules later as a separate step.
- Dependency hygiene target for this plan: once proxy generators leave core, `syringe-core/pom.xml`
  must no longer declare `net.bytebuddy:byte-buddy`.
- `net.bytebuddy:byte-buddy` must be declared only in shared module `syringe-proxy` (and not
  duplicated in `syringe-core`; feature modules consume it transitively via `syringe-proxy`).
- Message quality gate: feature no-op tests must fail if `NotEnabledFeatureException` does not
  match the canonical 2-line format or omits any required token.
