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
public class TradeRoutingStompSessionHandler extends StompSessionHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(TradeRoutingStompSessionHandler.class);
    
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    
    @Autowired
    @Qualifier("resendScheduler")
    private TaskScheduler scheduler;
    
    private Map<String, String> lastMessages = new HashMap<>();
    
    private boolean applicationReady;
    private boolean subscriptionsSent;
    private StompSession session;
    
    private ScheduledFuture<?> taskHandle = null;
    
    @Autowired
    private MessageSendingOperations<String> messagingTemplate;

    public TradeRoutingStompSessionHandler() {
        throw new ProgrammerErrorException("Attempting to instantiate deprecated class " + getClass());
    }
    
    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
	logger.info("Websockets connected to trading server.");
	this.session = session;
	// Are we ready to send events? If not, wait until we are to subscribe.
	if (!applicationReady) {
	    return;
	}
	if( session.isConnected() && taskHandle != null) {
	    taskHandle.cancel(true);
	    taskHandle = null;
	    logger.info("Trading server was successfully reconnected.");
	}
	session.subscribe("/topic/trading.state", this);
	session.subscribe("/topic/trading/condor.bid", this);
	session.subscribe("/topic/trading.time", this);
	logger.info("Stomp cient connected to trading server, subscribed to trading state, condor bid, and trading time.");
	subscriptionsSent = true;
    }

    @EventListener
    private void applicationReady(ApplicationReadyEvent event) {
	applicationReady = true;
	if (!subscriptionsSent) {
	    afterConnected(session, null);
	}
    }

    /**
     * Send an incoming message to the front end on the same topic
     */
    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
	List<String> destinations = headers.get(StompHeaders.DESTINATION);
	String destination = destinations.get(0);
	if (destination == null) {
	    return;
	}
	String message = (String)payload;
	messagingTemplate.convertAndSend(destination, message);
	logger.debug("Trading: Got a frame, sent {} to {}.", message, destination);
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
	messagingTemplate.convertAndSend(destination, message);
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
	List<String> destination = headers.get(StompHeaders.DESTINATION);
	if (destination.get(0) == null) {
	    return Object.class;
	}
	return String.class;
    }

    /**
     * This implementation is empty.
     */
    @Override
    public void handleException(StompSession session, @Nullable StompCommand command, StompHeaders headers,
	    byte[] payload, Throwable exception) {
	logger.warn(
		"Exception connecting to pricing server, session {}, command: {}, headers: {}, payload: {}, exception: ",
		session, command, headers, payload, exception);
    };

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
	logger.warn("Transport error connecting to trading server, session {}, exception: {}", session, exception.getMessage());
	if( "Connection closed".equals(exception.getMessage()) ) {
	    logger.info("Trading server disconnected, will attempt to reconnect in 10 seconds.");
	    taskHandle = scheduler.scheduleAtFixedRate( () -> {
		if( this.session.isConnected() ) {
		    taskHandle.cancel(true);
		    logger.info("Trading server was successfully reconnected.");
		}
		applicationEventPublisher.publishEvent(new ReconnectStompClientEvent(this));
	    }, Instant.now().plus(10, ChronoUnit.SECONDS), Duration.of(10, ChronoUnit.SECONDS));
	}
    }

}