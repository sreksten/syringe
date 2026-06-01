package com.threeamigos.common.util.implementations.injection.usage;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * IMPORTANT: this class' tests cannot be run in parallel due to the use of the
 * jakarta.enterprise.inject.scan.implicit environment variable.
 * <p>
 * See Chapter 27 - Packaging and deployment in Java SE, §27.1: Bean archive in Java SE.
 *
 * @author Stefano Reksten
 */
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
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
        String key = "jakarta.enterprise.inject.scan.implicit";
        String previous = System.getProperty(key);
        // This will find a TON of classes!
        System.setProperty(key, "true");
        try {
            SeContainerInitializer containerInit = SeContainerInitializer.newInstance();
            // Since the test package contains a failing test, this will be noticed and reported
            assertThrows(NonPortableBehaviourException.class, containerInit::initialize);
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
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
