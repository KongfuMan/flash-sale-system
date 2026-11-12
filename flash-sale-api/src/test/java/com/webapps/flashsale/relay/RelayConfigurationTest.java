package com.webapps.flashsale.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RelayConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(RelayConfiguration.class);

    private ApplicationContextRunner configured() {
        LettuceConnectionFactory redis = mock(LettuceConnectionFactory.class);
        when(redis.isClusterAware()).thenReturn(true);
        return configured(redis);
    }

    private ApplicationContextRunner configured(LettuceConnectionFactory redis) {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        doReturn(mock(ScheduledFuture.class)).when(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), any(Duration.class));
        return context.withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(LettuceConnectionFactory.class, () -> redis)
                .withBean(TaskScheduler.class, () -> scheduler)
                .withPropertyValues("relay.enabled=true", "relay.poll-ms=1234",
                        "spring.kafka.producer.acks=all",
                        "spring.kafka.producer.properties.enable.idempotence=true",
                        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer");
    }

    @Test
    void disabledByDefaultWithoutInfrastructureOrScheduler() {
        context.run(result -> {
            assertThat(result).hasNotFailed();
            assertThat(result).doesNotHaveBean(RelayWorker.class);
            assertThat(result).doesNotHaveBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
        });
        configured().withPropertyValues("relay.enabled=false").run(result -> {
            assertThat(result).hasNotFailed();
            assertThat(result).doesNotHaveBean(RelayWorker.class);
            assertThat(result).doesNotHaveBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
        });
    }

    @Test
    void enabledRegistersScheduledWorkerWithoutConnecting() {
        configured().run(result -> {
            assertThat(result).hasNotFailed().hasSingleBean(RelayWorker.class);
            var processor = result.getBean(ScheduledAnnotationBeanPostProcessor.class);
            assertThat(processor.getScheduledTasks()).hasSize(1);
            verify(result.getBean(TaskScheduler.class)).scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), eq(Duration.ofMillis(1234)));
            verify(result.getBean(LettuceConnectionFactory.class), never()).getRequiredNativeClient();
        });
    }

    @Test
    void acceptsMinusOneAcks() {
        configured().withPropertyValues("spring.kafka.producer.acks=-1")
                .run(result -> assertThat(result).hasNotFailed().hasSingleBean(RelayWorker.class));
    }

    @Test
    void rejectsUnsafeProducerOverrides() {
        for (String property : new String[]{
                "spring.kafka.producer.acks=0", "spring.kafka.producer.acks=1",
                "spring.kafka.producer.properties.acks=0",
                "spring.kafka.producer.properties.enable.idempotence=false",
                "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.ByteArraySerializer",
                "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.ByteArraySerializer"}) {
            configured().withPropertyValues(property).run(result ->
                    assertThat(result).as(property).hasFailed().getFailure().hasRootCauseInstanceOf(IllegalArgumentException.class));
        }
    }

    @Test
    void relayFactoryIsNonTransactionalAndUnaffectedBySharedFactoryChanges() {
        configured().withPropertyValues("spring.kafka.producer.transaction-id-prefix=other-").run(result -> {
            assertThat(result).hasNotFailed();
            ProducerFactory<?, ?> shared = result.getBean(ProducerFactory.class);
            ProducerFactory<?, ?> relay = (ProducerFactory<?, ?>) ReflectionTestUtils.getField(result.getBean(RelayWorker.class), "producerFactory");
            assertThat(shared.transactionCapable()).isTrue();
            assertThat(relay.transactionCapable()).isFalse();
            shared.updateConfigs(java.util.Map.of("acks", "0"));
            assertThat(relay.getConfigurationProperties()).containsEntry("acks", "all");
        });
    }

    @Test
    void enabledRejectsStandaloneRedis() {
        LettuceConnectionFactory redis = mock(LettuceConnectionFactory.class);
        configured(redis).run(result -> assertThat(result).hasFailed().getFailure()
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasStackTraceContaining("Relay requires Redis Cluster configuration"));
    }
}
