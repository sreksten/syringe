package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter15.par151specializingmanagedbean;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Specializes;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("15.1 - Specializing a Managed Bean")
@Execution(ExecutionMode.SAME_THREAD)
public class SpecializingManagedBeanTest {

    @Test
    @DisplayName("15.1 - If bean X is annotated @Specializes and directly extends managed bean Y, X directly specializes Y")
    public void shouldAllowDirectSpecializationOfManagedBean() {
        Syringe syringe = newSyringe(BaseService.class, SpecializedService.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(BaseService.class);
        assertEquals(1, beans.size());
        Bean<?> resolved = beanManager.resolve((Set) beans);
        assertNotNull(resolved);
        assertEquals(SpecializedService.class, resolved.getBeanClass());
    }

    @Test
    @DisplayName("15.1 - If @Specializes bean X does not directly extend another managed bean, container treats it as a definition error")
    public void shouldFailWhenSpecializingBeanDoesNotDirectlyExtendManagedBean() {
        Syringe syringe = newSyringeIncludingInvalid(InvalidSpecializingBean.class);
        assertThrows(DefinitionException.class, syringe::setup);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.exclude(InvalidSpecializingBean.class);
        return syringe;
    }

    private Syringe newSyringeIncludingInvalid(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @Dependent
    public static class BaseService {
    }

    @Dependent
    @Specializes
    public static class SpecializedService extends BaseService {
    }

    @Dependent
    @Specializes
    public static class InvalidSpecializingBean {
    }
}
