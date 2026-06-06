package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet1.NoNoArgConstructorConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet2.FinalClassConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet3.FinalMethodConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet4.SealedClassConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet4.SealedInterfaceConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet5.PrimitiveConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet6.ArrayConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet7.FinalDependentAllowedConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet8.InterceptedFinalBeanConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par310unproxyablebeantypes.bullet9.PrivateConstructorConsumer;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.DeploymentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("3.10 - Unproxyable bean types")
public class UnproxyableBeanTypesTest {

    @Test
    @DisplayName("3.10 - Class without non-private no-arg constructor is unproxyable for client proxy")
    void classWithoutNonPrivateNoArgConstructorIsDeploymentProblemWhenClientProxyRequired() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NoNoArgConstructorConsumer.class);

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.10 - Class with private constructor is unproxyable for client proxy")
    void classWithPrivateConstructorIsDeploymentProblemWhenClientProxyRequired() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), PrivateConstructorConsumer.class);

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.10 - Final class is unproxyable for client proxy")
    void finalClassIsDeploymentProblemWhenClientProxyRequired() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), FinalClassConsumer.class);

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.10 - Class with non-static final non-private method is unproxyable for client proxy")
    void classWithFinalBusinessMethodIsDeploymentProblemWhenClientProxyRequired() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), FinalMethodConsumer.class);

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.10 - Sealed class bean type is unproxyable")
    void sealedClassBeanTypeIsDeploymentProblemWhenClientProxyRequired() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), SealedClassConsumer.class);

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.10 - Sealed interface bean type is unproxyable")
    void sealedInterfaceBeanTypeIsDeploymentProblemWhenClientProxyRequired() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), SealedInterfaceConsumer.class);

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.10 - Primitive bean type is unproxyable")
    void primitiveBeanTypeIsDeploymentProblemWhenClientProxyRequired() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), PrimitiveConsumer.class);

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.10 - Array bean type is unproxyable")
    void arrayBeanTypeIsDeploymentProblemWhenClientProxyRequired() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ArrayConsumer.class);

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.10 - Regression: unproxyable array deployment keeps CDI deployment-problem semantics")
    void arrayUnproxyableFailureKeepsCdiDeploymentProblemSemantics() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ArrayConsumer.class);

        DeploymentException thrown = assertThrows(DeploymentException.class, syringe::setup);
        assertTrue(
                !throwableChainContains(thrown, "IllegalAccessError")
                        && !throwableChainContains(thrown, "NoClassDefFoundError")
                        && !throwableChainContains(thrown, "ClassNotFoundException"),
                "Expected CDI deployment problem, not linkage/classloading failure details");
    }

    @Test
    @DisplayName("3.10 - Unproxyable bean type is allowed when no client proxy and no bound interceptor are required")
    void unproxyableBeanTypeIsAllowedWhenNoProxyOrInterceptorIsRequired() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), FinalDependentAllowedConsumer.class);
        syringe.setup();

        FinalDependentAllowedConsumer instance = createManagedBeanInstance(syringe, FinalDependentAllowedConsumer.class);
        assertEquals("ok", instance.invoke());
    }

    @Test
    @DisplayName("3.10 - Unproxyable bean type with bound interceptor is a deployment problem")
    void unproxyableBeanTypeWithBoundInterceptorIsDeploymentProblem() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InterceptedFinalBeanConsumer.class);

        assertThrows(DeploymentException.class, syringe::setup);
    }

    @SuppressWarnings("unchecked")
    private <T> T createManagedBeanInstance(Syringe syringe, Class<T> beanClass) {
        Bean<?> bean = findManagedBean(syringe, beanClass);
        CreationalContext<?> creationalContext = syringe.getBeanManager().createCreationalContext(bean);
        return (T) bean.create((CreationalContext) creationalContext);
    }

    private Bean<?> findManagedBean(Syringe syringe, Class<?> beanClass) {
        return syringe.getKnowledgeBase().getBeans().stream()
                .filter(candidate -> candidate.getBeanClass().equals(beanClass))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Managed bean not found: " + beanClass.getName()));
    }

    private boolean throwableChainContains(Throwable throwable, String textFragment) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(textFragment)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
