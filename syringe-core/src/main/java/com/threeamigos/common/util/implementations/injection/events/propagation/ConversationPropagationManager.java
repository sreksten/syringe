package com.threeamigos.common.util.implementations.injection.events.propagation;

import com.threeamigos.common.util.implementations.injection.scopes.ConversationImpl;
import com.threeamigos.common.util.implementations.injection.scopes.ConversationScopedContext;
import jakarta.enterprise.context.Conversation;
import jakarta.enterprise.inject.spi.CDI;

/**
 * Transport-agnostic helper for conversation propagation.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Reads conversation id from a carrier and restores it.</li>
 *   <li>Writes the current conversation id back to a carrier for downstream hops.</li>
 *   <li>Clears thread-local state at the end of handling.</li>
 * </ul>
 */
public class ConversationPropagationManager {

    private final ConversationScopedContext conversationContext;

    public ConversationPropagationManager(ConversationScopedContext conversationContext) {
        this.conversationContext = conversationContext;
    }

    /**
     * Applies an incoming conversation id (if present) to the current thread.
     *
     * @param carrier transport carrier
     * @return true if an existing conversation was restored
     */
    public boolean handleIncoming(ConversationCarrier carrier) {
        if (carrier == null) {
            return false;
        }

        String conversationId = carrier.getConversationId();
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return false;
        }

        boolean restored = ConversationImpl.restoreConversation(conversationId);
        if (restored && conversationContext != null) {
            conversationContext.syncWithConversation(conversationId);
        }
        return restored;
    }

    /**
     * Writes the current conversation id to the carrier if the conversation is long-running.
     *
     * @param carrier transport carrier
     */
    public void handleOutgoing(ConversationCarrier carrier) {
        if (carrier == null) {
            return;
        }
        try {
            Conversation conversation = CDI.current().select(Conversation.class).get();
            if (conversation != null && !conversation.isTransient()) {
                carrier.setConversationId(conversation.getId());
                // Ensure context is in sync in case this is the first time the conversation became long-running
                if (conversationContext != null) {
                    conversationContext.syncWithConversation(conversation.getId());
                }
            }
        } catch (Exception ignored) {
            // CDI not available or no Conversation bean; skip propagation
        }
    }

    /**
     * Cleans up the thread-local state and optionally ends the conversation.
     *
     * @param carrier transport carrier
     */
    public void complete(ConversationCarrier carrier) {
        if (carrier != null && carrier.shouldEndConversation()) {
            try {
                Conversation conversation = CDI.current().select(Conversation.class).get();
                if (conversation != null && !conversation.isTransient()) {
                    conversation.end();
                }
            } catch (Exception ignored) {
                // If CDI/Conversation not available, we still clear thread state below
            }
        }

        ConversationImpl.clearCurrentConversation();
        if (conversationContext != null) {
            conversationContext.clearCurrentThread();
        }
    }
}
