package com.webapps.flashsale;

import com.webapps.flashsale.service.ReservationService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

// Requires a new, empty, disposable database; never points at the application datasource.
@EnabledIfSystemProperty(named = "reservation.test.url", matches = ".+")
class ReservationPostgresTest {
    @Test
    void migratesSchemaAndReservesAtomically() throws Exception {
        var ds = new DriverManagerDataSource(System.getProperty("reservation.test.url"), "postgres", "disposable");
        Flyway.configure().dataSource(ds).load().migrate();
        var sql = new JdbcTemplate(ds);
        sql.update("INSERT INTO users (user_id, username) VALUES (1, 'test')");
        sql.update("INSERT INTO products (product_id, name, total_inventory) VALUES (1, 'test', 10), (2, 'test2', 10)");
        sql.update("INSERT INTO flash_sale_events (event_id, name, start_at, end_at, status) VALUES (1, 'test', now(), now() + interval '1 hour', 'RUNNING')");
        sql.update("INSERT INTO flash_sale_event_products (event_product_id, event_id, product_id, allocated_quantity, flash_price) VALUES (1, 1, 1, 2, 12.34), (2, 1, 2, 1, 7.89)");
        Flyway.configure().dataSource(ds).load().migrate();

        var service = new ReservationService(new NamedParameterJdbcTemplate(ds), new DataSourceTransactionManager(ds));
        var first = service.reserve(1, 1, 1, "first");
        assertTrue(first.created());
        assertEquals(first.orderId(), service.reserve(1, 1, 1, "first").orderId());
        var item = sql.queryForMap("SELECT * FROM order_items WHERE order_id = ?", first.orderId());
        assertEquals(1L, item.get("order_item_id"));
        assertEquals(1L, item.get("product_id"));
        assertEquals(1L, item.get("event_product_id"));
        assertEquals(1, item.get("quantity"));
        assertEquals("12.34", item.get("unit_price_snapshot").toString());

        sql.execute("CREATE FUNCTION skip_reservation_item() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NULL; END $$");
        sql.execute("CREATE TRIGGER skip_reservation_item BEFORE INSERT ON order_items FOR EACH ROW EXECUTE FUNCTION skip_reservation_item()");
        assertThrows(IllegalStateException.class, () -> service.reserve(1, 1, 1, "rollback"));
        assertEquals(1, sql.queryForObject("SELECT reserved_quantity FROM flash_sale_event_products WHERE event_product_id = 1", Integer.class));
        assertEquals(0, sql.queryForObject("SELECT count(*) FROM orders WHERE idempotency_key = 'rollback'", Integer.class));
        sql.execute("DROP TRIGGER skip_reservation_item ON order_items");

        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return service.reserve(1, 2, 1, "last"); });
            var b = pool.submit(() -> { start.await(); return service.reserve(1, 2, 1, "last"); });
            start.countDown();
            var one = a.get(10, TimeUnit.SECONDS);
            var two = b.get(10, TimeUnit.SECONDS);
            assertEquals(one.orderId(), two.orderId());
            assertNotEquals(one.created(), two.created());
            assertEquals(1, sql.queryForObject("SELECT reserved_quantity FROM flash_sale_event_products WHERE event_product_id = 2", Integer.class));
            assertEquals(1, sql.queryForObject("SELECT count(*) FROM order_items WHERE order_id = ?", Integer.class, one.orderId()));
            assertThrows(ResponseStatusException.class, () -> service.reserve(1, 2, 1, "different"));
            assertThrows(ResponseStatusException.class, () -> service.reserve(1, 1, 1, "last"));
        } finally {
            pool.shutdownNow();
        }
    }
}
