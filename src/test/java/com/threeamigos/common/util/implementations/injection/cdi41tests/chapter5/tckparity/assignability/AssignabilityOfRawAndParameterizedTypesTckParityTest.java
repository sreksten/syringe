package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.tckparity.assignability;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("serial")
@DisplayName("5.2.4 - TCK parity for AssignabilityOfRawAndParameterizedTypesTest")
@Isolated
class AssignabilityOfRawAndParameterizedTypesTckParityTest {

    private static final Class<?>[] RESULT_TYPES = new Class<?>[]{ResultImpl.class, Result.class, Object.class};
    private static final Class<?>[] DAO_TYPES = new Class<?>[]{Dao.class, Object.class};
    private static final Class<?>[] BOX_TYPES = new Class<?>[]{BoxBarBazFooImpl.class, Box.class, Object.class};

    @Test
    @DisplayName("AssignabilityOfRawAndParameterizedTypesTest - assignability to raw required type")
    void shouldResolveAssignableBeansToRawRequiredType() {
        Syringe syringe = newSyringe();
        try {
            assertEquals(4, getBeans(syringe, Dao.class).size());
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("AssignabilityOfRawAndParameterizedTypesTest - actual types to actual types")
    void shouldResolveParameterizedTypeWithActualTypesToParameterizedTypeWithActualTypes() {
        Syringe syringe = newSyringe();
        try {
            assertEquals(2, getBeans(syringe, new TypeLiteral<Map<Integer, Integer>>() {}).size());

            Set<Bean<HashMap<Integer, Integer>>> beans = getBeans(syringe, new TypeLiteral<HashMap<Integer, Integer>>() {});
            assertEquals(1, beans.size());
            assertTrue(beans.iterator().next().getTypes().contains(IntegerHashMap.class));
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("AssignabilityOfRawAndParameterizedTypesTest - actual types to wildcard required type")
    void shouldResolveParameterizedTypeWithActualTypesToWildcardRequiredType() {
        Syringe syringe = newSyringe();
        try {
            Set<Bean<HashMap<? extends Number, ? super Integer>>> beans =
                    getBeans(syringe, new TypeLiteral<HashMap<? extends Number, ? super Integer>>() {});
            assertEquals(1, beans.size());
            assertTrue(beans.iterator().next().getTypes().contains(IntegerHashMap.class));
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("AssignabilityOfRawAndParameterizedTypesTest - wildcard injection point is legal")
    void shouldInjectWildcardParameterizedTypeAtInjectionPoint() {
        Syringe syringe = newSyringe();
        try {
            InjectedBean bean = syringe.inject(InjectedBean.class);
            assertTrue(bean.getMap() instanceof IntegerHashMap);
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("AssignabilityOfRawAndParameterizedTypesTest - type variables to wildcard parameters (case 1)")
    void shouldResolveTypeVariablesToWildcardRequiredTypeCase1() {
        Syringe syringe = newSyringe();
        try {
            Set<Bean<Result<? extends Throwable, ? super Exception>>> beans =
                    getBeans(syringe, new TypeLiteral<Result<? extends Throwable, ? super Exception>>() {});
            assertEquals(1, beans.size());
            assertTrue(rawTypeSetMatches(beans.iterator().next().getTypes(), RESULT_TYPES));
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("AssignabilityOfRawAndParameterizedTypesTest - type variables to wildcard parameters (case 2)")
    void shouldResolveTypeVariablesToWildcardRequiredTypeCase2() {
        Syringe syringe = newSyringe();
        try {
            Set<Bean<Result<? extends RuntimeException, ? super RuntimeException>>> beans =
                    getBeans(syringe, new TypeLiteral<Result<? extends RuntimeException, ? super RuntimeException>>() {});
            assertEquals(1, beans.size());
            assertTrue(rawTypeSetMatches(beans.iterator().next().getTypes(), RESULT_TYPES));
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("AssignabilityOfRawAndParameterizedTypesTest - type vars to wildcard lower bounds with multiple bounds")
    <T1 extends SubBar & SubBaz & Foo,
            T2 extends BarBazImpl & Foo,
            T3 extends SubBar & SubBaz & SuperFoo,
            T4 extends SubBar & SubBaz,
            T5 extends BarBazSuperFooImpl,
            T6 extends BarBazSuperFooImpl & SuperBarFooCloneable>
    void shouldResolveTypeVariablesToWildcardLowerBoundWithMultipleBounds() {
        Syringe syringe = newSyringe();
        try {
            Set<Bean<Result<? extends Exception, ? super Throwable>>> noResultBeans =
                    getBeans(syringe, new TypeLiteral<Result<? extends Exception, ? super Throwable>>() {});
            assertEquals(0, noResultBeans.size());

            Set<Bean<Box<? super T1>>> beans1 = getBeans(syringe, new TypeLiteral<Box<? super T1>>() {});
            assertEquals(1, beans1.size());
            assertTrue(rawTypeSetMatches(beans1.iterator().next().getTypes(), BOX_TYPES));

            Set<Bean<Box<? super T2>>> beans2 = getBeans(syringe, new TypeLiteral<Box<? super T2>>() {});
            assertEquals(1, beans2.size());
            assertTrue(rawTypeSetMatches(beans2.iterator().next().getTypes(), BOX_TYPES));

            Set<Bean<Box<? super T3>>> noBeans3 = getBeans(syringe, new TypeLiteral<Box<? super T3>>() {});
            assertEquals(0, noBeans3.size());

            Set<Bean<Box<? super T4>>> noBeans4 = getBeans(syringe, new TypeLiteral<Box<? super T4>>() {});
            assertEquals(0, noBeans4.size());

            Set<Bean<Box<? super T5>>> noBeans5 = getBeans(syringe, new TypeLiteral<Box<? super T5>>() {});
            assertEquals(0, noBeans5.size());

            Set<Bean<Box<? super T6>>> beans6 = getBeans(syringe, new TypeLiteral<Box<? super T6>>() {});
            assertEquals(1, beans6.size());
            assertTrue(rawTypeSetMatches(beans6.iterator().next().getTypes(), BOX_TYPES));
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("AssignabilityOfRawAndParameterizedTypesTest - type variables to actual required types")
    void shouldResolveTypeVariablesToActualRequiredTypes() {
        Syringe syringe = newSyringe();
        try {
            Set<Bean<Result<RuntimeException, IllegalStateException>>> beans =
                    getBeans(syringe, new TypeLiteral<Result<RuntimeException, IllegalStateException>>() {});
            assertEquals(1, beans.size());
            assertTrue(rawTypeSetMatches(beans.iterator().next().getTypes(), RESULT_TYPES));

            Set<Bean<Result<RuntimeException, Throwable>>> noBeans =
                    getBeans(syringe, new TypeLiteral<Result<RuntimeException, Throwable>>() {});
            assertEquals(0, noBeans.size());
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("AssignabilityOfRawAndParameterizedTypesTest - type vars with multiple bounds to actual required types")
    void shouldResolveTypeVariablesWithMultipleBoundsToActualRequiredTypes() {
        Syringe syringe = newSyringe();
        try {
            Set<Bean<Box<BarSubBazFooImpl>>> beans =
                    getBeans(syringe, new TypeLiteral<Box<BarSubBazFooImpl>>() {});
            assertEquals(1, beans.size());
            assertTrue(rawTypeSetMatches(beans.iterator().next().getTypes(), BOX_TYPES));

            Set<Bean<Box<BarBazSuperFooImpl>>> noBeans1 =
                    getBeans(syringe, new TypeLiteral<Box<BarBazSuperFooImpl>>() {});
            assertEquals(0, noBeans1.size());

            Set<Bean<Box<BarBazImpl>>> noBeans2 =
                    getBeans(syringe, new TypeLiteral<Box<BarBazImpl>>() {});
            assertEquals(0, noBeans2.size());
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("AssignabilityOfRawAndParameterizedTypesTest - type variables to required type variables")
    <T1 extends RuntimeException, T2 extends T1, T3>
    void shouldResolveTypeVariablesToRequiredTypeVariables() {
        Syringe syringe = newSyringe();
        try {
            Set<Bean<Result<T1, T2>>> beans = getBeans(syringe, new TypeLiteral<Result<T1, T2>>() {});
            assertEquals(1, beans.size());
            assertTrue(rawTypeSetMatches(beans.iterator().next().getTypes(), RESULT_TYPES));

            Set<Bean<Result<T1, T3>>> noBeans = getBeans(syringe, new TypeLiteral<Result<T1, T3>>() {});
            assertEquals(0, noBeans.size());

            Set<Bean<Dao<T1, T3>>> daoBeans = getBeans(syringe, new TypeLiteral<Dao<T1, T3>>() {});
            assertEquals(1, daoBeans.size());
            assertTrue(rawTypeSetMatches(daoBeans.iterator().next().getTypes(), DAO_TYPES));
        } finally {
            syringe.shutdown();
        }
    }

    @Test
    @DisplayName("AssignabilityOfRawAndParameterizedTypesTest - multiple-bounds type vars to required type vars")
    <T1 extends SubBar & SubBaz & Foo,
            T2 extends BarBazImpl & Foo,
            T3 extends SubBar & SubBaz & SuperFoo,
            T4 extends SubBar & SubBaz,
            T5 extends BarBazSuperFooImpl,
            T6 extends BarBazSuperFooImpl & SuperBarFooCloneable>
    void shouldResolveMultipleBoundsTypeVariablesToRequiredTypeVariables() {
        Syringe syringe = newSyringe();
        try {
            Set<Bean<Box<T1>>> beans1 = getBeans(syringe, new TypeLiteral<Box<T1>>() {});
            assertEquals(1, beans1.size());
            assertTrue(rawTypeSetMatches(beans1.iterator().next().getTypes(), BOX_TYPES));

            Set<Bean<Box<T2>>> beans2 = getBeans(syringe, new TypeLiteral<Box<T2>>() {});
            assertEquals(1, beans2.size());
            assertTrue(rawTypeSetMatches(beans2.iterator().next().getTypes(), BOX_TYPES));

            Set<Bean<Box<T3>>> noBeans3 = getBeans(syringe, new TypeLiteral<Box<T3>>() {});
            assertEquals(0, noBeans3.size());

            Set<Bean<Box<T4>>> noBeans4 = getBeans(syringe, new TypeLiteral<Box<T4>>() {});
            assertEquals(0, noBeans4.size());

            Set<Bean<Box<T5>>> noBeans5 = getBeans(syringe, new TypeLiteral<Box<T5>>() {});
            assertEquals(0, noBeans5.size());

            Set<Bean<Box<T6>>> beans6 = getBeans(syringe, new TypeLiteral<Box<T6>>() {});
            assertEquals(1, beans6.size());
            assertTrue(rawTypeSetMatches(beans6.iterator().next().getTypes(), BOX_TYPES));
        } finally {
            syringe.shutdown();
        }
    }

    private Syringe newSyringe() {
        Syringe syringe = new Syringe();
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.initialize();
        syringe.addDiscoveredClass(Dao.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(DaoProducer.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(ObjectDao.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(IntegerDao.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(ResultImpl.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(BoxBarBazFooImpl.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(BarBazImpl.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(BarSubBazFooImpl.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(BarBazSuperFooImpl.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(MapProducer.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(IntegerHashMap.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(InjectedBean.class, BeanArchiveMode.EXPLICIT);
        syringe.start();
        return syringe;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> Set<Bean<T>> getBeans(Syringe syringe, Class<T> type) {
        return (Set) syringe.getBeanManager().getBeans(type);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> Set<Bean<T>> getBeans(Syringe syringe, TypeLiteral<T> type) {
        return (Set) syringe.getBeanManager().getBeans(type.getType());
    }

    private static boolean rawTypeSetMatches(Set<Type> types, Class<?>... requiredTypes) {
        Set<Type> typesRawSet = new HashSet<Type>();
        for (Type type : types) {
            if (type instanceof Class<?>) {
                typesRawSet.add(type);
            } else if (type instanceof ParameterizedType) {
                typesRawSet.add(((ParameterizedType) type).getRawType());
            }
        }
        return typeSetMatches(typesRawSet, requiredTypes);
    }

    private static boolean typeSetMatches(Collection<? extends Type> types, Type... requiredTypes) {
        return requiredTypes.length == types.size() && types.containsAll(Arrays.asList(requiredTypes));
    }

    @Dependent
    static class Dao<T1, T2> {
    }

    @Dependent
    static class DaoProducer {
        @Produces
        Dao<Object, Object> getDao() {
            return new Dao<Object, Object>();
        }

        @SuppressWarnings("rawtypes")
        @Produces
        Dao getRawDao() {
            return getDao();
        }
    }

    @Dependent
    static class ObjectDao extends Dao<Object, Object> {
    }

    @Dependent
    static class IntegerDao extends Dao<Integer, Integer> {
    }

    interface Result<T1, T2> {
    }

    @Dependent
    static class ResultImpl<T1 extends Exception, T2 extends Exception> implements Result<T1, T2> {
    }

    interface Box<T> {
    }

    @Dependent
    static class BoxBarBazFooImpl<T extends Bar & Baz & Foo> implements Box<T> {
    }

    interface Bar extends SuperBar {
    }

    interface Baz {
    }

    interface Foo extends SuperFoo {
    }

    interface SuperFoo {
    }

    interface SubBar extends Bar {
    }

    interface SubBaz extends Baz {
    }

    interface SuperBar {
    }

    interface SuperBarFooCloneable extends SuperBar, Foo, Cloneable {
    }

    @Dependent
    static class BarBazImpl implements Bar, Baz {
    }

    @Dependent
    static class BarSubBazFooImpl extends BarBazImpl implements SubBaz, Foo {
    }

    @Dependent
    static class BarBazSuperFooImpl implements Bar, Baz, SuperFoo {
    }

    @Dependent
    static class MapProducer {
        @Produces
        Map<Integer, Integer> produceMap() {
            return new HashMap<Integer, Integer>();
        }
    }

    @Dependent
    static class IntegerHashMap extends HashMap<Integer, Integer> {
        private static final long serialVersionUID = 1L;
    }

    @Dependent
    static class InjectedBean {
        @Inject
        private HashMap<? extends Number, ? super Integer> map;

        HashMap<? extends Number, ? super Integer> getMap() {
            return map;
        }
    }
}
