package io.github.taylorone.fhirpipeline.gapanalysis.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.taylorone.fhirpipeline.gapanalysis.gaps.GapAnalysisService;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeasureRunController.class)
class MeasureRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GapAnalysisService gapAnalysisService;

    private static String envelope(String dataJson) {
        String data = dataJson == null ? "" : Base64.getEncoder().encodeToString(dataJson.getBytes());
        return """
                {"message": {"data": "%s", "messageId": "m-1"}, "subscription": "s"}""".formatted(data);
    }

    @Test
    void runsWithExplicitRunDateFromMessage() throws Exception {
        when(gapAnalysisService.run(LocalDate.of(2026, 1, 15)))
                .thenReturn(new GapAnalysisService.RunSummary(UUID.randomUUID(), 3, 5, 7));

        mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON)
                        .content(envelope("{\"runDate\": \"2026-01-15\"}")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("5 open")));

        verify(gapAnalysisService).run(LocalDate.of(2026, 1, 15));
    }

    @Test
    void defaultsToTodayForEmptyPayload() throws Exception {
        when(gapAnalysisService.run(any()))
                .thenReturn(new GapAnalysisService.RunSummary(UUID.randomUUID(), 3, 0, 0));

        mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON).content(envelope(null)))
                .andExpect(status().isOk());

        verify(gapAnalysisService).run(LocalDate.now());
    }

    @Test
    void acksMalformedMessagesInsteadOfRedeliveryLooping() throws Exception {
        mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON)
                        .content(envelope("{\"runDate\": \"not-a-date\"}")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("ignored")));
    }

    @Test
    void returns500SoPubSubRedeliversWhenTheRunFails() throws Exception {
        when(gapAnalysisService.run(any())).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON).content(envelope("{}")))
                .andExpect(status().isInternalServerError());
    }
}
