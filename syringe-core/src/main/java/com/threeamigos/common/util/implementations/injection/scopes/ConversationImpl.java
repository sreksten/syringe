package com.threeamigos.common.util.implementations.injection.scopes;

import jakarta.enterprise.context.Conversation;
import jakarta.enterprise.context.ContextNotActiveException;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CDI 4.1 Conversation implementation for managing conversation scope lifecycle.
 *
 * <p>This class implements the {@link Conversation} interface from CDI 4.1 specification,
 * providing mechanisms to programmatically control conversation-scoped contexts.
 *
 * <p><b>CDI 4.1 Conversation Features:</b>
 * <ul>
 *   <li>Transient-by-default - conversations must be explicitly made long-running via {@link #begin()}</li>
 *   <li>Long-running conversations persist across multiple requests</li>
 *   <li>Automatic timeout for long-running conversations (default: 30 minutes)</li>
 *   <li>Custom conversation IDs via {@link #begin(String)}</li>
 *   <li>Explicit termination via {@link #end()}</li>
 * </ul>
 *
 * <p><b>Conversation Lifecycle:</b>
 * <ol>
 *   <li><b>Transient:</b> Default state, destroyed at the end of the request</li>
 *   <li><b>Long-running:</b> Activated via begin(), persists across requests</li>
 *   <li><b>Terminated:</b> Ended via end() or timeout, beans destroyed</li>
 * </ol>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe using ThreadLocal for per-request state.
 *
 * <p><b>Implementation Note:</b> This is a simplified implementation that manages conversation
 * state internally without requiring external scope handlers. It uses ThreadLocal to maintain
 *  the per-request conversation state while tracking active conversations globally.
 *
 * @author Stefano Reksten
 * @see Conversation
 */
public class ConversationImpl implements Conversation, Serializable {

    private static final long serialVersionUID = 1L;
    private static final long DEFAULT_TIMEOUT = 30 * 60 * 1000; // 30 minutes in milliseconds

    // Shared registry of all active long-running conversations
    private static final Map<String, ConversationState> activeConversations = new ConcurrentHashMap<>();

    // Per-thread conversation state (one per request)
    private static final ThreadLocal<ConversationState> currentConversation = ThreadLocal.withInitial(() -> {
        ConversationState state = new ConversationState();
        state.id = generateStaticId();
        state.transientFlag = true;
        state.timeout = DEFAULT_TIMEOUT;
        return state;
    });

    // Per-thread reference to ConversationScopedContext for container isolation.
    private static final ThreadLocal<ConversationScopedContext> conversationContext = new ThreadLocal<>();

    /**
     * Sets the conversation context for integration between Conversation bean
     * and ConversationScopedContext. This should be called during container initialization.
     *
     * @param context the conversation scoped context
     */
    public static void setConversationContext(ConversationScopedContext context) {
        if (context == null) {
            conversationContext.remove();
        } else {
            conversationContext.set(context);
        }
    }

    /**
     * Creates a new Conversation instance.
     * State is managed via ThreadLocal, so each request thread gets its own conversation.
     */
    public ConversationImpl() {
        // No-arg constructor - state is managed via ThreadLocal
    }

    /**
     * Internal state holder for conversation data.
     */
    private static class ConversationState implements Serializable {
        private static final long serialVersionUID = 1L;
        String id;
        boolean transientFlag;
        long timeout;
    }

    /**
     * Promotes this conversation from transient to long-running with an auto-generated ID.
     *
     * <p>Per CDI 4.1 specification:
     * <ul>
     *   <li>Must be called within a conversation scope (typically a web request)</li>
     *   <li>Can only be called on a transient conversation</li>
     *   <li>Generates a unique conversation ID automatically</li>
     *   <li>The conversation will persist across requests until end() is called or timeout occurs</li>
     * </ul>
     *
     * @throws IllegalStateException if the conversation is already long-running
     */
    @Override
    public void begin() {
        ensureConversationContextActive();
        ConversationState state = currentConversation.get();
        if (!state.transientFlag) {
            throw new IllegalStateException("Conversation is already long-running");
        }
        state.transientFlag = false;
        // Register this conversation as active
        activeConversations.put(state.id, state);

        // Synchronize with ConversationScopedContext
        ConversationScopedContext context = getConversationContext();
        if (context != null) {
            context.syncWithConversation(state.id);
        }
    }

    /**
     * Promotes this conversation from transient to long-running with a custom ID.
     *
     * <p>This allows client code to specify a meaningful conversation identifier
     * that can be propagated across requests (typically via URL parameter or hidden form field).
     *
     * @param id the conversation identifier (must not be null or empty)
     * @throws IllegalStateException if the conversation is already long-running
     * @throws IllegalArgumentException if id is null or empty
     */
    @Override
    public void begin(String id) {
        ensureConversationContextActive();
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Conversation ID cannot be null or empty");
        }
        if (activeConversations.containsKey(id)) {
            throw new IllegalArgumentException("Long-running conversation with id '" + id + "' already exists");
        }
        ConversationState state = currentConversation.get();
        if (!state.transientFlag) {
            throw new IllegalStateException("Conversation is already long-running");
        }
        state.id = id;
        state.transientFlag = false;
        // Register this conversation as active
        activeConversations.put(state.id, state);

        // Synchronize with ConversationScopedContext
        ConversationScopedContext context = getConversationContext();
        if (context != null) {
            context.syncWithConversation(state.id);
        }
    }

    /**
     * Terminates this long-running conversation and destroys all conversation-scoped beans.
     *
     * <p>Per CDI 4.1 specification:
     * <ul>
     *   <li>All @ConversationScoped beans are destroyed and @PreDestroy callbacks invoked</li>
     *   <li>The conversation becomes transient again</li>
     *   <li>Calling end() on a transient conversation throws an exception</li>
     * </ul>
     *
     * @throws IllegalStateException if the conversation is transient
     */
    @Override
    public void end() {
        ensureConversationContextActive();
        ConversationState state = currentConversation.get();
        if (state.transientFlag) {
            throw new IllegalStateException("Cannot end a transient conversation");
        }

        String conversationId = state.id;

        // Remove from active conversations
        activeConversations.remove(conversationId);

        // End conversation in ConversationScopedContext (destroys beans)
        ConversationScopedContext context = getConversationContext();
        if (context != null) {
            context.endConversation(conversationId);
        }

        state.transientFlag = true;
        // Generate new ID for potential future begin()
        state.id = generateStaticId();
    }

    /**
     * Returns the unique identifier of this conversation.
     *
     * <p>The ID is:
     * <ul>
     *   <li>Auto-generated when the conversation is created or begins</li>
     *   <li>Custom if specified via {@link #begin(String)}</li>
     *   <li>Used to propagate conversation state across requests</li>
     * </ul>
     *
     * @return the conversation identifier (never null)
     */
    @Override
    public String getId() {
        ensureConversationContextActive();
        ConversationState state = currentConversation.get();
        return state.transientFlag ? null : state.id;
    }

    /**
     * Returns the timeout duration for this conversation in milliseconds.
     *
     * <p>After this period of inactivity, the container will automatically
     * destroy the conversation. Default is 30 minutes.
     *
     * @return timeout in milliseconds
     */
    @Override
    public long getTimeout() {
        ensureConversationContextActive();
        return currentConversation.get().timeout;
    }

    /**
     * Sets the timeout duration for this conversation in milliseconds.
     *
     * <p>This must be called before or immediately after {@link #begin()}
     * to take effect. The timeout determines how long the conversation remains
     * active without any requests.
     *
     * @param milliseconds the timeout in milliseconds (must be positive)
     * @throws IllegalArgumentException if milliseconds are not positive
     */
    @Override
    public void setTimeout(long milliseconds) {
        ensureConversationContextActive();
        if (milliseconds <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        currentConversation.get().timeout = milliseconds;
    }

    /**
     * Returns whether this conversation is transient.
     *
     * <p>A transient conversation:
     * <ul>
     *   <li>Is destroyed at the end of the current request</li>
     *   <li>Cannot be propagated to later requests</li>
     *   <li>Becomes long-running when {@link #begin()} is called</li>
     * </ul>
     *
     * @return true if the conversation is transient, false if long-running
     */
    @Override
    public boolean isTransient() {
        ensureConversationContextActive();
        return currentConversation.get().transientFlag;
    }

    private void ensureConversationContextActive() {
        ConversationScopedContext context = getConversationContext();
        if (context == null || !context.isActive()) {
            throw new ContextNotActiveException("Conversation scope is not active");
        }
    }

    private static ConversationScopedContext getConversationContext() {
        return conversationContext.get();
    }

    /**
     * Generates a unique conversation identifier.
     *
     * @return a unique ID string
     */
    private static String generateStaticId() {
        return "cid-" + UUID.randomUUID();
    }

    /**
     * Clears the current thread's conversation state.
     * Should be called at the end of each request to prevent memory leaks.
     */
    public static void clearCurrentConversation() {
        currentConversation.remove();
        conversationContext.remove();
    }

    public static boolean isCurrentConversationTransient() {
        ConversationState state = currentConversation.get();
        return state == null || state.transientFlag;
    }

    public static String getCurrentConversationStateId() {
        ConversationState state = currentConversation.get();
        return state != null ? state.id : null;
    }

    public static void clearAllGlobalState() {
        activeConversations.clear();
        clearCurrentConversation();
    }

    /**
     * Gets the active conversation state for a given ID (for propagation across requests).
     *
     * @param conversationId the conversation ID to restore
     * @return true if the conversation was found and restored
     */
    public static boolean restoreConversation(String conversationId) {
        ConversationState state = activeConversations.get(conversationId);
        if (state != null) {
            currentConversation.set(state);
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        ConversationState state = currentConversation.get();
        return "ConversationImpl{" +
                "id='" + state.id + '\'' +
                ", transient=" + state.transientFlag +
                ", timeout=" + state.timeout +
                '}';
    }
}
