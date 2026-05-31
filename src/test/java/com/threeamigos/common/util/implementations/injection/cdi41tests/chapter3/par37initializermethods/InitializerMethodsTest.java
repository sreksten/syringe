package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet1.VisibilityInitializerMethodsBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet2.DerivedBeanWithInheritedInitializerMethod;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet3.InvalidStaticInitializerMethodBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet4.InvalidGenericInitializerMethodBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet5.InvalidAbstractInitializerMethodBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet6.InitializerInterceptorBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet6.InitializerMethodInterceptor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet7.InvalidProducesInitializerMethodBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet8.InvalidDisposesInitializerMethodBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet9.InvalidObservesInitializerMethodBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet10.InvalidObservesAsyncInitializerMethodBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par37initializermethods.bullet11.MultiParameterInitializerMethodBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("3.7 - Initializer Methods")
public class InitializerMethodsTest {

    @Test
    @DisplayName("3.7 - Initializer methods may be default, public, protected or private and are invoked")
    void initializerMethodsWithSupportedVisibilitiesAreInvoked() {
        VisibilityInitializerMethodsBean.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), VisibilityInitializerMethodsBean.class);
        syringe.setup();

        VisibilityInitializerMethodsBean instance = createManagedBeanInstance(syringe, VisibilityInitializerMethodsBean.class);

        assertNotNull(instance);
        assertTrue(VisibilityInitializerMethodsBean.defaultInitializerCalled);
        assertTrue(VisibilityInitializerMethodsBean.publicInitializerCalled);
        assertTrue(VisibilityInitializerMethodsBean.protectedInitializerCalled);
        assertTrue(VisibilityInitializerMethodsBean.privateInitializerCalled);
    }

    @Test
    @DisplayName("3.7 - Initializer method in superclass supporting injection is invoked")
    void initializerMethodInSuperclassSupportingInjectionIsInvoked() {
        DerivedBeanWithInheritedInitializerMethod.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DerivedBeanWithInheritedInitializerMethod.class);
        syringe.setup();

        DerivedBeanWithInheritedInitializerMethod instance =
                createManagedBeanInstance(syringe, DerivedBeanWithInheritedInitializerMethod.class);

        assertNotNull(instance);
        assertTrue(DerivedBeanWithInheritedInitializerMethod.inheritedInitializerCalled);
        assertNotNull(instance.getInheritedDependency());
    }

    @Test
    @DisplayName("3.7 - Static initializer method is a definition error")
    void staticInitializerMethodIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidStaticInitializerMethodBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.7 - Generic method annotated @Inject is a definition error")
    void genericInitializerMethodIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidGenericInitializerMethodBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.7 - Abstract initializer method is a definition error")
    void abstractInitializerMethodIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidAbstractInitializerMethodBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.7 - Method interceptors are never called when container invokes initializer methods")
    void interceptorsAreNotCalledWhenContainerInvokesInitializerMethods() {
        InitializerInterceptorBean.reset();
        InitializerMethodInterceptor.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InitializerInterceptorBean.class);
        syringe.setup();

        assertTrue(syringe.getKnowledgeBase().getInterceptors().contains(InitializerMethodInterceptor.class));

        InitializerInterceptorBean instance = createManagedBeanInstance(syringe, InitializerInterceptorBean.class);

        assertNotNull(instance);
        assertEquals(1, InitializerInterceptorBean.initializerCalls);
        assertEquals(0, InitializerMethodInterceptor.aroundInvokeCalls);
    }

    @Test
    @DisplayName("3.7 - Initializer method annotated @Produces is a definition error")
    void initializerMethodAnnotatedProducesIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidProducesInitializerMethodBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.7 - Initializer method with @Disposes parameter is a definition error")
    void initializerMethodWithDisposesParameterIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidDisposesInitializerMethodBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.7 - Initializer method with @Observes parameter is a definition error")
    void initializerMethodWithObservesParameterIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidObservesInitializerMethodBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.7 - Initializer method with @ObservesAsync parameter is a definition error")
    void initializerMethodWithObservesAsyncParameterIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidObservesAsyncInitializerMethodBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.7 - Initializer method may have any number of parameters and all are injection points")
    void initializerMethodMayHaveAnyNumberOfParametersAndAllAreInjectionPoints() {
        MultiParameterInitializerMethodBean.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), MultiParameterInitializerMethodBean.class);
        syringe.setup();

        MultiParameterInitializerMethodBean instance =
                createManagedBeanInstance(syringe, MultiParameterInitializerMethodBean.class);

        assertNotNull(instance);
        assertEquals(1, MultiParameterInitializerMethodBean.initializerCalls);
        assertNotNull(instance.getDependencyA());
        assertNotNull(instance.getDependencyB());
        assertNotNull(instance.getDependencyC());
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
}
