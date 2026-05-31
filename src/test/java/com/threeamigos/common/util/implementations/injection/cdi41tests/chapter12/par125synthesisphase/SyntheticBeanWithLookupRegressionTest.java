package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter12.par125synthesisphase;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanDisposer;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("12.5 - Synthetic bean with lookup regression")
class SyntheticBeanWithLookupRegressionTest {

    @Test
    @DisplayName("12.5 / 12.2 - Synthetic creator/disposer can use lookup and lifecycle counters match expected values")
    void shouldSupportLookupInsideSyntheticBeanCreatorAndDisposer() {
        MyPojo.createdCounter.set(0);
        MyPojo.destroyedCounter.set(0);
        MyPojoCreator.counter.set(0);
        MyPojoDisposer.counter.set(0);
        MyDependentBean.createdCounter.set(0);
        MyDependentBean.destroyedCounter.set(0);

        Syringe syringe = new Syringe();
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addBuildCompatibleExtension(SyntheticBeanWithLookupExtension.class.getName());
        try {
            syringe.initialize();
            syringe.addDiscoveredClass(RootBean.class, BeanArchiveMode.EXPLICIT);
            syringe.start();

            Instance<Object> lookup = syringe.getBeanManager().createInstance();

            assertEquals(0, MyPojo.createdCounter.get());
            assertEquals(0, MyPojo.destroyedCounter.get());
            assertEquals(0, MyPojoCreator.counter.get());
            assertEquals(0, MyPojoDisposer.counter.get());
            assertEquals(0, MyDependentBean.createdCounter.get());
            assertEquals(0, MyDependentBean.destroyedCounter.get());

            Instance.Handle<MyPojo> bean = lookup.select(MyPojo.class).getHandle();
            assertEquals("Hello!", bean.get().hello());

            assertEquals(1, MyPojo.createdCounter.get());
            assertEquals(0, MyPojo.destroyedCounter.get());
            assertEquals(1, MyPojoCreator.counter.get());
            assertEquals(0, MyPojoDisposer.counter.get());
            assertEquals(1, MyDependentBean.createdCounter.get());
            assertEquals(0, MyDependentBean.destroyedCounter.get());

            bean.destroy();

            assertEquals(1, MyPojo.createdCounter.get());
            assertEquals(1, MyPojo.destroyedCounter.get());
            assertEquals(1, MyPojoCreator.counter.get());
            assertEquals(1, MyPojoDisposer.counter.get());
            assertEquals(2, MyDependentBean.createdCounter.get());
            assertEquals(2, MyDependentBean.destroyedCounter.get());
        } finally {
            syringe.shutdown();
        }
    }

    @Dependent
    public static class RootBean {
    }

    @Dependent
    public static class MyDependentBean {
        static final AtomicInteger createdCounter = new AtomicInteger(0);
        static final AtomicInteger destroyedCounter = new AtomicInteger(0);

        @PostConstruct
        void postConstruct() {
            createdCounter.incrementAndGet();
        }

        @PreDestroy
        void preDestroy() {
            destroyedCounter.incrementAndGet();
        }
    }

    public static class MyPojo {
        static final AtomicInteger createdCounter = new AtomicInteger(0);
        static final AtomicInteger destroyedCounter = new AtomicInteger(0);

        MyPojo() {
            createdCounter.incrementAndGet();
        }

        String hello() {
            return "Hello!";
        }

        void destroy() {
            destroyedCounter.incrementAndGet();
        }
    }

    public static class MyPojoCreator implements SyntheticBeanCreator<MyPojo> {
        static final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public MyPojo create(Instance<Object> lookup, Parameters params) {
            counter.incrementAndGet();
            lookup.select(MyDependentBean.class).get();
            return new MyPojo();
        }
    }

    public static class MyPojoDisposer implements SyntheticBeanDisposer<MyPojo> {
        static final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public void dispose(MyPojo instance, Instance<Object> lookup, Parameters params) {
            counter.incrementAndGet();
            lookup.select(MyDependentBean.class).get();
            instance.destroy();
        }
    }

    public static class SyntheticBeanWithLookupExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery(ScannedClasses scan) {
            scan.add(MyDependentBean.class.getName());
        }

        @Synthesis
        public void synthesis(SyntheticComponents syntheticComponents) {
            syntheticComponents.addBean(MyPojo.class)
                    .type(MyPojo.class)
                    .scope(Dependent.class)
                    .createWith(MyPojoCreator.class)
                    .disposeWith(MyPojoDisposer.class);
        }
    }
}
