package com.aleatory.websocketsrouting;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@SpringBootApplication
public class WebsocketsRoutingApplication {
    private static final Logger logger = LoggerFactory.getLogger(WebsocketsRoutingApplication.class);

    public static void main(String[] args) {
	SpringApplication.run(WebsocketsRoutingApplication.class, args);
    }
    
    @EventListener
    private void handleSubscribeEvent(SessionSubscribeEvent event) {
	Map<?,?> headers = (Map<?,?>)event.getMessage().getHeaders().get("nativeHeaders");
	String destination = headers.get("destination").toString();
	logger.info("Got session subscribe event for topic {}", destination );
    }

}
