package com.threeamigos.common.util.implementations.injection;

import com.threeamigos.common.util.implementations.injection.annotations.DefaultLiteral;
import com.threeamigos.common.util.implementations.injection.resolution.InstanceImpl;
import jakarta.enterprise.inject.*;
import jakarta.enterprise.inject.spi.Bean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("InstanceImpl unit tests")
class InstanceImplUnitTest {

    @Nested
    @DisplayName("mergeQualifiers tests")
    class MergeQualifiersTests {

        /**
         * Helper method to invoke the private mergeQualifiers method via reflection
         */
        @SuppressWarnings("unchecked")
        private Collection<Annotation> invokeMergeQualifiers(InstanceImpl<?> wrapper,
                                                             Collection<Annotation> existing,
                                                             Annotation... newAnnotations) throws Exception {
            Method method = InstanceImpl.class.getDeclaredMethod("mergeQualifiers", Collection.class, Annotation[].class);
            method.setAccessible(true);
            return (Collection<Annotation>) method.invoke(wrapper, existing, newAnnotations);
        }

        private InstanceImpl<String> createTestWrapper() {
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            return new InstanceImpl<>(String.class, Collections.singletonList(new DefaultLiteral()), mockStrategy);
        }

        @Test
        @DisplayName("mergeQualifiers should return existing if newAnnotations is null")
        void mergeQualifiersShouldReturnExistingIfNewAnnotationsIsNull() throws Exception {
            // Given
            InstanceImpl<String> wrapper = createTestWrapper();
            Collection<Annotation> existing = Collections.singletonList(new NamedLiteral("test"));
            Annotation[] newAnnotations = null;

            // When
            Collection<Annotation> merged = invokeMergeQualifiers(wrapper, existing, newAnnotations);

            // Then
            assertSame(existing, merged);
        }

        @Test
        @DisplayName("mergeQualifiers should return existing if newAnnotations is empty")
        void mergeQualifiersShouldReturnExistingIfNewAnnotationsIsEmpty() throws Exception {
            // Given
            InstanceImpl<String> wrapper = createTestWrapper();
            Collection<Annotation> existing = Collections.singletonList(new NamedLiteral("test"));
            Annotation[] newAnnotations = {};

            // When
            Collection<Annotation> merged = invokeMergeQualifiers(wrapper, existing, newAnnotations);

            // Then
            assertSame(existing, merged);
        }

        @Test
        @DisplayName("mergeQualifiers should merge existing qualifiers with new ones")
        void mergeQualifiersShouldMergeExistingQualifiersWithNewOnes() throws Exception {
            // Given
            InstanceImpl<String> wrapper = createTestWrapper();
            Collection<Annotation> existing = Collections.singletonList(new NamedLiteral("test"));
            Annotation[] newAnnotations = {new NamedLiteral("test2")};

            // When
            Collection<Annotation> merged = invokeMergeQualifiers(wrapper, existing, newAnnotations);

            // Then
            assertEquals(1, merged.size());
            assertFalse(merged.contains(new NamedLiteral("test")));
            assertTrue(merged.contains(new NamedLiteral("test2")));
        }

        @Test
        @DisplayName("mergeQualifiers should keep Default and add specific qualifiers")
        void mergeQualifiersShouldKeepDefault() throws Exception {
            // Given
            InstanceImpl<String> wrapper = createTestWrapper();
            Collection<Annotation> existing = Collections.singletonList(new DefaultLiteral());
            Annotation[] newAnnotations = {new NamedLiteral("test2")};

            // When
            Collection<Annotation> merged = invokeMergeQualifiers(wrapper, existing, newAnnotations);

            // Then
            assertEquals(2, merged.size());
            assertTrue(merged.contains(new DefaultLiteral()));
            assertTrue(merged.contains(new NamedLiteral("test2")));
        }
    }

    @Nested
    @DisplayName("Basic functionality tests")
    class BasicFunctionalityTests {

        @Test
        @DisplayName("Constructor should require non-null parameters")
        void constructorShouldRequireNonNullParameters() {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());

            // Then
            assertThrows(NullPointerException.class,
                () -> new InstanceImpl<>(null, qualifiers, mockStrategy));
            assertThrows(NullPointerException.class,
                () -> new InstanceImpl<>(String.class, null, mockStrategy));
            assertThrows(NullPointerException.class,
                () -> new InstanceImpl<>(String.class, qualifiers, null));
        }

        @Test
        @DisplayName("get() should delegate to resolution strategy")
        void getShouldDelegateToResolutionStrategy() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveInstance(any(), any())).thenReturn("test-instance");
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When
            String result = wrapper.get();

            // Then
            assertEquals("test-instance", result);
            verify(mockStrategy).resolveInstance(String.class, qualifiers);
        }

        @Test
        @DisplayName("get() should throw RuntimeException on failure")
        void getShouldThrowRuntimeExceptionOnFailure() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveInstance(any(), any())).thenThrow(new Exception("resolution failed"));
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When/Then
            RuntimeException thrown = assertThrows(RuntimeException.class, wrapper::get);
            assertTrue(thrown.getMessage().contains("Failed to inject java.lang.String"));
        }
    }

    @Nested
    @DisplayName("Select operations tests")
    class SelectOperationsTests {

        @Test
        @DisplayName("select(Annotation...) should create new wrapper with merged qualifiers")
        void selectShouldCreateNewWrapperWithMergedQualifiers() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveInstance(any(), any())).thenReturn("test-instance");
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When
            Instance<String> selected = wrapper.select(new NamedLiteral("specific"));
            String result = selected.get();

            // Then
            assertEquals("test-instance", result);
            verify(mockStrategy).resolveInstance(eq(String.class), argThat(quals ->
                quals.contains(new NamedLiteral("specific")) && quals.contains(new DefaultLiteral())
            ));
        }

        @Test
        @DisplayName("select(Class, Annotation...) should create wrapper for subtype")
        void selectClassShouldCreateWrapperForSubtype() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<CharSequence> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveInstance(any(Class.class), any())).thenReturn("test-string");
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<CharSequence> wrapper = new InstanceImpl<>(CharSequence.class, qualifiers, mockStrategy);

            // When
            Instance<String> selected = wrapper.select(String.class, new NamedLiteral("specific"));
            String result = selected.get();

            // Then
            assertEquals("test-string", result);
            verify(mockStrategy).resolveInstance(any(Class.class), argThat(quals -> {
                Collection<Annotation> annotations = (Collection<Annotation>) quals;
                return annotations.contains(new NamedLiteral("specific"));
            }));
        }
    }

    @Nested
    @DisplayName("Resolution state tests")
    class ResolutionStateTests {

        @Test
        @DisplayName("isUnsatisfied() should return true when no implementations exist")
        void isUnsatisfiedShouldReturnTrueWhenNoImplementations() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(Collections.emptyList());
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When/Then
            assertTrue(wrapper.isUnsatisfied());
        }

        @Test
        @DisplayName("isUnsatisfied() should return false when implementations exist")
        void isUnsatisfiedShouldReturnFalseWhenImplementationsExist() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(Collections.singletonList(String.class));
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When/Then
            assertFalse(wrapper.isUnsatisfied());
        }

        @Test
        @DisplayName("isUnsatisfied() should return true on exception")
        void isUnsatisfiedShouldReturnTrueOnException() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenThrow(new Exception("error"));
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When/Then
            assertTrue(wrapper.isUnsatisfied());
        }

        @Test
        @DisplayName("isAmbiguous() should return false when one implementation exists")
        void isAmbiguousShouldReturnFalseWhenOneImplementation() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(Collections.singletonList(String.class));
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When/Then
            assertFalse(wrapper.isAmbiguous());
        }

        @Test
        @DisplayName("isAmbiguous() should return true when multiple implementations exist")
        void isAmbiguousShouldReturnTrueWhenMultipleImplementations() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<CharSequence> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(Arrays.asList(String.class, StringBuilder.class));
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<CharSequence> wrapper = new InstanceImpl<>(CharSequence.class, qualifiers, mockStrategy);

            // When/Then
            assertTrue(wrapper.isAmbiguous());
        }

        @Test
        @DisplayName("isAmbiguous() should return false on exception")
        void isAmbiguousShouldReturnFalseOnException() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenThrow(new Exception("error"));
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When/Then
            assertFalse(wrapper.isAmbiguous());
        }
    }

    @Nested
    @DisplayName("Lifecycle tests")
    class LifecycleTests {

        @Test
        @DisplayName("destroy() should invoke preDestroy on instance")
        void destroyShouldInvokePreDestroy() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);
            String instance = "test-instance";

            // When
            wrapper.destroy(instance);

            // Then
            verify(mockStrategy).invokePreDestroy(instance);
        }

        @Test
        @DisplayName("destroy() should throw NullPointerException for null instance")
        void destroyShouldHandleNullInstance() {
            // Given
            final boolean[] preDestroyInvoked = {false};
            InstanceImpl.ResolutionStrategy<String> mockStrategy = new InstanceImpl.ResolutionStrategy<String>() {
                @Override
                public String resolveInstance(Class<String> type, Collection<Annotation> qualifiers) {
                    return null;
                }

                @Override
                public Collection<Class<? extends String>> resolveImplementations(Class<String> type,
                                                                                  Collection<Annotation> qualifiers) {
                    return Collections.emptyList();
                }

                @Override
                public void invokePreDestroy(String instance) {
                    preDestroyInvoked[0] = true;
                }
            };
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When
            assertThrows(NullPointerException.class, () -> wrapper.destroy(null));

            // Then
            assertFalse(preDestroyInvoked[0]);
        }

        @Test
        @DisplayName("destroy() should throw RuntimeException on failure")
        void destroyShouldThrowRuntimeExceptionOnFailure() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            doThrow(new InvocationTargetException(new Exception("destroy failed")))
                .when(mockStrategy).invokePreDestroy(any());
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When/Then
            RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> wrapper.destroy("test-instance"));
            assertTrue(thrown.getMessage().contains("Failed to invoke @PreDestroy"));
        }
    }

    @Nested
    @DisplayName("Handle operations tests")
    class HandleOperationsTests {

        @Test
        @DisplayName("getHandle() should return handle for single implementation")
        void getHandleShouldReturnHandleForSingleImplementation() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(Collections.singletonList(String.class));
            when(mockStrategy.resolveInstance(any(), any())).thenReturn("test-instance");
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When
            Instance.Handle<String> handle = wrapper.getHandle();

            // Then
            assertNotNull(handle);
            assertEquals("test-instance", handle.get());
        }

        @Test
        @DisplayName("getHandle() should throw UnsatisfiedResolutionException when no implementations")
        void getHandleShouldThrowWhenNoImplementations() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(Collections.emptyList());
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When/Then
            assertThrows(UnsatisfiedResolutionException.class, wrapper::getHandle);
        }

        @Test
        @DisplayName("getHandle() should throw AmbiguousResolutionException when multiple implementations")
        void getHandleShouldThrowWhenMultipleImplementations() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<CharSequence> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(Arrays.asList(String.class, StringBuilder.class));
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<CharSequence> wrapper = new InstanceImpl<>(CharSequence.class, qualifiers, mockStrategy);

            // When/Then
            assertThrows(AmbiguousResolutionException.class, wrapper::getHandle);
        }

        @Test
        @DisplayName("handles() should return handles for all implementations")
        void handlesShouldReturnHandlesForAllImplementations() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<CharSequence> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            List<Class<? extends CharSequence>> implementations = new ArrayList<>();
            implementations.add(String.class);
            implementations.add(StringBuilder.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(implementations);
            when(mockStrategy.resolveInstance(any(Class.class), any())).thenReturn("string-instance", new StringBuilder("builder-instance"));
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<CharSequence> wrapper = new InstanceImpl<>(CharSequence.class, qualifiers, mockStrategy);

            // When
            Iterable<? extends Instance.Handle<CharSequence>> handles = wrapper.handles();

            // Then
            List<Instance.Handle<CharSequence>> handleList = new ArrayList<>();
            handles.forEach(handleList::add);
            assertEquals(2, handleList.size());
        }

        @Test
        @DisplayName("Handle.get() should lazily create instance")
        void handleGetShouldLazilyCreateInstance() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(Collections.singletonList(String.class));
            when(mockStrategy.resolveInstance(any(), any())).thenReturn("test-instance");
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When
            Instance.Handle<String> handle = wrapper.getHandle();
            verify(mockStrategy, never()).resolveInstance(any(), any());

            String instance = handle.get();

            // Then
            assertEquals("test-instance", instance);
            verify(mockStrategy, times(1)).resolveInstance(String.class, qualifiers);

            // Calling get() again should not resolve again
            String instance2 = handle.get();
            assertEquals(instance, instance2);
            verify(mockStrategy, times(1)).resolveInstance(String.class, qualifiers);
        }

        @Test
        @DisplayName("Handle.destroy() should invoke preDestroy")
        void handleDestroyShouldInvokePreDestroy() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(Collections.singletonList(String.class));
            when(mockStrategy.resolveInstance(any(), any())).thenReturn("test-instance");
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When
            Instance.Handle<String> handle = wrapper.getHandle();
            handle.get();
            handle.destroy();

            // Then
            verify(mockStrategy).invokePreDestroy("test-instance");
        }

        @Test
        @DisplayName("Handle.destroy() should be idempotent")
        void handleDestroyShouldBeIdempotent() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(Collections.singletonList(String.class));
            when(mockStrategy.resolveInstance(any(), any())).thenReturn("test-instance");
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When
            Instance.Handle<String> handle = wrapper.getHandle();
            handle.get();
            handle.destroy();
            handle.destroy(); // Second call should be no-op

            // Then
            verify(mockStrategy, times(1)).invokePreDestroy("test-instance");
        }

        @Test
        @DisplayName("Handle.get() should throw after destroy()")
        void handleGetShouldThrowAfterDestroy() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(Collections.singletonList(String.class));
            when(mockStrategy.resolveInstance(any(), any())).thenReturn("test-instance");
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When
            Instance.Handle<String> handle = wrapper.getHandle();
            handle.get();
            handle.destroy();

            // Then
            assertThrows(IllegalStateException.class, handle::get);
        }

        @Test
        @DisplayName("Handle.close() should delegate to destroy()")
        void handleCloseShouldDelegateToDestroy() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(Collections.singletonList(String.class));
            when(mockStrategy.resolveInstance(any(), any())).thenReturn("test-instance");
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When
            Instance.Handle<String> handle = wrapper.getHandle();
            handle.get();
            handle.close();

            // Then
            verify(mockStrategy).invokePreDestroy("test-instance");
            assertThrows(IllegalStateException.class, handle::get);
        }

        @Test
        @DisplayName("Handle.getBean() should return bean when beanLookup is provided")
        void handleGetBeanShouldReturnBeanWhenLookupProvided() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(Collections.singletonList(String.class));

            Bean<String> mockBean = mock(Bean.class);
            Function<Class<? extends String>, Bean<? extends String>> beanLookup = clazz -> mockBean;

            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy, beanLookup);

            // When
            Instance.Handle<String> handle = wrapper.getHandle();
            Bean<String> bean = handle.getBean();

            // Then
            assertSame(mockBean, bean);
        }

        @Test
        @DisplayName("Handle.getBean() should return fallback BeanImpl when beanLookup is null")
        void handleGetBeanShouldReturnFallbackWhenLookupNull() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(Collections.singletonList(String.class));
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When
            Instance.Handle<String> handle = wrapper.getHandle();
            Bean<String> bean = handle.getBean();

            // Then
            assertNotNull(bean);
            assertEquals(String.class, bean.getBeanClass());
        }
    }

    @Nested
    @DisplayName("Iterator tests")
    class IteratorTests {

        @Test
        @DisplayName("iterator() should return instances for all implementations")
        void iteratorShouldReturnInstancesForAllImplementations() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<CharSequence> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            List<Class<? extends CharSequence>> implementations = new ArrayList<>();
            implementations.add(String.class);
            implementations.add(StringBuilder.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(implementations);
            when(mockStrategy.resolveInstance(any(Class.class), any())).thenReturn("string-instance", new StringBuilder("builder"));
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<CharSequence> wrapper = new InstanceImpl<>(CharSequence.class, qualifiers, mockStrategy);

            // When
            Iterator<CharSequence> iterator = wrapper.iterator();

            // Then
            assertTrue(iterator.hasNext());
            CharSequence first = iterator.next();
            assertEquals("string-instance", first.toString());

            assertTrue(iterator.hasNext());
            CharSequence second = iterator.next();
            assertEquals("builder", second.toString());

            assertFalse(iterator.hasNext());
        }

        @Test
        @DisplayName("iterator() should return empty iterator when no implementations")
        void iteratorShouldReturnEmptyIteratorWhenNoImplementations() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenReturn(Collections.emptyList());
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When
            Iterator<String> iterator = wrapper.iterator();

            // Then
            assertFalse(iterator.hasNext());
        }

        @Test
        @DisplayName("iterator() should throw RuntimeException on resolution failure")
        void iteratorShouldThrowRuntimeExceptionOnFailure() throws Exception {
            // Given
            InstanceImpl.ResolutionStrategy<String> mockStrategy = mock(InstanceImpl.ResolutionStrategy.class);
            when(mockStrategy.resolveImplementations(any(), any())).thenThrow(new Exception("resolution failed"));
            Collection<Annotation> qualifiers = Collections.singletonList(new DefaultLiteral());
            InstanceImpl<String> wrapper = new InstanceImpl<>(String.class, qualifiers, mockStrategy);

            // When/Then
            assertThrows(RuntimeException.class, wrapper::iterator);
        }
    }
}
