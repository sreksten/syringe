package com.threeamigos.common.util.implementations.injection.events.propagation;

import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTP implementation of {@link ConversationCarrier}.
 * <p>
 * Reads the conversation id from a request parameter (default "cid") and writes it back
 * to the response header "X-Conversation-Id" so clients can forward it on later calls.
 */
public class HttpConversationCarrier implements ConversationCarrier {

    private final HttpServletRequest request;
    private final ServletResponse response;
    private final String cidParameterName;

    public HttpConversationCarrier(HttpServletRequest request,
                                   ServletResponse response,
                                   String cidParameterName) {
        this.request = request;
        this.response = response;
        this.cidParameterName = cidParameterName;
    }

    @Override
    public String getConversationId() {
        return request.getParameter(cidParameterName);
    }

    @Override
    public void setConversationId(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return;
        }
        if (response instanceof HttpServletResponse) {
            ((HttpServletResponse) response).setHeader("X-Conversation-Id", conversationId);
        }
    }

    @Override
    public boolean shouldEndConversation() {
        String endFlag = request.getParameter("endConversation");
        return Boolean.parseBoolean(endFlag);
    }
}
