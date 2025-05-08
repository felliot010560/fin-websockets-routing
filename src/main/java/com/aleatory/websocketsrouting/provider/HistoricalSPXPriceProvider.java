package com.aleatory.websocketsrouting.provider;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * This class checks to verify that we haven't missed any closes for SPX (which
 * ensures that we can do expirations). It does a check 1 minute after the SPX
 * close in order to expire the condor for the day (if any) and also checks all
 * the dates as far back as Yahoo Finance has historical data for SPX closes.
 * 
 * Note: this class is <b>not</b> the source of yesterday's close for change
 * calculations or for determining if there was a >1% move. That comes from IB
 * via the pricing server. This class supplies only historical closes and (at
 * closing time) a pretty good close price that is not the last for expiring
 * options.
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
        scheduleCheckPriceAtClose();
        checkAllPreviousCloses();
    }

    private void scheduleCheckPriceAtClose() {
        ZonedDateTime nextClose = TradingDays.todaySPXCloseTime();
        if (ZonedDateTime.now().isAfter(nextClose)) {
            nextClose = TradingDays.nextSPXCloseTime();
        }
        // Make it 3:01 (or 12:01 for a half-day).
        nextClose = nextClose.plus(1, ChronoUnit.MINUTES);
        Instant closeInstant = nextClose.toInstant();
        logger.info("Will get next close from web at {}", nextClose);
        scheduler.schedule(() -> {
            checkClosePriceAtClose();
            scheduleCheckPriceAtClose();
        }, closeInstant);
    }

    /**
     * At 3:01 (or 12:01) on a short day, get the top (today's) row in the Yahoo
     * Finance SPX historical quotes page.
     * 
     * Closes from this code are considered non-final--they might change later in
     * the day and those changes will be caught by the checkAllPreviousCloses
     * routine the next time the service runs (presumably, not not necessarily, the
     * next morning).
     */
    private void checkClosePriceAtClose() {
        logger.info("Checking close price.");
        List<ClosePrice> closePrices = connectForClose(1);
        if (closePrices != null && closePrices.size() > 0) {
            logger.info("Firing new close event for date {}: price: {}.", closePrices.get(0).getCloseDate(), closePrices.get(0).getPrice());
            SPXCloseReceivedEvent event = new SPXCloseReceivedEvent(this, closePrices.get(0).getPrice(), closePrices.get(0).getCloseDate(), false);
            applicationEventPublisher.publishEvent(event);
        }
    }

    /**
     * Get all the historical closes from Yahoo for 100 days back and make sure
     * they're the same as what we have in the database.
     * 
     * Closes we get from this routine are all at least from the day after and so
     * are considered final--they can't be changed any more.
     * 
     * This check ensures all our old (pre-today) closing prices have been set to
     * the correct value (via Yahoo). We can miss one or two if the backend isn't
     * running for a day or two.
     */
    private void checkAllPreviousCloses() {
        logger.info("Checking previous closes 100 days back.");
        List<ClosePrice> closePrices = connectForClose(100);
        // Error occurred
        if (closePrices == null) {
            return;
        }
        Map<LocalDate, ClosePrice> dbClosePrices = dao.fetchAllSPXCloses();
        for (int i = 0; i < closePrices.size(); i++) {
            ClosePrice closePrice = closePrices.get(i);
            //Is it today's? If so, only check it if the market is closed today.
            if (LocalDate.now().equals(closePrice.getCloseDate()) && TradingDays.indexClosingTime().isAfter(LocalTime.now())) {
                continue;
            }
            ClosePrice dbClosePrice = dbClosePrices.get(closePrice.getCloseDate());
            Double dbPrice = dbClosePrice != null ? dbClosePrice.getPrice() : null;
            SPXCloseReceivedEvent event = new SPXCloseReceivedEvent(this, closePrices.get(i).getPrice(), closePrices.get(i).getCloseDate(), true);
            if (dbPrice == null || !closePrice.getPrice().equals(dbPrice)) {
                logger.info("Writing updated close for date {}: price: {}.", closePrices.get(i).getCloseDate(), closePrices.get(i).getPrice());
                applicationEventPublisher.publishEvent(event);
            } else if (!dbClosePrice.isFinalPrice()) {
                logger.info("Possibly finalizing close for date {}: price: {}.", closePrices.get(i).getCloseDate(), closePrices.get(i).getPrice());
                dao.storeSPXClose(event);
            }
        }
    }

    /**
     * This method screen-scrapes a page with the SPX closing price at one minute
     * after the SPX closing time (3PM usually, 12PM on short days). We do this
     * because our vendor (c,mon, it's IBK) doesn't send the adjusted close until
     * long after the close, and there is the possibility that we will trade when we
     * shouldn't (i.e., the last SPX tick was .999% up or down, then the close is
     * 1.0% up or down). In V1 of this feature, we get the close from the Yahoo web
     * page.
     * 
     * @param scheduleNextCheck if true, schedule a recheck tonight and tomorrow
     *                          check
     */
    private List<ClosePrice> connectForClose(int numDays) {
        Document doc = connect();
        if( doc == null ) {
            return null;
        }

        List<ClosePrice> closePrices = new ArrayList<>();

        // elements will hold a value for each row; we want the 1st and 6th cells in the
        // row, holding the date and price
        Elements elements = doc.select("#__next > div.md\\:relative.md\\:bg-white > div.relative.flex > div.md\\:grid-cols-\\[1fr_72px\\].md2\\:grid-cols-\\[1fr_420px\\].grid.flex-1.grid-cols-1.px-4.pt-5.font-sans-v2.text-\\[\\#232526\\].antialiased.transition-all.xl\\:container.sm\\:px-6.md\\:gap-6.md\\:px-7.md\\:pt-10.md2\\:gap-8.md2\\:px-8.xl\\:mx-auto.xl\\:gap-10.xl\\:px-10 > div.min-w-0 > div.mb-4.md\\:mb-10 > div.mt-6.flex.flex-col.items-start.overflow-x-auto.p-0.md\\:pl-1 > table > tbody>tr");
        LocalDate closeDateLocal;
        SimpleDateFormat parser = new SimpleDateFormat("MMM d, yyyy");
        Double price;
        for (int i = 0; i < numDays && i < elements.size(); i++) {
            Element element = elements.get(i);
            Element dateElement = null;
            Element priceElement = null;
            try {
                dateElement = element.select("td:nth-child(1)").get(0);
                priceElement = element.select("td:nth-child(2)").get(0);
            } catch (Exception e) {
                logger.warn("Exception while reading table row, row {} was:\n{}", i, element);
                continue;
            }
            String dateStr = dateElement.text();
            ;
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
    
    private Document connect() {
        Document doc = null;

        Elements elements = null;
        // Try to connect 5 times before quitting in disgrace and disgust
        for (int i = 0; (elements == null || elements.size() == 0) && i < 5; i++) {
            try {
                doc = Jsoup.connect("https://www.investing.com/indices/us-spx-500-historical-data")
                        .header("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36")
                        .header("Host", "www.investing.com")
                        .header("Cache-Control", "no-cache")
                        .header("Cookie", "__cf_bm=IiggSOluEITPr3O0Vqf.ZzoigCgiSoiCOTFK5yyMN3Q-1746659363-1.0.1.1-ktJtKtUnLf8mEnjWXTe7lpnZFOnyk3hTFnEsti7fEQnS8s1uIgE6wuPtil7y.AN3G9RUhpa8vTPjt.RfGYnBU_kogyyHi0cYEBtYtP_1dXu0O9HkQfUG8u7lp8r3fv2s; gcc=US; gsc=IL; inudid=81742163b7f59ebeb9fe807600304d06; invab=noadnews_0|ovpromo_2|regwall_1|stickytnb_0; smd=81742163b7f59ebeb9fe807600304d06-1746659362; udid=81742163b7f59ebeb9fe807600304d06; __cflb=0H28vY1WcQgbwwJpSw5YiDRSJhpofbwM554nBPNisLx")
                        .timeout(1000*5)
                        .get();
            } catch (IOException e) {
                logger.info("Error connecting to SPX history page; {} retry.\nError was: {}", (i + 1 < 5) ? "will" : "will not", e.getMessage());
                wait5Seconds();
                continue;
            }
            elements = doc.select("#__next > div.md\\:relative.md\\:bg-white > div.relative.flex > div.md\\:grid-cols-\\[1fr_72px\\].md2\\:grid-cols-\\[1fr_420px\\].grid.flex-1.grid-cols-1.px-4.pt-5.font-sans-v2.text-\\[\\#232526\\].antialiased.transition-all.xl\\:container.sm\\:px-6.md\\:gap-6.md\\:px-7.md\\:pt-10.md2\\:gap-8.md2\\:px-8.xl\\:mx-auto.xl\\:gap-10.xl\\:px-10 > div.min-w-0 > div.mb-4.md\\:mb-10 > div.mt-6.flex.flex-col.items-start.overflow-x-auto.p-0.md\\:pl-1 > table > tbody>tr:nth-child(1)");
            if (elements.size() == 0) {
                logger.info("Error parsing SPX history page; {} retry.", (i + 1 < 5) ? "will" : "will not");
                wait5Seconds();
            }
        }

        if (doc == null || elements == null || elements.size() == 0) {
            logger.info("Unable to read/parse SPX history page in 5 tries; giving it up.");
            return null;
        }
        
        return doc;
    }

    private synchronized void wait5Seconds() {
        try {
            this.wait(5000);
        } catch (InterruptedException e) {
            logger.warn("Five second wait interrupted.");
        }
    }

}
