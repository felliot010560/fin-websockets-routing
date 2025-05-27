package com.aleatory.websocketsrouting.provider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.aleatory.websocketsrouting.events.SPXCloseReceivedEvent;

@ExtendWith(MockitoExtension.class)
class HistoricalSPXPriceProviderTest {
    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(HistoricalSPXPriceProviderTest.class);
    
    HistoricalSPXPriceProvider cut;
    
    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @BeforeEach
    void setUp() throws Exception {
        cut = new HistoricalSPXPriceProvider();
        ReflectionTestUtils.setField(cut, "applicationEventPublisher", applicationEventPublisher);
    }

    @Test
    void testCheckClosePriceAtClose() {
        cut.checkClosePriceAtClose();
        verify(applicationEventPublisher).publishEvent(any(SPXCloseReceivedEvent.class));
    }
    


}
