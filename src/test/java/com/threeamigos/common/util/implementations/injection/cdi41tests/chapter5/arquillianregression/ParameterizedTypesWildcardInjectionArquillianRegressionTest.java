package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.arquillianregression;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("5.2.4 - Arquillian parity for wildcard parameterized injection")
class ParameterizedTypesWildcardInjectionArquillianRegressionTest {

    @Test
    @DisplayName("ParameterizedTypesInjectionToParameterizedWithWildcardTest parity")
    void shouldInjectParameterizedTypesWithWildcards() {
        Syringe syringe = newSyringe(
                Dao.class,
                IntegerStringDao.class,
                StringDao.class,
                NumberDao.class,
                ConsumerWildcard.class
        );
        try {
            assertDoesNotThrow(syringe::setup);

            ConsumerWildcard consumer = syringe.inject(ConsumerWildcard.class);
            assertNotNull(consumer.getDao());
            assertEquals(Dao.class.getName(), consumer.getDao().getId());

            assertNotNull(consumer.getIntegerStringDao());
            assertEquals(IntegerStringDao.class.getName(), consumer.getIntegerStringDao().getId());

            assertNotNull(consumer.getNumberDao());
            assertEquals(NumberDao.class.getName(), consumer.getNumberDao().getId());
        } finally {
            syringe.shutdown();
        }
    }

    private Syringe newSyringe(Class<?>... classes) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), classes);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @Dependent
    static class Dao<T1, T2> {
        String getId() {
            return Dao.class.getName();
        }
    }

    @IntegerPowered
    @Dependent
    static class IntegerStringDao extends Dao<Integer, String> {
        @Override
        String getId() {
            return IntegerStringDao.class.getName();
        }
    }

    @Dependent
    static class StringDao extends Dao<String, String> {
    }

    @Typed(NumberDao.class)
    @Dependent
    static class NumberDao<T1 extends Number, T2 extends Number> extends Dao<T1, T2> {
        @Override
        String getId() {
            return NumberDao.class.getName();
        }
    }

    @Dependent
    static class ConsumerWildcard {
        @Inject
        Dao<? extends Integer, ?> dao;

        @Inject
        @IntegerPowered
        Dao<? extends Integer, ? super String> integerStringDao;

        @Inject
        NumberDao<? extends Serializable, ? super Integer> numberDao;

        Dao<?, ?> getDao() {
            return dao;
        }

        Dao<? extends Integer, ? super String> getIntegerStringDao() {
            return integerStringDao;
        }

        NumberDao<? extends Number, ? extends Number> getNumberDao() {
            return numberDao;
        }
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Qualifier
    @interface IntegerPowered {
    }
}
