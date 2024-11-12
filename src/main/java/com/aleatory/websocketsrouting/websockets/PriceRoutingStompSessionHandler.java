package com.aleatory.websocketsrouting.websockets;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.messaging.core.MessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import com.aleatory.common.events.ReconnectStompClientEvent;
import com.aleatory.common.exceptions.ProgrammerErrorException;

@Deprecated
public class PriceRoutingStompSessionHandler extends StompSessionHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(PriceRoutingStompSessionHandler.class);
    @Autowired
    private MessageSendingOperations<String> messagingTemplate;
    
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    
    @Autowired
    @Qualifier("resendScheduler")
    private TaskScheduler scheduler;
    
    private boolean applicationReady;
    private boolean subscriptionsSent;
    private StompSession session;
    
    private ScheduledFuture<?> taskHandle = null;
    
    private Map<String, String> lastMessages = new HashMap<>();
    
    public PriceRoutingStompSessionHandler() {
        throw new ProgrammerErrorException("Attempting to instantiate deprecated class " + getClass());
    }
    
    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
	if( session == null ) {
	    logger.warn("Could not connect to price server.");
	    return;
	}
	if( session.isConnected() && taskHandle != null) {
	    taskHandle.cancel(true);
	    taskHandle = null;
	    logger.info("Pricing server was successfully reconnected.");
	}
	logger.info("Websockets connected to pricing server.");
	this.session = session;
	// Are we ready to send events? If not, wait until we are to subscribe.
	if (!applicationReady) {
	    return;
	}
	session.subscribe("/topic/prices.current.condor", this);
	session.subscribe("/topic/prices.condor", this);
	session.subscribe("/topic/prices.spx", this);
	session.subscribe("/topic/prices.impvol", this);
	logger.info("Stomp cient connected to pricing server, subscribed to condors and prices.");
	subscriptionsSent = true;
    }

    @EventListener
    private void applicationReady(ApplicationReadyEvent event) {
	applicationReady = true;
	if (!subscriptionsSent) {
	    afterConnected(session, null);
	}
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
	List<String> destinations = headers.get(StompHeaders.DESTINATION);
	String destination = destinations.get(0);
	if (destination == null) {
	    return;
	}
	String message = (String)payload;
	messagingTemplate.convertAndSend(destination, message);
	logger.debug("Got a frame, sent {} to {}.", message, destination);
	lastMessages.put(destination, message);
    }
    
    @EventListener
    private void handleSubscribeEvent(SessionSubscribeEvent event) {
	Map<?,?> headers = (Map<?,?>)event.getMessage().getHeaders().get("nativeHeaders");
	String destination = headers.get("destination").toString();
	destination = destination.substring(1, destination.length() - 1);
	String message = lastMessages.get(destination);
	if( message == null ) {
	    return;
	}
	logger.info("Sending old message {} to {}.", message, destination);
	delayAndSend(destination, message);
    }
    
    private void delayAndSend(String destination, String message) {
	scheduler.schedule( () -> {
	    messagingTemplate.convertAndSend(destination, message);
	}, Instant.now().plus(2, ChronoUnit.SECONDS));
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
	List<String> destination = headers.get(StompHeaders.DESTINATION);
	if (destination.get(0) == null) {
	    return Object.class;
	}
	return String.class;
    }

    @Override
    public void handleException(StompSession session, @Nullable StompCommand command, StompHeaders headers,
	    byte[] payload, Throwable exception) {
	logger.warn(
		"Exception connecting to pricing server, session {}, command: {}, headers: {}, payload: {}, exception: ",
		session, command, headers, payload, exception);
    };

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
	logger.warn("Transport error connecting to pricing server, session {}, exception: {}", session, exception.getMessage());
	if( "Connection closed".equals(exception.getMessage()) ) {
	    logger.info("Price server disconnected, will attempt to reconnect in 10 seconds.");
	    taskHandle = scheduler.scheduleAtFixedRate( () -> {
		if( this.session.isConnected() ) {
		    taskHandle.cancel(true);
		    logger.info("Pricing server was successfully reconnected.");
		}
		applicationEventPublisher.publishEvent(new ReconnectStompClientEvent(this));
	    }, Instant.now().plus(10, ChronoUnit.SECONDS), Duration.of(10, ChronoUnit.SECONDS));
	}
    }

}