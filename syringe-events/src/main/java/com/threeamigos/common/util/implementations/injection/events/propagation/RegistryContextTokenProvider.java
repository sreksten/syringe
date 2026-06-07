package com.threeamigos.common.util.implementations.injection.events.propagation;

import com.threeamigos.common.util.implementations.injection.events.ContextSnapshot;
import com.threeamigos.common.util.implementations.injection.events.ContextTokenProvider;

/**
 * ContextTokenProvider backed by ConversationPropagationRegistry.
 * Captures the current propagated conversation id; session support is omitted.
 */
public class RegistryContextTokenProvider implements ContextTokenProvider {
    @Override
    public ContextSnapshot capture() {
        String conversationId = ConversationPropagationRegistry.getConversationId();
        return new ContextSnapshot(null, conversationId, null, null);
    }
}
