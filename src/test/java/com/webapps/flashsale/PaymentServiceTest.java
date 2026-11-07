package com.webapps.flashsale;

import com.webapps.flashsale.dto.PaymentRequest;
import com.webapps.flashsale.service.PaymentService;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final PaymentRequest request = new PaymentRequest(10L, "txn", null);
    private PaymentService service;

    @BeforeEach
    void setup() throws Exception {
        when(manager.getTransaction(any(TransactionDefinition.class))).thenAnswer(inv -> new SimpleTransactionStatus());
        var proxy = new ProxyFactory(new PaymentService(jdbc));
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        service = (PaymentService) proxy.getProxy();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("event_id")).thenReturn(2L);
        when(rs.getLong("event_product_id")).thenReturn(3L);
        when(rs.getString("status")).thenReturn("PENDING_PAYMENT");
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(2)).mapRow(rs, 0)));
        when(jdbc.update(contains("UPDATE orders"), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(BigDecimal.class)))
                .thenReturn(List.of(new BigDecimal("12.34")));
    }

    @Test
    void paysAtCurrentPriceAndBindsIdentifiers() {
        var result = service.pay(5, request);
        assertEquals(new BigDecimal("12.34"), result.amount());
        verify(jdbc).query(contains("FOR UPDATE"), eq(java.util.Map.of("orderId", 5L)), any(RowMapper.class));
        verify(jdbc).update(contains("expires_at > clock_timestamp()"), any(MapSqlParameterSource.class));
        verify(jdbc).queryForList(argThat((String sql) -> sql.contains("event_id = :eventId AND event_product_id = :eventProductId")
                        && sql.contains("reserved_quantity >= 1") && sql.contains("RETURNING flash_price")),
                argThat((MapSqlParameterSource p) -> p.getValue("eventId").equals(2L)
                        && p.getValue("eventProductId").equals(3L)), eq(BigDecimal.class));
        verify(jdbc).update(contains("INSERT INTO order_payment"), argThat((MapSqlParameterSource p) ->
                p.getValue("paymentId").equals(10L) && p.getValue("orderId").equals(5L)
                        && p.getValue("transactionId").equals("txn") && p.getValue("paymentMethod") == null
                        && p.getValue("amount").equals(new BigDecimal("12.34"))));
        verify(manager).commit(any());
    }

    @Test
    void rollsBackWhenInventoryUnavailable() {
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(BigDecimal.class))).thenReturn(List.of());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.pay(5, request)).getStatusCode().value());
        verify(manager).rollback(any());
        verify(jdbc, never()).update(contains("INSERT INTO order_payment"), any(MapSqlParameterSource.class));
    }

    @Test
    void rollsBackDuplicateKey() {
        when(jdbc.update(contains("INSERT INTO order_payment"), any(MapSqlParameterSource.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        assertThrows(DuplicateKeyException.class, () -> service.pay(5, request));
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }

    @Test
    void rejectsExpiredAndMissingOrders() {
        when(jdbc.update(contains("UPDATE orders"), any(MapSqlParameterSource.class))).thenReturn(0);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.pay(5, request)).getStatusCode().value());
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.pay(5, request)).getStatusCode().value());
        verify(manager, times(2)).rollback(any());
        verify(jdbc, never()).queryForList(anyString(), any(MapSqlParameterSource.class), eq(BigDecimal.class));
    }

    @Test
    void rejectsAlreadyPaidOrder() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("status")).thenReturn("PAID");
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(2)).mapRow(rs, 0)));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.pay(5, request)).getStatusCode().value());
        verify(manager).rollback(any());
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }
}
