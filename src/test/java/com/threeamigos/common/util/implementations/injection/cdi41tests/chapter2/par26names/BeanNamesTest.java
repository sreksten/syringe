package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par26names;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par26nonportableinterceptor.NamedNonPortableInterceptor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par26names.bullet1.NamedManagedBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par26names.bullet2.MethodProducedType;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par26names.bullet2.MethodProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par26names.bullet3.FieldProducedType;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par26names.bullet3.FieldProducerFactory;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par26names.bullet4.StereotypedNamedBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par26names.bullet5.UnnamedBean;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.spi.Bean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("2.6 - Bean Names")
public class BeanNamesTest {

    @Test
    @DisplayName("2.6.2 - Managed bean with empty @Named gets default bean name")
    void managedBeanWithEmptyNamedGetsDefaultBeanName() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NamedManagedBean.class);
        syringe.setup();

        Bean<?> bean = findManagedBean(syringe, NamedManagedBean.class);
        assertEquals("namedManagedBean", bean.getName());
    }

    @Test
    @DisplayName("2.6.2 - Producer method with empty @Named gets default bean name")
    void producerMethodWithEmptyNamedGetsDefaultBeanName() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), MethodProducerFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByProducedType(syringe, MethodProducedType.class);
        assertEquals("methodProducedType", producerBean.getName());
    }

    @Test
    @DisplayName("2.6.2 - Producer field with empty @Named gets default bean name")
    void producerFieldWithEmptyNamedGetsDefaultBeanName() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), FieldProducerFactory.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByProducedType(syringe, FieldProducedType.class);
        assertEquals("fieldProducedType", producerBean.getName());
    }

    @Test
    @DisplayName("2.6.2 - Empty @Named declared by stereotype gives default bean name")
    void emptyNamedDeclaredByStereotypeAssignsDefaultBeanName() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), StereotypedNamedBean.class);
        syringe.setup();

        Bean<?> bean = findManagedBean(syringe, StereotypedNamedBean.class);
        assertEquals("stereotypedNamedBean", bean.getName());
    }

    @Test
    @DisplayName("2.6.3 - Bean has no name when neither bean nor stereotypes declare @Named")
    void beanHasNoNameWhenNamedIsNotDeclaredByBeanOrStereotypes() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), UnnamedBean.class);
        syringe.setup();

        Bean<?> bean = findManagedBean(syringe, UnnamedBean.class);
        assertTrue(bean.getName() == null || bean.getName().isEmpty());
    }

    @Test
    @DisplayName("2.6 - Interceptor with a bean name is non-portable")
    void interceptorWithBeanNameIsNonPortable() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NamedNonPortableInterceptor.class);

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    private Bean<?> findManagedBean(Syringe syringe, Class<?> beanClass) {
        return syringe.getKnowledgeBase().getBeans().stream()
                .filter(bean -> bean.getBeanClass().equals(beanClass))
                .filter(bean -> !(bean instanceof ProducerBean))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Managed bean not found: " + beanClass.getName()));
    }

    private ProducerBean<?> findProducerBeanByProducedType(Syringe syringe, Class<?> producedType) {
        return syringe.getKnowledgeBase().getProducerBeans().stream()
                .filter(producerBean -> producerBean.getTypes().contains(producedType))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Producer bean not found for type: " + producedType.getName()));
    }

}
