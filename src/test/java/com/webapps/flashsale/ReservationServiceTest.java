package com.webapps.flashsale;

import com.webapps.flashsale.service.ReservationService;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationServiceTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private ReservationService service;

    @BeforeEach
    void setUp() {
        when(manager.getTransaction(any(TransactionDefinition.class))).thenAnswer(inv -> new SimpleTransactionStatus());
        service = new ReservationService(jdbc, manager);
    }

    @Test
    void createsReservation() {
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM users"), anyMap(), eq(Long.class))).thenReturn(List.of(3L));
        when(jdbc.queryForList(contains("FROM flash_sale_events"), anyMap(), eq(Long.class))).thenReturn(List.of(1L));
        when(jdbc.queryForList(contains("WITH reserved"), anyMap(), eq(Long.class))).thenReturn(List.of(42L));
        assertEquals(new ReservationService.Result(42, true), service.reserve(1, 2, 3, "key"));
        verify(jdbc).queryForList(contains("event_id = :eventId AND event_product_id = :eventProductId"),
                argThat((Map<String, ?> params) -> Long.valueOf(2).equals(params.get("eventProductId"))), eq(Long.class));
        verify(manager).commit(any());
    }

    @Test
    void rechecksKeyAfterLastUnitWasTaken() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("event_id")).thenReturn(1L);
        when(rs.getLong("event_product_id")).thenReturn(2L);
        when(rs.getLong("user_id")).thenReturn(3L);
        when(rs.getLong("order_id")).thenReturn(42L);
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of())
                .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(2)).mapRow(rs, 0)));
        when(jdbc.queryForList(contains("FROM users"), anyMap(), eq(Long.class))).thenReturn(List.of(3L));
        when(jdbc.queryForList(contains("FROM flash_sale_events"), anyMap(), eq(Long.class))).thenReturn(List.of(1L));
        when(jdbc.queryForList(contains("WITH reserved"), anyMap(), eq(Long.class))).thenReturn(List.of());
        assertEquals(new ReservationService.Result(42, false), service.reserve(1, 2, 3, "key"));
    }

    @Test
    void doesNotTreatOtherIntegrityFailuresAsIdempotencyConflicts() {
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenThrow(new DataIntegrityViolationException("other constraint"));
        assertThrows(DataIntegrityViolationException.class, () -> service.reserve(1, 2, 3, "key"));
        verify(manager).rollback(any());
    }

    @Test
    void rejectsMissingUser() {
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM users"), anyMap(), eq(Long.class))).thenReturn(List.of());
        var error = assertThrows(ResponseStatusException.class, () -> service.reserve(1, 2, 3, "key"));
        assertEquals(400, error.getStatusCode().value());
        verify(manager).rollback(any());
    }

    @Test
    void looksUpNamedUniqueConflictOnlyAfterRollback() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("event_id")).thenReturn(1L);
        when(rs.getLong("event_product_id")).thenReturn(2L);
        when(rs.getLong("user_id")).thenReturn(3L);
        when(rs.getLong("order_id")).thenReturn(42L);
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of())
                .thenAnswer(inv -> {
                    verify(manager).rollback(any());
                    return List.of(((RowMapper<?>) inv.getArgument(2)).mapRow(rs, 0));
                });
        when(jdbc.queryForList(contains("FROM users"), anyMap(), eq(Long.class))).thenReturn(List.of(3L));
        when(jdbc.queryForList(contains("FROM flash_sale_events"), anyMap(), eq(Long.class))).thenReturn(List.of(1L));
        var pg = new PSQLException(new ServerErrorMessage("SERROR\0C23505\0norders_idempotency_key_key\0Mduplicate\0\0"));
        when(jdbc.queryForList(contains("WITH reserved"), anyMap(), eq(Long.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate", pg));
        assertEquals(new ReservationService.Result(42, false), service.reserve(1, 2, 3, "key"));
        verify(manager).commit(any());
    }

    @Test
    void rejectsMismatchedIdempotencyKey() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("event_id")).thenReturn(1L);
        when(rs.getLong("user_id")).thenReturn(3L);
        when(rs.getLong("event_product_id")).thenReturn(9L);
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenAnswer(inv -> List.of(((RowMapper<?>) inv.getArgument(2)).mapRow(rs, 0)));
        var error = assertThrows(ResponseStatusException.class, () -> service.reserve(1, 2, 3, "key"));
        assertEquals(409, error.getStatusCode().value());
        verify(manager).rollback(any());
    }
}
