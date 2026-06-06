package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter6.par63normalscopespseudoscopes;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.scopes.ContextManager;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ConversationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Scope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("6.3 - Normal scopes and pseudo-scopes")
public class NormalScopesPseudoScopesTest {

    @Test
    @DisplayName("6.3 - Normal scope maps one current instance per contextual type and thread")
    void shouldShareCurrentInstanceForNormalScopedDependencyAcrossBeans() {
        Syringe syringe = newSyringe(SharedNormalScopedBean.class);

        BeanManager beanManager = syringe.getBeanManager();
        Context appContext = beanManager.getContext(ApplicationScoped.class);
        Bean<SharedNormalScopedBean> bean = resolveBean(beanManager, SharedNormalScopedBean.class);
        SharedNormalScopedBean first = appContext.get(bean, beanManager.createCreationalContext(bean));
        SharedNormalScopedBean second = appContext.get(bean, beanManager.createCreationalContext(bean));

        assertEquals(first.id(), second.id());
    }

    @Test
    @DisplayName("6.3 - Pseudo-scope does not provide a shared current instance semantics")
    void shouldNotShareDependentPseudoScopedInstancesAcrossBeans() {
        Syringe syringe = newSyringe(DependentPseudoScopedBean.class);

        BeanManager beanManager = syringe.getBeanManager();
        Context dependentContext = beanManager.getContext(Dependent.class);
        Bean<DependentPseudoScopedBean> bean = resolveBean(beanManager, DependentPseudoScopedBean.class);
        DependentPseudoScopedBean first = dependentContext.get(bean, beanManager.createCreationalContext(bean));
        DependentPseudoScopedBean second = dependentContext.get(bean, beanManager.createCreationalContext(bean));

        assertNotSame(first, second);
    }

    @Test
    @DisplayName("6.3 - Destroying a normal context destroys all mapped instances")
    void shouldDestroyMappedInstancesWhenRequestContextIsDestroyed() {
        ApplicationScopedLifecycleBean.reset();
        Syringe syringe = newSyringe(ApplicationScopedLifecycleBean.class);
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        ContextManager contextManager = beanManager.getContextManager();
        Context appContext = beanManager.getContext(ApplicationScoped.class);
        Bean<ApplicationScopedLifecycleBean> bean = resolveBean(beanManager, ApplicationScopedLifecycleBean.class);

        appContext.get(bean, beanManager.createCreationalContext(bean));
        assertEquals(0, ApplicationScopedLifecycleBean.preDestroyCalls());

        contextManager.destroyAll();
        assertEquals(1, ApplicationScopedLifecycleBean.preDestroyCalls());
    }

    @Test
    @DisplayName("6.3 - Built-in scopes are classified as normal except @Dependent pseudo-scope")
    void shouldClassifyBuiltInScopesAsNormalOrPseudo() {
        Syringe syringe = newSyringe(SharedNormalScopedBean.class);
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();

        assertTrue(beanManager.isNormalScope(ApplicationScoped.class));
        assertTrue(beanManager.isNormalScope(RequestScoped.class));
        assertTrue(beanManager.isNormalScope(SessionScoped.class));
        assertTrue(beanManager.isNormalScope(ConversationScoped.class));
        assertFalse(beanManager.isNormalScope(Dependent.class));
    }

    @Test
    @DisplayName("6.3 - Built-in normal scopes are declared with @NormalScope and @Dependent with @Scope")
    void shouldHaveExpectedScopeMetaAnnotations() {
        assertTrue(ApplicationScoped.class.isAnnotationPresent(NormalScope.class));
        assertTrue(RequestScoped.class.isAnnotationPresent(NormalScope.class));
        assertTrue(SessionScoped.class.isAnnotationPresent(NormalScope.class));
        assertTrue(ConversationScoped.class.isAnnotationPresent(NormalScope.class));
        assertTrue(Dependent.class.isAnnotationPresent(Scope.class));
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T reference(BeanManager beanManager, Class<T> type) {
        Bean<T> bean = resolveBean(beanManager, type);
        CreationalContext<T> ctx = beanManager.createCreationalContext(bean);
        return (T) beanManager.getReference(bean, type, ctx);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> Bean<T> resolveBean(BeanManager beanManager, Class<T> type) {
        return (Bean<T>) beanManager.resolve((Set) beanManager.getBeans(type));
    }

}
