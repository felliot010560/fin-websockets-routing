package com.aleatory.websocketsrouting.backend.messaging.redis;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.aleatory.common.messaging.impl.redis.RedisPubSubMessagingOperations;
import com.aleatory.websocketsrouting.events.SendMessageToFrontendEvent;

@Service
@ConditionalOnProperty(value = "backend.messaging.transport", havingValue = "redis", matchIfMissing = true)
public class WebsocketsRoutingRedisMessaging extends RedisPubSubMessagingOperations {
    private static final Logger logger = LoggerFactory.getLogger(WebsocketsRoutingRedisMessaging.class);

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    // We don't resend these to the front end--they're purely backend topics
    private List<String> excludedTopics = new ArrayList<>();

    public WebsocketsRoutingRedisMessaging() {
        excludedTopics.add("/topic/prices.current.condor.full");
    }

    @Override
    public void receiveMessages(String topic, Object payload) {
        if (excludedTopics.contains(topic)) {
            return;
        }
        if (!handlers.containsKey(topic)) {
            logger.info("Adding new topic {}", topic);
            handlers.put(topic, (message) -> applicationEventPublisher.publishEvent(new SendMessageToFrontendEvent(this, topic, message)));
        }
        super.receiveMessages(topic, payload);
    }

}
