package com.threeamigos.common.util.implementations.injection.builtinbeans;

import com.threeamigos.common.util.implementations.injection.decorators.DecoratorSupport;
import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.injection.scopes.ScopeSupport;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import jakarta.enterprise.context.Conversation;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Built-in bean for injecting the Conversation object for conversation scope management.
 *
 * <p>CDI 4.1 Specification (Section 6.7.4) requires that the Conversation
 * is available for injection to allow programmatic conversation lifecycle control:
 * <pre>{@code
 * @Inject Conversation conversation;
 *
 * public String startWizard() {
 *     conversation.begin();
 *     return "wizard-step1";
 * }
 *
 * public String completeWizard() {
 *     conversation.end();
 *     return "wizard-complete";
 * }
 * }</pre>
 *
 * <p><b>Conversation Usage:</b>
 * <ul>
 *   <li>Inject Conversation to control the conversation lifecycle</li>
 *   <li>Call {@code begin()} to promote from transient to long-running</li>
 *   <li>Call {@code end()} to terminate and destroy conversation-scoped beans</li>
 *   <li>Set timeout via {@code setTimeout()} to control an inactivity period</li>
 * </ul>
 *
 * <p><b>Bean Characteristics:</b>
 * <ul>
 *   <li>Type: Conversation</li>
 *   <li>Scope: @ApplicationScoped (singleton, but uses ThreadLocal internally)</li>
 *   <li>Qualifiers: @Default, @Any</li>
 *   <li>Stereotypes: None</li>
 *   <li>Alternative: No</li>
 * </ul>
 *
 * <p><b>Implementation Note:</b> Although the bean is @ApplicationScoped (singleton),
 * the concrete conversation implementation uses ThreadLocal per-request state internally,
 * making it effectively request-scoped in behavior while being a singleton in the container.
 *
 * @author Stefano Reksten
 * @see Conversation
 */
public class ConversationBean implements Bean<Conversation> {

    private final BeanManagerImpl beanManager;
    private final ScopeSupport scopeSupport;

    public ConversationBean(BeanManagerImpl beanManager, ScopeSupport scopeSupport) {
        this.beanManager = beanManager;
        this.scopeSupport = scopeSupport;
    }

    @Override
    public Class<?> getBeanClass() {
        return Conversation.class;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet(); // Built-in beans have no injection points
    }

    @Override
    public Conversation create(CreationalContext<Conversation> context) {
        Conversation conversation = createConversationInstance();
        try {
            jakarta.enterprise.inject.spi.BeanManager beanManager = resolveBeanManager();
            if (!(beanManager instanceof BeanManagerImpl)) {
                return conversation;
            }
            BeanManagerImpl beanManagerImpl = (BeanManagerImpl) beanManager;
            DecoratorSupport decoratorSupport = beanManagerImpl.getDecoratorSupport();
            if (decoratorSupport == null) {
                return conversation;
            }

            Set<Type> types = new HashSet<>();
            types.add(Conversation.class);
            types.add(Object.class);
            Set<Annotation> qualifiers = new HashSet<>();
            qualifiers.add(Default.Literal.INSTANCE);
            qualifiers.add(Any.Literal.INSTANCE);
            List<DecoratorInfo> infos = decoratorSupport.resolve(types, qualifiers);
            if (infos.isEmpty()) {
                return conversation;
            }
            Object decorated = decoratorSupport.applyDecoratorChain(conversation, infos, beanManagerImpl, context);
            return decorated instanceof Conversation ? (Conversation) decorated : conversation;
        } catch (Exception ignored) {
            // Built-in conversation remains usable even if decorator wrapping fails.
            return conversation;
        }
    }

    @Override
    public void destroy(Conversation instance, CreationalContext<Conversation> context) {
        // Singleton instance is never destroyed
        // Thread-local cleanup is handled by normal scope support during request lifecycle teardown.
        if (context != null) {
            context.release();
        }
    }

    @Override
    public Set<Type> getTypes() {
        Set<Type> types = new HashSet<>();
        types.add(Conversation.class);
        types.add(Object.class);
        return types;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        Set<Annotation> qualifiers = new HashSet<>();
        qualifiers.add(Default.Literal.INSTANCE);
        qualifiers.add(Any.Literal.INSTANCE);
        return qualifiers;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return RequestScoped.class;
    }

    @Override
    public String getName() {
        return "jakarta.enterprise.context.conversation";
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.emptySet();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

    @Override
    public String toString() {
        return "ConversationBean[type=Conversation, scope=@RequestScoped, qualifiers=@Default]";
    }

    private jakarta.enterprise.inject.spi.BeanManager resolveBeanManager() {
        if (beanManager != null) {
            return beanManager;
        }

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ConversationBean.class.getClassLoader();
        }
        BeanManagerImpl registered = BeanManagerImpl.getRegisteredBeanManager(classLoader);
        if (registered != null) {
            return registered;
        }

        try {
            return CDI.current().getBeanManager();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Conversation createConversationInstance() {
        ScopeSupport activeScopeSupport = scopeSupport;
        if (activeScopeSupport == null && beanManager != null) {
            activeScopeSupport = beanManager.getScopeSupport();
        }
        if (activeScopeSupport == null) {
            throw new IllegalStateException("Scope support is not configured");
        }
        return activeScopeSupport.createConversation();
    }
}
