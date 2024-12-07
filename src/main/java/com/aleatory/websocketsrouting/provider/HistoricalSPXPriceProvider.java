package com.aleatory.websocketsrouting.provider;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.aleatory.common.util.TradingDays;
import com.aleatory.websocketsrouting.dao.SPXHistoryDao;
import com.aleatory.websocketsrouting.domain.ClosePrice;
import com.aleatory.websocketsrouting.events.SPXCloseReceivedEvent;

/**
 * This class checks to verify that we haven't missed any closes for SPX (which ensures that
 * we can do expirations). It does a check 1 minute after the SPX close in order to expire the
 * condor for the day (if any) and also checks all the dates as far back as Yahoo Finance has
 * historical data for SPX closes.
 */
@Component
public class HistoricalSPXPriceProvider {
    private static final Logger logger = LoggerFactory.getLogger(HistoricalSPXPriceProvider.class);
    
    @Autowired
    @Qualifier("messagingScheduler")
    private TaskScheduler scheduler;
    
    @Autowired
    private SPXHistoryDao dao;
    
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    
    @EventListener(ApplicationReadyEvent.class)
    private void scheduleYahooCloseFetch() {
    	ZonedDateTime nextClose = TradingDays.todaySPXCloseTime().plus(1,ChronoUnit.MINUTES);
    	Instant closeInstant = nextClose.toInstant();
    	logger.info("Will get next close from web at {}", nextClose);
    	scheduler.schedule(() -> {
    		checkClosePriceAtClose();
    	}, closeInstant);
    	checkAllPreviousCloses();
    }
    
    /**
     * At 3:01 (or 12:01) on a short day, get the top (today's) row in the Yahoo Finance
     * SPX historical quotes page.
     * 
     * Closes from this code are considered non-final--they might change later in the day
     * and those changes will be caught by the checkAllPreviousCloses routine the next
     * time the service runs (presumably, not not necessarily, the next morning).
     */
    private void checkClosePriceAtClose() {
    	logger.info("Checking close price.");
		List<ClosePrice> closePrices = connectToYahooForClose(1);
		if( closePrices != null && closePrices.size() > 0 ) {
			logger.info("Firing new close event for date {}: price: {}.", closePrices.get(0).getCloseDate(), closePrices.get(0).getPrice() );
	    	SPXCloseReceivedEvent event = new SPXCloseReceivedEvent(this, closePrices.get(0).getPrice(), closePrices.get(0).getCloseDate(), false);
	    	applicationEventPublisher.publishEvent(event);
		}
    }
    
    /**
     * Get all the historical closes from Yahoo for 100 days back and make sure they're 
     * the same as what we have in the database.
     * 
     * Closes we get from this routine are all at least from the day after and so are
     * considered final--they can't be changed any more.
     * 
     * This check ensures all our old (pre-today) closing prices have been set 
     * to the correct value (via Yahoo). We can miss one or two if the backend 
     * isn't running for a day or two.
     */
    private void checkAllPreviousCloses() {
    	List<ClosePrice> closePrices = connectToYahooForClose(100);
    	Map<LocalDate, ClosePrice> dbClosePrices = dao.fetchAllSPXCloses();
    	for( int i = 0; i < closePrices.size(); i++ ) {
    		ClosePrice closePrice = closePrices.get(i);
    		if( LocalDate.now().equals(closePrice.getCloseDate())) {
    			continue;
    		}
    		ClosePrice dbClosePrice = dbClosePrices.get(closePrice.getCloseDate());
    		Double dbPrice = dbClosePrice != null ? dbClosePrice.getPrice() : null;
	    	SPXCloseReceivedEvent event = new SPXCloseReceivedEvent(this, closePrices.get(i).getPrice(), closePrices.get(i).getCloseDate(), true);
    		if( dbPrice == null || !closePrice.getPrice().equals(dbPrice)) {
    			logger.info("Writing updated close for date {}: price: {}.", closePrices.get(i).getCloseDate(), closePrices.get(i).getPrice() );
    	    	applicationEventPublisher.publishEvent(event);
    		} else if ( !dbClosePrice.isFinalPrice() ) {
    			logger.info("Possibly finalizing close for date {}: price: {}.", closePrices.get(i).getCloseDate(), closePrices.get(i).getPrice() );    			
    			dao.storeSPXClose(event);
    		}
    	}
    }
    
    /**
     * This method screen-scrapes a page with the SPX closing price at one minute after the SPX closing time (3PM usually,
     * 12PM on short days). We do this because our vendor (c,mon, it's IBK) doesn't send the adjusted close until long
     * after the close, and there is the possibility that we will trade when we shouldn't (i.e., the last SPX tick was
     * .999% up or down, then the close is 1.0% up or down). In V1 of this feature, we get the close from the Yahoo
     * web page.
     * 
     * @param scheduleNextCheck if true, schedule a recheck tonight and tomorrow check
     */
    private List<ClosePrice> connectToYahooForClose(int numDays) {
    	Document doc = null;
    	
    	Elements elements = null;
    	// Try to connect 5 times before quitting in disgrace and disgust
    	for( int i = 0; ( elements == null || elements.size() == 0 ) && i < 5; i++ ) {
        	try {
    			doc = Jsoup.connect("https://finance.yahoo.com/quote/%5EGSPC/history/").get();
    		} catch (IOException e) {
    			logger.info("Error connecting to SPX history page; {} retry.\nError was: {}", (i + 1 < 5) ? "will" : "will not", e.getMessage());
    			wait5Seconds();
    			continue;
    		}
        	elements = doc.select("#nimbus-app > section > section > section > article > div.container > div.table-container > table > tbody > tr:nth-child(1) > td:nth-child(1)");
        	if( elements.size() == 0 ) {
    			logger.info("Error parsing SPX history page; {} retry.", (i + 1 < 5) ? "will" : "will not");
    			wait5Seconds();
        	}
    	}

    	if( doc == null || elements == null || elements.size() == 0 ) {
    		logger.info("Unable to read/parse SPX history page in 5 tries; giving it up.");
    		return null;
    	}
    	
    	List<ClosePrice> closePrices = new ArrayList<>();

    	//elements will hold a value for each row; we want the 1st and 6th cells in the row, holding the date and price
    	elements = doc.select("#nimbus-app > section > section > section > article > div.container > div.table-container > table > tbody > tr");
    	LocalDate closeDateLocal;
    	SimpleDateFormat parser = new SimpleDateFormat("MMM d, yyyy");
    	Double price;
		for (int i=0; i < numDays && i < elements.size(); i++) {
			Element element = elements.get(i);
			Element dateElement = element.select("td:nth-child(1)").get(0);
			Element priceElement = element.select("td:nth-child(6)").get(0);
			String dateStr = dateElement.text();
			Date closeDate;
			try {
				closeDate = parser.parse(dateStr);
			} catch (ParseException e) {
				logger.info("Could not parse Yahoo closing date string {}", dateStr);
				return closePrices;
			}
			closeDateLocal = LocalDate.ofInstant(closeDate.toInstant(), ZoneId.systemDefault());
			logger.debug("Yahoo close date is {}, converted to local: {}", dateElement.text(), closeDateLocal);
			String priceStr = priceElement.text();
			priceStr = priceStr.replaceAll(",", "");
			logger.debug("Yahoo close is {}", priceStr);
			price = Double.parseDouble(priceStr);
			closePrices.add(new ClosePrice(closeDateLocal, price));
		}
    	
    	return closePrices;
    	
    }
    
    private synchronized void wait5Seconds() {
    	try {
			this.wait(5000);
		} catch (InterruptedException e) {
			logger.warn("Five second wait interrupted.");
		}
    }
    
}
