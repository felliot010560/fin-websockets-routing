package com.aleatory.websocketsrouting.events;

import org.springframework.context.ApplicationEvent;

public class SendMessageToFrontendEvent extends ApplicationEvent {
    private static final long serialVersionUID = 1L;
    
    private String destination;
    private Object payload;

    public SendMessageToFrontendEvent(Object source, String destination, Object payload) {
        super(source);
        this.destination = destination;
        this.payload = payload;
    }

    public String getDestination() {
        return destination;
    }

    public Object getPayload() {
        return payload;
    }

}
