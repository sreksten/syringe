package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter13.par132deployment;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.build.compatible.spi.Validation;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("13.2 - Deployment tests")
@Execution(ExecutionMode.SAME_THREAD)
public class DeploymentTest {

    @Test
    @DisplayName("13.2 - Deployment executes BCE callbacks in order when no synthetic registration model is produced")
    public void shouldExecuteBuildCompatibleExtensionPhasesInDeploymentOrder() {
        DeploymentPhaseRecorder.reset();
        Syringe syringe = newSyringe(DeploymentRootBean.class);
        syringe.addBuildCompatibleExtension(AllPhasesRecordingExtension.class.getName());

        syringe.setup();

        // Registration phase runs twice at container level; this extension only observes
        // the first pass because it does not register matching synthetic components.
        assertEquals(5, DeploymentPhaseRecorder.phases.size());
        assertEquals("discovery", DeploymentPhaseRecorder.phases.get(0));
        assertEquals("enhancement", DeploymentPhaseRecorder.phases.get(1));
        assertEquals("registration", DeploymentPhaseRecorder.phases.get(2));
        assertEquals("synthesis", DeploymentPhaseRecorder.phases.get(3));
        assertEquals("validation", DeploymentPhaseRecorder.phases.get(4));
    }

    @Test
    @DisplayName("13.2 - Deployment aborts when a definition error occurs during type discovery")
    public void shouldAbortDeploymentWhenDiscoveryStepFails() {
        DeploymentPhaseRecorder.reset();
        Syringe syringe = newSyringe(DeploymentRootBean.class);
        syringe.addBuildCompatibleExtension(FailingDiscoveryExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
        assertEquals(1, DeploymentPhaseRecorder.phases.size());
        assertEquals("discovery", DeploymentPhaseRecorder.phases.get(0));
    }

    @Test
    @DisplayName("13.2 - Deployment validates bean dependencies before @Validation and aborts on deployment problems")
    public void shouldAbortDeploymentForUnsatisfiedDependencyBeforeValidationPhase() {
        DeploymentPhaseRecorder.reset();
        Syringe syringe = newErrorSyringe(UnsatisfiedRootBean.class);
        syringe.addBuildCompatibleExtension(ValidationRecordingExtension.class.getName());

        assertThrows(RuntimeException.class, syringe::setup);
        assertTrue(!DeploymentPhaseRecorder.phases.contains("validation"));
    }

    @Test
    @DisplayName("13.2 - Deployment aborts when @Validation registers deployment problems")
    public void shouldAbortDeploymentWhenValidationReportsProblem() {
        DeploymentPhaseRecorder.reset();
        Syringe syringe = newSyringe(DeploymentRootBean.class);
        syringe.addBuildCompatibleExtension(ValidationErrorExtension.class.getName());

        assertThrows(DefinitionException.class, syringe::setup);
        assertTrue(DeploymentPhaseRecorder.phases.contains("validation"));
    }

    private Syringe newSyringe(Class<?> rootClass) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), rootClass);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(UnsatisfiedRootBean.class);
        syringe.exclude(MissingDependency.class);
        return syringe;
    }

    private Syringe newErrorSyringe(Class<?> rootClass) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), rootClass);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @Dependent
    public static class DeploymentRootBean {
    }

    @Dependent
    public static class UnsatisfiedRootBean {
        @Inject
        MissingDependency missingDependency;
    }

    public interface MissingDependency {
    }

    static class DeploymentPhaseRecorder {
        static final List<String> phases = new ArrayList<String>();

        static void reset() {
            phases.clear();
        }
    }

    public static class AllPhasesRecordingExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery() {
            DeploymentPhaseRecorder.phases.add("discovery");
        }

        @Enhancement(types = DeploymentRootBean.class)
        public void enhancement(ClassInfo ignored) {
            DeploymentPhaseRecorder.phases.add("enhancement");
        }

        @Registration(types = DeploymentRootBean.class)
        public void registration(BeanInfo ignored) {
            DeploymentPhaseRecorder.phases.add("registration");
        }

        @Synthesis
        public void synthesis(SyntheticComponents ignored) {
            DeploymentPhaseRecorder.phases.add("synthesis");
        }

        @Validation
        public void validation() {
            DeploymentPhaseRecorder.phases.add("validation");
        }
    }

    public static class FailingDiscoveryExtension implements BuildCompatibleExtension {
        @Discovery
        public void discovery() {
            DeploymentPhaseRecorder.phases.add("discovery");
            throw new IllegalStateException("discovery-failure");
        }

        @Enhancement(types = DeploymentRootBean.class)
        public void enhancement(ClassInfo ignored) {
            DeploymentPhaseRecorder.phases.add("enhancement");
        }
    }

    public static class ValidationRecordingExtension implements BuildCompatibleExtension {
        @Validation
        public void validation() {
            DeploymentPhaseRecorder.phases.add("validation");
        }
    }

    public static class ValidationErrorExtension implements BuildCompatibleExtension {
        @Validation
        public void validation(Messages messages) {
            DeploymentPhaseRecorder.phases.add("validation");
            messages.error("validation-problem");
        }
    }
}
