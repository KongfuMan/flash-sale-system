package com.webapps.flashsale.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "relay.enabled", havingValue = "true")
class RelayConfiguration {
    @Bean(destroyMethod = "close")
    RelayWorker relayWorker(ObjectMapper mapper, LettuceConnectionFactory redis,
                            ProducerFactory<String, String> producerFactory,
                            @Value("${relay.topic:order-requests-v1}") String topic,
                            @Value("${relay.timeout-ms:5000}") long timeoutMs) {
        Map<String, Object> properties = new HashMap<>(producerFactory.getConfigurationProperties());
        Object acks = properties.get(ProducerConfig.ACKS_CONFIG);
        if (!"all".equals(String.valueOf(acks)) && !"-1".equals(String.valueOf(acks))) {
            throw new IllegalArgumentException("Relay requires Kafka producer acks=all or -1");
        }
        if (!"true".equalsIgnoreCase(String.valueOf(properties.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)))) {
            throw new IllegalArgumentException("Relay requires Kafka producer enable.idempotence=true");
        }
        for (String key : new String[]{ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG}) {
            Object serializer = properties.get(key);
            if (!StringSerializer.class.equals(serializer) && !StringSerializer.class.getName().equals(serializer)) {
                throw new IllegalArgumentException("Relay requires StringSerializer for " + key);
            }
        }
        if (properties.containsKey(ProducerConfig.TRANSACTIONAL_ID_CONFIG)) {
            throw new IllegalArgumentException("Relay does not support a transactional.id");
        }
        return new RelayWorker(mapper, redis, new DefaultKafkaProducerFactory<>(properties), topic, timeoutMs);
    }
}
