package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter22;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.resolution.BeanImpl;
import com.threeamigos.common.util.implementations.injection.spi.spievents.ProcessManagedBeanImpl;
import com.threeamigos.common.util.implementations.injection.spi.wrappers.AnnotatedMethodWrapper;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.invoke.Invoker;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("22 - Method invokers in CDI")
public class MethodInvokersInCDIFullTest {

    private static final Class<?>[] FIXTURE_CLASSES = new Class<?>[]{
            ProcessManagedBeanAnchor.class,
            CaptureAndBuildInvokerExtension.class,
            PrimaryInvokerBean.class,
            SecondaryInvokerBean.class,
            DecoratorContract.class,
            DecoratorTarget.class
    };

    @Test
    @DisplayName("22.1 - Building an invoker for a decorator target bean is a deployment problem")
    void shouldRejectInvokerCreationForDecoratorTargetBean() throws Exception {
        Syringe syringe = newSyringe(ProcessManagedBeanAnchor.class);
        syringe.setup();

        BeanManager beanManager = syringe.getBeanManager();
        BeanImpl<DecoratorTarget> decoratorBean = new BeanImpl<DecoratorTarget>(DecoratorTarget.class, false);
        decoratorBean.setScope(Dependent.class);
        Set<Type> types = new HashSet<Type>();
        types.add(DecoratorTarget.class);
        types.add(DecoratorContract.class);
        types.add(Object.class);
        decoratorBean.setTypes(types);

        AnnotatedType<DecoratorTarget> annotatedType = beanManager.createAnnotatedType(DecoratorTarget.class);
        ProcessManagedBeanImpl<DecoratorTarget> event = new ProcessManagedBeanImpl<DecoratorTarget>(
                new InMemoryMessageHandler(),
                syringe.getKnowledgeBase(),
                decoratorBean,
                annotatedType,
                beanManager
        );

        Method method = DecoratorTarget.class.getDeclaredMethod("action", String.class);
        AnnotatedMethod<? super DecoratorTarget> wrapped = new AnnotatedMethodWrapper(method, annotatedType);

        assertThrows(DefinitionException.class, () -> event.createInvoker(wrapped));
    }

    @Test
    @DisplayName("22.2 - Portable extensions obtain InvokerBuilder from ProcessManagedBean.createInvoker() and invoker targets that bean and method")
    void shouldCreateInvokerInProcessManagedBeanForTargetBeanAndMethod() throws Exception {
        CapturedInvokers.reset();
        Syringe syringe = newSyringe(PrimaryInvokerBean.class, SecondaryInvokerBean.class);
        syringe.addExtension(CaptureAndBuildInvokerExtension.class.getName());
        syringe.setup();

        Invoker primaryInvoker = CapturedInvokers.get(PrimaryInvokerBean.class);
        Invoker secondaryInvoker = CapturedInvokers.get(SecondaryInvokerBean.class);
        assertNotNull(primaryInvoker);
        assertNotNull(secondaryInvoker);

        PrimaryInvokerBean primary = getBeanInstance(syringe, PrimaryInvokerBean.class);
        SecondaryInvokerBean secondary = getBeanInstance(syringe, SecondaryInvokerBean.class);

        assertEquals("primary:x", primaryInvoker.invoke(primary, "x"));
        assertEquals("secondary:y", secondaryInvoker.invoke(secondary, "y"));
        assertThrows(IllegalArgumentException.class, () -> primaryInvoker.invoke(secondary, "z"));
    }

    @Test
    @DisplayName("22.2 - InvokerBuilder.build() produces invoker stored by extension and usable at runtime")
    void shouldUseInvokerBuiltDuringBootstrapAtRuntime() throws Exception {
        CapturedInvokers.reset();
        Syringe syringe = newSyringe(PrimaryInvokerBean.class);
        syringe.addExtension(CaptureAndBuildInvokerExtension.class.getName());
        syringe.setup();

        Invoker invoker = CapturedInvokers.get(PrimaryInvokerBean.class);
        assertNotNull(invoker);

        PrimaryInvokerBean bean = getBeanInstance(syringe, PrimaryInvokerBean.class);
        assertEquals("primary:runtime", invoker.invoke(bean, "runtime"));
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        for (Class<?> fixture : FIXTURE_CLASSES) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        return syringe;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T getBeanInstance(Syringe syringe, Class<T> beanClass) {
        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(beanClass);
        Bean<T> bean = (Bean<T>) beanManager.resolve((Set) beans);
        return (T) beanManager.getContext(bean.getScope()).get(bean, beanManager.createCreationalContext(bean));
    }

    public static class ProcessManagedBeanAnchor {
    }

    @ApplicationScoped
    public static class PrimaryInvokerBean {
        public String action(String value) {
            return "primary:" + value;
        }
    }

    @ApplicationScoped
    public static class SecondaryInvokerBean {
        public String action(String value) {
            return "secondary:" + value;
        }
    }

    public interface DecoratorContract {
        String action(String value);
    }

    @Decorator
    public static class DecoratorTarget implements DecoratorContract {
        @Inject
        @Delegate
        DecoratorContract delegate;

        @Override
        public String action(String value) {
            return delegate.action(value);
        }
    }

    public static class CaptureAndBuildInvokerExtension implements Extension {
        public void capture(@Observes ProcessManagedBean<?> processManagedBean) {
            Class<?> beanClass = processManagedBean.getBean().getBeanClass();
            try {
                if (PrimaryInvokerBean.class.equals(beanClass)) {
                    Method method = PrimaryInvokerBean.class.getDeclaredMethod("action", String.class);
                    AnnotatedMethodWrapper wrapper = new AnnotatedMethodWrapper(method, processManagedBean.getAnnotatedBeanClass());
                    Invoker invoker = (Invoker) processManagedBean.createInvoker(wrapper).build();
                    CapturedInvokers.put(beanClass, invoker);
                } else if (SecondaryInvokerBean.class.equals(beanClass)) {
                    Method method = SecondaryInvokerBean.class.getDeclaredMethod("action", String.class);
                    AnnotatedMethodWrapper wrapper = new AnnotatedMethodWrapper(method, processManagedBean.getAnnotatedBeanClass());
                    Invoker invoker = (Invoker) processManagedBean.createInvoker(wrapper).build();
                    CapturedInvokers.put(beanClass, invoker);
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class CapturedInvokers {
        private static final Map<Class<?>, Invoker> INVOKERS =
                Collections.synchronizedMap(new HashMap<Class<?>, Invoker>());

        static void reset() {
            INVOKERS.clear();
        }

        static void put(Class<?> beanClass, Invoker invoker) {
            INVOKERS.put(beanClass, invoker);
        }

        static Invoker get(Class<?> beanClass) {
            return INVOKERS.get(beanClass);
        }
    }
}
