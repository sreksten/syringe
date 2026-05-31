package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter23.par235alternativemetadatasources.tckparity.alternativemetadata;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("23.5 - TCK parity for AlternativeMetadataTest")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class AlternativeMetadataTckParityTest {

    private static final Annotation ANY = Any.Literal.INSTANCE;

    private static final Class<?>[] FIXTURE_CLASSES = new Class<?>[]{
            Bill.class,
            Bread.class,
            Carrot.class,
            Cheap.class,
            CheapLiteral.class,
            Expensive.class,
            ExpensiveLiteral.class,
            Fruit.class,
            Grocery.class,
            GroceryInterceptor.class,
            GroceryInterceptorBinding.class,
            GroceryWrapper.class,
            ItalianFood.class,
            Market.class,
            MarketWrapper.class,
            Milk.class,
            NamedStereotype.class,
            ProcessAnnotatedTypeObserver.class,
            Sausage.class,
            Shop.class,
            TropicalFruit.class,
            Vegetables.class,
            Water.class,
            Yogurt.class
    };

    @Test
    @DisplayName("23.5 / ha - field injection point type uses wrapped Annotated.getBaseType()")
    void testGetBaseTypeUsedToDetermineTypeOfInjectionPoint() {
        withBootedSyringe((syringe, bm) -> {
            assertTrue(GroceryWrapper.isGetBaseTypeOfFruitFieldUsed());
            Grocery grocery = getContextualReference(bm, Grocery.class, ANY);
            assertEquals(TropicalFruit.class, grocery.getFruit().getMetadata().getType());
        });
    }

    @Test
    @DisplayName("23.5 / hb - initializer injection point type uses wrapped Annotated.getBaseType()")
    void testGetBaseTypeUsedToDetermineTypeOfInitializerInjectionPoint() {
        withBootedSyringe((syringe, bm) -> {
            Grocery grocery = getContextualReference(bm, Grocery.class, ANY);
            assertEquals(TropicalFruit.class, grocery.getInitializerFruit().getMetadata().getType());
            assertTrue(GroceryWrapper.isGetBaseTypeOfInitializerTropicalFruitParameterUsed());
        });
    }

    @Test
    @DisplayName("23.5 / hc,s - constructor injection point type/qualifiers use wrapped metadata")
    void testGetBaseTypeUsedToDetermineTypeOfConstructorInjectionPoint() {
        withBootedSyringe((syringe, bm) -> {
            Market market = getContextualReference(bm, Market.class);
            assertEquals(TropicalFruit.class, market.getConstructorFruit().getMetadata().getType());
            assertTrue(MarketWrapper.isGetBaseTypeOfMarketConstructorParameterUsed());
            assertTrue(market.getConstructorFruit().getMetadata().getQualifiers().contains(Any.Literal.INSTANCE));
        });
    }

    @Test
    @DisplayName("23.5 / hd - producer method parameter type uses wrapped Annotated.getBaseType()")
    void testGetBaseTypeUsedToDetermineTypeOfProducerInjectionPoint() {
        withBootedSyringe((syringe, bm) -> {
            Bill bill = getContextualReference(bm, Bill.class, new ExpensiveLiteral());
            assertEquals(TropicalFruit.class, bill.getFruit().getMetadata().getType());
            assertTrue(MarketWrapper.isGetBaseTypeOfBillProducerParameterUsed());
        });
    }

    @Test
    @DisplayName("23.5 / he - observer method injection point type uses wrapped Annotated.getBaseType()")
    void testGetBaseTypeUsedToDetermineTypeOfObserverInjectionPoint() {
        withBootedSyringe((syringe, bm) -> {
            bm.getEvent().select(Milk.class).fire(new Milk(false));
            Grocery grocery = getContextualReference(bm, Grocery.class, ANY);
            assertEquals(TropicalFruit.class, grocery.getObserverFruit().getMetadata().getType());
            assertTrue(GroceryWrapper.isGetBaseTypeOfObserverInjectionPointUsed());
        });
    }

    @Test
    @DisplayName("23.5 / hf,ad - disposer method injection point type/qualifiers use wrapped metadata")
    void testGetBaseTypeUsedToDetermineTypeOfDisposerInjectionPoint() {
        withBootedSyringe((syringe, bm) -> {
            Bean<Bill> billBean = resolveBean(bm, Bill.class, new CheapLiteral());
            CreationalContext<Bill> cc = bm.createCreationalContext(billBean);
            Bill bill = bm.getContext(billBean.getScope()).get(billBean);
            billBean.destroy(bill, cc);

            Grocery grocery = getContextualReference(bm, Grocery.class, ANY);
            assertEquals(TropicalFruit.class, grocery.getDisposerFruit().getMetadata().getType());
            assertTrue(GroceryWrapper.isGetBaseTypeOfBillDisposerParameterUsed());
            assertTrue(grocery.getDisposerFruit().getMetadata().getQualifiers().contains(Any.Literal.INSTANCE));
        });
    }

    @Test
    @DisplayName("23.5 / i - observer event parameter type uses wrapped Annotated.getBaseType()")
    void testGetBaseTypeUsedToDetermineTypeOfEventParameter() {
        withBootedSyringe((syringe, bm) -> {
            bm.getEvent().select(Carrot.class).fire(new Carrot());
            Grocery grocery = getContextualReference(bm, Grocery.class, ANY);
            assertEquals(Carrot.class, grocery.getWrappedEventParameter().getClass());
            assertTrue(GroceryWrapper.isGetBaseTypeOfObserverParameterUsed());
        });
    }

    @Test
    @DisplayName("23.5 / j,ac - disposer parameter type/qualifiers use wrapped metadata")
    void testGetBaseTypeUsedToDetermineTypeOfDisposalParameter() {
        withBootedSyringe((syringe, bm) -> {
            Bean<Carrot> carrotBean = resolveBean(bm, Carrot.class, new CheapLiteral());
            CreationalContext<Carrot> cc = bm.createCreationalContext(carrotBean);
            Carrot carrot = carrotBean.create(cc);
            carrotBean.destroy(carrot, cc);

            Grocery grocery = getContextualReference(bm, Grocery.class, ANY);
            assertNotNull(grocery.getWrappedDisposalParameter());
            assertEquals(Carrot.class, grocery.getWrappedDisposalParameter().getClass());
        });
    }

    @Test
    @DisplayName("23.5 / ka - bean type closure uses wrapped AnnotatedType.getTypeClosure()")
    void testGetTypeClosureUsed() {
        withBootedSyringe((syringe, bm) -> {
            assertTrue(GroceryWrapper.isGetTypeClosureUsed());
            assertEquals(2, bm.getBeans(Grocery.class, Any.Literal.INSTANCE).iterator().next().getTypes().size());
            assertEquals(0, bm.getBeans(Shop.class, Any.Literal.INSTANCE).size());
        });
    }

    @Test
    @DisplayName("23.5 / kc - producer field bean types use wrapped AnnotatedField.getTypeClosure()")
    void testGetTypeClosureUsedToDetermineTypeOfProducerField() {
        withBootedSyringe((syringe, bm) -> {
            Bean<Carrot> carrot = resolveBean(bm, Carrot.class, new ExpensiveLiteral());
            assertEquals(1, carrot.getTypes().size());
            assertEquals(Carrot.class, carrot.getTypes().iterator().next());
            assertTrue(MarketWrapper.isGetTypeCLosureOfProducerFieldUsed());
        });
    }

    @Test
    @DisplayName("23.5 / kd - producer method bean types use wrapped AnnotatedMethod.getTypeClosure()")
    void testGetTypeClosureUsedToDetermineTypeOfProducerMethod() {
        withBootedSyringe((syringe, bm) -> {
            Bean<Carrot> carrot = resolveBean(bm, Carrot.class, new CheapLiteral());
            assertEquals(2, carrot.getTypes().size());
            assertFalse(carrot.getTypes().contains(Vegetables.class));
            assertTrue(GroceryWrapper.isGetTypeClosureOfProducerMethodUsed());
        });
    }

    @Test
    @DisplayName("23.5 / l - scope metadata uses wrapped type-level annotations")
    void testGetAnnotationUsedForGettingScopeInformation() {
        withBootedSyringe((syringe, bm) -> {
            assertEquals(RequestScoped.class, bm.getBeans(Grocery.class, Any.Literal.INSTANCE).iterator().next().getScope());
        });
    }

    @Test
    @DisplayName("23.5 / m - qualifier metadata uses wrapped type-level annotations")
    void testGetAnnotationUsedForGettingQualifierInformation() {
        withBootedSyringe((syringe, bm) -> {
            assertEquals(1, bm.getBeans(Grocery.class, new CheapLiteral()).size());
            assertEquals(0, bm.getBeans(Grocery.class, new ExpensiveLiteral()).size());
        });
    }

    @Test
    @DisplayName("23.5 / n - stereotype metadata uses wrapped type-level annotations")
    void testGetAnnotationUsedForGettingStereotypeInformation() {
        withBootedSyringe((syringe, bm) -> {
            Grocery grocery = getContextualReferenceByName(bm, "grocery", Grocery.class);
            assertNotNull(grocery);
        });
    }

    @Test
    @DisplayName("23.5 / p - interceptor metadata uses wrapped type-level annotations")
    void testGetAnnotationUsedForGettingInterceptorInformation() {
        withBootedSyringe((syringe, bm) -> {
            Grocery grocery = getContextualReference(bm, Grocery.class, Any.Literal.INSTANCE);
            assertEquals("foo", grocery.foo());
        });
    }

    @Test
    @DisplayName("23.5 / r - non-@Inject constructor becomes bean constructor via wrapped metadata")
    void testPreviouslyNonInjectAnnotatedConstructorIsUsed() {
        withBootedSyringe((syringe, bm) -> {
            assertTrue(getContextualReference(bm, Grocery.class, Any.Literal.INSTANCE).isConstructorWithParameterUsed());
        });
    }

    @Test
    @DisplayName("23.5 / t - non-@Inject field becomes injection point via wrapped metadata")
    void testPreviouslyNonInjectAnnotatedFieldIsInjected() {
        withBootedSyringe((syringe, bm) -> {
            assertTrue(getContextualReference(bm, Grocery.class, Any.Literal.INSTANCE).isVegetablesInjected());
        });
    }

    @Test
    @DisplayName("23.5 / u - extra qualifier added to injected field is honored")
    void testExtraQualifierIsAppliedToInjectedField() {
        withBootedSyringe((syringe, bm) -> {
            Grocery grocery = getContextualReference(bm, Grocery.class, Any.Literal.INSTANCE);
            assertNotNull(grocery.getFruit());
            Set<Annotation> qualifiers = grocery.getFruit().getMetadata().getQualifiers();
            assertEquals(1, qualifiers.size());
            assertTrue(annotationSetMatches(qualifiers, Cheap.class));
        });
    }

    @Test
    @DisplayName("23.5 / v - added @Produces creates producer field")
    void testProducesCreatesProducerField() {
        withBootedSyringe((syringe, bm) -> {
            assertEquals(1, bm.getBeans(Bread.class, Any.Literal.INSTANCE).size());
        });
    }

    @Test
    @DisplayName("23.5 / w - added @Inject creates initializer method")
    void testInjectCreatesInitializerMethod() {
        withBootedSyringe((syringe, bm) -> {
            assertTrue(getContextualReference(bm, Grocery.class, Any.Literal.INSTANCE).isWaterInjected());
        });
    }

    @Test
    @DisplayName("23.5 / x - qualifier added to initializer parameter is honored")
    void testQualifierAddedToInitializerParameter() {
        withBootedSyringe((syringe, bm) -> {
            Set<Annotation> qualifiers = getContextualReference(bm, Grocery.class, Any.Literal.INSTANCE)
                    .getInitializerFruit()
                    .getMetadata()
                    .getQualifiers();
            assertTrue(annotationSetMatches(qualifiers, Cheap.class));
        });
    }

    @Test
    @DisplayName("23.5 / y - added @Produces creates producer method")
    void testProducesCreatesProducerMethod() {
        withBootedSyringe((syringe, bm) -> {
            assertEquals(1, bm.getBeans(Milk.class, Any.Literal.INSTANCE).size());
        });
    }

    @Test
    @DisplayName("23.5 / z - qualifier added to producer method is honored")
    void testQualifierIsAppliedToProducerMethod() {
        withBootedSyringe((syringe, bm) -> {
            assertEquals(1, bm.getBeans(Yogurt.class, new ExpensiveLiteral()).size());
            assertEquals(0, bm.getBeans(Yogurt.class, new CheapLiteral()).size());
        });
    }

    @Test
    @DisplayName("23.5 / aa - qualifier added to producer method parameter is honored")
    void testQualifierIsAppliedToProducerMethodParameter() {
        withBootedSyringe((syringe, bm) -> {
            Set<Annotation> qualifiers = getContextualReference(bm, Yogurt.class, Any.Literal.INSTANCE)
                    .getFruit()
                    .getMetadata()
                    .getQualifiers();
            assertEquals(1, qualifiers.size());
            assertTrue(annotationSetMatches(qualifiers, Cheap.class));
        });
    }

    @Test
    @DisplayName("23.5 / ab - @Disposes added to method parameter turns method into disposer")
    void testDisposesIsAppliedToMethodParameter() {
        withBootedSyringe((syringe, bm) -> {
            Bean<Yogurt> yogurtBean = resolveBean(bm, Yogurt.class, new ExpensiveLiteral());
            CreationalContext<Yogurt> cc = bm.createCreationalContext(yogurtBean);
            Yogurt yogurt = yogurtBean.create(cc);
            yogurtBean.destroy(yogurt, cc);
            assertTrue(Grocery.isDisposerMethodCalled());
        });
    }

    @Test
    @DisplayName("23.5 / ae,ag - observer method parameters and qualifiers come from wrapped metadata")
    void testObserverMethod() {
        withBootedSyringe((syringe, bm) -> {
            bm.getEvent().select(Milk.class).fire(new Milk(true));
            Grocery grocery = getContextualReference(bm, Grocery.class, Any.Literal.INSTANCE);
            Milk event = grocery.getObserverEvent();
            TropicalFruit parameter = grocery.getObserverParameter();
            assertNotNull(event);
            assertNotNull(parameter);
            assertEquals(1, parameter.getMetadata().getQualifiers().size());
            assertTrue(annotationSetMatches(parameter.getMetadata().getQualifiers(), Cheap.class));
        });
    }

    @Test
    @DisplayName("23.5 / af - extra qualifier added to observer parameter affects observer resolution")
    void testExtraQualifierAppliedToObservesMethodParameter() {
        withBootedSyringe((syringe, bm) -> {
            bm.getEvent().select(Bread.class).fire(new Bread(true));
            assertFalse(getContextualReference(bm, Grocery.class, Any.Literal.INSTANCE).isObserver2Used());
        });
    }

    @Test
    @DisplayName("23.5 / h - container uses Annotated operations, not reflection members directly")
    void testContainerUsesOperationsOfAnnotatedNotReflectionApi() {
        withBootedSyringe((syringe, bm) -> {
            assertEquals(1, bm.getBeans(Sausage.class, Any.Literal.INSTANCE).size());
            assertTrue(bm.getBeans(Sausage.class, new Expensive.Literal()).isEmpty());
            assertTrue(bm.getBeans(Sausage.class, new Cheap.Literal()).isEmpty());
        });
    }

    private interface Scenario {
        void run(Syringe syringe, BeanManager beanManager) throws Exception;
    }

    private void withBootedSyringe(Scenario scenario) {
        InMemoryMessageHandler messageHandler = new InMemoryMessageHandler();
        Syringe syringe = new Syringe(messageHandler, FIXTURE_CLASSES);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addExtension(ProcessAnnotatedTypeObserver.class.getName());
        addBeansXmlInterceptors(syringe, GroceryInterceptor.class.getName());
        try {
            syringe.setup();
            syringe.activateRequestContextIfNeeded();
            scenario.run(syringe, syringe.getBeanManager());
        } catch (Exception e) {
            if (!messageHandler.getAllErrorMessages().isEmpty()) {
                StringBuilder details = new StringBuilder("Syringe errors during setup:\n");
                for (String error : messageHandler.getAllErrorMessages()) {
                    details.append(error).append('\n');
                }
                throw new RuntimeException(details.toString(), e);
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        } finally {
            try {
                syringe.deactivateRequestContextIfActive();
            } catch (RuntimeException ignored) {
                // Best effort for already-inactive contexts.
            }
            syringe.shutdown();
        }
    }

    private static void addBeansXmlInterceptors(Syringe syringe, String... interceptorClassNames) {
        StringBuilder classes = new StringBuilder();
        for (String interceptorClassName : interceptorClassNames) {
            classes.append("<class>").append(interceptorClassName).append("</class>");
        }
        String xml = "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd\" " +
                "version=\"3.0\">" +
                "<interceptors>" + classes + "</interceptors>" +
                "</beans>";
        BeansXml beansXml = new BeansXmlParser().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        syringe.addBeansXmlConfiguration(beansXml);
    }

    @SuppressWarnings("unchecked")
    private static <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> type, Annotation... qualifiers) {
        return (Bean<T>) beanManager.resolve(beanManager.getBeans(type, qualifiers));
    }

    private static <T> T getContextualReference(BeanManager beanManager, Class<T> type, Annotation... qualifiers) {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(type, qualifiers));
        assertNotNull(bean, "No bean resolved for type " + type.getName());
        CreationalContext<?> ctx = beanManager.createCreationalContext(bean);
        return type.cast(beanManager.getReference(bean, type, ctx));
    }

    private static <T> T getContextualReferenceByName(BeanManager beanManager, String name, Class<T> type) {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(name));
        assertNotNull(bean, "No bean resolved for name " + name);
        CreationalContext<?> ctx = beanManager.createCreationalContext(bean);
        return type.cast(beanManager.getReference(bean, type, ctx));
    }

    @SafeVarargs
    private static boolean annotationSetMatches(Set<Annotation> qualifiers,
                                                Class<? extends Annotation>... expectedAnnotationTypes) {
        if (qualifiers == null || expectedAnnotationTypes == null) {
            return false;
        }
        if (qualifiers.size() != expectedAnnotationTypes.length) {
            return false;
        }
        for (Class<? extends Annotation> expected : expectedAnnotationTypes) {
            boolean found = false;
            for (Annotation qualifier : qualifiers) {
                if (expected.equals(qualifier.annotationType())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
