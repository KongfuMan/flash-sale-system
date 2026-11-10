package com.webapps.consumer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.listener.ContainerProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsumerConfigurationTest {
    @Test
    void disablesAutoCommitAndUsesManualImmediateAcknowledgment() {
        var properties = new KafkaProperties();
        properties.getConsumer().setEnableAutoCommit(true);
        var factory = new ConsumerConfiguration().kafkaListenerContainerFactory(properties);
        var config = factory.getConsumerFactory().getConfigurationProperties();
        assertEquals(false, config.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
        assertEquals(StringDeserializer.class, config.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals("earliest", config.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
        assertEquals(ContainerProperties.AckMode.MANUAL_IMMEDIATE, factory.getContainerProperties().getAckMode());
    }
}
