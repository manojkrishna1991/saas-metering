package com.example.demo.ingestion;

import com.example.demo.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestionController.class)
@Import(GlobalExceptionHandler.class)
class IngestionControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean IngestionService service;

    static final String VALID = """
            {"eventId":"11111111-1111-1111-1111-111111111111","tenantId":"t1",
             "metricType":"api_calls","quantity":3,"eventTimestamp":"2026-01-01T00:00:00Z"}""";

    @Test
    void validEventIsAccepted() throws Exception {
        mvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isAccepted());
    }

    @Test
    void zeroQuantityIsRejected() throws Exception {
        mvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("\"quantity\":3", "\"quantity\":0")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void missingEventIdIsRejected() throws Exception {
        mvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("\"eventId\":\"11111111-1111-1111-1111-111111111111\",", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishFailureReturns503() throws Exception {
        doThrow(new EventPublishException("x", null)).when(service).ingest(any());
        mvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isServiceUnavailable());
    }
}
