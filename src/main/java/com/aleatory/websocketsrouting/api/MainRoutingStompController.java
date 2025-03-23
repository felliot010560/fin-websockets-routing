package com.aleatory.websocketsrouting.api;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.core.MessageSendingOperations;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import com.aleatory.common.util.TradingDays;
import com.aleatory.websocketsrouting.events.SendMessageToFrontendEvent;

import jakarta.annotation.PostConstruct;

@Service
public class MainRoutingStompController {
    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(MainRoutingStompController.class);
    
    private static final String REDIS_KEY = "CONDORS:LAST.MESSAGES";

    @Autowired
    protected MessageSendingOperations<String> messagingTemplate;
    
    @Autowired
    protected RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    @Qualifier("messagingScheduler")
    protected TaskScheduler scheduler;
    
    @PostConstruct
    private void scheduleCacheClear() {
        //Clear the cache at 12:05 AM on the next trading day (if process is running then).
        LocalDate nextTradingDay = TradingDays.nextTradingDay();
        LocalDateTime nextKeyExpiry = LocalDateTime.of(nextTradingDay, LocalTime.of(0, 5));
        
        //Also set the last-messages key to expire in Redis at the same time--do that every day just in
        //case we're not taking the server down every night (as we do in prod)
        //Don't do the expiration setting if it's between 11:30PM and 12:30AM (so as not to reset the
        //expiration between 12AM and 12:05AM).
        scheduler.scheduleWithFixedDelay( () -> {
            if( LocalTime.now().isAfter(LocalTime.of(23, 30)) || LocalTime.now().isBefore(LocalTime.of(0, 30))) {
                logger.info("Not resetting redis expiry between 11:30PM and 12:30AM.");
                return;
            }
            long secondsToExpiry = ChronoUnit.SECONDS.between(LocalDateTime.now(), nextKeyExpiry);
            redisTemplate.expire(REDIS_KEY, Duration.of(secondsToExpiry, ChronoUnit.SECONDS));
            logger.info("Set redis expiration to {}, {} seconds from now.", nextKeyExpiry, secondsToExpiry);
        }, LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant(), Duration.of(12, ChronoUnit.HOURS));
    }

    @EventListener
    private void sendToFrontEnd(SendMessageToFrontendEvent event) {
        messagingTemplate.convertAndSend(event.getDestination(), event.getPayload());
        redisTemplate.opsForHash().put(REDIS_KEY, event.getDestination(), event.getPayload());
    }

    @EventListener
    private void sendLastMessageToFrontEndOnSubscription(SessionSubscribeEvent event) {
        Map<?, ?> headers = (Map<?, ?>) event.getMessage().getHeaders().get("nativeHeaders");
        String destHeader = headers.get("destination").toString();
        if( destHeader == null ) {
            return;
        }
        String destination = destHeader.substring(1, destHeader.length()-1);
        Object message = redisTemplate.opsForHash().get(REDIS_KEY, destination);
        if (message == null) {
            return;
        }
        messagingTemplate.convertAndSend(destination, message);
    }

}
