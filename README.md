# Syringe – a CDI 4.1 compatible injection framework

Part of the common-util classes, designed to help when writing standalone Java applications.

## Context Dependency and Injection

Syringe provides a lightweight and flexible way to manage dependencies and perform dependency injection in standalone Java applications. It is compatible with the CDI 4.1 specification. It does not support EL.

Syringe supports both `jakarta.inject` and legacy `javax.inject` annotations, and is validated against CDI/TCK-style tests in this repository.

Syringe is modular: you can run with a minimal baseline (`syringe-core`) and add only the feature modules you actually use.

- Core: discovery/scanning, bean resolution/injection, `@Dependent` lifecycle, `Instance` lookup, `CDI.select(...)` semantics.
- Optional runtime features: normal scopes, events/observers, interceptors, decorators, portable extensions, build-compatible extensions (BCE), legacy `@New` annotation, SE integration.
- If an optional module is missing and that feature is used, Syringe throws `NotEnabledFeatureException` with a message telling you which `syringe-*` artifact to add to your classpath.

Usage (single-artifact style):

```xml
<dependency>
    <groupId>com.threeamigos</groupId>
    <artifactId>syringe</artifactId>
    <version>4.1.0</version>
</dependency>
```

Syringe relies on these internal dependencies:

- `common-utils-messagehandler`
- `common-utils-collections`
- `common-utils-concurrency`

Usage examples:

```java
@Execution(ExecutionMode.SAME_THREAD)
public class UsageTest {

    @Test
    @DisplayName("Standard CDI initialization, explicit scan")
    public void standardCDIInitializationExplicitScan() {
        SeContainerInitializer containerInit = SeContainerInitializer.newInstance();
        containerInit.addPackages(MyBean.class);
        SeContainer container = containerInit.initialize();
        // retrieve a bean and do work with it
        MyBean myBean = container.select(MyBean.class).get();
        myBean.doWork();
        // when done
        container.close();
    }

    @Test
    @DisplayName("Standard CDI initialization, implicit scan")
    public void standardCDIInitializationImplicitScan() {
        // This will find a TON of classes!
        System.setProperty("jakarta.enterprise.inject.scan.implicit", "true");
        SeContainerInitializer containerInit = SeContainerInitializer.newInstance();
        // Since the test package contains a failing test, this will be noticed and reported
        assertThrows(NonPortableBehaviourException.class, containerInit::initialize);
        System.setProperty("jakarta.enterprise.inject.scan.implicit", "false");
    }

    @Test
    @DisplayName("Syringe initialization")
    public void syringeInitialization() {
        Syringe syringe = new Syringe("com.threeamigos.common.util.implementations.injection.usage");
        syringe.setup();
        MyBean myBean = syringe.inject(MyBean.class);
        myBean.doWork();
        syringe.shutdown();
    }
}
```

## Syringe Subpackages and Features

### `syringe-core`

Core container runtime: `Syringe`, `BeanManagerImpl`, discovery/scanning, bean registration/validation, `@Dependent` context, core annotations/utilities, `Instance`/`CDI.select(...)`, and no-op fallbacks for optional features.

### `syringe-proxy`

Shared ByteBuddy-based proxy infrastructure used by feature modules (`client proxy`, `interceptor-aware proxy`, `decorator-aware proxy`). No CDI features semantics by itself.

### `syringe-scopes`

Normal scopes and context management:

- `@ApplicationScoped`, `@RequestScoped`, `@SessionScoped`, `@ConversationScoped`
- context lifecycle hooks (`@Initialized`, `@BeforeDestroyed`, `@Destroyed`)
- request/session/conversation activation helpers
- client proxy generation for normal-scoped beans

### `syringe-events`

Event and observer support:

- `Event<T>` implementation
- observer discovery (`@Observes`, `@ObservesAsync`)
- startup/shutdown lifecycle events
- async delivery and conversation context propagation helpers

### `syringe-interceptors`

Interceptor support:

- interceptor resolution and invocation chains
- `@Interceptor`, `@InterceptorBinding`, `@AroundInvoke`, `@AroundConstruct`
- `InterceptionFactory` support
- beans.xml interceptor validation

### `syringe-decorators`

Decorator support:

- `@Decorator` and `@Delegate` resolution
- decorator chain/proxy runtime
- beans.xml and programmatic decorator validation

### `syringe-extensions`

Portable CDI extensions:

- `Extension` discovery and registration
- lifecycle/SPIs (`BeforeBeanDiscovery`, `Process*`, `AfterTypeDiscovery`, `AfterBeanDiscovery`, `AfterDeploymentValidation`, `BeforeShutdown`)
- `@WithAnnotations` support

### `syringe-bce`

Build-Compatible Extensions (CDI 4.x BCE):

- `BuildCompatibleExtension` discovery/registration
- BCE lifecycle phases (`Discovery`, `Enhancement`, `Registration`, `Synthesis`, `Validation`)
- `@SkipIfPortableExtensionPresent` handling
- Invoker API integration

### `syringe-legacy`

Optional legacy support for deprecated CDI 1.0 `@javax.enterprise.inject.New`.

### `syringe-se`

SE integration module:

- `SeContainerInitializer` and `SeContainer` implementation
- `CDIProvider` wiring so `CDI.current()` resolves the active Syringe container

### `syringe-full`

Full-runtime/integration-test module used in this repository to run the full CDI feature set together.
This module publishes the aggregate artifact `com.threeamigos:syringe`.

## Primer: How to Use Syringe

### 1) Choose your dependency set

Minimal baseline:

```xml
<dependency>
    <groupId>com.threeamigos</groupId>
    <artifactId>syringe-core</artifactId>
    <version>4.1.0</version>
</dependency>
```

Then add only the necessary features, for example:

- `syringe-scopes` for normal scopes
- `syringe-events` for observers/events
- `syringe-interceptors` for interceptors
- `syringe-decorators` for decorators
- `syringe-extensions` for portable extensions
- `syringe-bce` for build-compatible extensions
- `syringe-legacy` for `@New`
- `syringe-se` for `SeContainerInitializer` / `CDI.current()`

### 2) Bootstrap with Syringe API (works with `syringe-core`)

```java
Syringe syringe = new Syringe("com.myapp");
syringe.setup();

MyBean myBean = syringe.inject(MyBean.class);
myBean.doWork();

syringe.shutdown();
```

### 3) Bootstrap with CDI SE API (requires `syringe-se`)

```java
SeContainerInitializer init = SeContainerInitializer.newInstance();
init.addPackages(MyBean.class);

try (SeContainer container = init.initialize()) {
    MyBean myBean = container.select(MyBean.class).get();
    myBean.doWork();
}
```

### 4) Enable optional APIs only if their module is present

- `syringe.addExtension(...)` requires `syringe-extensions`
- `syringe.addBuildCompatibleExtension(...)` requires `syringe-bce`
- `syringe.enableLegacyCdi10New(true)` requires `syringe-legacy`

If the module is missing and feature usage is detected, Syringe throws `NotEnabledFeatureException` with the exact artifact name to add.

## Build Instructions

By default, the parent build runs a module-local clean during `initialize`, so every module JAR is rebuilt before shading the aggregate JAR.
You can disable this behavior with:

```bash
mvn -Dsyringe.skip.prebuild.clean=true ...
```

### Build all module JARs (single-module artifacts)

From the repository root:

```bash
mvn -DskipTests package
```

This produces one JAR per module under each module `target/` directory, for example:

- `syringe-core/target/syringe-core-4.1.0.jar`
- `syringe-events/target/syringe-events-4.1.0.jar`
- `syringe-interceptors/target/syringe-interceptors-4.1.0.jar`
- `...`

### Build just one specific module JAR

Example (`syringe-events`):

```bash
mvn -pl syringe-events -am -DskipTests package
```

Output:

- `syringe-events/target/syringe-events-4.1.0.jar`

### Build one full "all-in-one" JAR

`syringe-full/pom.xml` (artifactId `syringe`) is configured to do this automatically during `package`:

1. build shaded full-runtime JAR in `syringe-full/target`
2. copy it to the repository root as `syringe-<version>.jar`

```bash
mvn -pl syringe-full -am -DskipTests package
```

Result:

- shaded JAR in `syringe-full/target/`: `syringe-4.1.0.jar`
- copied JAR at repo root: `syringe-4.1.0.jar`

The shaded aggregate JAR includes only Syringe modules (`syringe-*`) and excludes non-Syringe dependencies.

### Install aggregate artifact to local Maven repository

```bash
mvn -pl syringe-full -am -DskipTests install
```

Installs:

- `~/.m2/repository/com/threeamigos/syringe/4.1.0/syringe-4.1.0.jar`
- `~/.m2/repository/com/threeamigos/syringe/4.1.0/syringe-4.1.0.pom`
