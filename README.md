# Syringe – a CDI 4.1 compatible injection framework

Part of the common-util classes, designed to help when writing standalone Java applications.

This subpackage addresses the following needs:

### Context Dependency and Injection

Syringe provides a lightweight and flexible way to manage dependencies and perform dependency injection in standalone
Java applications. It is compatible with the CDI 4.1 specification. It does **not** support EL, since it was
deprecated in 4.1 (and it's hardly necessary for standalone applications).

Syringe also supports the older `javax.inject` annotations.

It was tested using the official TCK.

Usage:

Add Syringe to your project's build configuration file. Syringe depends on the common-utils-messagehandler package.

```
        <dependency>
            <groupId>com.threeamigos</groupId>
            <artifactId>syringe</artifactId>
            <version>4.1.0</version>
        </dependency>
        <dependency>
            <groupId>com.threeamigos</groupId>
            <artifactId>common-utils-messagehandler</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
```

To use Syringe, see the UsageTest class:

```
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