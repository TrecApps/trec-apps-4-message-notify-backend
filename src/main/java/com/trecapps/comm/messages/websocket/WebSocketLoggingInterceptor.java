package com.trecapps.comm.messages.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WebSocketLoggingInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketLoggingInterceptor.class);

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // Skip heartbeat messages to avoid log clutter
        if (accessor.getCommand() == null) {
            return message;
        }

        String sessionId = accessor.getSessionId();
        String user = accessor.getUser() != null ? accessor.getUser().getName() : "Anonymous";
        // Fetch the destination topic/queue/mapping (e.g., "/app/chat" or "/topic/messages")
        String destination = accessor.getDestination();

        switch (accessor.getCommand()) {
            case CONNECT:
                logger.info("WebSocket CONNECT - Session: {}, User: {}, destination: {}", sessionId, user, destination);
                break;
            case SUBSCRIBE:
                logger.info("WebSocket SUBSCRIBE - Session: {}, Destination: {}", sessionId, destination);
                break;
            case SEND:
                String payload = new String((byte[]) message.getPayload());
                logger.info("WebSocket SEND - Session: {}, Destination: {}, Payload: {}",
                        sessionId, destination, payload);
                break;
            case DISCONNECT:
                logger.info("WebSocket DISCONNECT - Session: {}", sessionId);
                break;
            default:
                break;
        }

        return message;
    }
}

