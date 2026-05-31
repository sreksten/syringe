package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter10.par103usinginvokerbuilder;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.bce.BceMetadata;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.Messages;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("10.3.1 - withArgumentLookup bounds regression")
class BadArgumentLookupGreaterThanNumberOfParamsRegressionTest {

    @Test
    @DisplayName("10.3.1 - Argument lookup position greater than parameter count is a deployment problem")
    void shouldFailDeploymentWhenArgumentLookupPositionIsGreaterThanNumberOfParameters() {
        Syringe syringe = new Syringe();
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.addBuildCompatibleExtension(BadArgumentLookupExtension.class.getName());
        try {
            syringe.initialize();
            syringe.addDiscoveredClass(MyService.class, BeanArchiveMode.EXPLICIT);
            syringe.addDiscoveredClass(MyDependency.class, BeanArchiveMode.EXPLICIT);

            assertThrows(DefinitionException.class, syringe::start);
        } finally {
            syringe.shutdown();
        }
    }

    @ApplicationScoped
    public static class MyDependency {
        public int getId() {
            return 0;
        }
    }

    @ApplicationScoped
    public static class MyService {
        public String hello(MyDependency dependency) {
            return "foobar" + dependency.getId();
        }
    }

    public static class BadArgumentLookupExtension implements BuildCompatibleExtension {
        @Registration(types = MyService.class)
        public void registration(BeanInfo bean, InvokerFactory invokers, Messages messages) {
            try {
                invokers.createInvoker(
                        bean,
                        BceMetadata.methodInfo(method(MyService.class, "hello", MyDependency.class))
                ).withArgumentLookup(2);
            } catch (IllegalArgumentException expected) {
                messages.error(expected);
            }
        }

        private static java.lang.reflect.Method method(Class<?> owner, String name, Class<?>... parameterTypes) {
            try {
                return owner.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
