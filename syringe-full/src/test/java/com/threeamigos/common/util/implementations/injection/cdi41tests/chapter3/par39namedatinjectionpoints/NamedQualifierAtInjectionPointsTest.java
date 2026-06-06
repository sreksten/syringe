package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par39namedatinjectionpoints;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par39namedatinjectionpoints.bullet1.NamedFieldInjectionPointBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par39namedatinjectionpoints.bullet2.InvalidEmptyNamedConstructorParameterBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("3.9 - The @Named qualifier at injection points")
public class NamedQualifierAtInjectionPointsTest {

    @Test
    @DisplayName("3.9 - Injected field annotated @Named without value uses the field name")
    void injectedFieldNamedWithoutValueUsesFieldName() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NamedFieldInjectionPointBean.class);
        syringe.setup();

        NamedFieldInjectionPointBean instance = createManagedBeanInstance(syringe, NamedFieldInjectionPointBean.class);
        assertEquals("primary", instance.getPaymentService().id());
        assertEquals(NamedFieldInjectionPointBean.PaymentServiceImpl.class, instance.getPaymentService().getClass());
    }

    @Test
    @DisplayName("3.9 - Non-field injection point annotated @Named without value is a definition error")
    void nonFieldInjectionPointNamedWithoutValueIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidEmptyNamedConstructorParameterBean.class);

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
