package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.tckparity.namedatinjectionpoint;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("5.3.13 - TCK parity for NamedAtInjectionPointTest")
@Isolated
class NamedAtInjectionPointTckParityTest {

    @Test
    @DisplayName("NamedAtInjectionPointTest - @Named at injection point uses field name")
    void shouldUseFieldNameAsBeanNameAtInjectionPoint() {
        Syringe syringe = newSyringe();
        try {
            FishingNet fishingNet = syringe.inject(FishingNet.class);
            assertNotNull(fishingNet);
            assertNotNull(fishingNet.getNamedAtInjectionPointCarp());
            assertEquals(Integer.valueOf(1), fishingNet.getNamedAtInjectionPointCarp().ping());

            boolean activatedRequest = syringe.activateRequestContextIfNeeded();
            try {
                Pike pike = syringe.inject(Pike.class);
                assertNotNull(pike);
                assertNotNull(pike.getNamedAtInjectionPointDaphnia());
                assertEquals(DaphniaProducer.NAME, pike.getNamedAtInjectionPointDaphnia().getName());
            } finally {
                if (activatedRequest) {
                    syringe.deactivateRequestContextIfActive();
                }
            }
        } finally {
            syringe.shutdown();
        }
    }

    private Syringe newSyringe() {
        Syringe syringe = new Syringe();
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.initialize();
        syringe.addDiscoveredClass(FishingNet.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(Pike.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(NamedAtInjectionPointCarp.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(DaphniaProducer.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(Daphnia.class, BeanArchiveMode.EXPLICIT);
        syringe.start();
        return syringe;
    }

    interface Animal {
        Integer ping();
    }

    @Named
    @Dependent
    static class NamedAtInjectionPointCarp implements Animal {
        @Override
        public Integer ping() {
            return 1;
        }
    }

    @Dependent
    static class FishingNet {
        @Inject
        @Named
        private Animal namedAtInjectionPointCarp;

        Animal getNamedAtInjectionPointCarp() {
            return namedAtInjectionPointCarp;
        }
    }

    @RequestScoped
    static class Pike {
        @Inject
        @Named
        private Daphnia namedAtInjectionPointDaphnia;

        Daphnia getNamedAtInjectionPointDaphnia() {
            return namedAtInjectionPointDaphnia;
        }
    }

    @Dependent
    static class DaphniaProducer {
        static final String NAME = "Tom";

        @Produces
        @Named("namedAtInjectionPointDaphnia")
        Daphnia produceDaphnia() {
            return new Daphnia(NAME);
        }
    }

    static class Daphnia {
        private final String name;

        Daphnia(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }
    }
}
