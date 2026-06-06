package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par563annotationliteralandtypeliteral;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.AlternativeIteratorCandidate;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.PlainIteratorCandidateA;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.PlainIteratorCandidateB;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.ProgrammaticLookupConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.ProgrammaticLookupIteratorConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.RawInstanceInjectionPointBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par562builtininstance.BuiltInInstanceConsumer;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("5.6.3 - Using AnnotationLiteral and TypeLiteral")
public class AnnotationLiteralAndTypeLiteralTest {

    @Test
    @DisplayName("5.6.3 - AnnotationLiteral simplifies select() with qualifier members")
    void shouldSelectUsingAnnotationLiteralQualifiers() {
        Syringe syringe = createLiteralSelectionSyringe();
        LiteralSelectionConsumer consumer = syringe.inject(LiteralSelectionConsumer.class);

        assertEquals("sync-cheque", consumer.getSynchronousPaymentProcessor(PaymentMethod.CHEQUE));
    }

    @Test
    @DisplayName("5.6.3 - TypeLiteral simplifies select() with parameterized type and actual type arguments")
    void shouldSelectUsingTypeLiteralForParameterizedType() {
        Syringe syringe = createLiteralSelectionSyringe();
        LiteralSelectionConsumer consumer = syringe.inject(LiteralSelectionConsumer.class);

        assertEquals("generic-cheque", consumer.getChequePaymentProcessorViaTypeLiteral());
    }

    private Syringe createLiteralSelectionSyringe() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), LiteralSelectionConsumer.class);
        syringe.exclude(RawInstanceInjectionPointBean.class);
        syringe.exclude(ProgrammaticLookupConsumer.class);
        syringe.exclude(ProgrammaticLookupIteratorConsumer.class);
        syringe.exclude(BuiltInInstanceConsumer.class);
        syringe.exclude(CardGenericPaymentProcessor.class);
        syringe.exclude(PlainIteratorCandidateA.class);
        syringe.exclude(PlainIteratorCandidateB.class);
        syringe.exclude(AlternativeIteratorCandidate.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }
}
