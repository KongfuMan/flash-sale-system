package com.webapps.flashsale;

import com.webapps.flashsale.repository.FlashSaleEventRepository;
import com.webapps.flashsale.repository.FlashSaleEventProductRepository;
import com.webapps.flashsale.repository.OrderRepository;
import com.webapps.flashsale.repository.OrderItemRepository;
import com.webapps.flashsale.repository.OrderPaymentRepository;
import com.webapps.flashsale.repository.ProductRepository;
import com.webapps.flashsale.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "citus.integration", matches = "true")
@Transactional(readOnly = true)
class CitusRepositoryIntegrationTest {
    @Autowired private UserRepository users;
    @Autowired private ProductRepository products;
    @Autowired private FlashSaleEventRepository events;
    @Autowired private FlashSaleEventProductRepository eventProducts;
    @Autowired private OrderRepository orders;
    @Autowired private OrderItemRepository orderItems;
    @Autowired private OrderPaymentRepository payments;

    @Test
    void readsBusinessTables() {
        var page = PageRequest.of(0, 1);
        users.findAll(page);
        products.findAll(page);
        events.findAll(page);
        eventProducts.findAll(page);
        var orderPage = orders.findAllBy(page);
        orderItems.findAll(page);
        var orderIds = orderPage.getContent().stream().map(order -> order.orderId).toList();
        orderItems.findByOrderIdInOrderByOrderItemIdAsc(orderIds.isEmpty() ? java.util.List.of(-1L) : orderIds);
        payments.findAll(page);
    }
}
