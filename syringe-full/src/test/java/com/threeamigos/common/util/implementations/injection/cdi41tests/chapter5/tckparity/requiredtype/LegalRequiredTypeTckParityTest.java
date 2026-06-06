package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter5.tckparity.requiredtype;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("5.2.3 - TCK parity for LegalRequiredTypeTest")
@Isolated
class LegalRequiredTypeTckParityTest {

    @Test
    @DisplayName("LegalRequiredTypeTest - legal required injection point types resolve")
    void shouldResolveLegalRequiredTypeInjections() {
        Syringe syringe = newSyringe();
        try {
            Forest forest = syringe.inject(Forest.class);

            forest.ping();
            assertEquals(10, forest.getAge());
            assertNotNull(forest.getNeedles());
            assertEquals(1, forest.getNeedles().length);
        } finally {
            syringe.shutdown();
        }
    }

    private Syringe newSyringe() {
        Syringe syringe = new Syringe();
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.initialize();
        syringe.addDiscoveredClass(Forest.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(Conifer.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(Spruce.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(Leaf.class, BeanArchiveMode.EXPLICIT);
        syringe.addDiscoveredClass(Needle.class, BeanArchiveMode.EXPLICIT);
        syringe.start();
        return syringe;
    }

    interface Tree {
        void ping();
    }

    @Dependent
    static class Forest {
        @Inject
        Tree tree;

        @Inject
        Conifer conifer;

        @Inject
        Spruce spruce;

        @Inject
        @Named("age")
        int age;

        @Inject
        Needle[] needles;

        @Inject
        Leaf<?> leaf;

        @Inject
        Leaf<Spruce> spruceLeaf;

        void ping() {
            tree.ping();
            conifer.ping();
            spruce.ping();
            leaf.ping();
            spruceLeaf.ping();
        }

        int getAge() {
            return age;
        }

        Needle[] getNeedles() {
            return needles;
        }
    }

    @Dependent
    abstract static class Conifer implements Tree {
        @Override
        public void ping() {
            // no-op
        }
    }

    @Dependent
    static final class Spruce extends Conifer {
        @Produces
        @Named("age")
        int produceAge() {
            return 10;
        }

        @Produces
        Needle[] produceNeedles() {
            return new Needle[]{new Needle()};
        }
    }

    @Dependent
    static class Leaf<T extends Tree> {
        void ping() {
            // no-op
        }
    }

    @Dependent
    static class Needle {
    }
}
