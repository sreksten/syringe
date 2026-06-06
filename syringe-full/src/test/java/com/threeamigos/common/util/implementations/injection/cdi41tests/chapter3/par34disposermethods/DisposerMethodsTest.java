package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet1.InvalidAbstractDisposerMethodBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet1.ValidVisibilityAndStaticDisposerMethodsBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet2.InvalidMultipleDisposesParametersBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet3.InvalidUnmatchedDisposerTypeBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet4.AssignableProducerMethodDisposerBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet5.QualifierAwareDisposerBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet6.ProducerFieldDisposerBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet7.MultiProducerSingleDisposerBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet8.InvalidInjectDisposerMethodBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet8.InvalidObservesAsyncParameterDisposerMethodBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet8.InvalidObservesParameterDisposerMethodBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet8.InvalidProducesDisposerMethodBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet9.InvalidInterceptorDisposerMethod;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet10.AdditionalParametersDisposerMethodBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet11.InvalidMultipleDisposersForSingleProducerFieldBean;
import com.threeamigos.common.util.implementations.injection.cdi41tests.chapter3.par34disposermethods.bullet11.InvalidMultipleDisposersForSingleProducerMethodBean;
import com.threeamigos.common.util.implementations.injection.resolution.ProducerBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("3.4 - Disposer Methods")
public class DisposerMethodsTest {

    @Test
    @DisplayName("3.4 - Abstract disposer method is a definition error")
    void abstractDisposerMethodIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidAbstractDisposerMethodBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.4 - Disposer method may have any Java access modifier and may be static or non-static")
    void disposerMethodVisibilityAndStaticnessAreAllowed() {
        InMemoryMessageHandler messageHandler = new InMemoryMessageHandler();
        Syringe syringe = new Syringe(messageHandler, ValidVisibilityAndStaticDisposerMethodsBean.class);
        syringe.exclude(InvalidAbstractDisposerMethodBean.class);

        assertDoesNotThrow(syringe::setup, () ->
                "Unexpected deployment errors: " + String.join(" | ", messageHandler.getAllErrorMessages()));

        assertNotNull(findProducerBeanByDeclaringClassAndMethodName(
                syringe, ValidVisibilityAndStaticDisposerMethodsBean.class, "producePackagePrivate").getDisposerMethod());
        assertNotNull(findProducerBeanByDeclaringClassAndMethodName(
                syringe, ValidVisibilityAndStaticDisposerMethodsBean.class, "producePublic").getDisposerMethod());
        assertNotNull(findProducerBeanByDeclaringClassAndMethodName(
                syringe, ValidVisibilityAndStaticDisposerMethodsBean.class, "produceProtected").getDisposerMethod());
        assertNotNull(findProducerBeanByDeclaringClassAndMethodName(
                syringe, ValidVisibilityAndStaticDisposerMethodsBean.class, "producePrivate").getDisposerMethod());
        assertNotNull(findProducerBeanByDeclaringClassAndMethodName(
                syringe, ValidVisibilityAndStaticDisposerMethodsBean.class, "produceStatic").getDisposerMethod());
        assertNotNull(findProducerBeanByDeclaringClassAndMethodName(
                syringe, ValidVisibilityAndStaticDisposerMethodsBean.class, "produceNonStatic").getDisposerMethod());
    }

    @Test
    @DisplayName("3.4.1 - Disposer method with multiple @Disposes parameters is a definition error")
    void disposerMethodWithMultipleDisposedParametersIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidMultipleDisposesParametersBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.4.3 - Disposer method with no assignable producer method or field is a definition error")
    void disposerMethodWithoutMatchingProducerTypeIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidUnmatchedDisposerTypeBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.4.1 - Disposer method with assignable disposed parameter is invoked for producer method product")
    void disposerMethodWithAssignableDisposedParameterIsInvokedForProducerMethodProduct() {
        AssignableProducerMethodDisposerBean.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), AssignableProducerMethodDisposerBean.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClassAndMethodName(
                syringe, AssignableProducerMethodDisposerBean.class, "produceProcessor");
        assertNotNull(producerBean.getDisposerMethod());

        CreationalContext<?> creationalContext = syringe.getBeanManager().createCreationalContext(producerBean);
        Object produced = producerBean.create((CreationalContext) creationalContext);
        ((ProducerBean) producerBean).destroy(produced, creationalContext);

        assertEquals(1, AssignableProducerMethodDisposerBean.disposeCount);
        assertSame(produced, AssignableProducerMethodDisposerBean.lastDisposedInstance);
    }

    @Test
    @DisplayName("3.4.1 - Disposer resolution considers disposed parameter qualifiers")
    void disposerResolutionConsidersDisposedParameterQualifiers() {
        QualifierAwareDisposerBean.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), QualifierAwareDisposerBean.class);
        syringe.setup();

        ProducerBean<?> fastProducerBean = findProducerBeanByDeclaringClassAndMethodName(
                syringe, QualifierAwareDisposerBean.class, "produceFast");
        ProducerBean<?> slowProducerBean = findProducerBeanByDeclaringClassAndMethodName(
                syringe, QualifierAwareDisposerBean.class, "produceSlow");

        CreationalContext<?> fastContext = syringe.getBeanManager().createCreationalContext(fastProducerBean);
        Object fastProduct = fastProducerBean.create((CreationalContext) fastContext);
        ((ProducerBean) fastProducerBean).destroy(fastProduct, fastContext);

        CreationalContext<?> slowContext = syringe.getBeanManager().createCreationalContext(slowProducerBean);
        Object slowProduct = slowProducerBean.create((CreationalContext) slowContext);
        ((ProducerBean) slowProducerBean).destroy(slowProduct, slowContext);

        assertEquals(1, QualifierAwareDisposerBean.fastDisposeCount);
        assertEquals(1, QualifierAwareDisposerBean.slowDisposeCount);
    }

    @Test
    @DisplayName("3.4.1 - Matching disposer method is invoked for producer field product")
    void matchingDisposerMethodIsInvokedForProducerFieldProduct() {
        ProducerFieldDisposerBean.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), ProducerFieldDisposerBean.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClassAndFieldName(
                syringe, ProducerFieldDisposerBean.class, "producedProcessor");
        assertNotNull(producerBean.getDisposerMethod());

        CreationalContext<?> creationalContext = syringe.getBeanManager().createCreationalContext(producerBean);
        Object produced = producerBean.create((CreationalContext) creationalContext);
        ((ProducerBean) producerBean).destroy(produced, creationalContext);

        assertEquals(1, ProducerFieldDisposerBean.disposeCount);
        assertSame(produced, ProducerFieldDisposerBean.lastDisposedInstance);
    }

    @Test
    @DisplayName("3.4.1 - One disposer method may resolve to multiple producer methods or fields")
    void oneDisposerMethodMayResolveToMultipleProducerMethodsOrFields() {
        MultiProducerSingleDisposerBean.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), MultiProducerSingleDisposerBean.class);
        syringe.setup();

        ProducerBean<?> methodProducerBean = findProducerBeanByDeclaringClassAndMethodName(
                syringe, MultiProducerSingleDisposerBean.class, "produceMethodProduct");
        ProducerBean<?> fieldProducerBean = findProducerBeanByDeclaringClassAndFieldName(
                syringe, MultiProducerSingleDisposerBean.class, "producedFieldProduct");

        assertNotNull(methodProducerBean.getDisposerMethod());
        assertNotNull(fieldProducerBean.getDisposerMethod());
        assertEquals(methodProducerBean.getDisposerMethod().toGenericString(),
                fieldProducerBean.getDisposerMethod().toGenericString());

        CreationalContext<?> methodContext = syringe.getBeanManager().createCreationalContext(methodProducerBean);
        Object methodProduct = methodProducerBean.create((CreationalContext) methodContext);
        ((ProducerBean) methodProducerBean).destroy(methodProduct, methodContext);

        CreationalContext<?> fieldContext = syringe.getBeanManager().createCreationalContext(fieldProducerBean);
        Object fieldProduct = fieldProducerBean.create((CreationalContext) fieldContext);
        ((ProducerBean) fieldProducerBean).destroy(fieldProduct, fieldContext);

        assertEquals(2, MultiProducerSingleDisposerBean.disposeCount);
        assertEquals(2, MultiProducerSingleDisposerBean.disposedInstances.size());
        assertTrue(MultiProducerSingleDisposerBean.disposedInstances.containsKey(methodProduct));
        assertTrue(MultiProducerSingleDisposerBean.disposedInstances.containsKey(fieldProduct));
    }

    @Test
    @DisplayName("3.4 - Disposer method annotated @Produces is a definition error")
    void disposerMethodAnnotatedProducesIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidProducesDisposerMethodBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.4 - Disposer method annotated @Inject is a definition error")
    void disposerMethodAnnotatedInjectIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidInjectDisposerMethodBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.4 - Disposer method with @Observes parameter is a definition error")
    void disposerMethodWithObservesParameterIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidObservesParameterDisposerMethodBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.4 - Disposer method with @ObservesAsync parameter is a definition error")
    void disposerMethodWithObservesAsyncParameterIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidObservesAsyncParameterDisposerMethodBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.4 - Interceptor declaring disposer method is a definition error")
    void interceptorDeclaringDisposerMethodIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidInterceptorDisposerMethod.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.4 - Disposer method additional parameters are injection points and may declare qualifiers")
    void disposerMethodAdditionalParametersAreInjectionPointsAndMayDeclareQualifiers() {
        AdditionalParametersDisposerMethodBean.reset();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), AdditionalParametersDisposerMethodBean.class);
        syringe.setup();

        ProducerBean<?> producerBean = findProducerBeanByDeclaringClassAndMethodName(
                syringe, AdditionalParametersDisposerMethodBean.class, "produceProduct");
        assertNotNull(producerBean.getDisposerMethod());

        CreationalContext<?> creationalContext = syringe.getBeanManager().createCreationalContext(producerBean);
        Object produced = producerBean.create((CreationalContext) creationalContext);
        ((ProducerBean) producerBean).destroy(produced, (CreationalContext) creationalContext);

        assertEquals(1, AdditionalParametersDisposerMethodBean.disposeCount);
        assertSame(produced, AdditionalParametersDisposerMethodBean.lastDisposedInstance);
        assertEquals("primary", AdditionalParametersDisposerMethodBean.capturedPrimaryId);
        assertEquals("secondary", AdditionalParametersDisposerMethodBean.capturedSecondaryId);
        assertEquals("plain", AdditionalParametersDisposerMethodBean.capturedPlainId);
    }

    @Test
    @DisplayName("3.4.3 - Multiple disposer methods for a single producer method is a definition error")
    void multipleDisposerMethodsForSingleProducerMethodIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidMultipleDisposersForSingleProducerMethodBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("3.4.3 - Multiple disposer methods for a single producer field is a definition error")
    void multipleDisposerMethodsForSingleProducerFieldIsDefinitionError() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidMultipleDisposersForSingleProducerFieldBean.class);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    private ProducerBean<?> findProducerBeanByDeclaringClassAndMethodName(Syringe syringe,
                                                                           Class<?> declaringClass,
                                                                           String methodName) {
        return syringe.getKnowledgeBase().getProducerBeans().stream()
                .filter(producerBean -> producerBean.getDeclaringClass().equals(declaringClass))
                .filter(producerBean -> producerBean.getProducerMethod() != null)
                .filter(producerBean -> producerBean.getProducerMethod().getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Producer bean not found for " + declaringClass.getName() + "#" + methodName));
    }

    private ProducerBean<?> findProducerBeanByDeclaringClassAndFieldName(Syringe syringe,
                                                                          Class<?> declaringClass,
                                                                          String fieldName) {
        return syringe.getKnowledgeBase().getProducerBeans().stream()
                .filter(producerBean -> producerBean.getDeclaringClass().equals(declaringClass))
                .filter(producerBean -> producerBean.getProducerField() != null)
                .filter(producerBean -> producerBean.getProducerField().getName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Producer bean not found for " + declaringClass.getName() + "#" + fieldName));
    }

}
