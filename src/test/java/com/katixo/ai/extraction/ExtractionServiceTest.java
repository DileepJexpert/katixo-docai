package com.katixo.ai.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.katixo.ai.config.AiProperties;
import com.katixo.ai.llm.LlmClient;
import com.katixo.ai.llm.LlmRequest;
import com.katixo.ai.llm.LlmResponse;
import com.katixo.ai.llm.LlmRole;
import com.katixo.ai.model.ExceptionType;
import com.katixo.ai.model.ModelPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtractionServiceTest {

    private static final String VALID_JSON = """
            {"docType":"INVOICE","header":{"supplierName":"Sri Balaji Pharma Distributors",
            "supplierGstin":"29ABCDE1234F1ZW","invoiceNumber":"SBP/24-25/00187","invoiceDate":"2024-05-14",
            "subTotal":8950.00,"cgst":537.00,"sgst":537.00,"igst":null,"roundOff":0.00,"grandTotal":10024.00,
            "currency":"INR"},"lineItems":[{"description":"Paracetamol 500mg","hsn":"3004","qty":100.0,
            "uom":"BOX","rate":45.00,"discount":0.00,"taxableValue":4500.00,"gstRate":12.0,"lineTotal":5040.00}]}
            """;

    private LlmClient llm;
    private ExtractionService service;

    @BeforeEach
    void setup() {
        llm = mock(LlmClient.class);
        AiProperties props = new AiProperties();
        PromptService prompts = new PromptService(props);
        ExtractionSchemaValidator validator = new ExtractionSchemaValidator();
        validator.init();
        service = new ExtractionService(llm, prompts, validator, new ObjectMapper(), props);
    }

    @Test
    void validJsonOnFirstTry() {
        when(llm.generate(any())).thenReturn(new LlmResponse(VALID_JSON, "qwen2.5:7b-instruct", 120));

        ExtractionStepResult step = service.extract("ocr text", 0.95, List.of(), "INVOICE");

        assertThat(step.document()).isNotNull();
        assertThat(step.document().header().grandTotal()).isEqualTo(10024.00);
        assertThat(step.jsonValid()).isTrue();
        assertThat(step.repaired()).isFalse();
        assertThat(step.modelPath()).isEqualTo(ModelPath.TEXT_LLM);
        assertThat(step.structuralExceptions()).isEmpty();
        verify(llm, times(1)).generate(any());
    }

    @Test
    void oneRepairRetryRecoversInvalidJson() {
        when(llm.generate(any())).thenReturn(
                new LlmResponse("sorry, here is the data but not json", "qwen", 50),
                new LlmResponse(VALID_JSON, "qwen", 60));

        ExtractionStepResult step = service.extract("ocr text", 0.95, List.of(), null);

        assertThat(step.document()).isNotNull();
        assertThat(step.jsonValid()).isTrue();
        assertThat(step.repaired()).isTrue();
        assertThat(step.structuralExceptions()).isEmpty();
        verify(llm, times(2)).generate(any());
    }

    @Test
    void stillInvalidAfterRepairReturnsStructuredFailureNotCrash() {
        when(llm.generate(any())).thenReturn(
                new LlmResponse("garbage", "qwen", 10),
                new LlmResponse("still garbage", "qwen", 10));

        ExtractionStepResult step = service.extract("ocr text", 0.95, List.of(), null);

        assertThat(step.jsonValid()).isFalse();
        assertThat(step.repaired()).isTrue();
        assertThat(step.document()).isNull();
        assertThat(step.structuralExceptions()).extracting(e -> e.type())
                .contains(ExceptionType.JSON_INVALID);
        verify(llm, times(2)).generate(any());
    }

    @Test
    void lowOcrConfidenceRoutesToVlmWithImages() {
        when(llm.generate(any())).thenReturn(new LlmResponse(VALID_JSON, "minicpm-v", 200));

        ExtractionStepResult step = service.extract("", 0.10, List.of("BASE64IMAGE"), "INVOICE");

        assertThat(step.modelPath()).isEqualTo(ModelPath.VLM);
        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm).generate(captor.capture());
        assertThat(captor.getValue().role()).isEqualTo(LlmRole.VISION);
        assertThat(captor.getValue().base64Images()).containsExactly("BASE64IMAGE");
    }
}
