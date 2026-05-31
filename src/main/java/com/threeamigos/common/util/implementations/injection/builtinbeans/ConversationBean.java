package com.threeamigos.common.util.implementations.injection.builtinbeans;

import com.threeamigos.common.util.implementations.injection.decorators.DecoratorAwareProxyGenerator;
import com.threeamigos.common.util.implementations.injection.decorators.DecoratorChain;
import com.threeamigos.common.util.implementations.injection.knowledgebase.DecoratorInfo;
import com.threeamigos.common.util.implementations.injection.scopes.ConversationImpl;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import jakarta.enterprise.context.Conversation;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.Decorator;
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
 * the ConversationImpl uses ThreadLocal to maintain the per-request conversation state,
 * making it effectively request-scoped in behavior while being a singleton in the container.
 *
 * @author Stefano Reksten
 * @see Conversation
 * @see ConversationImpl
 */
public class ConversationBean implements Bean<Conversation> {

    private final BeanManagerImpl beanManager;

    public ConversationBean(BeanManagerImpl beanManager) {
        this.beanManager = beanManager;
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
        Conversation conversation = new ConversationImpl();
        try {
            jakarta.enterprise.inject.spi.BeanManager beanManager = resolveBeanManager();
            if (beanManager == null) {
                return conversation;
            }
            Set<Type> types = new HashSet<>();
            types.add(Conversation.class);
            types.add(Object.class);
            List<Decorator<?>> decorators = beanManager.resolveDecorators(types, Default.Literal.INSTANCE);
            if (decorators.isEmpty() || !(beanManager instanceof BeanManagerImpl)) {
                return conversation;
            }
            BeanManagerImpl beanManagerImpl = (BeanManagerImpl) beanManager;
            List<DecoratorInfo> infos = new ArrayList<>();
            for (Decorator<?> decorator : decorators) {
                DecoratorInfo info = findDecoratorInfoByClass(
                        beanManagerImpl.getKnowledgeBase().getDecoratorInfos(),
                        decorator.getBeanClass());
                if (info != null) {
                    infos.add(info);
                }
            }
            if (infos.isEmpty()) {
                return conversation;
            }
            DecoratorChain chain = new DecoratorAwareProxyGenerator()
                    .createDecoratorChain(conversation, infos, beanManager, context);
            return (Conversation) chain.getOutermostInstance();
        } catch (Exception ignored) {
            // Built-in conversation remains usable even if decorator wrapping fails.
            return conversation;
        }
    }

    @Override
    public void destroy(Conversation instance, CreationalContext<Conversation> context) {
        // Singleton instance is never destroyed
        // ThreadLocal cleanup should happen at the end of the request via ConversationImpl.clearCurrentConversation()
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

    private DecoratorInfo findDecoratorInfoByClass(Collection<DecoratorInfo> infos, Class<?> decoratorClass) {
        for (DecoratorInfo info : infos) {
            if (info.getDecoratorClass().equals(decoratorClass)) {
                return info;
            }
        }
        return null;
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
}
