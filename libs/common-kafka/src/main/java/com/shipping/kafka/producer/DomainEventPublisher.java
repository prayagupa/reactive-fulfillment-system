package com.shipping.kafka.producer;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Thin wrapper around KafkaTemplate that adds structured logging,
 * metrics, and a consistent error-handling contract.
 */
@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public DomainEventPublisher(KafkaTemplate<String, SpecificRecord> kafkaTemplate,
                                MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Publish an Avro event with the given key (used for Kafka partition routing).
     * The returned future is non-blocking; callers may join it for end-to-end flow control.
     */
    public CompletableFuture<SendResult<String, SpecificRecord>> publish(
            String topic, String key, SpecificRecord event) {

        log.info("Publishing event type={} key={} topic={}", event.getSchema().getName(), key, topic);

        CompletableFuture<SendResult<String, SpecificRecord>> future =
                kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event type={} key={} topic={}: {}",
                        event.getSchema().getName(), key, topic, ex.getMessage());
                meterRegistry.counter("kafka.publish.error",
                        "topic", topic, "event", event.getSchema().getName()).increment();
            } else {
                meterRegistry.counter("kafka.publish.success",
                        "topic", topic, "event", event.getSchema().getName()).increment();
            }
        });

        return future;
    }
}
