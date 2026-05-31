package com.threeamigos.common.util.implementations.injection.events.propagation;

import com.threeamigos.common.util.implementations.injection.events.EventImpl;

/**
 * ContextTokenProvider backed by ConversationPropagationRegistry.
 * Captures the current propagated conversation id; session support is omitted.
 */
public class RegistryContextTokenProvider implements EventImpl.ContextTokenProvider {
    @Override
    public EventImpl.ContextSnapshot capture() {
        String conversationId = ConversationPropagationRegistry.getConversationId();
        return new EventImpl.ContextSnapshot(null, conversationId, null, null);
    }
}
