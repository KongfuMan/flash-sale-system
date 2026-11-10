package com.webapps.flashsale;

import com.webapps.flashsale.entity.OrderEntity;
import com.webapps.flashsale.entity.OrderItemEntity;
import com.webapps.flashsale.inventory.InventoryPrimitives;
import com.webapps.flashsale.repository.OrderItemRepository;
import com.webapps.flashsale.repository.OrderRepository;
import com.webapps.flashsale.service.OrderService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final OrderService service = new OrderService(orders, items, mock(InventoryPrimitives.class));

    @Test
    void groupsItemsInOneQueryAndKeepsOrdersWithoutItems() {
        var page = PageRequest.of(0, 3, Sort.by("orderId"));
        OrderEntity first = new OrderEntity();
        first.orderId = 1L;
        OrderEntity second = new OrderEntity();
        second.orderId = 2L;
        OrderItemEntity item1 = new OrderItemEntity();
        item1.orderId = 1L;
        item1.orderItemId = 10L;
        item1.unitPriceSnapshot = new BigDecimal("1.20");
        OrderItemEntity item2 = new OrderItemEntity();
        item2.orderId = 1L;
        item2.orderItemId = 11L;
        when(orders.findAllBy(page)).thenReturn(new SliceImpl<>(List.of(first, second), page, true));
        when(items.findByOrderIdInOrderByOrderItemIdAsc(List.of(1L, 2L))).thenReturn(List.of(item1, item2));

        var result = service.getOrders(0, 3);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.orders()).hasSize(2);
        assertThat(result.orders().get(0).items()).extracting("orderItemId").containsExactly(10L, 11L);
        assertThat(result.orders().get(0).items().get(0).unitPriceSnapshot()).isEqualByComparingTo("1.20");
        assertThat(result.orders().get(1).items()).isEmpty();
        verify(items).findByOrderIdInOrderByOrderItemIdAsc(List.of(1L, 2L));
    }

    @Test
    void skipsItemsQueryForEmptyPage() {
        var page = PageRequest.of(0, 20, Sort.by("orderId"));
        when(orders.findAllBy(page)).thenReturn(new SliceImpl<>(List.of(), page, false));
        assertThat(service.getOrders(0, 20).orders()).isEmpty();
        verifyNoInteractions(items);
    }

    @Test
    void retrievesOrderAndItsItems() {
        OrderEntity order = new OrderEntity();
        order.orderId = 1L;
        OrderItemEntity item = new OrderItemEntity();
        item.orderItemId = 10L;
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        when(items.findByOrderIdInOrderByOrderItemIdAsc(List.of(1L))).thenReturn(List.of(item));
        assertThat(service.getOrder(1L).orElseThrow().items()).extracting("orderItemId").containsExactly(10L);
    }

    @Test
    void missingOrderDoesNotQueryItems() {
        when(orders.findById(99L)).thenReturn(Optional.empty());
        assertThat(service.getOrder(99L)).isEmpty();
        verifyNoInteractions(items);
    }
}
