package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter4.tckparity;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Specializes;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("4 - TCK parity for specialization")
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class SpecializationTckParityTest {

    private static final Class<?>[] FIXTURE_CLASSES = new Class<?>[] {
            Mock.class,
            SpecializationBean.class,
            MockSpecializationBean.class,
            StaticNestedClassesParent.class,
            DataProvider.class,
            DataProviderProducer.class,
            MockDataProviderProducer.class,
            Employee.class,
            Maid.class,
            Manager.class,
            Mammal.class,
            CowBroken.class
    };

    @Test
    @DisplayName("4.3 / SpecializingBeanQualifiersTest - specializing bean has inherited qualifiers and specializing type")
    void shouldExposeSpecializingBeanWithInheritedQualifiers() {
        Syringe syringe = newSyringe(SpecializationBean.class, MockSpecializationBean.class, Mock.class);
        syringe.setup();

        Bean<?> bean = onlyMockQualifiedBean(syringe.getBeanManager(), SpecializationBean.class);
        assertTrue(bean.getTypes().contains(MockSpecializationBean.class));
        assertEquals(2, bean.getQualifiers().size());
        assertTrue(bean.getQualifiers().contains(Any.Literal.INSTANCE));
        assertTrue(bean.getQualifiers().contains(new Mock.Literal()));
    }

    @Test
    @DisplayName("4.3 / SpecializingBeanQualifiersTest - static nested specializing bean preserves qualifiers")
    void shouldExposeNestedSpecializingBeanWithInheritedQualifiers() {
        Syringe syringe = newSyringe(
                StaticNestedClassesParent.StaticSpecializationBean.class,
                StaticNestedClassesParent.StaticMockSpecializationBean.class,
                Mock.class
        );
        syringe.setup();

        Bean<?> bean = onlyMockQualifiedBean(syringe.getBeanManager(), StaticNestedClassesParent.StaticSpecializationBean.class);
        assertTrue(bean.getTypes().contains(StaticNestedClassesParent.StaticMockSpecializationBean.class));
        assertEquals(2, bean.getQualifiers().size());
        assertTrue(bean.getQualifiers().contains(Any.Literal.INSTANCE));
        assertTrue(bean.getQualifiers().contains(new Mock.Literal()));
    }

    @Test
    @DisplayName("4.3 / SpecializingBeanQualifiersTest - specialized producer method preserves inherited qualifiers")
    void shouldExposeSpecializedProducerWithInheritedQualifiers() {
        Syringe syringe = newSyringe(DataProvider.class, DataProviderProducer.class, MockDataProviderProducer.class, Mock.class);
        syringe.setup();

        Bean<?> bean = onlyMockQualifiedBean(syringe.getBeanManager(), DataProvider.class);
        assertEquals(2, bean.getQualifiers().size());
        assertTrue(bean.getQualifiers().contains(Any.Literal.INSTANCE));
        assertTrue(bean.getQualifiers().contains(new Mock.Literal()));
    }

    @Test
    @DisplayName("4.3 / InconsistentSpecializationTest - two beans specializing same direct bean are inconsistent specialization")
    void shouldFailForInconsistentSpecialization() {
        Syringe syringe = newSyringe(Employee.class, Maid.class, Manager.class);
        assertThrows(DeploymentException.class, syringe::setup);
    }

    @Test
    @DisplayName("4.3 / SpecializingClassExtendsNonSimpleBeanTest - specializing bean may not extend non-simple bean")
    void shouldFailWhenSpecializingBeanExtendsNonSimpleBean() {
        Syringe syringe = newSyringe(Mammal.class, CowBroken.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    private Bean<?> onlyMockQualifiedBean(BeanManager beanManager, Class<?> type) {
        Set<Bean<?>> beans = beanManager.getBeans(type, new Mock.Literal());
        assertEquals(1, beans.size());
        return beans.iterator().next();
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

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    public @interface Mock {
        final class Literal extends AnnotationLiteral<Mock> implements Mock {
        }
    }

    @Mock
    @Dependent
    public static class SpecializationBean {
    }

    @Specializes
    @Dependent
    public static class MockSpecializationBean extends SpecializationBean {
    }

    public static class StaticNestedClassesParent {
        @Mock
        @Dependent
        public static class StaticSpecializationBean {
        }

        @Specializes
        @Dependent
        public static class StaticMockSpecializationBean extends StaticSpecializationBean {
        }
    }

    public static class DataProvider {
    }

    @Dependent
    public static class DataProviderProducer {
        @Produces
        @Mock
        public DataProvider produceDataProvider() {
            return new DataProvider();
        }
    }

    @Dependent
    public static class MockDataProviderProducer extends DataProviderProducer {
        @Override
        @Produces
        @Specializes
        public DataProvider produceDataProvider() {
            return new DataProvider();
        }
    }

    @Dependent
    public static class Employee {
    }

    @Specializes
    @Dependent
    public static class Maid extends Employee {
    }

    @Specializes
    @Dependent
    public static class Manager extends Employee {
    }

    @Dependent
    public static class Mammal {
        public Mammal(String type) {
        }
    }

    @Specializes
    @Dependent
    public static class CowBroken extends Mammal {
        public CowBroken() {
            super("Herbivore");
        }
    }
}
