package com.aleatory.websocketsrouting.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.aleatory.common.events.ReconnectStompClientEvent;
import com.aleatory.websocketsrouting.backend.messaging.websockets.PortfolioWebsocketsBackendMessagingClient;
import com.aleatory.websocketsrouting.backend.messaging.websockets.WebsocketsBackendMessagingClient;

import jakarta.annotation.PostConstruct;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketsConfig implements WebSocketMessageBrokerConfigurer {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketsConfig.class);

    @Value("${pricing.websockets.url}")
    private String PRICING_SERVER_URL;

    @Value("${trading.websockets.url}")
    private String TRADING_SERVER_URL;

    @Value("${portfolio.websockets.url}")
    private String PORTFOLIO_SERVER_URL;

    @PostConstruct
    private void printPricingServerURL() {
        logger.info("Pricing server URL = {}", PRICING_SERVER_URL);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/condors").setAllowedOrigins("*");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setSendBufferSizeLimit(1024 * 1024);
    }

    private static class JSONLovingStringMessageConverter extends StringMessageConverter {
        @Override
        protected boolean supportsMimeType(@Nullable MessageHeaders headers) {
            return true;
        }
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        MessageConverter conv = new JSONLovingStringMessageConverter();
        messageConverters.add(conv);
        return false;
    }

    private WebSocketStompClient pricingStompClient;
    private WebsocketsBackendMessagingClient priceHandler;

    private static final String[] PRICE_DESTINATIONS = new String[] { "/topic/prices.current.condor", "/topic/prices.condor", "/topic/prices.spx", "/topic/prices.impvol" };

    @Bean
    @ConditionalOnProperty(value = "backend.messaging.transport", havingValue = "websockets", matchIfMissing = false)
    public WebsocketsBackendMessagingClient priceHandler() {
        return priceHandler = new WebsocketsBackendMessagingClient("price", PRICE_DESTINATIONS);
    }

    @Bean
    @ConditionalOnProperty(value = "backend.messaging.transport", havingValue = "websockets", matchIfMissing = false)
    public WebSocketStompClient pricingStompClient(@Qualifier("priceHandler") WebsocketsBackendMessagingClient handler) {
        WebSocketClient client = new StandardWebSocketClient();

        WebSocketStompClient stompClient = pricingStompClient = new WebSocketStompClient(client);
        stompClient.setMessageConverter(new JSONLovingStringMessageConverter());

        stompClient.connectAsync(PRICING_SERVER_URL, handler);
        return stompClient;
    }

    private WebSocketStompClient tradingStompClient;
    private WebsocketsBackendMessagingClient tradingHandler;

    private static final String[] TRADING_DESTINATIONS = new String[] { "/topic/trading.state", "/topic/trading/condor.bid", "/topic/trading.time" };

    @Bean
    @ConditionalOnProperty(value = "backend.messaging.transport", havingValue = "websockets", matchIfMissing = false)
    public WebsocketsBackendMessagingClient tradingHandler() {
        return tradingHandler = new WebsocketsBackendMessagingClient("trading", TRADING_DESTINATIONS);
    }

    @Bean
    @ConditionalOnProperty(value = "backend.messaging.transport", havingValue = "websockets", matchIfMissing = false)
    public WebSocketStompClient tradingStompClient(@Qualifier("tradingHandler") WebsocketsBackendMessagingClient handler) {
        WebSocketClient client = new StandardWebSocketClient();

        WebSocketStompClient stompClient = tradingStompClient = new WebSocketStompClient(client);
        stompClient.setMessageConverter(new JSONLovingStringMessageConverter());

        stompClient.connectAsync(TRADING_SERVER_URL, handler);
        return stompClient;
    }

    private WebSocketStompClient portfolioStompClient;
    private PortfolioWebsocketsBackendMessagingClient portfolioHandler;

    @Bean
    @ConditionalOnProperty(value = "backend.messaging.transport", havingValue = "websockets", matchIfMissing = false)
    public WebsocketsBackendMessagingClient portfolioHandler() {
        return portfolioHandler = new PortfolioWebsocketsBackendMessagingClient();
    }

    @Bean
    @ConditionalOnProperty(value = "backend.messaging.transport", havingValue = "websockets", matchIfMissing = false)
    public WebSocketStompClient portfolioStompClient(@Qualifier("portfolioHandler") WebsocketsBackendMessagingClient handler) {
        WebSocketClient client = new StandardWebSocketClient();

        WebSocketStompClient stompClient = portfolioStompClient = new WebSocketStompClient(client);
        stompClient.setMessageConverter(new JSONLovingStringMessageConverter());

        stompClient.connectAsync(PORTFOLIO_SERVER_URL, handler);
        return stompClient;
    }

    @EventListener
    private void reconnectStompClient(ReconnectStompClientEvent event) {
        if (event.getSource() == priceHandler) {
            logger.info("Attempting to reconnect to price server");
            pricingStompClient.connectAsync(PRICING_SERVER_URL, priceHandler);
        } else if (event.getSource() == tradingHandler) {
            logger.info("Attempting to reconnect to trading server");
            tradingStompClient.connectAsync(TRADING_SERVER_URL, tradingHandler);
        } else if (event.getSource() == portfolioHandler) {
            logger.info("Attempting to reconnect to portfolio server");
            portfolioStompClient.connectAsync(PORTFOLIO_SERVER_URL, portfolioHandler);
        }
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
