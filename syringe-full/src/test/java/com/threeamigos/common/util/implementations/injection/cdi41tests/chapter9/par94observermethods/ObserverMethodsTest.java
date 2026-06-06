package com.threeamigos.common.util.implementations.injection.cdi41tests.chapter9.par94observermethods;

import com.threeamigos.common.util.implementations.injection.Syringe;
import com.threeamigos.common.util.implementations.injection.discovery.BeanArchiveMode;
import com.threeamigos.common.util.implementations.injection.spi.BeanManagerImpl;
import com.threeamigos.common.util.implementations.injection.util.tx.TransactionServices;
import com.threeamigos.common.util.implementations.injection.util.tx.TransactionSynchronizationCallbacks;
import com.threeamigos.common.util.implementations.messagehandler.InMemoryMessageHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.DefinitionException;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName( "9.4 - Observer methods test")
@Execution(ExecutionMode.SAME_THREAD)
public class ObserverMethodsTest {

    @Test
    @DisplayName("9.4.1 - Observer method can be static or non-static in a managed bean class")
    void shouldSupportStaticAndNonStaticObserverMethods() {
        Recorder.reset();
        Syringe syringe = newSyringe(StaticAndNonStaticObservers.class);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("evt"));

        assertEquals(1, Recorder.count("static"));
        assertEquals(1, Recorder.count("non-static"));
    }

    @Test
    @DisplayName("9.4.1 - There may be arbitrarily many observer methods with the same event type and qualifiers")
    void shouldNotifyManyObserversWithSameEventTypeAndQualifiers() {
        Recorder.reset();
        Syringe syringe = newSyringe(SameQualifiedObserverA.class, SameQualifiedObserverB.class, SameQualifiedObserverC.class);

        syringe.getBeanManager().getEvent()
                .select(SimpleEvent.class, UpdatedLiteral.INSTANCE, ByAdminLiteral.INSTANCE)
                .fire(new SimpleEvent("evt"));

        assertEquals(3, Recorder.countPrefix("same-qualified-"));
    }

    @Test
    @DisplayName("9.4.1 - A bean may declare multiple observer methods")
    void shouldAllowBeanToDeclareMultipleObserverMethods() {
        Recorder.reset();
        Syringe syringe = newSyringe(MultiObserverBean.class);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("s"));
        syringe.getBeanManager().getEvent().select(OtherEvent.class).fire(new OtherEvent("o"));

        assertEquals(1, Recorder.count("multi-simple"));
        assertEquals(1, Recorder.count("multi-other"));
    }

    @Test
    @DisplayName("9.4.1 - Each observer method must have exactly one event parameter")
    void shouldRejectObserverMethodWithMoreThanOneEventParameter() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidMultipleEventParametersBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("9.4.1 - If event parameter declares no qualifier the observer method observes events with no qualifier")
    void shouldNotifyUnqualifiedObserverForUnqualifiedEvent() {
        Recorder.reset();
        Syringe syringe = newSyringe(UnqualifiedObserverBean.class);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("no-qualifier"));

        assertEquals(1, Recorder.count("unqualified"));
    }

    @Test
    @DisplayName("9.4.1 - Event parameter type may contain a wildcard")
    void shouldAllowWildcardEventParameterType() {
        Recorder.reset();
        Syringe syringe = newSyringe(GenericParameterObservers.class);

        syringe.getBeanManager().getEvent().fire(new IntegerPayload(7));

        assertEquals(1, Recorder.count("wildcard-parameter"));
    }

    @Test
    @DisplayName("9.4.1 - Event parameter type may contain a type variable")
    void shouldAllowTypeVariableEventParameterType() {
        Recorder.reset();
        Syringe syringe = newSyringe(GenericParameterObservers.class);

        syringe.getBeanManager().getEvent().fire(Integer.valueOf(42));

        assertEquals(1, Recorder.count("type-variable-parameter"));
    }

    @Test
    @DisplayName("9.4.1 - Event parameter may be an array type whose component type contains wildcard or type variable")
    void shouldAllowArrayEventParameterWithWildcardOrTypeVariableComponent() {
        Recorder.reset();
        Syringe syringe = newSyringe(GenericArrayParameterObservers.class);

        syringe.getBeanManager().getEvent().fire(new IntegerPayload[]{new IntegerPayload(1), new IntegerPayload(2)});
        syringe.getBeanManager().getEvent().fire(new Integer[]{1, 2, 3});

        assertEquals(1, Recorder.count("array-wildcard-component"));
        assertEquals(1, Recorder.count("array-type-variable-component"));
    }

    @Test
    @DisplayName("9.4.1 - Modifications to event parameter in synchronous observer are propagated to following observers")
    void shouldPropagateSynchronousEventMutationsToFollowingObservers() {
        Recorder.reset();
        Syringe syringe = newSyringe(MutableEventObservers.class);

        MutableEvent event = new MutableEvent("initial");
        syringe.getBeanManager().getEvent().select(MutableEvent.class).fire(event);

        assertEquals("changed-by-first", event.message);
        assertTrue(Recorder.contains("first-mutated"));
        assertTrue(Recorder.contains("second-saw:changed-by-first"));
    }

    @Test
    @DisplayName("9.4.1 - Container is not required to guarantee consistent state for event parameter modified by asynchronous observers")
    void shouldAllowAsyncMutationWithoutAssumingConsistencyGuarantee() throws Exception {
        Recorder.reset();
        Syringe syringe = newSyringe(AsyncMutationObservers.class);

        MutableEvent event = new MutableEvent("initial-async");
        syringe.getBeanManager().getEvent().select(MutableEvent.class).fireAsync(event)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(Recorder.countPrefix("async-") >= 2);
    }

    @Test
    @DisplayName("9.4.2 - Observer method may be default-access public protected or private")
    void shouldAllowObserverMethodsAcrossAllSupportedMethodVisibilities() {
        Recorder.reset();
        Syringe syringe = newSyringe(VisibilityObserverBean.class);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("visibility"));

        assertEquals(1, Recorder.count("visibility-default"));
        assertEquals(1, Recorder.count("visibility-public"));
        assertEquals(1, Recorder.count("visibility-protected"));
        assertEquals(1, Recorder.count("visibility-private"));
    }

    @Test
    @DisplayName("9.4.2 - @Observes declares synchronous observer and @ObservesAsync declares asynchronous observer")
    void shouldRouteObservesToSyncAndObservesAsyncToAsync() throws Exception {
        Recorder.reset();
        Syringe syringe = newSyringe(SyncAsyncDeclarationObserverBean.class);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("sync"));
        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fireAsync(new SimpleEvent("async"))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(1, Recorder.count("decl-sync"));
        assertEquals(1, Recorder.count("decl-async"));
    }

    @Test
    @DisplayName("9.4.2 - Event parameter qualifiers are considered when declaring observer method")
    void shouldConsiderObservedEventQualifiersOnEventParameter() {
        Recorder.reset();
        Syringe syringe = newSyringe(QualifiedDeclarationObserverBean.class);

        syringe.getBeanManager().getEvent()
                .select(SimpleEvent.class, UpdatedLiteral.INSTANCE, ByAdminLiteral.INSTANCE)
                .fire(new SimpleEvent("q1"));
        syringe.getBeanManager().getEvent()
                .select(SimpleEvent.class, UpdatedLiteral.INSTANCE)
                .fire(new SimpleEvent("q2"));

        assertEquals(2, Recorder.count("decl-qual-updated"));
        assertEquals(1, Recorder.count("decl-qual-updated-admin"));
    }

    @Test
    @DisplayName("9.4.2 - If a method has a parameter annotated @Observes and @ObservesAsync it is a definition error")
    void shouldRejectParameterAnnotatedWithBothObservesAndObservesAsync() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidMixedObservesOnSameParameterBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("9.4.2 - Observer method annotated @Produces is a definition error")
    void shouldRejectObserverMethodAnnotatedProduces() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidProducesObserverBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("9.4.2 - Observer method annotated @Inject is a definition error")
    void shouldRejectObserverMethodAnnotatedInject() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidInjectObserverBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("9.4.2 - Observer method with @Disposes parameter is a definition error")
    void shouldRejectObserverMethodWithDisposesParameter() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidDisposesObserverBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("9.4.2 - Interceptor may not declare observer methods and container treats it as definition error")
    void shouldRejectInterceptorDeclaringObserverMethod() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidInterceptorObserver.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("9.4.2 - Observer method additional parameters are injection points and may declare qualifiers")
    void shouldInjectAdditionalParametersOfObserverMethod() {
        Recorder.reset();
        Syringe syringe = new Syringe(new InMemoryMessageHandler(),
                ObserverWithInjectedParametersBean.class, ByAdminDependency.class, LoggerBean.class);
        syringe.exclude(InvalidMultipleEventParametersBean.class);
        syringe.exclude(InvalidMixedObservesOnSameParameterBean.class);
        syringe.exclude(InvalidProducesObserverBean.class);
        syringe.exclude(InvalidInjectObserverBean.class);
        syringe.exclude(InvalidDisposesObserverBean.class);
        syringe.exclude(InvalidInterceptorObserver.class);
        syringe.exclude(InvalidEventMetadataFieldInjectionBean.class);
        syringe.exclude(InvalidDependentConditionalObserverBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("invoke"));

        assertTrue(Recorder.contains("decl-extra-params"));
    }

    @Test
    @DisplayName("9.4.3 - Observer method can receive EventMetadata and getQualifiers() returns qualifiers used to fire event")
    void shouldProvideEventMetadataQualifiersToObserverMethod() {
        MetadataRecorder.reset();
        Syringe syringe = newSyringe(EventMetadataObserverBean.class);

        syringe.getBeanManager().getEvent()
                .select(SimpleEvent.class, UpdatedLiteral.INSTANCE, ByAdminLiteral.INSTANCE)
                .fire(new SimpleEvent("meta-qualifiers"));

        Set<String> qualifierTypeNames = MetadataRecorder.lastQualifierTypeNames();
        assertTrue(qualifierTypeNames.contains(Updated.class.getName()));
        assertTrue(qualifierTypeNames.contains(ByAdmin.class.getName()));
    }

    @Test
    @DisplayName("9.4.3 - EventMetadata.getInjectionPoint() is null when event is fired from BeanContainer.getEvent()")
    void shouldProvideNullInjectionPointWhenFiredFromBeanManagerEvent() {
        MetadataRecorder.reset();
        Syringe syringe = newSyringe(EventMetadataObserverBean.class);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("bean-manager-fire"));

        assertNull(MetadataRecorder.lastInjectionPointMemberName());
    }

    @Test
    @DisplayName("9.4.3 - EventMetadata.getInjectionPoint() returns source injection point when event is fired from injected Event")
    void shouldProvideInjectionPointWhenFiredFromInjectedEvent() {
        MetadataRecorder.reset();
        Syringe syringe = new Syringe(new InMemoryMessageHandler(),
                EventMetadataObserverBean.class, EventMetadataRelayObserverBean.class);
        syringe.exclude(InvalidMultipleEventParametersBean.class);
        syringe.exclude(InvalidMixedObservesOnSameParameterBean.class);
        syringe.exclude(InvalidProducesObserverBean.class);
        syringe.exclude(InvalidInjectObserverBean.class);
        syringe.exclude(InvalidDisposesObserverBean.class);
        syringe.exclude(InvalidInterceptorObserver.class);
        syringe.exclude(ObserverWithInjectedParametersBean.class);
        syringe.exclude(ByAdminDependency.class);
        syringe.exclude(LoggerBean.class);
        syringe.exclude(InvalidEventMetadataFieldInjectionBean.class);
        syringe.exclude(InvalidDependentConditionalObserverBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        syringe.getBeanManager().getEvent().select(TriggerEvent.class).fire(new TriggerEvent());

        assertEquals("relay", MetadataRecorder.lastInjectionPointMemberName());
    }

    @Test
    @DisplayName("9.4.3 - EventMetadata.getType() returns runtime event type with type variables resolved")
    void shouldProvideRuntimeEventTypeInMetadata() {
        MetadataRecorder.reset();
        Syringe syringe = newSyringe(EventMetadataObserverBean.class);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new ExtendedSimpleEvent("runtime-subtype"));

        Type lastType = MetadataRecorder.lastType();
        assertNotNull(lastType);
        assertEquals(ExtendedSimpleEvent.class, lastType);
    }

    @Test
    @DisplayName("9.4.3 - Injection point of type EventMetadata and qualifier @Default outside observer method is a definition error")
    void shouldRejectEventMetadataInjectionOutsideObserverMethod() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidEventMetadataFieldInjectionBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("9.4.4 - Conditional synchronous observer with notifyObserver=IF_EXISTS is not notified when bean instance does not exist")
    void shouldSkipConditionalSyncObserverWhenBeanInstanceDoesNotExist() {
        Recorder.reset();
        Syringe syringe = newSyringe(ConditionalSyncObserverBean.class);

        syringe.getBeanManager().getEvent()
                .select(SimpleEvent.class, UpdatedLiteral.INSTANCE)
                .fire(new SimpleEvent("cond-sync"));

        assertEquals(0, Recorder.count("conditional-sync"));
    }

    @Test
    @DisplayName("9.4.4 - Conditional synchronous observer with notifyObserver=IF_EXISTS is notified when bean instance already exists")
    void shouldNotifyConditionalSyncObserverWhenBeanInstanceExists() {
        Recorder.reset();
        Syringe syringe = newSyringe(ConditionalSyncObserverBean.class);
        ensureContextualInstanceExists(syringe, ConditionalSyncObserverBean.class);

        syringe.getBeanManager().getEvent()
                .select(SimpleEvent.class, UpdatedLiteral.INSTANCE)
                .fire(new SimpleEvent("cond-sync-existing"));

        assertEquals(1, Recorder.count("conditional-sync"));
    }

    @Test
    @DisplayName("9.4.4 - Conditional asynchronous observer with notifyObserver=IF_EXISTS is not notified when bean instance does not exist")
    void shouldSkipConditionalAsyncObserverWhenBeanInstanceDoesNotExist() throws Exception {
        Recorder.reset();
        Syringe syringe = newSyringe(ConditionalAsyncObserverBean.class);

        syringe.getBeanManager().getEvent()
                .select(SimpleEvent.class, UpdatedLiteral.INSTANCE)
                .fireAsync(new SimpleEvent("cond-async"))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, Recorder.count("conditional-async"));
    }

    @Test
    @DisplayName("9.4.4 - Conditional asynchronous observer with notifyObserver=IF_EXISTS is notified when bean instance already exists")
    void shouldNotifyConditionalAsyncObserverWhenBeanInstanceExists() throws Exception {
        Recorder.reset();
        Syringe syringe = newSyringe(ConditionalAsyncObserverBean.class);
        ensureContextualInstanceExists(syringe, ConditionalAsyncObserverBean.class);

        syringe.getBeanManager().getEvent()
                .select(SimpleEvent.class, UpdatedLiteral.INSTANCE)
                .fireAsync(new SimpleEvent("cond-async-existing"))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(1, Recorder.count("conditional-async"));
    }

    @Test
    @DisplayName("9.4.4 - Bean with scope @Dependent may not declare conditional observer method notifyObserver=IF_EXISTS")
    void shouldRejectDependentBeanDeclaringConditionalObserver() {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), InvalidDependentConditionalObserverBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);

        assertThrows(DefinitionException.class, syringe::setup);
    }

    @Test
    @DisplayName("9.4.4 - Reception enum identifies allowed notifyObserver values IF_EXISTS and ALWAYS")
    void shouldExposeReceptionEnumValues() {
        assertEquals(Reception.IF_EXISTS, Reception.valueOf("IF_EXISTS"));
        assertEquals(Reception.ALWAYS, Reception.valueOf("ALWAYS"));
    }

    @Test
    @DisplayName("9.4.5 - Transactional observers are notified immediately when no transaction is in progress")
    void shouldNotifyTransactionalObserversImmediatelyWhenNoTransactionIsActive() throws Exception {
        Recorder.reset();
        Syringe syringe = newSyringe(TransactionalObserverBean.class);
        ControlledTransactionServices tx = new ControlledTransactionServices(false, false);
        setTransactionServices(syringe, tx);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("tx-none"));

        assertTrue(Recorder.contains("tx-before"));
        assertTrue(Recorder.contains("tx-after-completion"));
        assertTrue(Recorder.contains("tx-after-success"));
        assertTrue(Recorder.contains("tx-after-failure"));
        assertEquals(0, tx.registerCount);
    }

    @Test
    @DisplayName("9.4.5 - Transactional observers are notified in transaction phases when transaction commits")
    void shouldNotifyTransactionalObserversInCommitPhases() throws Exception {
        Recorder.reset();
        Syringe syringe = newSyringe(TransactionalObserverBean.class);
        ControlledTransactionServices tx = new ControlledTransactionServices(true, false);
        setTransactionServices(syringe, tx);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("tx-commit"));

        assertEquals(1, tx.registerCount);
        assertTrue(Recorder.contains("tx-in-progress"));
        assertEquals(0, Recorder.count("tx-before"));
        assertEquals(0, Recorder.count("tx-after-completion"));
        assertEquals(0, Recorder.count("tx-after-success"));
        assertEquals(0, Recorder.count("tx-after-failure"));

        tx.beforeCompletion();
        assertEquals(1, Recorder.count("tx-before"));
        assertEquals(0, Recorder.count("tx-after-success"));
        assertEquals(0, Recorder.count("tx-after-completion"));

        tx.afterCompletion(true);
        assertEquals(1, Recorder.count("tx-after-success"));
        assertEquals(1, Recorder.count("tx-after-completion"));
        assertEquals(0, Recorder.count("tx-after-failure"));
    }

    @Test
    @DisplayName("9.4.5 - Transactional observers are notified in transaction phases when transaction rolls back")
    void shouldNotifyTransactionalObserversInRollbackPhases() throws Exception {
        Recorder.reset();
        Syringe syringe = newSyringe(TransactionalObserverBean.class);
        ControlledTransactionServices tx = new ControlledTransactionServices(true, false);
        setTransactionServices(syringe, tx);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("tx-rollback"));
        assertEquals(1, tx.registerCount);

        tx.beforeCompletion();
        tx.afterCompletion(false);

        assertEquals(1, Recorder.count("tx-before"));
        assertEquals(1, Recorder.count("tx-after-completion"));
        assertEquals(1, Recorder.count("tx-after-failure"));
        assertEquals(0, Recorder.count("tx-after-success"));
    }

    @Test
    @DisplayName("9.4.5 - If synchronization registration fails, before/after-completion/after-failure run immediately and after-success is skipped")
    void shouldFallbackWhenSynchronizationRegistrationFails() throws Exception {
        Recorder.reset();
        Syringe syringe = newSyringe(TransactionalObserverBean.class);
        ControlledTransactionServices tx = new ControlledTransactionServices(true, true);
        setTransactionServices(syringe, tx);

        syringe.getBeanManager().getEvent().select(SimpleEvent.class).fire(new SimpleEvent("tx-fallback"));

        assertEquals(1, tx.registerCount);
        assertEquals(1, Recorder.count("tx-in-progress"));
        assertEquals(1, Recorder.count("tx-before"));
        assertEquals(1, Recorder.count("tx-after-completion"));
        assertEquals(1, Recorder.count("tx-after-failure"));
        assertEquals(0, Recorder.count("tx-after-success"));
    }

    @Test
    @DisplayName("9.4.5 - TransactionPhase enum identifies allowed transactional observer phases")
    void shouldExposeTransactionPhaseEnumValues() {
        assertEquals(TransactionPhase.IN_PROGRESS, TransactionPhase.valueOf("IN_PROGRESS"));
        assertEquals(TransactionPhase.BEFORE_COMPLETION, TransactionPhase.valueOf("BEFORE_COMPLETION"));
        assertEquals(TransactionPhase.AFTER_COMPLETION, TransactionPhase.valueOf("AFTER_COMPLETION"));
        assertEquals(TransactionPhase.AFTER_FAILURE, TransactionPhase.valueOf("AFTER_FAILURE"));
        assertEquals(TransactionPhase.AFTER_SUCCESS, TransactionPhase.valueOf("AFTER_SUCCESS"));
    }

    @Test
    @DisplayName("9.4.5 - Asynchronous observers cannot be declared transactional")
    void shouldExposeNoTransactionalPhaseOnObservesAsync() throws Exception {
        Method observesDuring = Observes.class.getMethod("during");
        assertNotNull(observesDuring);
        assertThrows(NoSuchMethodException.class, () -> ObservesAsync.class.getMethod("during"));
    }

    private Syringe newSyringe(Class<?>... beanClasses) {
        Syringe syringe = new Syringe(new InMemoryMessageHandler(), beanClasses);
        syringe.exclude(InvalidMultipleEventParametersBean.class);
        syringe.exclude(InvalidMixedObservesOnSameParameterBean.class);
        syringe.exclude(InvalidProducesObserverBean.class);
        syringe.exclude(InvalidInjectObserverBean.class);
        syringe.exclude(InvalidDisposesObserverBean.class);
        syringe.exclude(InvalidInterceptorObserver.class);
        syringe.exclude(ObserverWithInjectedParametersBean.class);
        syringe.exclude(ByAdminDependency.class);
        syringe.exclude(LoggerBean.class);
        syringe.exclude(InvalidEventMetadataFieldInjectionBean.class);
        syringe.exclude(EventMetadataRelayObserverBean.class);
        syringe.exclude(InvalidDependentConditionalObserverBean.class);
        syringe.forceBeanArchiveMode(BeanArchiveMode.EXPLICIT);
        syringe.setup();
        return syringe;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> void ensureContextualInstanceExists(Syringe syringe, Class<T> beanClass) {
        BeanManager beanManager = syringe.getBeanManager();
        Set<Bean<?>> beans = beanManager.getBeans(beanClass);
        Bean<T> bean = (Bean<T>) beanManager.resolve((Set) beans);
        beanManager.getContext(bean.getScope()).get(bean, beanManager.createCreationalContext(bean));
    }

    private void setTransactionServices(Syringe syringe, TransactionServices services) throws Exception {
        BeanManagerImpl beanManager = (BeanManagerImpl) syringe.getBeanManager();
        Object beanResolver = beanManager.getBeanResolver();
        Method setter = beanResolver.getClass().getDeclaredMethod("setTransactionServices", TransactionServices.class);
        setter.setAccessible(true);
        setter.invoke(beanResolver, services);
    }

    @ApplicationScoped
    public static class StaticAndNonStaticObservers {
        static void onStatic(@Observes SimpleEvent event) {
            Recorder.record("static");
        }

        void onNonStatic(@Observes SimpleEvent event) {
            Recorder.record("non-static");
        }
    }

    @ApplicationScoped
    public static class SameQualifiedObserverA {
        void observe(@Observes @Updated @ByAdmin SimpleEvent event) {
            Recorder.record("same-qualified-a");
        }
    }

    @ApplicationScoped
    public static class SameQualifiedObserverB {
        void observe(@Observes @Updated @ByAdmin SimpleEvent event) {
            Recorder.record("same-qualified-b");
        }
    }

    @ApplicationScoped
    public static class SameQualifiedObserverC {
        void observe(@Observes @Updated @ByAdmin SimpleEvent event) {
            Recorder.record("same-qualified-c");
        }
    }

    @ApplicationScoped
    public static class MultiObserverBean {
        void observeSimple(@Observes SimpleEvent event) {
            Recorder.record("multi-simple");
        }

        void observeOther(@Observes OtherEvent event) {
            Recorder.record("multi-other");
        }
    }

    @ApplicationScoped
    public static class InvalidMultipleEventParametersBean {
        void invalid(@Observes SimpleEvent first, @Observes OtherEvent second) {
            Recorder.record("invalid");
        }
    }

    @ApplicationScoped
    public static class UnqualifiedObserverBean {
        void observe(@Observes SimpleEvent event) {
            Recorder.record("unqualified");
        }
    }

    @ApplicationScoped
    public static class GenericParameterObservers {
        void observeWildcard(@Observes GenericPayload<?> event) {
            Recorder.record("wildcard-parameter");
        }

        <T extends Number> void observeTypeVariable(@Observes T event) {
            Recorder.record("type-variable-parameter");
        }
    }

    @ApplicationScoped
    public static class GenericArrayParameterObservers {
        void observeWildcardArray(@Observes GenericPayload<?>[] event) {
            Recorder.record("array-wildcard-component");
        }

        <T extends Number> void observeTypeVariableArray(@Observes T[] event) {
            Recorder.record("array-type-variable-component");
        }
    }

    @ApplicationScoped
    public static class MutableEventObservers {
        @Priority(10)
        void first(@Observes MutableEvent event) {
            event.message = "changed-by-first";
            Recorder.record("first-mutated");
        }

        @Priority(100)
        void second(@Observes MutableEvent event) {
            Recorder.record("second-saw:" + event.message);
        }
    }

    @ApplicationScoped
    public static class AsyncMutationObservers {
        void first(@ObservesAsync MutableEvent event) {
            event.message = "changed-by-async-first";
            Recorder.record("async-first");
        }

        void second(@ObservesAsync MutableEvent event) {
            Recorder.record("async-second:" + event.message);
        }
    }

    @ApplicationScoped
    public static class VisibilityObserverBean {
        void observeDefault(@Observes SimpleEvent event) {
            Recorder.record("visibility-default");
        }

        public void observePublic(@Observes SimpleEvent event) {
            Recorder.record("visibility-public");
        }

        protected void observeProtected(@Observes SimpleEvent event) {
            Recorder.record("visibility-protected");
        }

        private void observePrivate(@Observes SimpleEvent event) {
            Recorder.record("visibility-private");
        }
    }

    @ApplicationScoped
    public static class SyncAsyncDeclarationObserverBean {
        void observeSync(@Observes SimpleEvent event) {
            Recorder.record("decl-sync");
        }

        void observeAsync(@ObservesAsync SimpleEvent event) {
            Recorder.record("decl-async");
        }
    }

    @ApplicationScoped
    public static class QualifiedDeclarationObserverBean {
        void observeUpdated(@Observes @Updated SimpleEvent event) {
            Recorder.record("decl-qual-updated");
        }

        void observeUpdatedByAdmin(@Observes @Updated @ByAdmin SimpleEvent event) {
            Recorder.record("decl-qual-updated-admin");
        }
    }

    @ApplicationScoped
    public static class InvalidMixedObservesOnSameParameterBean {
        void invalid(@Observes @ObservesAsync SimpleEvent event) {
            Recorder.record("invalid-mixed-observes");
        }
    }

    @ApplicationScoped
    public static class InvalidProducesObserverBean {
        @Produces
        String invalid(@Observes SimpleEvent event) {
            return "bad";
        }
    }

    @ApplicationScoped
    public static class InvalidInjectObserverBean {
        @Inject
        void invalid(@Observes SimpleEvent event) {
            Recorder.record("invalid-inject-observer");
        }
    }

    @ApplicationScoped
    public static class InvalidDisposesObserverBean {
        void invalid(@Observes SimpleEvent event, @Disposes String disposed) {
            Recorder.record("invalid-disposes-observer");
        }
    }

    @Interceptor
    public static class InvalidInterceptorObserver {
        @AroundInvoke
        Object around(InvocationContext context) throws Exception {
            return context.proceed();
        }

        void invalidObserver(@Observes SimpleEvent event) {
            Recorder.record("invalid-interceptor-observer");
        }
    }

    @ApplicationScoped
    public static class ObserverWithInjectedParametersBean {
        void observe(@Observes SimpleEvent event, @ByAdmin ByAdminDependency dependency, LoggerBean logger) {
            if (dependency != null && logger != null && logger.name != null) {
                Recorder.record("decl-extra-params");
            }
        }
    }

    @ApplicationScoped
    public static class EventMetadataObserverBean {
        void observe(@Observes SimpleEvent event, EventMetadata metadata) {
            MetadataRecorder.record(metadata);
        }
    }

    @ApplicationScoped
    public static class EventMetadataRelayObserverBean {
        void relay(@Observes TriggerEvent trigger, Event<SimpleEvent> events) {
            events.fire(new SimpleEvent("from-relay"));
        }
    }

    @ApplicationScoped
    public static class InvalidEventMetadataFieldInjectionBean {
        @Inject
        EventMetadata metadata;
    }

    @ApplicationScoped
    public static class ConditionalSyncObserverBean {
        void observe(@Observes(notifyObserver = Reception.IF_EXISTS) @Updated SimpleEvent event) {
            Recorder.record("conditional-sync");
        }

        void touch() {
            // Force contextual instance creation for IF_EXISTS semantics.
        }
    }

    @ApplicationScoped
    public static class ConditionalAsyncObserverBean {
        void observe(@ObservesAsync(notifyObserver = Reception.IF_EXISTS) @Updated SimpleEvent event) {
            Recorder.record("conditional-async");
        }

        void touch() {
            // Force contextual instance creation for IF_EXISTS semantics.
        }
    }

    @ApplicationScoped
    public static class TransactionalObserverBean {
        void inProgress(@Observes(during = TransactionPhase.IN_PROGRESS) SimpleEvent event) {
            Recorder.record("tx-in-progress");
        }

        void before(@Observes(during = TransactionPhase.BEFORE_COMPLETION) SimpleEvent event) {
            Recorder.record("tx-before");
        }

        void afterCompletion(@Observes(during = TransactionPhase.AFTER_COMPLETION) SimpleEvent event) {
            Recorder.record("tx-after-completion");
        }

        void afterSuccess(@Observes(during = TransactionPhase.AFTER_SUCCESS) SimpleEvent event) {
            Recorder.record("tx-after-success");
        }

        void afterFailure(@Observes(during = TransactionPhase.AFTER_FAILURE) SimpleEvent event) {
            Recorder.record("tx-after-failure");
        }
    }

    @Dependent
    public static class InvalidDependentConditionalObserverBean {
        void invalid(@Observes(notifyObserver = Reception.IF_EXISTS) SimpleEvent event) {
            Recorder.record("invalid-dependent-conditional");
        }
    }

    @ApplicationScoped
    @ByAdmin
    public static class ByAdminDependency {
        public ByAdminDependency() {
        }
    }

    @ApplicationScoped
    public static class LoggerBean {
        final String name = "logger";
    }

    public static class Recorder {
        private static final List<String> EVENTS = new ArrayList<String>();

        static synchronized void reset() {
            EVENTS.clear();
        }

        static synchronized void record(String value) {
            EVENTS.add(value);
        }

        static synchronized boolean contains(String value) {
            return EVENTS.contains(value);
        }

        static synchronized int count(String value) {
            int c = 0;
            for (String current : EVENTS) {
                if (value.equals(current)) {
                    c++;
                }
            }
            return c;
        }

        static synchronized int countPrefix(String prefix) {
            int c = 0;
            for (String current : EVENTS) {
                if (current.startsWith(prefix)) {
                    c++;
                }
            }
            return c;
        }
    }

    public static class MetadataRecorder {
        private static Set<String> qualifierTypeNames;
        private static String injectionPointMemberName;
        private static Type type;

        static synchronized void reset() {
            qualifierTypeNames = null;
            injectionPointMemberName = null;
            type = null;
        }

        static synchronized void record(EventMetadata metadata) {
            qualifierTypeNames = new java.util.HashSet<String>();
            for (java.lang.annotation.Annotation qualifier : metadata.getQualifiers()) {
                qualifierTypeNames.add(qualifier.annotationType().getName());
            }
            InjectionPoint injectionPoint = metadata.getInjectionPoint();
            injectionPointMemberName = injectionPoint == null || injectionPoint.getMember() == null
                    ? null
                    : injectionPoint.getMember().getName();
            type = metadata.getType();
        }

        static synchronized Set<String> lastQualifierTypeNames() {
            return qualifierTypeNames == null ? java.util.Collections.<String>emptySet() : qualifierTypeNames;
        }

        static synchronized String lastInjectionPointMemberName() {
            return injectionPointMemberName;
        }

        static synchronized Type lastType() {
            return type;
        }
    }

    public static class SimpleEvent {
        final String value;

        SimpleEvent(String value) {
            this.value = value;
        }
    }

    public static class OtherEvent {
        final String value;

        OtherEvent(String value) {
            this.value = value;
        }
    }

    public static class TriggerEvent {
    }

    public static class ExtendedSimpleEvent extends SimpleEvent {
        ExtendedSimpleEvent(String value) {
            super(value);
        }
    }

    public static class MutableEvent {
        String message;

        MutableEvent(String message) {
            this.message = message;
        }
    }

    public static class GenericPayload<T> {
        final T value;

        GenericPayload(T value) {
            this.value = value;
        }
    }

    public static class IntegerPayload extends GenericPayload<Integer> {
        IntegerPayload(Integer value) {
            super(value);
        }
    }

    @Qualifier
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Updated {
    }

    @Qualifier
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ByAdmin {
    }

    public static class UpdatedLiteral extends AnnotationLiteral<Updated> implements Updated {
        static final UpdatedLiteral INSTANCE = new UpdatedLiteral();
    }

    public static class ByAdminLiteral extends AnnotationLiteral<ByAdmin> implements ByAdmin {
        static final ByAdminLiteral INSTANCE = new ByAdminLiteral();
    }

    private static final class ControlledTransactionServices implements TransactionServices {
        private final boolean active;
        private final boolean failRegistration;
        private TransactionSynchronizationCallbacks callbacks;
        private int registerCount;

        private ControlledTransactionServices(boolean active, boolean failRegistration) {
            this.active = active;
            this.failRegistration = failRegistration;
        }

        @Override
        public boolean isTransactionActive() {
            return active;
        }

        @Override
        public void registerSynchronization(TransactionSynchronizationCallbacks callbacks) {
            registerCount++;
            if (failRegistration) {
                throw new IllegalStateException("cannot register synchronization");
            }
            this.callbacks = callbacks;
        }

        private void beforeCompletion() {
            if (callbacks != null) {
                callbacks.beforeCompletion();
            }
        }

        private void afterCompletion(boolean committed) {
            if (callbacks != null) {
                callbacks.afterCompletion(committed);
            }
        }
    }
}
