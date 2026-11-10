package com.webapps.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webapps.flashsale.contract.MessageEnvelope;
import com.webapps.flashsale.contract.MessageType;
import com.webapps.flashsale.contract.OrderRequested;
import com.webapps.consumer.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class MessageListener {
    private static final TypeReference<MessageEnvelope<OrderRequested>> REQUEST_TYPE = new TypeReference<>() {};
    private final ObjectMapper mapper;
    private final OrderService orders;

    public MessageListener(ObjectMapper mapper, OrderService orders) {
        this.mapper = mapper;
        this.orders = orders;
    }

    @KafkaListener(topics = "${reservation.kafka.topic:order-requests-v1}",
            groupId = "${reservation.kafka.group-id:reservation-consumer}")
    public void receive(String json, Acknowledgment acknowledgment) throws Exception {
        var root = mapper.readTree(json);
        if (root == null || !root.isObject() || !root.path("message_type").isTextual()
                || !root.path("schema_version").isIntegralNumber() || !root.path("schema_version").canConvertToInt()) {
            throw new IllegalArgumentException("Invalid or unsupported message type or version");
        }
        switch (MessageType.valueOf(root.path("message_type").textValue())) {
            case ORDER_REQUESTED -> {
                if (root.path("schema_version").intValue() != 2) {
                    throw new IllegalArgumentException("Unsupported ORDER_REQUESTED version");
                }
                MessageEnvelope<OrderRequested> event = mapper.readerFor(REQUEST_TYPE)
                        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(root);
                orders.handleOrderRequested(event);
            }
            case ORDER_CREATED -> throw new IllegalArgumentException("Unsupported message type: ORDER_CREATED");
        }
        acknowledgment.acknowledge();
    }
}
