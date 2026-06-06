package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par35beanconstructors;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par35beanconstructors.bullet1.InvalidAbstractBeanClass;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par35beanconstructors.bullet2.NoInjectNoArgConstructorBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par35beanconstructors.bullet3.InvalidMultipleInjectConstructorsBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par35beanconstructors.bullet4.InvalidDisposesConstructorParameterBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par35beanconstructors.bullet4.InvalidObservesAsyncConstructorParameterBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par35beanconstructors.bullet4.InvalidObservesConstructorParameterBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par35beanconstructors.bullet5.MultiParameterConstructorBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("3.5 - Bean constructors")
public class BeanConstructorsTest {

    /**
     * Java does not allow abstract constructors. This test asserts the equivalent invalid case:
     * an abstract bean class (thus non-instantiable by constructor).
     */
    @Test
    @DisplayName("3.5 - Abstract bean class is a definition error")
    void abstractBeanClassIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidAbstractBeanClass.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.5 - If no constructor is annotated @Inject, the no-arg constructor is the bean constructor")
    void noInjectConstructorUsesNoArgConstructorAsBeanConstructor() {
        NoInjectNoArgConstructorBean.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NoInjectNoArgConstructorBean.class);
        syringe.setup();

        Bean<?> bean = findManagedBean(syringe, NoInjectNoArgConstructorBean.class);
        CreationalContext<?> creationalContext = syringe.getBeanManager().createCreationalContext(bean);
        NoInjectNoArgConstructorBean instance = (NoInjectNoArgConstructorBean) bean.create((CreationalContext) creationalContext);

        assertNotNull(instance);
        assertEquals(1, NoInjectNoArgConstructorBean.noArgConstructorCalls);
        assertEquals(0, NoInjectNoArgConstructorBean.parameterizedConstructorCalls);
    }

    @Test
    @DisplayName("3.5 - Bean class with more than one @Inject constructor is a definition error")
    void multipleInjectConstructorsIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidMultipleInjectConstructorsBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.5 - Bean constructor parameter annotated @Disposes is a definition error")
    void constructorParameterAnnotatedDisposesIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidDisposesConstructorParameterBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.5 - Bean constructor parameter annotated @Observes is a definition error")
    void constructorParameterAnnotatedObservesIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidObservesConstructorParameterBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.5 - Bean constructor parameter annotated @ObservesAsync is a definition error")
    void constructorParameterAnnotatedObservesAsyncIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidObservesAsyncConstructorParameterBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.5 - Bean constructor may declare multiple parameters and all are injection points")
    void constructorWithMultipleParametersUsesInjectionPoints() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), MultiParameterConstructorBean.class);
        syringe.setup();

        Bean<?> bean = findManagedBean(syringe, MultiParameterConstructorBean.class);
        CreationalContext<?> creationalContext = syringe.getBeanManager().createCreationalContext(bean);
        MultiParameterConstructorBean instance = (MultiParameterConstructorBean) bean.create((CreationalContext) creationalContext);

        assertNotNull(instance);
        assertNotNull(instance.getDependencyA());
        assertNotNull(instance.getDependencyB());
        assertNotNull(instance.getDependencyC());
    }

    private Bean<?> findManagedBean(Syringe syringe, Class<?> beanClass) {
        return syringe.getKnowledgeBase().getBeans().stream()
                .filter(bean -> bean.getBeanClass().equals(beanClass))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Managed bean not found: " + beanClass.getName()));
    }
}
