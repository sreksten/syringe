package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par23qualifiers;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import com.threeamigos.common.util.implementations.injection.annotations.AnnotationLiteral;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("2.3 - Qualifiers")
public class QualifiersTest {

    @Test
    @DisplayName("2.3.1 - Every bean has the built-in @Any qualifier")
    void everyBeanHasAnyQualifier() {
        Set<Annotation> synchronousQualifiers =
                QualifiersHelper.extractBeanQualifiers(SynchronousPaymentProcessor.class.getAnnotations());
        Set<Annotation> asynchronousQualifiers =
                QualifiersHelper.extractBeanQualifiers(AsynchronousPaymentProcessor.class.getAnnotations());
        Set<Annotation> reliableSynchronousQualifiers =
                QualifiersHelper.extractBeanQualifiers(ReliableSynchronousPaymentProcessor.class.getAnnotations());

        assertTrue(hasQualifier(synchronousQualifiers, Any.class), "SynchronousPaymentProcessor must include @Any");
        assertTrue(hasQualifier(asynchronousQualifiers, Any.class), "AsynchronousPaymentProcessor must include @Any");
        assertTrue(hasQualifier(reliableSynchronousQualifiers, Any.class),
                "ReliableSynchronousPaymentProcessor must include @Any");
    }

    @Test
    @DisplayName("2.3.1 - Every bean not declaring @Named or @Any has the built-in @Default qualifier")
    void everyBeanNotDeclaringNamedOrAnyHasDefaultQualifier() {
        Set<Annotation> noExplicitQualifiers =
                QualifiersHelper.extractBeanQualifiers(PaymentProcessor.class.getAnnotations());
        Set<Annotation> synchronousQualifiers =
                QualifiersHelper.extractBeanQualifiers(SynchronousPaymentProcessor.class.getAnnotations());
        Set<Annotation> asynchronousQualifiers =
                QualifiersHelper.extractBeanQualifiers(AsynchronousPaymentProcessor.class.getAnnotations());

        assertTrue(hasQualifier(noExplicitQualifiers, Default.class),
                "PaymentProcessor (no explicit qualifier) must include @Default");
        assertFalse(hasQualifier(synchronousQualifiers, Default.class),
                "SynchronousPaymentProcessor declares @Synchronous and should not include @Default");
        assertFalse(hasQualifier(asynchronousQualifiers, Default.class),
                "AsynchronousPaymentProcessor declares @Asynchronous and should not include @Default");
    }

    @Test
    @DisplayName("2.3.3 - A bean can have more than one qualifier")
    void beanCanHaveMoreThanOneQualifier() {
        // Use the ReliableSynchronousPaymentProcessor to demonstrate this
        Set<Annotation> qualifiers =
                QualifiersHelper.extractBeanQualifiers(ReliableSynchronousPaymentProcessor.class.getAnnotations());

        assertTrue(hasQualifier(qualifiers, Reliable.class),
                "ReliableSynchronousPaymentProcessor must include @Reliable");
        assertTrue(hasQualifier(qualifiers, Synchronous.class),
                "ReliableSynchronousPaymentProcessor must include @Synchronous");

        long explicitQualifierCount = qualifiers.stream()
                .map(Annotation::annotationType)
                .filter(type -> !type.equals(Any.class) && !type.equals(Default.class))
                .count();
        assertTrue(explicitQualifierCount >= 2,
                "ReliableSynchronousPaymentProcessor should have at least two explicit qualifiers");
        assertFalse(hasQualifier(qualifiers, Default.class),
                "ReliableSynchronousPaymentProcessor has explicit qualifiers and should not include @Default");
    }

    @Test
    @DisplayName("2.3.4. Specifying qualifiers of an injected field")
    void specifyingQualifiersOfAnInjectedField() {
        // Given
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ShoppingCart.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        // When
        ShoppingCart shoppingCart = syringe.inject(ShoppingCart.class);
        String paymentResult = shoppingCart.processPayment();
        // Then
        assertEquals("Processed synchronously and reliably", paymentResult);
    }

    @Test
    @DisplayName("2.3.4 - Specifying qualifiers of an injected Instance")
    void specifyingQualifiersOfAnInjectedInstance() {
        // Given
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ShoppingCartWithInstance.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        // When
        ShoppingCartWithInstance shoppingCart = syringe.inject(ShoppingCartWithInstance.class);
        String paymentResult = shoppingCart.processPayment();
        // Then
        assertEquals("Processed synchronously and reliably", paymentResult);
    }

    @Test
    @DisplayName("2.3.4 - Specifying qualifiers of an injected @Any Instance")
    void specifyingQualifiersOfAnInjectedAnyInstance() {
        // Given
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ShoppingCartWithAnyInstance.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        // When
        ShoppingCartWithAnyInstance shoppingCart = syringe.inject(ShoppingCartWithAnyInstance.class);
        String paymentResult = shoppingCart.getPaymentProcessorInstance()
                .select(AnnotationLiteral.of(Reliable.class),
                        AnnotationLiteral.of(Synchronous.class)).get().processPayment();
        // Then
        assertEquals("Processed synchronously and reliably", paymentResult);
    }

    @Test
    @DisplayName("2.3.5 - Qualifiers with a ShoppingCart using PaymentProcessorFactory")
    void qualifiersWithShoppingCartUsingPaymentProcessorFactory() {
        // Given
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ShoppingCartUsingPaymentProcessorFactory.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        // When
        ShoppingCartUsingPaymentProcessorFactory shoppingCart =
                syringe.inject(ShoppingCartUsingPaymentProcessorFactory.class);
        String paymentResult = shoppingCart.processPayment();
        // Then
        assertEquals("Processed synchronously and reliably", paymentResult);
    }

    @Test
    @DisplayName("2.3.6 - Repeating Qualifiers")
    void repeatingQualifiers() {
        // Given
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), CoordinateConsumer.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        // When
        CoordinateConsumer consumer = syringe.inject(CoordinateConsumer.class);
        Coordinate coordinate = consumer.getCoordinate();
        // Then
        assertEquals(2, coordinate.getCoordinates().length);
    }

    private boolean hasQualifier(Set<Annotation> qualifiers, Class<? extends Annotation> qualifierType) {
        return qualifiers.stream().anyMatch(q -> q.annotationType().equals(qualifierType));
    }

}
