package com.aleatory.websocketsrouting.backend.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.aleatory.common.domain.WireClose;
import com.aleatory.common.messaging.PubSubMessagingOperations;
import com.aleatory.websocketsrouting.events.SPXCloseReceivedEvent;

/**
 * Sends messages to the backend services that depend on prices. Depending on
 * the backend implementation, this might be Websockets or Redis pub/sub (and
 * possibly others [SNS/SQS, RabbitMQ) in the future). Note that for Websockets
 * on the backend,
 * {@link PubSubMessagingOperations#publishMessage(String, Object)} is a no-op,
 * which avoids sending the same message twice, once from the
 * {@link PriceStompController} and once from here.
 * 
 * Message-sending should be entirely event-driven.
 */
@Service
public class WebsocketsRoutingMessagingController {
	private static final Logger logger = LoggerFactory.getLogger(WebsocketsRoutingMessagingController.class);

	@Autowired
	private PubSubMessagingOperations messagingOperations;

	@EventListener
	private void sendSPXClose(SPXCloseReceivedEvent event) {
		WireClose wireClose = new WireClose();
		wireClose.setSymbol("SPX");
		wireClose.setForDay(event.getForDate());
		wireClose.setClose(event.getPrice());
		logger.info("Sending SPX close of {} for day {}", wireClose.getClose(), wireClose.getForDay());
		messagingOperations.publishMessage("/topic/prices.spx.close", wireClose);
	}
}
