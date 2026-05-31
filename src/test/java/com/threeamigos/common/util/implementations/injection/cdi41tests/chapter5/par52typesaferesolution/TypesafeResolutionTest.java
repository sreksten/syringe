package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguousresolutionbean.AmbiguousInterface;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguityresolution.highestpriority.ConsumerBeanHighestPriority;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguityresolution.highestpriority.HighPriorityAlternativeService;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguityresolution.nonalternativeelimination.ConsumerBeanNonAlternativeElimination;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguityresolution.nonalternativeelimination.ProducedByAlternativeProducerService;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguityresolution.producerpriority.ConsumerBeanProducerPriority;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguityresolution.producerpriority.DeclaringPriorityAlternativeProducer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguityresolution.producerpriority.MethodPriorityAlternativeProducer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguityresolution.producerpriority.ProducedByMethodPriorityService;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.ambiguityresolution.producerpriority.ResolutionServiceProducerPriority;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.assignability.parameterizedexact.StringBoxConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.assignability.parameterizedtoraw.ObjectRepositoryRawRequiredConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.qualifierannotationswithmembers.bindingandnonbinding.ChequePaymentConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.qualifierannotationswithmembers.bindingandnonbinding.ChequePaymentProcessor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.qualifierannotationswithmembers.bindingandnonbinding.CreditCardPaymentConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.qualifierannotationswithmembers.bindingandnonbinding.CreditCardPaymentProcessor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.qualifierannotationswithmembers.bindingandnonbinding.NonbindingCommentConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.multiplequalifiers.ChequeOnlyProcessor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.multiplequalifiers.ChequeSynchronousProcessor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.multiplequalifiers.PayByChequeOnlyConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.multiplequalifiers.SynchronousChequeConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.multiplequalifiers.SynchronousOnlyConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.multiplequalifiers.SynchronousOnlyProcessor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.qualifierannotationswithmembers.nonportableannotationmember.AnnotationMemberNonPortableAnchorBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.qualifierannotationswithmembers.nonportablearraymember.ArrayMemberNonPortableAnchorBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.primitiveandnullvalues.primitivewrapperequivalence.PrimitiveWrapperEquivalenceConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.primitiveandnullvalues.wrapperfromprimitive.WrapperFromPrimitiveConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.primitiveandnullvalues.nulltoprimitivedefault.NullToPrimitiveDefaultConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.assignability.specexamples.UserDaoParameterizedConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.legalinjectionpointtypes.TypeVariableInjectionPointBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.legalinjectionpointtypes.WildcardInjectionConsumerBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.unresolvablebean.UnresolvableInterface;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.messagehandler.ConsoleMessageHandler;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.Bean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName( "5.2 - Typesafe resolution tests")
public class TypesafeResolutionTest {

    @Test
    @DisplayName("5.2 - Should find an unresolvable dependency")
    void shouldFindAnUnresolvableDependency() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), UnresolvableInterface.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        assertThrows(DeploymentException.class, syringe::setup);
        assertTrue(
                syringe.getKnowledgeBase().getInjectionErrors().stream()
                        .anyMatch(error -> error.contains("unsatisfied dependency - no bean found")),
                "Expected unsatisfied programmatic lookup to be registered as injection error"
        );
    }

    @Test
    @DisplayName("5.2 - Should find an ambiguous dependency")
    void shouldFindAnAmbiguousDependency() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), AmbiguousInterface.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        assertThrows(DeploymentException.class, syringe::setup);
        assertTrue(
                syringe.getKnowledgeBase().getInjectionErrors().stream()
                        .anyMatch(error -> error.contains("ambiguous dependency - multiple beans found for type")),
                "Expected ambiguous programmatic lookup to be registered as injection error"
        );
    }

    @Test
    @DisplayName("5.2.2 - Producer of alternative is retained while non-alternatives are eliminated")
    void shouldResolveByEliminatingNonAlternativesAndKeepingAlternativeProducer() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ConsumerBeanNonAlternativeElimination.class);

        assertDoesNotThrow(syringe::setup);
        ConsumerBeanNonAlternativeElimination consumer = syringe.inject(ConsumerBeanNonAlternativeElimination.class);
        assertInstanceOf(ProducedByAlternativeProducerService.class, consumer.getService());
    }

    @Test
    @DisplayName("5.2.2 - Highest priority alternative wins")
    void shouldResolveAmbiguityUsingHighestPriorityAlternative() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ConsumerBeanHighestPriority.class);

        assertDoesNotThrow(syringe::setup);
        ConsumerBeanHighestPriority consumer = syringe.inject(ConsumerBeanHighestPriority.class);
        assertInstanceOf(HighPriorityAlternativeService.class, consumer.getService());
    }

    @Test
    @DisplayName("5.2.2 - Producer method priority takes precedence over declaring alternative priority")
    void shouldUseProducerMemberPriorityBeforeDeclaringBeanPriority() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ConsumerBeanProducerPriority.class);
        syringe.enableAlternative(DeclaringPriorityAlternativeProducer.class);
        syringe.enableAlternative(MethodPriorityAlternativeProducer.class);

        assertDoesNotThrow(syringe::setup);

        Set<Bean<?>> candidates = syringe.getBeanManager().getBeans(ResolutionServiceProducerPriority.class);
        @SuppressWarnings("unchecked")
        Bean<ResolutionServiceProducerPriority> resolvedBean =
                (Bean<ResolutionServiceProducerPriority>) syringe.getBeanManager().resolve((Set) candidates);
        CreationalContext<ResolutionServiceProducerPriority> creationalContext =
                syringe.getBeanManager().createCreationalContext(resolvedBean);
        ResolutionServiceProducerPriority resolvedInstance =
                (ResolutionServiceProducerPriority) syringe.getBeanManager()
                        .getReference(resolvedBean, ResolutionServiceProducerPriority.class, creationalContext);

        assertInstanceOf(ProducedByMethodPriorityService.class, resolvedInstance);
    }

    @Test
    @DisplayName("5.2.3 - Injection point type may contain wildcard type parameter")
    void shouldAllowWildcardInjectionPointTypeParameter() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), WildcardInjectionConsumerBean.class);
        syringe.exclude(TypeVariableInjectionPointBean.class);

        assertDoesNotThrow(syringe::setup);
        WildcardInjectionConsumerBean consumer = syringe.inject(WildcardInjectionConsumerBean.class);
        assertNotNull(consumer.getValues());
    }

    @Test
    @DisplayName("5.2.3 - Injection point type variable is a definition error")
    void shouldTreatTypeVariableInjectionPointAsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), TypeVariableInjectionPointBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.2.4 - UserDao is assignable to Dao<User>, Dao<?>, Dao<? extends Persistent>, Dao<? extends User>")
    void shouldAssignUserDaoToSupportedParameterizedRequiredTypes() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), UserDaoParameterizedConsumer.class);
        syringe.exclude(
                com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par52typesaferesolution.assignability.specexamples.Dao.class
        );

        assertDoesNotThrow(syringe::setup);
        UserDaoParameterizedConsumer consumer = syringe.inject(UserDaoParameterizedConsumer.class);
        assertNotNull(consumer.getUserDao());
        assertNotNull(consumer.getAnyDao());
        assertNotNull(consumer.getPersistentDao());
        assertNotNull(consumer.getExtendsUserDao());
    }

    @Test
    @DisplayName("5.2.4 - Parameterized bean type with identical actual type is assignable to parameterized required type")
    void shouldAssignParameterizedBeanTypeToMatchingParameterizedRequiredType() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), StringBoxConsumer.class);

        assertDoesNotThrow(syringe::setup);
        StringBoxConsumer consumer = syringe.inject(StringBoxConsumer.class);
        assertNotNull(consumer.getBox());
    }

    @Test
    @DisplayName("5.2.4 - Parameterized bean type is assignable to raw required type when parameter is Object")
    void shouldAssignParameterizedBeanTypeToRawRequiredTypeWhenParameterIsObject() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ObjectRepositoryRawRequiredConsumer.class);

        assertDoesNotThrow(syringe::setup);
        ObjectRepositoryRawRequiredConsumer consumer = syringe.inject(ObjectRepositoryRawRequiredConsumer.class);
        assertNotNull(consumer.getRepository());
    }

    @Test
    @DisplayName("5.2.5 - Primitive and wrapper types are considered identical and assignable")
    void shouldResolvePrimitiveAndWrapperTypesAsAssignable() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), PrimitiveWrapperEquivalenceConsumer.class);

        assertDoesNotThrow(syringe::setup);
        PrimitiveWrapperEquivalenceConsumer consumer = syringe.inject(PrimitiveWrapperEquivalenceConsumer.class);
        assertEquals(42, consumer.getPrimitiveValue());
        assertEquals(42, consumer.getWrapperValue());
    }

    @Test
    @DisplayName("5.2.5 - Container performs boxing/unboxing for primitive and wrapper injection points")
    void shouldPerformBoxingAndUnboxingBetweenPrimitiveAndWrapperTypes() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), WrapperFromPrimitiveConsumer.class);

        assertDoesNotThrow(syringe::setup);
        WrapperFromPrimitiveConsumer consumer = syringe.inject(WrapperFromPrimitiveConsumer.class);
        assertEquals(73, consumer.getWrapperValue());
    }

    @Test
    @DisplayName("5.2.5 - Null producer value injected into primitive uses default primitive value")
    void shouldInjectDefaultPrimitiveValueWhenProducerReturnsNull() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NullToPrimitiveDefaultConsumer.class);

        assertDoesNotThrow(syringe::setup);
        NullToPrimitiveDefaultConsumer consumer = syringe.inject(NullToPrimitiveDefaultConsumer.class);
        assertEquals(0, consumer.getPrimitiveIntValue());
    }

    @Test
    @DisplayName("5.2.6 - Qualifier member value selects the matching bean")
    void shouldSelectBeanUsingQualifierMemberValue() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ChequePaymentConsumer.class);

        assertDoesNotThrow(syringe::setup);
        ChequePaymentConsumer consumer = syringe.inject(ChequePaymentConsumer.class);
        assertInstanceOf(ChequePaymentProcessor.class, consumer.getPaymentProcessor());
    }

    @Test
    @DisplayName("5.2.6 - Different qualifier member value selects a different bean")
    void shouldSelectDifferentBeanForDifferentQualifierMemberValue() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), CreditCardPaymentConsumer.class);

        assertDoesNotThrow(syringe::setup);
        CreditCardPaymentConsumer consumer = syringe.inject(CreditCardPaymentConsumer.class);
        assertInstanceOf(CreditCardPaymentProcessor.class, consumer.getPaymentProcessor());
    }

    @Test
    @DisplayName("5.2.6 - @Nonbinding qualifier member is ignored during matching")
    void shouldIgnoreNonbindingQualifierMemberWhenMatching() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonbindingCommentConsumer.class);

        assertDoesNotThrow(syringe::setup);
        NonbindingCommentConsumer consumer = syringe.inject(NonbindingCommentConsumer.class);
        assertInstanceOf(ChequePaymentProcessor.class, consumer.getPaymentProcessor());
    }

    @Test
    @DisplayName("5.2.6 - Array-valued qualifier member without @Nonbinding is non-portable")
    void shouldFailForArrayValuedQualifierMemberWithoutNonbinding() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ArrayMemberNonPortableAnchorBean.class);

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.2.6 - Annotation-valued qualifier member without @Nonbinding is non-portable")
    void shouldFailForAnnotationValuedQualifierMemberWithoutNonbinding() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), AnnotationMemberNonPortableAnchorBean.class);

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.2.7 - Bean with multiple qualifiers is candidate for injection point with one qualifier (@PayBy)")
    void shouldMatchMultipleQualifierBeanWhenInjectionPointDeclaresPayByOnly() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), PayByChequeOnlyConsumer.class);
        syringe.exclude(ChequeOnlyProcessor.class);
        syringe.exclude(SynchronousOnlyProcessor.class);

        assertDoesNotThrow(syringe::setup);
        PayByChequeOnlyConsumer consumer = syringe.inject(PayByChequeOnlyConsumer.class);
        assertInstanceOf(ChequeSynchronousProcessor.class, consumer.getPaymentProcessor());
    }

    @Test
    @DisplayName("5.2.7 - Bean with multiple qualifiers is candidate for injection point with one qualifier (@Synchronous)")
    void shouldMatchMultipleQualifierBeanWhenInjectionPointDeclaresSynchronousOnly() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), SynchronousOnlyConsumer.class);
        syringe.exclude(ChequeOnlyProcessor.class);
        syringe.exclude(SynchronousOnlyProcessor.class);

        assertDoesNotThrow(syringe::setup);
        SynchronousOnlyConsumer consumer = syringe.inject(SynchronousOnlyConsumer.class);
        assertInstanceOf(ChequeSynchronousProcessor.class, consumer.getPaymentProcessor());
    }

    @Test
    @DisplayName("5.2.7 - Bean must declare all qualifiers required by injection point")
    void shouldRequireBeanToDeclareAllQualifiersRequiredByInjectionPoint() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), SynchronousChequeConsumer.class);
        syringe.exclude(ChequeSynchronousProcessor.class);

        assertThrows(DeploymentException.class, syringe::setup);
        assertTrue(
                syringe.getKnowledgeBase().getInjectionErrors().stream()
                        .anyMatch(error -> error.contains("unsatisfied dependency - no bean found")),
                "Expected unsatisfied dependency because no bean declares both qualifiers"
        );
    }

}
