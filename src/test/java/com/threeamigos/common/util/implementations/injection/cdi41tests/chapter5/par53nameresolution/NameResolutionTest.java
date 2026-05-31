package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par53nameresolution;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par53nameresolution.ambiguityhighestpriority.DefaultNamedPriorityService;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par53nameresolution.ambiguityhighestpriority.HighPriorityAlternativeNamedService;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par53nameresolution.ambiguityresolvable.AlternativeNamedService;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par53nameresolution.availability.AvailableNamedBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par53nameresolution.basic.NamedBasicBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par53nameresolution.dottednameconflict.OrderBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.par53nameresolution.ambiguityunresolvable.FirstUnresolvableNamedBean;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("5.3 - Name resolution")
public class NameResolutionTest {

    @Test
    @DisplayName("5.3 - Bean resolves by bean name")
    void shouldResolveBeanByName() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NamedBasicBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        syringe.setup();

        Set<Bean<?>> beans = syringe.getBeanManager().getBeans("basicPaymentService");
        assertEquals(1, beans.size());
        Bean<?> resolved = syringe.getBeanManager().resolve((Set) beans);
        assertNotNull(resolved);
        assertEquals(NamedBasicBean.class, resolved.getBeanClass());
    }

    @Test
    @DisplayName("5.3 - Name resolution considers only beans available for injection")
    void shouldResolveOnlyAvailableBeansByName() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), AvailableNamedBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        syringe.setup();

        Set<Bean<?>> beans = syringe.getBeanManager().getBeans("availabilityService");
        assertEquals(1, beans.size());
        Bean<?> resolved = syringe.getBeanManager().resolve((Set) beans);
        assertNotNull(resolved);
        assertEquals(AvailableNamedBean.class, resolved.getBeanClass());
    }

    @Test
    @DisplayName("5.3.1 - Ambiguous name is resolvable when selected alternative remains")
    void shouldResolveAmbiguousNameUsingSelectedAlternative() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), AlternativeNamedService.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        syringe.setup();

        Set<Bean<?>> beans = syringe.getBeanManager().getBeans("resolvableByAlternativeName");
        Bean<?> resolved = syringe.getBeanManager().resolve((Set) beans);
        assertNotNull(resolved);
        assertEquals(AlternativeNamedService.class, resolved.getBeanClass());
    }

    @Test
    @DisplayName("5.3.1 - Ambiguous name resolves to highest-priority alternative")
    void shouldResolveAmbiguousNameUsingHighestPriorityAlternative() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), DefaultNamedPriorityService.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        syringe.setup();

        Set<Bean<?>> beans = syringe.getBeanManager().getBeans("resolvableByHighestPriority");
        Bean<?> resolved = syringe.getBeanManager().resolve((Set) beans);
        assertNotNull(resolved);
        assertEquals(HighPriorityAlternativeNamedService.class, resolved.getBeanClass());
    }

    @Test
    @DisplayName("5.3.1 - Unresolvable ambiguous bean name is detected at initialization")
    void shouldFailDeploymentForUnresolvableAmbiguousName() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), FirstUnresolvableNamedBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
        assertTrue(
                syringe.getKnowledgeBase().getDefinitionErrors().stream()
                        .anyMatch(error -> error.contains("Ambiguous bean name") && error.contains("unresolvableName")),
                "Expected ambiguous bean name deployment problem to be registered"
        );
    }

    @Test
    @DisplayName("5.3.1 - x and x.y name conflict is detected as deployment problem")
    void shouldFailDeploymentForDotSeparatedNameConflict() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), OrderBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
        assertTrue(
                syringe.getKnowledgeBase().getDefinitionErrors().stream()
                        .anyMatch(error -> error.contains("name conflict") && error.contains("order") && error.contains("order.item")),
                "Expected x and x.y bean name conflict to be registered"
        );
    }
}
