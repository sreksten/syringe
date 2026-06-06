package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par562builtininstance;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.AlternativeIteratorCandidate;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.DefaultPaymentProcessor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.HandleLazyBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.PlainIteratorCandidateA;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.PlainIteratorCandidateB;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.ProgrammaticLookupConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.ProgrammaticLookupIteratorConsumer;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.RawInstanceInjectionPointBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par561instanceinterface.SpecialPaymentProcessor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par56programmaticlookup.par563annotationliteralandtypeliteral.LiteralSelectionConsumer;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("5.6.2 - The built-in Instance")
public class BuiltInInstanceTest {

    @Test
    @DisplayName("5.6.2 - Built-in Instance/Provider is injectable for legal bean type with any qualifiers")
    void shouldInjectBuiltInInstanceAndProviderForLegalTypeAndQualifiers() {
        Syringe syringe = createBuiltInInstanceSyringe();
        BuiltInInstanceConsumer consumer = syringe.inject(BuiltInInstanceConsumer.class);

        assertTrue(consumer.hasAllBuiltInLookupsInjected());
        assertEquals("default", consumer.defaultInstanceValue());
        assertEquals("default", consumer.defaultProviderValue());
        assertEquals("special", consumer.specialInstanceValue());
        assertEquals("special", consumer.specialProviderValue());
    }

    @Test
    @DisplayName("5.6.2 - Built-in Instance bean has @Dependent scope semantics")
    void shouldExposeDependentScopeSemanticsForBuiltInInstanceBean() {
        Syringe syringe = createBuiltInInstanceSyringe();
        BuiltInInstanceConsumer consumer = syringe.inject(BuiltInInstanceConsumer.class);
        assertTrue(consumer.dependentBuiltInBeanScopeLooksDependent());
    }

    private Syringe createBuiltInInstanceSyringe() {
        Syringe syringe = new Syringe(
                new InMemoryMessageHandler(),
                BuiltInInstanceConsumer.class,
                DefaultPaymentProcessor.class,
                SpecialPaymentProcessor.class,
                HandleLazyBean.class
        );
        syringe.exclude(RawInstanceInjectionPointBean.class);
        syringe.exclude(ProgrammaticLookupConsumer.class);
        syringe.exclude(ProgrammaticLookupIteratorConsumer.class);
        syringe.exclude(LiteralSelectionConsumer.class);
        syringe.exclude(PlainIteratorCandidateA.class);
        syringe.exclude(PlainIteratorCandidateB.class);
        syringe.exclude(AlternativeIteratorCandidate.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }
}
