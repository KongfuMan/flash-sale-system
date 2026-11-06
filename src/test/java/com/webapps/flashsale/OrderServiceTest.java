package com.webapps.flashsale;

import com.webapps.flashsale.entity.OrderEntity;
import com.webapps.flashsale.repository.OrderRepository;
import com.webapps.flashsale.service.OrderService;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceTest {
    @Test
    void mapsAllOrdersToResponses() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderEntity entity = new OrderEntity();
        entity.orderId = 1L;
        entity.userId = 2L;
        entity.status = "PENDING";
        when(repository.findAll()).thenReturn(List.of(entity));

        var result = new OrderService(repository).getAllOrders();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).orderId()).isEqualTo(1L);
        assertThat(result.get(0).userId()).isEqualTo(2L);
        assertThat(result.get(0).status()).isEqualTo("PENDING");
    }
}
