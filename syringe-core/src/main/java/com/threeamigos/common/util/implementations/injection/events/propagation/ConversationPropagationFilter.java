package com.threeamigos.common.util.implementations.injection.events.propagation;

import com.threeamigos.common.util.implementations.injection.scopes.ConversationScopedContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Servlet filter that propagates conversation IDs across HTTP requests.
 *
 * <p>Per CDI 4.1 Section 6.7.4, long-running conversations are propagated
 * via request parameter (default: "cid"). This filter:
 * <ul>
 *   <li>Checks for the "cid" parameter in the request</li>
 *   <li>Restores the conversation if ID is valid</li>
 *   <li>Creates a new transient conversation if no ID present</li>
 *   <li>Cleans up ThreadLocal at the request end</li>
 * </ul>
 *
 * <p><b>Usage in web.xml:</b>
 * <pre>{@code
 * <filter>
 *     <filter-name>ConversationPropagationFilter</filter-name>
 *     <filter-class>com.threeamigos.common.util.implementations.injection.http.ConversationPropagationFilter</filter-class>
 * </filter>
 * <filter-mapping>
 *     <filter-name>ConversationPropagationFilter</filter-name>
 *     <url-pattern>/*</url-pattern>
 * </filter-mapping>
 * }</pre>
 *
 * <p><b>Conversation Propagation Example:</b>
 * <pre>{@code
 * // In a JSF/servlet controller
 * @Inject Conversation conversation;
 *
 * public String startWizard() {
 *     conversation.begin();
 *     return "step1?cid=" + conversation.getId(); // Propagate conversation ID
 * }
 *
 * // Next request with ?cid=xxx will restore the conversation
 * public String nextStep() {
 *     // conversation is restored automatically by this filter
 *     return "step2?cid=" + conversation.getId();
 * }
 * }</pre>
 *
 * <p><b>Thread Safety:</b> This filter is thread-safe. Each request thread
 * gets its own conversation state via ThreadLocal. The filter ensures proper
 * cleanup at the request end to prevent memory leaks.
 *
 * <p><b>Implementation Note:</b> This filter requires a reference to the
 * ConversationScopedContext to properly synchronize the conversation state.
 * The context reference can be set via {@link #setConversationContext(ConversationScopedContext)}.
 *
 * @author Stefano Reksten
 * @see ConversationScopedContext
 */
public class ConversationPropagationFilter implements Filter {

    /**
     * Default parameter name for conversation ID in HTTP requests.
     * Can be overridden via filter init parameter "cidParameterName".
     */
    private static final String DEFAULT_CID_PARAMETER = "cid";

    private String cidParameterName = DEFAULT_CID_PARAMETER;

    /**
     * Reference to the conversation context for state synchronization.
     * This is typically injected by the CDI container or set manually.
     */
    private ConversationScopedContext conversationContext;
    private volatile ConversationPropagationManager propagationManager;

    /**
     * Sets the conversation context for synchronization.
     * This should be called during filter initialization.
     *
     * @param context the conversation scoped context
     */
    public void setConversationContext(ConversationScopedContext context) {
        this.conversationContext = context;
        this.propagationManager = new ConversationPropagationManager(context);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Allow custom conversation ID parameter name
        String paramName = filterConfig.getInitParameter("cidParameterName");
        if (paramName != null && !paramName.trim().isEmpty()) {
            this.cidParameterName = paramName.trim();
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        ensureManager();
        HttpConversationCarrier carrier =
            new HttpConversationCarrier((HttpServletRequest) request, response, cidParameterName);

        try {
            propagationManager.handleIncoming(carrier);
            chain.doFilter(request, response);
            propagationManager.handleOutgoing(carrier);
        } finally {
            propagationManager.complete(carrier);
        }
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }

    private void ensureManager() {
        if (propagationManager == null) {
            synchronized (this) {
                if (propagationManager == null) {
                    propagationManager = new ConversationPropagationManager(conversationContext);
                }
            }
        }
    }
}
