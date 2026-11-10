package com.webapps.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webapps.flashsale.contract.MessageEnvelope;
import com.webapps.flashsale.contract.MessageType;
import com.webapps.flashsale.contract.OrderRequested;
import com.webapps.flashsale.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class ReservationListener {
    private static final Logger log = LoggerFactory.getLogger(ReservationListener.class);
    private static final TypeReference<MessageEnvelope<OrderRequested>> REQUEST_TYPE = new TypeReference<>() {};
    private final ObjectMapper mapper;
    private final ReservationService reservations;

    public ReservationListener(ObjectMapper mapper, ReservationService reservations) {
        this.mapper = mapper;
        this.reservations = reservations;
    }

    @KafkaListener(topics = "${reservation.kafka.topic:order-requests-v1}",
            groupId = "${reservation.kafka.group-id:reservation-consumer}")
    public void receive(String json, Acknowledgment acknowledgment) throws Exception {
        var root = mapper.readTree(json);
        if (root == null || !root.isObject() || !root.path("message_type").isTextual()
                || !MessageType.ORDER_REQUESTED.name().equals(root.path("message_type").textValue())
                || !root.path("schema_version").isIntegralNumber() || !root.path("schema_version").canConvertToInt()
                || root.path("schema_version").intValue() != 2) {
            throw new IllegalArgumentException("Invalid or unsupported message type or version");
        }
        MessageEnvelope<OrderRequested> event = mapper.readerFor(REQUEST_TYPE)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(root);
        if (event.messageId() == null || event.occurredAt() == null || event.data() == null
                || event.data().flashSaleEventId() == null || event.data().flashSaleEventId() <= 0
                || event.data().userId() == null || event.data().userId() <= 0
                || event.data().items() == null || event.data().items().size() != 1
                || event.data().items().get(0) == null
                || event.data().items().get(0).eventProductId() == null
                || event.data().items().get(0).eventProductId() <= 0
                || !Integer.valueOf(1).equals(event.data().items().get(0).quantity()))
            throw new IllegalArgumentException("Invalid OrderRequested message");
        var request = event.data();
        var result = reservations.reserve(request.flashSaleEventId(), request.items().get(0).eventProductId(), request.userId(),
                "kafka:" + event.messageId());
        log.info("Processed message {} for order {}", event.messageId(), result.orderId());
        acknowledgment.acknowledge();
    }
}
