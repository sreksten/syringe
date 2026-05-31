package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet1.AlternativePaymentProcessor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet1.PaymentService;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet2.CheckoutService;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet3.AlternativeClient;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet3.StereotypedAlternativeService;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet4priority.BeanClassStereotypePriorityClient;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet4priority.DeclaringClassPriorityClient;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet4priority.FieldPriorityClient;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet4priority.MethodPriorityClient;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet4priority.ProducerFieldStereotypePriorityClient;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.bullet4priority.ProducerMethodStereotypePriorityClient;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par27alternatives.nonportableinterceptor.AlternativeInterceptor;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.spi.Bean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("2.8 - Alternatives")
public class AlternativesTest {

    @Test
    @DisplayName("2.8 - @Alternative bean is not selected unless enabled")
    void alternativeBeanIsNotSelectedUnlessEnabled() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), PaymentService.class);
        syringe.setup();

        PaymentService service = syringe.inject(PaymentService.class);
        assertEquals("standard", service.processorType());

        Bean<?> alternativeBean = findManagedBean(syringe, AlternativePaymentProcessor.class);
        assertTrue(alternativeBean.isAlternative());
    }

    @Test
    @DisplayName("2.8 - Programmatically enabled alternative is selected")
    void programmaticallyEnabledAlternativeIsSelected() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), PaymentService.class);
        syringe.enableAlternative(AlternativePaymentProcessor.class);
        syringe.setup();

        PaymentService service = syringe.inject(PaymentService.class);
        assertEquals("alternative", service.processorType());
    }

    @Test
    @DisplayName("2.8 - @Priority alternatives are enabled and highest priority wins")
    void priorityAlternativesAreEnabledAndHighestPriorityWins() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), CheckoutService.class);
        syringe.setup();

        CheckoutService checkoutService = syringe.inject(CheckoutService.class);
        assertEquals("highPriorityAlternative", checkoutService.gatewayType());
    }

    @Test
    @DisplayName("2.8 - @Priority on producer method enables alternative")
    void priorityOnProducerMethodEnablesAlternative() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), MethodPriorityClient.class);
        setupOrFailWithDetails(syringe);

        MethodPriorityClient client = syringe.inject(MethodPriorityClient.class);
        assertEquals("methodPriorityAlternative", client.serviceType());
    }

    @Test
    @DisplayName("2.8 - @Priority on producer field enables alternative")
    void priorityOnProducerFieldEnablesAlternative() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), FieldPriorityClient.class);
        setupOrFailWithDetails(syringe);

        FieldPriorityClient client = syringe.inject(FieldPriorityClient.class);
        assertEquals("fieldPriorityAlternative", client.serviceType());
    }

    @Test
    @DisplayName("2.8 - @Priority on declaring bean class enables producer alternative")
    void priorityOnDeclaringBeanClassEnablesProducerAlternative() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DeclaringClassPriorityClient.class);
        setupOrFailWithDetails(syringe);

        DeclaringClassPriorityClient client = syringe.inject(DeclaringClassPriorityClient.class);
        assertEquals("declaringClassPriorityAlternative", client.serviceType());
    }

    @Test
    @DisplayName("2.8 - @Priority on stereotype applied to bean class enables alternative")
    void priorityOnStereotypeAppliedToBeanClassEnablesAlternative() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), BeanClassStereotypePriorityClient.class);
        setupOrFailWithDetails(syringe);

        BeanClassStereotypePriorityClient client = syringe.inject(BeanClassStereotypePriorityClient.class);
        assertEquals("beanClassStereotypePriorityAlternative", client.serviceType());
    }

    @Test
    @DisplayName("2.8 - @Priority on stereotype applied to producer method enables alternative")
    void priorityOnStereotypeAppliedToProducerMethodEnablesAlternative() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ProducerMethodStereotypePriorityClient.class);
        setupOrFailWithDetails(syringe);

        ProducerMethodStereotypePriorityClient client = syringe.inject(ProducerMethodStereotypePriorityClient.class);
        assertEquals("producerMethodStereotypePriorityAlternative", client.serviceType());
    }

    @Test
    @DisplayName("2.8 - @Priority on stereotype applied to producer field enables alternative")
    void priorityOnStereotypeAppliedToProducerFieldEnablesAlternative() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ProducerFieldStereotypePriorityClient.class);
        setupOrFailWithDetails(syringe);

        ProducerFieldStereotypePriorityClient client = syringe.inject(ProducerFieldStereotypePriorityClient.class);
        assertEquals("producerFieldStereotypePriorityAlternative", client.serviceType());
    }

    @Test
    @DisplayName("2.8 - Alternative declared by stereotype can be enabled programmatically")
    void alternativeDeclaredByStereotypeCanBeEnabledProgrammatically() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), AlternativeClient.class);
        syringe.enableAlternative(StereotypedAlternativeService.class);
        syringe.setup();

        AlternativeClient client = syringe.inject(AlternativeClient.class);
        assertEquals("stereotypedAlternative", client.serviceType());
    }

    @Test
    @DisplayName("2.8 - Alternative interceptor is non-portable")
    void alternativeInterceptorIsNonPortable() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), AlternativeInterceptor.class);

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    private Bean<?> findManagedBean(Syringe syringe, Class<?> beanClass) {
        return syringe.getKnowledgeBase().getBeans().stream()
                .filter(bean -> bean.getBeanClass().equals(beanClass))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Bean not found: " + beanClass.getName()));
    }

    private void setupOrFailWithDetails(Syringe syringe) {
        try {
            syringe.setup();
        } catch (RuntimeException e) {
            throw new AssertionError(
                    "Syringe setup failed."
                            + "\nDefinition errors: " + syringe.getKnowledgeBase().getDefinitionErrors()
                            + "\nInjection errors: " + syringe.getKnowledgeBase().getInjectionErrors()
                            + "\nGeneric errors: " + syringe.getKnowledgeBase().getErrors(),
                    e
            );
        }
    }
}
