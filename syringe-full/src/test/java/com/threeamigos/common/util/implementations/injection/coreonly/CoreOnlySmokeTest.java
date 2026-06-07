package com.threeamigos.common.util.implementations.injection.coreonly;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.coreonly.fixtures.smoke.ProgrammaticDependency;
import com.threeamigos.common.util.implementations.injection.coreonly.fixtures.smoke.SmokeRootBean;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfSystemProperty(named = "syringe.core.only", matches = "true")
class CoreOnlySmokeTest {

    @Test
    void setupDiscoveryInjectionAndProgrammaticLookupWorkInCoreOnlyMode() {
        ProgrammaticDependency.resetCounters();

        Syringe syringe = new Syringe(new InMemoryMessageHandler(), SmokeRootBean.class);
        boolean initialized = false;
        try {
            syringe.setup();
            initialized = true;

            SmokeRootBean injected = syringe.inject(SmokeRootBean.class);
            assertNotNull(injected);
            assertNotNull(injected.getConstructorDependency());
            assertNotNull(injected.getFieldDependency());
            assertNotNull(injected.getMethodDependency());
            assertNotNull(injected.getProgrammaticDependencies());

            ProgrammaticDependency dependentInstance = injected.getProgrammaticDependencies().get();
            assertNotNull(dependentInstance);
            injected.getProgrammaticDependencies().destroy(dependentInstance);
            assertEquals(1, ProgrammaticDependency.getPreDestroyCount());

            Instance<Object> rootInstance = syringe.getBeanManager().createInstance();
            SmokeRootBean selected = rootInstance.select(SmokeRootBean.class).get();
            assertNotNull(selected);
        } finally {
            if (initialized) {
                syringe.shutdown();
            }
        }
    }
}
