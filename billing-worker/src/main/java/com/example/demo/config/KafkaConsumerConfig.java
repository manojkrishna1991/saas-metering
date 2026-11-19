package com.example.demo.config;

import com.example.demo.event.UsageEvent;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Clock;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Producer used only to publish failed records to the dead letter topic. */
    @Bean
    KafkaTemplate<String, Object> deadLetterTemplate(KafkaProperties properties) {
        Map<String, Object> config = properties.buildProducerProperties();
        DelegatingByTypeSerializer valueSerializer = new DelegatingByTypeSerializer(Map.of(
                byte[].class, new ByteArraySerializer(),
                UsageEvent.class, new JacksonJsonSerializer<UsageEvent>()));
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config, new StringSerializer(), valueSerializer));
    }

    /** Retry transient failures 3 times, one second apart, then publish to {@code <topic>.DLT}. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> deadLetterTemplate,
                                          @Value("${billing.kafka.dlt-suffix:.DLT}") String dltSuffix) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(record.topic() + dltSuffix, -1));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}
