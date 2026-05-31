package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par24scopes;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par24nonportableinterceptor.NonPortableScopedInterceptor;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.par24nonportablescope.NonPortableScopeAnchorBean;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.spi.Bean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.enterprise.inject.spi.DefinitionException;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("2.4 - Scopes")
public class ScopesTest {

    @Test
    @DisplayName("2.4.1 - Built-in scope types")
    public void testBuiltInScopeTypes() {
        // Given
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ScopesTest.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        // The InvalidSCopedObject has two scope annotations, that is not permitted.
        syringe.exclude(InvalidScopedObject.class);
        // This class intentionally violates 2.4.4 and is tested separately.
        syringe.exclude(ConflictingStereotypesScopedObject.class);
        syringe.setup();
        // When
        ObjectsConsumer consumer = syringe.inject(ObjectsConsumer.class);
        // Then
        assertNotNull(consumer.getApplicationScopedObject());
        assertNotNull(consumer.getSessionScopedObject());
        assertNotNull(consumer.getRequestScopedObject());
        assertNotNull(consumer.getDependentObject());
    }

    @Test
    @DisplayName("2.4.3 - Bean Scope may specify at most one scope type annotation")
    public void testBeanScopeMaySpecifyAtMostOneScopeTypeAnnotation() {
        // Given
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ScopesTest.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        // Isolate this test from 2.4.4 conflict fixtures.
        syringe.exclude(ConflictingStereotypesScopedObject.class);
        // The unfiltered InvalidScopedObject has two scope annotations, that is not permitted.
        // When / Then
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("2.4.4 - Bean without explicit scope and without stereotype default scope is @Dependent")
    public void testDefaultScopeIsDependentWhenNoScopeOrDefaultStereotype() {
        // Given
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ScopesTest.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(InvalidScopedObject.class);
        syringe.exclude(ConflictingStereotypesScopedObject.class);
        syringe.setup();
        // When
        Class<? extends Annotation> actualScope = getBeanScope(syringe, NoScopeNoStereotypeObject.class);
        // Then
        assertEquals(Dependent.class, actualScope);
    }

    @Test
    @DisplayName("2.4.4 - Bean inherits default scope when all stereotype default scopes are identical")
    public void testBeanInheritsIdenticalDefaultScopeFromStereotypes() {
        // Given
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ScopesTest.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(InvalidScopedObject.class);
        syringe.exclude(ConflictingStereotypesScopedObject.class);
        syringe.setup();
        // When
        Class<? extends Annotation> actualScope = getBeanScope(syringe, SessionScopedByStereotypesObject.class);
        // Then
        assertEquals(SessionScoped.class, actualScope);
    }

    @Test
    @DisplayName("2.4.4 - Bean with conflicting stereotype default scopes and no explicit scope is a definition error")
    public void testConflictingDefaultScopesFromStereotypesCauseDefinitionError() {
        // Given
        InMemoryMessageHandler messageHandler = new InMemoryMessageHandler();
        Syringe syringe = new Syringe(messageHandler, ScopesTest.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        // Keep InvalidScopedObject excluded to isolate this specific 2.4.4 failure.
        // ConflictingStereotypesScopedObject will produce a definition error.
        syringe.exclude(InvalidScopedObject.class);
        // When / Then
        assertThrows(DefinitionException.class, syringe::setup);
        assertTrue(messageHandler.getLastMessage().contains("ConflictingStereotypesScopedObject: conflicting scopes inherited from stereotypes"));
    }

    @Test
    @DisplayName("2.4.4 - Explicit bean scope overrides stereotype default scopes")
    public void testExplicitScopeOverridesStereotypeDefaultScope() {
        // Given
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ScopesTest.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(InvalidScopedObject.class);
        syringe.exclude(ConflictingStereotypesScopedObject.class);
        syringe.setup();
        // When
        Class<? extends Annotation> actualScope = getBeanScope(syringe, ExplicitScopeOverridesStereotypeObject.class);
        // Then
        assertEquals(Dependent.class, actualScope);
    }

    @Test
    @DisplayName("2.4.4 - Producer method and field without explicit scope default to @Dependent")
    public void testProducerMethodAndFieldDefaultToDependentScope() {
        // Given
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ScopesTest.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(InvalidScopedObject.class);
        syringe.exclude(ConflictingStereotypesScopedObject.class);
        syringe.setup();
        // Then
        // Those are produced by DefaultScopeProducerFactory
        assertProducerBeanScope(syringe, MethodProducedDefaultScopeObject.class, Dependent.class);
        assertProducerBeanScope(syringe, FieldProducedDefaultScopeObject.class, Dependent.class);
    }

    @Test
    @DisplayName("2.4 - Interceptor with scope other than @Dependent fails with NonPortableBehaviourException")
    public void interceptorWithNonDependentScopeFailsWithNonPortableBehaviourException() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonPortableScopedInterceptor.class);

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    @Test
    @DisplayName("2.4.2 - Scope type with attributes fails with NonPortableBehaviourException")
    public void scopeTypeWithAttributesFailsWithNonPortableBehaviourException() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), NonPortableScopeAnchorBean.class);

        assertThrows(NonPortableBehaviourException.class, syringe::setup);
    }

    private Class<? extends Annotation> getBeanScope(Syringe syringe, Class<?> beanClass) {
        Bean<?> bean = syringe.getKnowledgeBase().getBeans().stream()
                .filter(candidate -> candidate.getBeanClass().equals(beanClass))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Bean not found: " + beanClass.getName()));
        return bean.getScope();
    }

    private void assertProducerBeanScope(Syringe syringe,
                                         Class<?> producedType,
                                         Class<? extends Annotation> expectedScope) {
        Bean<?> producerBean = syringe.getKnowledgeBase().getBeans().stream()
                .filter(candidate -> candidate.getBeanClass().equals(DefaultScopeProducerFactory.class))
                .filter(candidate -> candidate.getTypes().contains(producedType))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Producer bean not found for type: " + producedType.getName()));
        assertEquals(expectedScope, producerBean.getScope());
    }

}
