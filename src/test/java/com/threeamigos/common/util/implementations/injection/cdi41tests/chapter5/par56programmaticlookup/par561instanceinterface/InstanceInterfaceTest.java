package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par562builtininstance.BuiltInInstanceConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par563annotationliteralandtypeliteral.CardGenericPaymentProcessor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par563annotationliteralandtypeliteral.LiteralSelectionConsumer;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.resolution.InstanceImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("5.6.1 - The Instance interface")
public class InstanceInterfaceTest {

    @Test
    @DisplayName("5.6.1 - select() creates child Instance and applies implicit @Default before typesafe resolution")
    void shouldCreateChildInstanceAndApplyDefaultQualifierRules() {
        Syringe syringe = createProgrammaticLookupSyringe();
        ProgrammaticLookupConsumer consumer = syringe.inject(ProgrammaticLookupConsumer.class);

        assertEquals("default", consumer.resolveDefaultFromParent());
        assertEquals("special", consumer.resolveSpecialFromAnyChildSelect());
        assertTrue(consumer.isSpecialUnsatisfiedWhenSelectedFromDefaultParent());
    }

    @Test
    @DisplayName("5.6.1 - Raw Instance injection point is a definition error")
    void shouldRejectRawInstanceInjectionPoint() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), RawInstanceInjectionPointBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("5.6.1 - select() with duplicate non-repeating qualifier type throws IllegalArgumentException")
    void shouldRejectDuplicateNonRepeatingQualifierInSelect() {
        Syringe syringe = createProgrammaticLookupSyringe();
        ProgrammaticLookupConsumer consumer = syringe.inject(ProgrammaticLookupConsumer.class);
        assertThrows(IllegalArgumentException.class, consumer::selectWithDuplicateNonRepeatingQualifier);
    }

    @Test
    @DisplayName("5.6.1 - select() with non-qualifier annotation throws IllegalArgumentException")
    void shouldRejectNonQualifierAnnotationInSelect() {
        Syringe syringe = createProgrammaticLookupSyringe();
        ProgrammaticLookupConsumer consumer = syringe.inject(ProgrammaticLookupConsumer.class);
        assertThrows(IllegalArgumentException.class, consumer::selectWithNonQualifierAnnotation);
    }

    @Test
    @DisplayName("5.6.1 - get() throws AmbiguousResolutionException for unresolvable ambiguity")
    void shouldThrowAmbiguousResolutionExceptionWhenGetIsAmbiguous() {
        Syringe syringe = createProgrammaticLookupSyringe();
        ProgrammaticLookupConsumer consumer = syringe.inject(ProgrammaticLookupConsumer.class);
        assertThrows(AmbiguousResolutionException.class, consumer::getFromAmbiguousAnyInstance);
    }

    @Test
    @DisplayName("5.6.1 - get() throws UnsatisfiedResolutionException for unsatisfied dependency")
    void shouldThrowUnsatisfiedResolutionExceptionWhenGetIsUnsatisfied() {
        Syringe syringe = createProgrammaticLookupSyringe();
        ProgrammaticLookupConsumer consumer = syringe.inject(ProgrammaticLookupConsumer.class);
        assertThrows(UnsatisfiedResolutionException.class, consumer::getFromUnsatisfiedChildInstance);
    }

    @Test
    @DisplayName("5.6.1 - isUnsatisfied(), isAmbiguous() and isResolvable() report resolution state correctly")
    void shouldReportUnsatisfiedAmbiguousAndResolvableStates() {
        Syringe syringe = createProgrammaticLookupSyringe();
        ProgrammaticLookupConsumer consumer = syringe.inject(ProgrammaticLookupConsumer.class);

        assertTrue(consumer.isUnsatisfiedForMissingQualifier());
        assertTrue(consumer.isAmbiguousForAnyProcessors());
        assertTrue(!consumer.isResolvableForAnyProcessors());
        assertTrue(!consumer.isUnsatisfiedForDefaultProcessors());
        assertTrue(consumer.isResolvableForDefaultProcessors());
    }

    @Test
    @DisplayName("5.6.1 - iterator() returns all candidate contextual references when ambiguous and no alternatives exist")
    void shouldIterateAllCandidatesWhenAmbiguousWithoutAlternatives() {
        Syringe syringe = createIteratorSyringeWithoutAlternatives();
        ProgrammaticLookupIteratorConsumer consumer = syringe.inject(ProgrammaticLookupIteratorConsumer.class);

        java.util.List<String> ids = consumer.iterateAnyCandidateIds();
        assertEquals(2, ids.size());
        assertTrue(ids.contains("plain-a"));
        assertTrue(ids.contains("plain-b"));
    }

    @Test
    @DisplayName("5.6.1 - iterator() returns empty set for unsatisfied dependency")
    void shouldReturnEmptyIteratorWhenUnsatisfied() {
        Syringe syringe = createIteratorSyringeWithoutAlternatives();
        ProgrammaticLookupIteratorConsumer consumer = syringe.inject(ProgrammaticLookupIteratorConsumer.class);
        assertEquals(0, consumer.iterateUnsatisfiedCandidateCount());
    }

    @Test
    @DisplayName("5.6.1 - iterator() applies ambiguity elimination and keeps non-eliminated alternatives")
    void shouldIterateOnlyNonEliminatedAlternativesWhenAlternativesPresent() {
        Syringe syringe = createIteratorSyringeWithAlternatives();
        ProgrammaticLookupIteratorConsumer consumer = syringe.inject(ProgrammaticLookupIteratorConsumer.class);

        java.util.List<String> ids = consumer.iterateAnyCandidateIds();
        assertEquals(1, ids.size());
        assertEquals("alt", ids.get(0));
    }

    @Test
    @DisplayName("5.6.1 - stream() returns all candidate contextual references when ambiguous and no alternatives exist")
    void shouldStreamAllCandidatesWhenAmbiguousWithoutAlternatives() {
        Syringe syringe = createIteratorSyringeWithoutAlternatives();
        ProgrammaticLookupIteratorConsumer consumer = syringe.inject(ProgrammaticLookupIteratorConsumer.class);

        java.util.List<String> ids = consumer.streamAnyCandidateIds();
        assertEquals(2, ids.size());
        assertTrue(ids.contains("plain-a"));
        assertTrue(ids.contains("plain-b"));
    }

    @Test
    @DisplayName("5.6.1 - stream() returns empty set for unsatisfied dependency")
    void shouldReturnEmptyStreamWhenUnsatisfied() {
        Syringe syringe = createIteratorSyringeWithoutAlternatives();
        ProgrammaticLookupIteratorConsumer consumer = syringe.inject(ProgrammaticLookupIteratorConsumer.class);
        assertEquals(0L, consumer.streamUnsatisfiedCandidateCount());
    }

    @Test
    @DisplayName("5.6.1 - stream() applies ambiguity elimination and streams non-eliminated alternatives")
    void shouldStreamOnlyNonEliminatedAlternativesWhenAlternativesPresent() {
        Syringe syringe = createIteratorSyringeWithAlternatives();
        ProgrammaticLookupIteratorConsumer consumer = syringe.inject(ProgrammaticLookupIteratorConsumer.class);

        java.util.List<String> ids = consumer.streamAnyCandidateIds();
        assertEquals(1, ids.size());
        assertEquals("alt", ids.get(0));
    }

    @Test
    @DisplayName("5.6.1 - handles() iterates contextual reference handles for all beans produced by iterator()/stream()")
    void shouldReturnHandlesForAllBeansProducedByIteratorAndStream() {
        Syringe syringe = createIteratorSyringeWithoutAlternatives();
        ProgrammaticLookupIteratorConsumer consumer = syringe.inject(ProgrammaticLookupIteratorConsumer.class);

        java.util.List<String> iteratorIds = consumer.iterateAnyCandidateIds();
        java.util.List<String> streamIds = consumer.streamAnyCandidateIds();
        java.util.List<String> handleIds = consumer.handleAnyCandidateIds();

        assertEquals(iteratorIds.size(), handleIds.size());
        assertTrue(handleIds.containsAll(iteratorIds));
        assertTrue(handleIds.containsAll(streamIds));
    }

    @Test
    @DisplayName("5.6.1 - handles() returns stateless Iterable: each iterator() yields a new handle set")
    void shouldProvideStatelessHandlesIterable() {
        Syringe syringe = createIteratorSyringeWithoutAlternatives();
        ProgrammaticLookupIteratorConsumer consumer = syringe.inject(ProgrammaticLookupIteratorConsumer.class);
        assertTrue(consumer.handlesIterableCreatesNewHandleSetPerIteratorCall());
    }

    @Test
    @DisplayName("5.6.1 - handlesStream() is a Stream equivalent of handles()")
    void shouldProvideHandlesStreamEquivalentToHandles() {
        Syringe syringe = createIteratorSyringeWithoutAlternatives();
        ProgrammaticLookupIteratorConsumer consumer = syringe.inject(ProgrammaticLookupIteratorConsumer.class);

        java.util.List<String> handlesIds = consumer.handleAnyCandidateIds();
        java.util.List<String> handlesStreamIds = consumer.handleStreamAnyCandidateIds();

        assertEquals(handlesIds.size(), handlesStreamIds.size());
        assertTrue(handlesIds.containsAll(handlesStreamIds));
        assertTrue(handlesStreamIds.containsAll(handlesIds));
    }

    @Test
    @DisplayName("5.6.1 - destroy() destroys dependent instance obtained from the same Instance")
    void shouldDestroyDependentInstanceFromSameInstance() {
        DependentDestroyableBean.reset();
        Syringe syringe = createProgrammaticLookupSyringe();
        ProgrammaticLookupConsumer consumer = syringe.inject(ProgrammaticLookupConsumer.class);
        assertEquals(1, consumer.destroyDependentInstanceAndGetPreDestroyCalls());
    }

    @Test
    @DisplayName("5.6.1 - destroy() accepts normal scoped client proxy instance")
    void shouldAcceptDestroyForNormalScopedProxy() {
        Syringe syringe = createProgrammaticLookupSyringe();
        ProgrammaticLookupConsumer consumer = syringe.inject(ProgrammaticLookupConsumer.class);
        consumer.destroyNormalScopedProxy();
    }

    @Test
    @DisplayName("5.6.1 - destroy() propagates UnsupportedOperationException when context does not support destroy")
    void shouldThrowUnsupportedOperationWhenDestroyNotSupported() {
        InstanceImpl<Object> instance = new InstanceImpl<>(
                Object.class,
                java.util.Collections.<java.lang.annotation.Annotation>emptyList(),
                new InstanceImpl.ResolutionStrategy<Object>() {
                    @Override
                    public Object resolveInstance(Class<Object> type, java.util.Collection<java.lang.annotation.Annotation> qualifiers) {
                        return new Object();
                    }

                    @Override
                    public java.util.Collection<Class<? extends Object>> resolveImplementations(
                            Class<Object> type,
                            java.util.Collection<java.lang.annotation.Annotation> qualifiers
                    ) {
                        return java.util.Collections.<Class<? extends Object>>singletonList(Object.class);
                    }

                    @Override
                    public void invokePreDestroy(Object instance) {
                        throw new UnsupportedOperationException("Destroy not supported");
                    }
                }
        );

        assertThrows(UnsupportedOperationException.class, () -> instance.destroy(new Object()));
    }

    @Test
    @DisplayName("5.6.1 - getHandle() throws UnsatisfiedResolutionException when no bean matches")
    void shouldThrowUnsatisfiedResolutionExceptionForGetHandle() {
        Syringe syringe = createProgrammaticLookupSyringe();
        ProgrammaticLookupConsumer consumer = syringe.inject(ProgrammaticLookupConsumer.class);
        assertThrows(UnsatisfiedResolutionException.class, consumer::getUnsatisfiedHandle);
    }

    @Test
    @DisplayName("5.6.1 - getHandle() throws AmbiguousResolutionException when more than one bean matches")
    void shouldThrowAmbiguousResolutionExceptionForGetHandle() {
        Syringe syringe = createProgrammaticLookupSyringe();
        ProgrammaticLookupConsumer consumer = syringe.inject(ProgrammaticLookupConsumer.class);
        assertThrows(AmbiguousResolutionException.class, consumer::getAmbiguousHandle);
    }

    private Syringe createProgrammaticLookupSyringe() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ProgrammaticLookupConsumer.class);
        syringe.exclude(RawInstanceInjectionPointBean.class);
        syringe.exclude(BuiltInInstanceConsumer.class);
        syringe.exclude(ProgrammaticLookupIteratorConsumer.class);
        syringe.exclude(PlainIteratorCandidateA.class);
        syringe.exclude(PlainIteratorCandidateB.class);
        syringe.exclude(AlternativeIteratorCandidate.class);
        syringe.exclude(LiteralSelectionConsumer.class);
        syringe.exclude(CardGenericPaymentProcessor.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    private Syringe createIteratorSyringeWithoutAlternatives() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ProgrammaticLookupIteratorConsumer.class);
        syringe.exclude(RawInstanceInjectionPointBean.class);
        syringe.exclude(ProgrammaticLookupConsumer.class);
        syringe.exclude(BuiltInInstanceConsumer.class);
        syringe.exclude(LiteralSelectionConsumer.class);
        syringe.exclude(CardGenericPaymentProcessor.class);
        syringe.exclude(AlternativeIteratorCandidate.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    private Syringe createIteratorSyringeWithAlternatives() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ProgrammaticLookupIteratorConsumer.class);
        syringe.exclude(RawInstanceInjectionPointBean.class);
        syringe.exclude(ProgrammaticLookupConsumer.class);
        syringe.exclude(BuiltInInstanceConsumer.class);
        syringe.exclude(LiteralSelectionConsumer.class);
        syringe.exclude(CardGenericPaymentProcessor.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }
}
