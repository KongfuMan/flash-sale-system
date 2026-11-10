package com.webapps.flashsale;

import com.webapps.flashsale.entity.FlashSaleEventProductEntity;
import com.webapps.flashsale.entity.OrderPaymentEntity;
import com.webapps.flashsale.entity.UserEntity;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BusinessEntityMappingTest {
    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndReloadsAssignedIdsAndScalarColumns() {
        UserEntity user = new UserEntity();
        user.userId = 101L;
        user.username = "mapping-test";
        entityManager.persist(user);

        FlashSaleEventProductEntity eventProduct = new FlashSaleEventProductEntity();
        eventProduct.eventProductId = 102L;
        eventProduct.eventId = 103L;
        eventProduct.productId = 104L;
        eventProduct.allocatedQuantity = 10;
        eventProduct.soldQuantity = 2;
        eventProduct.reservedQuantity = 1;
        eventProduct.perUserLimit = 1;
        eventProduct.flashPrice = new BigDecimal("12.34");
        entityManager.persist(eventProduct);

        OrderPaymentEntity payment = new OrderPaymentEntity();
        payment.paymentId = 105L;
        payment.orderId = 106L;
        payment.amount = new BigDecimal("12.34");
        payment.status = "INIT";
        entityManager.persist(payment);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(UserEntity.class, 101L).username).isEqualTo("mapping-test");
        FlashSaleEventProductEntity stored = entityManager.find(FlashSaleEventProductEntity.class, 102L);
        assertThat(stored.eventId).isEqualTo(103L);
        assertThat(stored.productId).isEqualTo(104L);
        assertThat(stored.flashPrice).isEqualByComparingTo("12.34");
        assertThat(stored.reservedQuantity).isEqualTo(1);
        assertThat(entityManager.find(OrderPaymentEntity.class, 105L).amount).isEqualByComparingTo("12.34");
    }
}
