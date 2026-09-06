package com.aleatory.websocketsrouting.events;

import org.springframework.context.ApplicationEvent;

public class SendLastSPXCloseEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;
    
    private Double lastClose;

    public SendLastSPXCloseEvent(Object source, Double lastClose) {
        super(source);
        this.lastClose = lastClose;
    }

    public Double getLastClose() {
        return lastClose;
    }

    public void setLastClose(Double lastClose) {
        this.lastClose = lastClose;
    }


}
