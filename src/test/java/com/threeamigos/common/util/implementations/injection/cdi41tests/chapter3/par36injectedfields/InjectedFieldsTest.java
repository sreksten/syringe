package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par36injectedfields;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par36injectedfields.bullet1.InvalidStaticInjectedFieldBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par36injectedfields.bullet2.InvalidFinalInjectedFieldBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par36injectedfields.bullet3.ValidInjectedFieldBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par36injectedfields.bullet4.DerivedBeanWithInheritedInjectedField;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par36injectedfields.bullet5.InvalidInjectProducesFieldBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("3.6 - Injected Fields")
public class InjectedFieldsTest {

    @Test
    @DisplayName("3.6 - Injected field must be non-static")
    void injectedFieldMustBeNonStatic() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidStaticInjectedFieldBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.6 - Injected field must be non-final")
    void injectedFieldMustBeNonFinal() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidFinalInjectedFieldBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.6 - Non-static non-final injected field in bean class is valid")
    void nonStaticNonFinalInjectedFieldInBeanClassIsValid() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ValidInjectedFieldBean.class);
        syringe.setup();

        ValidInjectedFieldBean instance = createManagedBeanInstance(syringe, ValidInjectedFieldBean.class);
        assertNotNull(instance.getDependency());
    }

    @Test
    @DisplayName("3.6 - Non-static non-final injected field in superclass supporting injection is valid")
    void nonStaticNonFinalInjectedFieldInSuperclassSupportingInjectionIsValid() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DerivedBeanWithInheritedInjectedField.class);
        syringe.setup();

        DerivedBeanWithInheritedInjectedField instance = createManagedBeanInstance(
                syringe, DerivedBeanWithInheritedInjectedField.class);
        assertNotNull(instance.getInheritedDependency());
    }

    @Test
    @DisplayName("3.6 - Injected field annotated @Produces is a definition error")
    void injectedFieldAnnotatedProducesIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidInjectProducesFieldBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
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
