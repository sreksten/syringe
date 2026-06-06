package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter10.par101buildinganinvoker;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.discovery.NonPortableBehaviourException;
import com.threeamigos.common.util.implementations.injection.spi.wrappers.AnnotatedMethodWrapper;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Typed;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.invoke.Invoker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("10.1 - Building an Invoker")
@Execution(ExecutionMode.SAME_THREAD)
public class BuildingAnInvokerTest {

    @Test
    @DisplayName("10.1 - Container builds an invoker for a method of an enabled managed bean")
    void shouldBuildInvokerForManagedBeanMethod() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(InvokableManagedBean.class);
        syringe.setup();

        ProcessManagedBean<?> pmb = CapturedManagedBeans.get(InvokableManagedBean.class);
        Invoker invoker = createInvoker(pmb, InvokableManagedBean.class.getDeclaredMethod("target", String.class));

        InvokableManagedBean bean = getBeanInstance(syringe, InvokableManagedBean.class);
        assertEquals("invoked:hello", invoker.invoke(bean, "hello"));
    }

    @Test
    @DisplayName("10.1 - Building invoker for private target method leads to deployment problem")
    void shouldRejectPrivateTargetMethod() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(InvalidTargetMethodsBean.class);
        syringe.setup();

        ProcessManagedBean<?> pmb = CapturedManagedBeans.get(InvalidTargetMethodsBean.class);
        Method privateMethod = InvalidTargetMethodsBean.class.getDeclaredMethod("privateTarget");

        assertThrows(DefinitionException.class, () -> createInvoker(pmb, privateMethod));
    }

    @Test
    @DisplayName("10.1 - Building invoker for java.lang.Object method except toString leads to deployment problem")
    void shouldRejectObjectMethodOtherThanToString() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(InvokableManagedBean.class);
        syringe.setup();

        ProcessManagedBean<?> pmb = CapturedManagedBeans.get(InvokableManagedBean.class);
        Method hashCodeMethod = Object.class.getMethod("hashCode");
        assertThrows(DefinitionException.class, () -> createInvoker(pmb, hashCodeMethod));
    }

    @Test
    @DisplayName("10.1 - Building invoker for java.lang.Object.toString is allowed")
    void shouldAllowObjectToStringTargetMethod() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(InvokableManagedBean.class);
        syringe.setup();

        ProcessManagedBean<?> pmb = CapturedManagedBeans.get(InvokableManagedBean.class);
        Method toStringMethod = Object.class.getMethod("toString");
        Invoker invoker = createInvoker(pmb, toStringMethod);

        assertNotNull(invoker);
        InvokableManagedBean bean = getBeanInstance(syringe, InvokableManagedBean.class);
        assertNotNull(invoker.invoke(bean));
    }

    @Test
    @DisplayName("10.1 - Building invoker for method not declared on bean class or inherited supertypes leads to deployment problem")
    void shouldRejectMethodNotDeclaredOnBeanClassHierarchy() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(InvokableManagedBean.class);
        syringe.setup();

        ProcessManagedBean<?> pmb = CapturedManagedBeans.get(InvokableManagedBean.class);
        Method unrelated = UnrelatedType.class.getDeclaredMethod("unrelated");
        assertThrows(DefinitionException.class, () -> createInvoker(pmb, unrelated));
    }

    @Test
    @DisplayName("10.1 - Building invoker for non-static method declared on type not present in bean types is non-portable")
    void shouldRejectDeclaringTypeNotPresentInBeanTypes() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(TypedBean.class);
        syringe.setup();

        ProcessManagedBean<?> pmb = CapturedManagedBeans.get(TypedBean.class);
        Method typedMethod = TypedBean.class.getDeclaredMethod("typedMethod");
        assertThrows(NonPortableBehaviourException.class, () -> createInvoker(pmb, typedMethod));
    }

    @Test
    @DisplayName("10.1 - For normal-scoped target bean, non-static target method on unproxyable bean type is non-portable")
    void shouldRejectUnproxyableDeclaringTypeForNormalScopedBean() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(FinalNormalScopedBean.class);
        syringe.setup();

        ProcessManagedBean<?> pmb = CapturedManagedBeans.get(FinalNormalScopedBean.class);
        Method ping = FinalNormalScopedBean.class.getDeclaredMethod("ping");
        assertThrows(NonPortableBehaviourException.class, () -> createInvoker(pmb, ping));
    }

    @Test
    @DisplayName("10.1 - Invoker built for one target bean cannot be used to invoke inherited method on another target bean instance")
    void shouldBindInvokerToEachTargetBeanIndividually() throws Exception {
        CapturedManagedBeans.reset();
        Syringe syringe = newSyringe(ChildBeanA.class, ChildBeanB.class);
        syringe.setup();

        Method shared = ParentType.class.getDeclaredMethod("shared");
        Invoker invokerA = createInvoker(CapturedManagedBeans.get(ChildBeanA.class), shared);
        Invoker invokerB = createInvoker(CapturedManagedBeans.get(ChildBeanB.class), shared);

        ChildBeanA a = getBeanInstance(syringe, ChildBeanA.class);
        ChildBeanB b = getBeanInstance(syringe, ChildBeanB.class);

        assertEquals("A", invokerA.invoke(a));
        assertEquals("B", invokerB.invoke(b));
        assertThrows(IllegalArgumentException.class, () -> invokerA.invoke(b));
        assertThrows(IllegalArgumentException.class, () -> invokerB.invoke(a));
    }

    @Test
    @DisplayName("10.1 - InvokerBuilder is not exposed to application runtime API through BeanManager")
    void shouldNotExposeInvokerBuilderThroughBeanManager() {
        boolean hasInvokerBuilderFactory = Arrays.stream(BeanManager.class.getMethods())
                .anyMatch(method -> method.getReturnType().getName().contains("InvokerBuilder"));
        assertEquals(false, hasInvokerBuilderFactory);
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.addExtension(CaptureManagedBeanExtension.class.getName());
        Set<Class<?>> included = new HashSet<Class<?>>(Arrays.asList(beanClasses));
        Class<?>[] allFixtures = new Class<?>[]{
                InvokableManagedBean.class,
                InvalidTargetMethodsBean.class,
                UnrelatedType.class,
                TypedBean.class,
                FinalNormalScopedBean.class,
                ChildBeanA.class,
                ChildBeanB.class
        };
        for (Class<?> fixture : allFixtures) {
            if (!included.contains(fixture)) {
                syringe.exclude(fixture);
            }
        }
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        return syringe;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Invoker createInvoker(ProcessManagedBean<?> pmb, Method javaMethod) {
        AnnotatedMethodWrapper wrapper = new AnnotatedMethodWrapper(javaMethod, pmb.getAnnotatedBeanClass());
        return (Invoker) pmb.createInvoker(wrapper).build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T getBeanInstance(Syringe syringe, Class<T> beanClass) {
        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(beanClass);
        Bean<T> bean = (Bean<T>) beanManager.resolve((Set) beans);
        return (T) beanManager.getContext(bean.getScope()).get(bean, beanManager.createCreationalContext(bean));
    }

    public static class CaptureManagedBeanExtension implements Extension {
        public void capture(@Observes ProcessBean<?> processBean) {
            if (processBean instanceof ProcessManagedBean) {
                ProcessManagedBean<?> pmb = (ProcessManagedBean<?>) processBean;
                CapturedManagedBeans.put(pmb.getBean().getBeanClass(), pmb);
            }
        }
    }

    public static class CapturedManagedBeans {
        private static final Map<Class<?>, ProcessManagedBean<?>> BEANS =
                new HashMap<Class<?>, ProcessManagedBean<?>>();

        static synchronized void reset() {
            BEANS.clear();
        }

        static synchronized void put(Class<?> beanClass, ProcessManagedBean<?> pmb) {
            BEANS.put(beanClass, pmb);
        }

        static synchronized ProcessManagedBean<?> get(Class<?> beanClass) {
            return BEANS.get(beanClass);
        }
    }

    @ApplicationScoped
    public static class InvokableManagedBean {
        public String target(String value) {
            return "invoked:" + value;
        }
    }

    @ApplicationScoped
    public static class InvalidTargetMethodsBean {
        private String privateTarget() {
            return "private";
        }
    }

    public static class UnrelatedType {
        public String unrelated() {
            return "x";
        }
    }

    public interface TypedApi {
        String api();
    }

    @Typed(TypedApi.class)
    @ApplicationScoped
    public static class TypedBean implements TypedApi {
        public String api() {
            return "api";
        }

        public String typedMethod() {
            return "typed";
        }
    }

    @ApplicationScoped
    public static final class FinalNormalScopedBean {
        public String ping() {
            return "pong";
        }
    }

    public static class ParentType {
        public String shared() {
            return "parent";
        }
    }

    @ApplicationScoped
    public static class ChildBeanA extends ParentType {
        @Override
        public String shared() {
            return "A";
        }
    }

    @ApplicationScoped
    public static class ChildBeanB extends ParentType {
        @Override
        public String shared() {
            return "B";
        }
    }
}
