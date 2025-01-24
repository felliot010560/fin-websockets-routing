package com.aleatory.websocketsrouting.backend.messaging.websockets;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import com.aleatory.websocketsrouting.events.SendMessageToFrontendEvent;

/**
 * For portfolio, we don't know the topics in advance, so we have to dynamically
 * subscribe to them as they come in.
 */
public class PortfolioWebsocketsBackendMessagingClient extends WebsocketsBackendMessagingClient {
    private static final Logger logger = LoggerFactory.getLogger(WebsocketsBackendMessagingClient.class);
    private static final String CONDORS_TICK_TOPIC_PREFIX = "/topic/prices.condor.portfolio.";

    public PortfolioWebsocketsBackendMessagingClient() {
        super("portfolio", new String[0]);
    }

    /**
     * If the frontend asks for a portfolio condor or portfolio condor leg option,
     * subscribe to the portfolio server
     * 
     * @param event
     */
    @EventListener
    private void handleSubscribeEvent(SessionSubscribeEvent event) {
        Map<?, ?> headers = (Map<?, ?>) event.getMessage().getHeaders().get("nativeHeaders");
        String destinationWithBrackets = headers.get("destination").toString();
        String destination = destinationWithBrackets.substring(1, destinationWithBrackets.length() - 1);
        // If we don't have the destination, it has to be a portfolio condor tick
        if (!destination.startsWith(CONDORS_TICK_TOPIC_PREFIX)) {
            return;
        }
        logger.info("Subscribing to portfolio topic {}", destination);
        session.subscribe(destination, this);
        handlers.put(destination, (payload) -> applicationEventPublisher.publishEvent(new SendMessageToFrontendEvent(this, destination, payload)));
    }
}
