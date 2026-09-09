package com.aleatory.websocketsrouting.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.aleatory.common.util.TradingDays;

@ExtendWith(MockitoExtension.class)
class TestSPXHistoryDao {
    private static final Logger logger = LoggerFactory.getLogger(TestSPXHistoryDao.class);
    
    @Mock
    NamedParameterJdbcTemplate template;

    @Captor
    ArgumentCaptor<String> queryCaptor;

    @InjectMocks
    SPXHistoryDao dao;

    @BeforeEach
    void setUp() throws Exception {
    }

    @Test
    void testGetLastSPXCloseInTradingHours() {
        LocalDate today = LocalDate.of(2026, 9, 9);
        LocalTime now = LocalTime.of(8, 45);
        try (MockedStatic<LocalDate> mockedStaticDate = mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS); MockedStatic<LocalTime> mockedStaticTime = mockStatic(LocalTime.class, Mockito.CALLS_REAL_METHODS)) {
            mockedStaticDate.when(() -> LocalDate.now()).thenReturn(today);
            mockedStaticTime.when(() -> LocalTime.now()).thenReturn(now);
            logger.info("Now is {}", LocalDateTime.of(today, now));
            //Reinit to set the new now to be tested
            TradingDays.init();
            when(template.queryForObject(eq(SPXHistoryDao.HAS_CLOSE_FOR_TODAY_QUERY), Mockito.anyMap(), eq(Boolean.class))).thenReturn(false);
            
            dao.getLastSPXClose();
            
            Mockito.verify(template).queryForObject(queryCaptor.capture(), anyMap(), eq(Double.class));
            assertEquals(SPXHistoryDao.LAST_SPX_CLOSE_QUERY, queryCaptor.getValue());
        }
    }
    
    @Test
    void testGetLastSPXCloseAfterTradingHours() {
        LocalDate today = LocalDate.of(2026, 9, 9);
        LocalTime now = LocalTime.of(15, 45);
        try (MockedStatic<LocalDate> mockedStaticDate = mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS); MockedStatic<LocalTime> mockedStaticTime = mockStatic(LocalTime.class, Mockito.CALLS_REAL_METHODS)) {
            mockedStaticDate.when(() -> LocalDate.now()).thenReturn(today);
            mockedStaticTime.when(() -> LocalTime.now()).thenReturn(now);
            logger.info("Now is {}", LocalDateTime.of(today, now));
            //Reinit to set the new now to be tested
            TradingDays.init();
            when(template.queryForObject(eq(SPXHistoryDao.HAS_CLOSE_FOR_TODAY_QUERY), Mockito.anyMap(), eq(Boolean.class))).thenReturn(true);
            
            dao.getLastSPXClose();
            
            Mockito.verify(template).queryForObject(queryCaptor.capture(), anyMap(), eq(Double.class));
            assertEquals(SPXHistoryDao.NEXT_TO_LAST_CLOSE_QUERY, queryCaptor.getValue());
        }
    }
    
    @Test
    void testGetLastSPXCloseBeforeTradingHours() {
        LocalDate today = LocalDate.of(2026, 9, 9);
        LocalTime now = LocalTime.of(8, 0);
        try (MockedStatic<LocalDate> mockedStaticDate = mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS); MockedStatic<LocalTime> mockedStaticTime = mockStatic(LocalTime.class, Mockito.CALLS_REAL_METHODS)) {
            mockedStaticDate.when(() -> LocalDate.now()).thenReturn(today);
            mockedStaticTime.when(() -> LocalTime.now()).thenReturn(now);
            logger.info("Now is {}", LocalDateTime.of(today, now));
            //Reinit to set the new now to be tested
            TradingDays.init();
            when(template.queryForObject(eq(SPXHistoryDao.HAS_CLOSE_FOR_TODAY_QUERY), Mockito.anyMap(), eq(Boolean.class))).thenReturn(false);
            
            dao.getLastSPXClose();
            
            Mockito.verify(template).queryForObject(queryCaptor.capture(), anyMap(), eq(Double.class));
            assertEquals(SPXHistoryDao.NEXT_TO_LAST_CLOSE_QUERY, queryCaptor.getValue());
        }
    }
    
    @Test
    void testGetLastSPXCloseNotTradingDay() {
        LocalDate today = LocalDate.of(2026, 9, 6);
        LocalTime now = LocalTime.of(12, 0);
        try (MockedStatic<LocalDate> mockedStaticDate = mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS); MockedStatic<LocalTime> mockedStaticTime = mockStatic(LocalTime.class, Mockito.CALLS_REAL_METHODS)) {
            mockedStaticDate.when(() -> LocalDate.now()).thenReturn(today);
            mockedStaticTime.when(() -> LocalTime.now()).thenReturn(now);
            logger.info("Now is {}", LocalDateTime.of(today, now));
            //Reinit to set the new now to be tested
            TradingDays.init();
            
            dao.getLastSPXClose();
            
            Mockito.verify(template).queryForObject(queryCaptor.capture(), anyMap(), eq(Double.class));
            assertEquals(SPXHistoryDao.NEXT_TO_LAST_CLOSE_QUERY, queryCaptor.getValue());
        }
    }

}
