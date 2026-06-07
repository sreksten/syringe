package com.threeamigos.common.util.implementations.injection.coreonly;

import com.threeamigos.common.util.implementations.injection.NotEnabledFeatureException;
import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.coreonly.fixtures.negative.DecoratorUsageBean;
import com.threeamigos.common.util.implementations.injection.coreonly.fixtures.negative.InterceptorUsageBean;
import com.threeamigos.common.util.implementations.injection.coreonly.fixtures.negative.observers.ObserverUsageBean;
import com.threeamigos.common.util.implementations.injection.coreonly.fixtures.negative.scopes.NormalScopedUsageBean;
import com.threeamigos.common.util.implementations.injection.decorators.NoOpDecoratorSupport;
import com.threeamigos.common.util.implementations.injection.interceptors.NoOpInterceptorSupport;
import com.threeamigos.common.util.implementations.injection.knowledgebase.KnowledgeBase;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.spi.DeploymentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "syringe.core.only", matches = "true")
class CoreOnlyNegativeFeatureGateTest {

    @Test
    void extensionApiCallThrowsCanonicalGuidanceWhenExtensionsModuleIsMissing() {
        Syringe syringe = new Syringe();

        NotEnabledFeatureException ex = assertThrows(
                NotEnabledFeatureException.class,
                () -> syringe.addExtension("com.example.SomeExtension")
        );

        assertMessageContains(ex,
                "API call found at Syringe.addExtension(String)",
                "Add syringe-extensions to your classpath");
    }

    @Test
    void buildCompatibleExtensionApiCallThrowsCanonicalGuidanceWhenBceModuleIsMissing() {
        Syringe syringe = new Syringe();

        NotEnabledFeatureException ex = assertThrows(
                NotEnabledFeatureException.class,
                () -> syringe.addBuildCompatibleExtension("com.example.SomeBuildCompatibleExtension")
        );

        assertMessageContains(ex,
                "API call found at Syringe.addBuildCompatibleExtension(String)",
                "Add syringe-bce to your classpath");
    }

    @Test
    void setupFailsWhenObserverMethodsArePresentWithoutEventsModule() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ObserverUsageBean.class);

        DeploymentException deploymentException = assertThrows(
                DeploymentException.class,
                syringe::setup
        );

        NotEnabledFeatureException ex = extractNotEnabledFeatureException(deploymentException);
        assertMessageContains(ex,
                "Event/observer support is not available",
                "Add syringe-events to your classpath");
    }

    @Test
    void setupFailsWhenNormalScopeUsageIsPresentWithoutScopesModule() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NormalScopedUsageBean.class);

        DeploymentException deploymentException = assertThrows(
                DeploymentException.class,
                syringe::setup
        );

        NotEnabledFeatureException ex = extractNotEnabledFeatureException(deploymentException);
        assertMessageContains(ex,
                "@ApplicationScoped found on class " + NormalScopedUsageBean.class.getName(),
                "Add syringe-scopes to your classpath");
    }

    @Test
    void interceptorUsageDetectionThrowsCanonicalGuidanceWithoutInterceptorsModule() {
        NoOpInterceptorSupport noOp = new NoOpInterceptorSupport();
        KnowledgeBase knowledgeBase = new KnowledgeBase(new InMemoryMessageHandler());
        knowledgeBase.addInterceptor(InterceptorUsageBean.class);
        noOp.setKnowledgeBase(knowledgeBase);

        NotEnabledFeatureException ex = assertThrows(
                NotEnabledFeatureException.class,
                noOp::validateBeansXmlInterceptorConfiguration
        );

        assertMessageContains(ex,
                "@Interceptor found at class " + InterceptorUsageBean.class.getName(),
                "Add syringe-interceptors to your classpath");
    }

    @Test
    void decoratorUsageDetectionThrowsCanonicalGuidanceWithoutDecoratorsModule() {
        NoOpDecoratorSupport noOp = new NoOpDecoratorSupport();
        KnowledgeBase knowledgeBase = new KnowledgeBase(new InMemoryMessageHandler());
        knowledgeBase.addDecorator(DecoratorUsageBean.class);
        noOp.setKnowledgeBase(knowledgeBase);

        NotEnabledFeatureException ex = assertThrows(
                NotEnabledFeatureException.class,
                noOp::validateBeansXmlDecoratorConfiguration
        );

        assertMessageContains(ex,
                "@Decorator found at class " + DecoratorUsageBean.class.getName(),
                "Add syringe-decorators to your classpath");
    }

    private void assertMessageContains(NotEnabledFeatureException ex, String... tokens) {
        String message = ex.getMessage();
        for (String token : tokens) {
            assertTrue(message.contains(token),
                    "Expected message to contain: " + token + ", but was: " + message);
        }
    }

    private NotEnabledFeatureException extractNotEnabledFeatureException(DeploymentException deploymentException) {
        assertTrue(deploymentException.getCause() instanceof NotEnabledFeatureException,
                "Expected DeploymentException cause to be NotEnabledFeatureException, but was: " + deploymentException.getCause());
        return (NotEnabledFeatureException) deploymentException.getCause();
    }
}
