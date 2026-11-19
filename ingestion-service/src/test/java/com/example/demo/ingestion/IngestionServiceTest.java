package com.example.demo.ingestion;

import com.example.demo.event.UsageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    @Mock KafkaTemplate<String, UsageEvent> kafka;

    IngestionService service;
    UsageEvent event = new UsageEvent(UUID.randomUUID(), "tenant-1", "api_calls", 5L, Instant.now());
    String key = "idempotent:event:" + event.eventId();
    String usageKey = "quota:usage:tenant-1:api_calls:" + java.time.YearMonth.from(event.eventTimestamp().atZone(java.time.ZoneOffset.UTC));
    String limitKey = "quota:limit:tenant-1:api_calls";

    @BeforeEach
    void setUp() {
        service = new IngestionService(redis, kafka, new IngestionProperties(null, null, null));
        when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    @SuppressWarnings("unchecked")
    void newEventIsPublishedWithTenantKey() {
        when(ops.setIfAbsent(key, "1", Duration.ofHours(24))).thenReturn(true);
        when(ops.increment(usageKey, 5L)).thenReturn(5L);
        when(kafka.send("tenant-usage-events", "tenant-1", event))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        service.ingest(event);

        verify(kafka).send("tenant-usage-events", "tenant-1", event);
        verify(redis, never()).delete(anyString());
        verify(ops).increment(usageKey, 5L);
    }

    @Test
    void duplicateIsNotPublished() {
        when(ops.setIfAbsent(key, "1", Duration.ofHours(24))).thenReturn(false);

        service.ingest(event);

        verifyNoInteractions(kafka);
    }

    @Test
    void kafkaFailureDeletesKeyAndThrows() {
        when(ops.setIfAbsent(key, "1", Duration.ofHours(24))).thenReturn(true);
        when(ops.increment(usageKey, 5L)).thenReturn(5L);
        when(kafka.send(anyString(), anyString(), any(UsageEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThrows(EventPublishException.class, () -> service.ingest(event));

        verify(redis).delete(key);
        verify(ops).decrement(usageKey, 5L);
    }

    @Test
    void overLimitReturns429AndSkipsKafka() {
        when(ops.setIfAbsent(key, "1", Duration.ofHours(24))).thenReturn(true);
        when(ops.increment(usageKey, 5L)).thenReturn(101L);
        when(ops.get(limitKey)).thenReturn("100");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.ingest(event));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        verifyNoInteractions(kafka);
        verify(ops).decrement(usageKey, 5L);
        verify(redis).delete(key);
    }

    @Test
    @SuppressWarnings("unchecked")
    void exactlyAtLimitIsAllowed() {
        when(ops.setIfAbsent(key, "1", Duration.ofHours(24))).thenReturn(true);
        when(ops.increment(usageKey, 5L)).thenReturn(100L);
        when(ops.get(limitKey)).thenReturn("100");
        when(kafka.send(anyString(), anyString(), any(UsageEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        service.ingest(event);

        verify(kafka).send("tenant-usage-events", "tenant-1", event);
    }
}
