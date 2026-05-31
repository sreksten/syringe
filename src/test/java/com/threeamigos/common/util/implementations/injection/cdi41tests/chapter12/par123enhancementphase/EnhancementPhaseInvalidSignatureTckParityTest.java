package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter12.par123enhancementphase;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.MethodConfig;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Isolated
@DisplayName("12.3 - TCK parity for invalid enhancement signatures")
@Execution(ExecutionMode.SAME_THREAD)
public class EnhancementPhaseInvalidSignatureTckParityTest {

    @Test
    @DisplayName("12.3 - EnhancementMultipleParams2Test parity: @Enhancement method with multiple model parameters is a deployment problem")
    void shouldRejectEnhancementMethodWithThreeModelParameters() {
        Syringe syringe = newSyringe();
        syringe.addBuildCompatibleExtension(InvalidEnhancementMultipleModelsExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
    }

    private Syringe newSyringe() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), RootBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @Dependent
    public static class RootBean {
    }

    public static class InvalidEnhancementMultipleModelsExtension implements BuildCompatibleExtension {
        @Enhancement(types = RootBean.class, withSubtypes = false)
        public void enhance(ClassConfig cc, MethodConfig mc, FieldInfo fi) {
            // deployment must fail before invocation
        }
    }
}
