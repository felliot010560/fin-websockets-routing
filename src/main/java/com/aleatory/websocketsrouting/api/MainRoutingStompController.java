package com.aleatory.websocketsrouting.api;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.core.MessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import com.aleatory.websocketsrouting.events.SendMessageToFrontendEvent;

@Service
public class MainRoutingStompController {
    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(MainRoutingStompController.class);

    @Autowired
    protected MessageSendingOperations<String> messagingTemplate;

    Map<String, Object> lastMessages = new HashMap<>();

    @EventListener
    private void sendToFrontEnd(SendMessageToFrontendEvent event) {
        messagingTemplate.convertAndSend(event.getDestination(), event.getPayload());
        lastMessages.put(event.getDestination(), event.getPayload());
    }

    @EventListener
    private void sendLastMessageToFrontEndOnSubscription(SessionSubscribeEvent event) {
        Map<?, ?> headers = (Map<?, ?>) event.getMessage().getHeaders().get("nativeHeaders");
        String destination = headers.get("destination").toString();
        Object message = lastMessages.get(destination);
        if (message == null) {
            return;
        }
        messagingTemplate.convertAndSend(destination, message);
    }

}
