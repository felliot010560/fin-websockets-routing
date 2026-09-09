package com.aleatory.websocketsrouting.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.aleatory.common.domain.ClosePrice;
import com.aleatory.common.util.TradingDays;
import com.aleatory.websocketsrouting.events.SPXCloseReceivedEvent;

@Repository
public class SPXHistoryDao {
    private static Logger logger = LoggerFactory.getLogger(SPXHistoryDao.class);

    @Autowired
    private NamedParameterJdbcTemplate template;

    static final String HAS_CLOSE_FOR_TODAY_QUERY = "SELECT EXISTS(SELECT * from spx_history WHERE trade_date = CURRENT_DATE);";
    static final String LAST_SPX_CLOSE_QUERY = "SELECT close FROM spx_history WHERE trade_date=(SELECT MAX(trade_date) FROM spx_history WHERE trade_date <= CURRENT_DATE);";
    static final String NEXT_TO_LAST_CLOSE_QUERY = "SELECT close FROM spx_history WHERE trade_date="
            + "	(SELECT MAX(trade_date) FROM spx_history WHERE trade_date <"
            + "	(SELECT MAX(trade_date) FROM spx_history WHERE trade_date <= CURRENT_DATE));";

    private static final String INSERT_SPX_CLOSE_SQL = "INSERT INTO public.spx_history (trade_date, close, is_final) VALUES (:forDate, :price, :finalPrice)\n" //
            + "	ON CONFLICT (trade_date) " //
            + "DO UPDATE SET close=:price, is_final=:finalPrice " + "WHERE NOT spx_history.is_final OR CURRENT_DATE > :forDate;";

    private static final String ALL_SPX_CLOSE_DATES_QUERY = "SELECT trade_date, close, is_final FROM spx_history ORDER BY trade_date DESC;";

    /**
     * If today is not a trading day, use the next-to-last SPX close. (Last close is considered the last price.)
     * If today is a trading day and there's no close for today yet, use the last SPX close (which will be from the previous trading day).
     * If today is a trading day and there's a close price for today, the last close will be today's, added at close, but we want the last trading day before, so use the next-to-last.
     * @return
     */
    public Double getLastSPXClose() {
        String query;

        if (!TradingDays.isTradingDay()) {
            query = NEXT_TO_LAST_CLOSE_QUERY;
        } else {
            Boolean hasCloseForToday = template.queryForObject(HAS_CLOSE_FOR_TODAY_QUERY, Collections.emptyMap(), Boolean.class);
            if (!hasCloseForToday && LocalTime.now().isAfter(TradingDays.getStartTime())) {
                query = LAST_SPX_CLOSE_QUERY;
            } else {
                query = NEXT_TO_LAST_CLOSE_QUERY;
            }
        }

        Double lastSPXClose = template.queryForObject(query, Collections.emptyMap(), Double.class);
        return lastSPXClose;
    }

    public Map<LocalDate, ClosePrice> fetchAllSPXCloses() {
        List<ClosePrice> closePrices = template.query(ALL_SPX_CLOSE_DATES_QUERY, new RowMapper<ClosePrice>() {

            @Override
            public ClosePrice mapRow(ResultSet rs, int rowNum) throws SQLException {
                ClosePrice closePrice = new ClosePrice(rs.getDate("trade_date").toLocalDate(), rs.getDouble("close"));
                closePrice.setFinalPrice(rs.getBoolean("is_final"));
                return closePrice;
            }
        });
        Map<LocalDate, ClosePrice> closePriceIndex = new HashMap<>();
        for (ClosePrice closePrice : closePrices) {
            closePriceIndex.put(closePrice.getCloseDate(), closePrice);
        }
        return closePriceIndex;
    }

    /**
     * Listens for {@link SPXCloseReceivedEvent} to store the SPX close.
     * 
     * @param event the event that gives us the SPX close to store
     */
    @EventListener
    public void storeSPXClose(SPXCloseReceivedEvent event) {
        logger.info("Writing close of {} for day {} ({}) to database.", event.getPrice(), event.getForDate(), event.isFinalPrice() ? "final" : "not final");
        BeanPropertySqlParameterSource params = new BeanPropertySqlParameterSource(event);
        try {
            template.update(INSERT_SPX_CLOSE_SQL, params);
            logger.debug("Wrote close for {} to DB.", event.getForDate());
        } catch (DataAccessException e) {
            logger.error("Could not write close for {} to database.", event.getForDate(), e);
        }
    }
}
