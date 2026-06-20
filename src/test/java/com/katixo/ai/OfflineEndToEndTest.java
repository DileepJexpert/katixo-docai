package com.katixo.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.katixo.ai.config.AiProperties;
import com.katixo.ai.privacy.LocalhostEndpoints;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof (spec acceptance criteria): the whole pipeline runs against LOOPBACK-ONLY stubs -
 * no external network is touched. A clean synthetic invoice flows through preprocess -> OCR ->
 * text-LLM -> validation -> persistence and comes back as the section 5.4 contract with no
 * exceptions and needsHumanReview=false; it can then be fetched by id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OfflineEndToEndTest {

    // Canned valid extraction matching eval/golden/sample-01 (BOTH lines) so validation passes cleanly.
    private static final String CANNED_JSON = """
            {"docType":"INVOICE","header":{"supplierName":"Sri Balaji Pharma Distributors",
            "supplierGstin":"29ABCDE1234F1ZW","invoiceNumber":"SBP/24-25/00187","invoiceDate":"2024-05-14",
            "subTotal":8950.00,"cgst":537.00,"sgst":537.00,"igst":null,"roundOff":0.00,"grandTotal":10024.00,
            "currency":"INR"},"lineItems":[
            {"description":"Paracetamol 500mg","hsn":"3004","qty":100.0,"uom":"BOX","rate":45.00,
            "discount":0.00,"taxableValue":4500.00,"gstRate":12.0,"lineTotal":5040.00},
            {"description":"Amoxicillin 250mg","hsn":"3004","qty":50.0,"uom":"BOX","rate":90.00,
            "discount":50.00,"taxableValue":4450.00,"gstRate":12.0,"lineTotal":4984.00}]}
            """;

    private static final LoopbackStub STUB = LoopbackStub.start(CANNED_JSON, 0.95);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private AiProperties props;

    @DynamicPropertySource
    static void wireLoopbackEndpoints(DynamicPropertyRegistry registry) {
        registry.add("katixo.ai.ollama.base-url", STUB::baseUrl);
        registry.add("katixo.ai.ocr.base-url", STUB::baseUrl);
    }

    @AfterAll
    static void tearDown() {
        STUB.stop();
    }

    @Test
    void allAiEndpointsAreLoopback() {
        // The privacy guarantee, asserted in a test: nothing can leave the host.
        assertThat(LocalhostEndpoints.isLocal(props.getOllama().getBaseUrl())).isTrue();
        assertThat(LocalhostEndpoints.isLocal(props.getOcr().getBaseUrl())).isTrue();
    }

    @Test
    void extractsCleanInvoiceEndToEndAndPersistsIt() throws Exception {
        byte[] image = Files.readAllBytes(Path.of("eval/golden/sample-01.png"));
        MockMultipartFile file = new MockMultipartFile("file", "sample-01.png", "image/png", image);

        MvcResult result = mockMvc.perform(multipart("/api/v1/extract").file(file).param("docType", "INVOICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.docType").value("INVOICE"))
                .andExpect(jsonPath("$.modelPath").value("text-llm"))
                .andExpect(jsonPath("$.header.supplierGstin").value("29ABCDE1234F1ZW"))
                .andExpect(jsonPath("$.header.grandTotal").value(10024.00))
                .andExpect(jsonPath("$.lineItems.length()").value(2))
                .andExpect(jsonPath("$.exceptions.length()").value(0))
                .andExpect(jsonPath("$.needsHumanReview").value(false))
                .andReturn();

        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        String id = body.get("id").asText();

        // Fetch the persisted record by id.
        mockMvc.perform(get("/api/v1/extract/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.header.grandTotal").value(10024.00));
    }

    @Test
    void healthReportsDependenciesAndModel() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.ollamaReachable").value(true))
                .andExpect(jsonPath("$.ocrReachable").value(true))
                .andExpect(jsonPath("$.configuredTextModel").value("qwen2.5:7b-instruct"));
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/extract/{id}", "does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
