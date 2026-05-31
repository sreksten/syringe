package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter2.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXml;
import com.threeamigos.common.util.implementations.injection.beansxml.BeansXmlParser;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("2 - TCK parity for alternatives and stereotypes")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class AlternativeAndStereotypeTckParityTest {

    private static final Class<?>[] FIXTURE_CLASSES = new Class<?>[] {
            SomeInterface.class,
            StandardImpl.class,
            AlternativeImpl.class,
            AlternativeStereotype.class,
            FallowDeer.class,
            RoeDeer.class,
            StereotypeWithEmptyNamed.class,
            DummyAnnotation.class,
            PriorityStereotype.class,
            AnotherPriorityStereotype.class,
            SomeOtherBean.class,
            TestBean.class,
            Foo.class,
            Bar.class,
            BarProducer.class,
            Boss.class,
            SimpleTestBean.class,
            Alpha.class,
            Bravo.class,
            Charlie.class,
            AssertBean.class,
            Wild.class,
            Tame.class
    };

    @Test
    @DisplayName("2.7 / StereotypeWithAlternativeSelectedByPriorityTest - stereotype alternative selected by @Priority")
    void shouldSelectStereotypeAlternativeByPriority() {
        Syringe syringe = newSyringe(SomeInterface.class, StandardImpl.class, AlternativeImpl.class, AlternativeStereotype.class);
        syringe.setup();

        SomeInterface bean = resolveManagedBean(syringe.getBeanManager(), SomeInterface.class);
        assertEquals(AlternativeImpl.class.getSimpleName(), bean.ping());
    }

    @Test
    @DisplayName("2.8 / DefaultNamedTest - stereotype with empty @Named gives default bean name")
    void shouldApplyDefaultNameFromNamedStereotype() {
        Syringe syringe = newSyringe(FallowDeer.class, StereotypeWithEmptyNamed.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(FallowDeer.class));
        assertNotNull(bean);
        assertEquals("fallowDeer", bean.getName());
        assertTrue(bean.getQualifiers().contains(Any.Literal.INSTANCE));
        assertTrue(bean.getQualifiers().contains(Default.Literal.INSTANCE));
    }

    @Test
    @DisplayName("2.8 / DefaultNamedTest - bean @Named overrides stereotype-derived default name")
    void shouldAllowBeanNamedToOverrideStereotypeDefaultName() {
        Syringe syringe = newSyringe(RoeDeer.class, StereotypeWithEmptyNamed.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(RoeDeer.class));
        assertNotNull(bean);
        assertEquals("roe", bean.getName());
        assertTrue(bean.getQualifiers().contains(Any.Literal.INSTANCE));
        assertTrue(bean.getQualifiers().contains(Default.Literal.INSTANCE));
        assertTrue(bean.getQualifiers().contains(NamedLiteral.of("roe")));
    }

    @Test
    @DisplayName("2.7 / NoAnnotationWithSpecifiedNameTest - unknown stereotype in beans.xml alternatives is deployment problem")
    void shouldFailForUnknownStereotypeNameInBeansXmlAlternatives() {
        Syringe syringe = newSyringe(DummyAnnotation.class);
        addBeansXmlAlternatives(syringe, "",
                "<stereotype>org.jboss.cdi.tck.tests.policy.broken.incorrect.name.stereotype.Mock</stereotype>");

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("2.8 / ConflictingPrioritiesFromSingleStereotypeTest - conflicting inherited stereotype priorities are definition error")
    void shouldFailForConflictingPrioritiesFromSingleStereotypeHierarchy() {
        Syringe syringe = newSyringe(SomeOtherBean.class, AnotherPriorityStereotype.class, PriorityStereotype.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("2.7 / SelectedAlternative01Test - application-selected alternatives available across consumers")
    void shouldSelectAlternativeBeansAndProducersApplicationWide() {
        Syringe syringe = newSyringe(
                Alpha.class,
                Bravo.class,
                Charlie.class,
                Foo.class,
                Bar.class,
                BarProducer.class,
                Boss.class,
                SimpleTestBean.class,
                Wild.class,
                Tame.class,
                TestBean.class
        );
        syringe.setup();

        Alpha alpha = resolveManagedBean(syringe.getBeanManager(), Alpha.class);
        Bravo bravo = resolveManagedBean(syringe.getBeanManager(), Bravo.class);
        Charlie charlie = resolveManagedBean(syringe.getBeanManager(), Charlie.class);

        alpha.assertAvailable(Foo.class);
        bravo.assertAvailable(Foo.class);
        charlie.assertAvailable(Foo.class);

        alpha.assertAvailable(Bar.class, Wild.Literal.INSTANCE);
        bravo.assertAvailable(Bar.class, Wild.Literal.INSTANCE);
        charlie.assertAvailable(Bar.class, Wild.Literal.INSTANCE);

        alpha.assertAvailable(Bar.class, Tame.Literal.INSTANCE);
        bravo.assertAvailable(Bar.class, Tame.Literal.INSTANCE);
        charlie.assertAvailable(Bar.class, Tame.Literal.INSTANCE);
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), classes);
        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(classes));
        for (Class<?> fixture : FIXTURE_CLASSES) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    private void addBeansXmlAlternatives(Syringe syringe, String classEntries, String stereotypeEntries) {
        String xml = "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_3_0.xsd\" " +
                "version=\"3.0\">" +
                "<alternatives>" + classEntries + stereotypeEntries + "</alternatives>" +
                "</beans>";
        BeansXml beansXml = new BeansXmlParser()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        syringe.getKnowledgeBase().addBeansXml(beansXml);
    }

    @SuppressWarnings("unchecked")
    private <T> T resolveManagedBean(BeanManager beanManager, Class<T> type) {
        return (T) beanManager.getReference(
                beanManager.resolve(beanManager.getBeans(type)),
                type,
                beanManager.createCreationalContext(null));
    }

    public interface SomeInterface {
        String ping();
    }

    @Dependent
    public static class StandardImpl implements SomeInterface {
        @Override
        public String ping() {
            return StandardImpl.class.getSimpleName();
        }
    }

    @Priority(1000)
    @AlternativeStereotype
    public static class AlternativeImpl implements SomeInterface {
        @Override
        public String ping() {
            return AlternativeImpl.class.getSimpleName();
        }
    }

    @Stereotype
    @Alternative
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface AlternativeStereotype {
    }

    @Stereotype
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Named
    public @interface StereotypeWithEmptyNamed {
    }

    @StereotypeWithEmptyNamed
    public static class FallowDeer {
    }

    @Named("roe")
    @StereotypeWithEmptyNamed
    public static class RoeDeer {
    }

    public @interface DummyAnnotation {
    }

    @Stereotype
    @Priority(100)
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface PriorityStereotype {
    }

    @Stereotype
    @Priority(200)
    @PriorityStereotype
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface AnotherPriorityStereotype {
    }

    @Dependent
    @Alternative
    @AnotherPriorityStereotype
    public static class SomeOtherBean {
        public String ping() {
            return SomeOtherBean.class.getSimpleName();
        }
    }

    public interface TestBean {
        String getId();
    }

    @Priority(1000)
    @Alternative
    @Dependent
    public static class Foo implements TestBean {
        @Override
        public String getId() {
            return Foo.class.getName();
        }
    }

    @Priority(2000)
    @Alternative
    @Dependent
    public static class Bar implements TestBean {
        @Override
        public String getId() {
            return Bar.class.getName();
        }
    }

    @Priority(1100)
    @Dependent
    public static class BarProducer {
        @Alternative
        @Produces
        @Wild
        public final Bar producedBar = new Bar();

        @Alternative
        @Produces
        @Tame
        public Bar produceTameBar() {
            return new Bar();
        }
    }

    @Priority(900)
    @Alternative
    @Dependent
    public static class Boss {
        @Produces
        public TestBean produceSimpleTestBean() {
            return new SimpleTestBean();
        }
    }

    public static class SimpleTestBean implements TestBean {
        @Override
        public String getId() {
            return SimpleTestBean.class.getName();
        }
    }

    @Dependent
    public static class Alpha extends AssertBean {
    }

    @Dependent
    public static class Bravo extends AssertBean {
    }

    @Dependent
    public static class Charlie extends AssertBean {
    }

    public static abstract class AssertBean {
        @Inject
        @Any
        Instance<Object> instance;

        @Inject
        BeanManager beanManager;

        public <T> T assertAvailable(Class<T> beanType, Annotation... qualifiers) {
            assertNotNull(beanManager.resolve(beanManager.getBeans(beanType, qualifiers)));
            Instance<T> subtypeInstance = instance.select(beanType, qualifiers);
            T beanInstance = subtypeInstance.get();
            assertNotNull(beanInstance);
            return beanInstance;
        }
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    public @interface Wild {
        final class Literal extends AnnotationLiteral<Wild> implements Wild {
            static final Wild INSTANCE = new Literal();
        }
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    public @interface Tame {
        final class Literal extends AnnotationLiteral<Tame> implements Tame {
            static final Tame INSTANCE = new Literal();
        }
    }
}
