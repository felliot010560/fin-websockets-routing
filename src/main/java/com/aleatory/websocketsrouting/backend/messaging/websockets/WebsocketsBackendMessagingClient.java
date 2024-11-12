package com.aleatory.websocketsrouting.backend.messaging.websockets;

import java.lang.reflect.Type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;

import com.aleatory.common.messaging.impl.websockets.WebsocketsPubSubMessagingOperations;
import com.aleatory.websocketsrouting.events.SendMessageToFrontendEvent;

public class WebsocketsBackendMessagingClient extends WebsocketsPubSubMessagingOperations {
    private static final Logger logger = LoggerFactory.getLogger(WebsocketsBackendMessagingClient.class);

    @Autowired
    protected ApplicationEventPublisher applicationEventPublisher;


    public WebsocketsBackendMessagingClient(String serverName, String[] topics) {
        super(serverName, null);
        for (String topic : topics) {
            handlers.put(topic, (payload) -> applicationEventPublisher.publishEvent(new SendMessageToFrontendEvent(this, topic, payload)));
        }
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return String.class;
    }

    @Override
    public void handleException(StompSession session, @Nullable StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
        logger.warn("Exception connecting to {} server, session {}, command: {}, headers: {}, payload: {}, exception: ", serverName, session, command, headers, payload, exception);
    };

}
