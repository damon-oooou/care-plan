package com.careplan.controller;

import com.careplan.entity.*;
import com.careplan.repository.*;
import com.careplan.service.RedisQueueService;
import com.careplan.worker.CarePlanWorker;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PatientRepository patientRepo;
    @Autowired private ProviderRepository providerRepo;
    @Autowired private CareOrderRepository orderRepo;
    @Autowired private CarePlanRepository carePlanRepo;

    @MockitoBean private RedisQueueService redisQueue;
    @MockitoBean private CarePlanWorker carePlanWorker;

    @BeforeEach
    void setUp() {
        carePlanRepo.deleteAll();
        orderRepo.deleteAll();
        patientRepo.deleteAll();
        providerRepo.deleteAll();

        doNothing().when(redisQueue).pushToQueue(anyString());
    }

    private Map<String, Object> makeRequest(String firstName, String lastName, String mrn,
                                             String dob, String provider, String npi,
                                             String medication, boolean confirm) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("patientFirstName", firstName);
        request.put("patientLastName", lastName);
        request.put("mrn", mrn);
        if (dob != null) request.put("dateOfBirth", dob);
        request.put("referringProvider", provider);
        request.put("referringProviderNpi", npi);
        request.put("primaryDiagnosis", "E11.65");
        request.put("medicationName", medication);
        request.put("patientRecords", "test");
        request.put("confirmWarnings", confirm);
        return request;
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("POST /api/orders with new patient → 202 Accepted")
        void createOrder_newPatient_shouldReturn202() throws Exception {
            Map<String, Object> request = makeRequest(
                    "John", "Doe", "123456", "1990-01-15",
                    "Dr. Smith", "1234567890", "Metformin", false);

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.orderId").isNumber())
                    .andExpect(jsonPath("$.carePlanId").isNumber())
                    .andExpect(jsonPath("$.status").value("pending"));
        }

        @Test
        @DisplayName("GET /api/orders → returns order list")
        void listOrders_shouldReturnList() throws Exception {
            Map<String, Object> request = makeRequest(
                    "John", "Doe", "123456", null,
                    "Dr. Smith", "1234567890", "Metformin", false);
            mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));

            mockMvc.perform(get("/api/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").isNumber())
                    .andExpect(jsonPath("$[0].patientName").value("John Doe"));
        }

        @Test
        @DisplayName("GET /api/orders/{id} → returns single order")
        void getOrder_shouldReturnOrder() throws Exception {
            Map<String, Object> request = makeRequest(
                    "John", "Doe", "123456", null,
                    "Dr. Smith", "1234567890", "Metformin", false);
            String response = mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn().getResponse().getContentAsString();

            Long orderId = objectMapper.readTree(response).get("orderId").asLong();

            mockMvc.perform(get("/api/orders/" + orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.patientName").value("John Doe"));
        }

        @Test
        @DisplayName("GET /api/orders/99999 → 404 Not Found")
        void getOrder_notFound_shouldReturn404() throws Exception {
            mockMvc.perform(get("/api/orders/99999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /api/careplan/{id}/status → returns status")
        void getCarePlanStatus_shouldReturnStatus() throws Exception {
            Map<String, Object> request = makeRequest(
                    "John", "Doe", "123456", null,
                    "Dr. Smith", "1234567890", "Metformin", false);
            String response = mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn().getResponse().getContentAsString();

            Long carePlanId = objectMapper.readTree(response).get("carePlanId").asLong();

            mockMvc.perform(get("/api/careplan/" + carePlanId + "/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("pending"))
                    .andExpect(jsonPath("$.carePlanId").value(carePlanId));
        }
    }

    @Nested
    @DisplayName("Block Errors (409)")
    class BlockErrors {

        @Test
        @DisplayName("NPI conflict → 409 with type=block")
        void npiConflict_shouldReturn409() throws Exception {
            Map<String, Object> request1 = makeRequest(
                    "John", "Doe", "123456", null,
                    "Dr. Smith", "1234567890", "Metformin", false);
            mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request1)));

            Map<String, Object> request2 = makeRequest(
                    "Jane", "Doe", "654321", null,
                    "Dr. Fake", "1234567890", "Aspirin", false);

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request2)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.type").value("block"))
                    .andExpect(jsonPath("$.error.code").value("npi_conflict"));
        }

        @Test
        @DisplayName("Same patient + same medication + same day → 409 duplicate_order")
        void duplicateOrder_sameDay_shouldReturn409() throws Exception {
            Map<String, Object> request = makeRequest(
                    "John", "Doe", "123456", null,
                    "Dr. Smith", "1234567890", "Metformin", false);
            mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.type").value("block"))
                    .andExpect(jsonPath("$.error.code").value("duplicate_order"));
        }
    }

    @Nested
    @DisplayName("Warnings (200)")
    class Warnings {

        @Test
        @DisplayName("MRN exists + different name → warning")
        void mrnConflict_differentName_shouldReturnWarning() throws Exception {
            Map<String, Object> request1 = makeRequest(
                    "John", "Doe", "123456", "1990-01-15",
                    "Dr. Smith", "1234567890", "Metformin", false);
            mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request1)));

            Map<String, Object> request2 = makeRequest(
                    "Wrong", "Name", "123456", "1990-01-15",
                    "Dr. Smith", "1234567890", "Aspirin", false);

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.type").value("warning"))
                    .andExpect(jsonPath("$.error.code").value("needs_confirmation"))
                    .andExpect(jsonPath("$.error.warnings").isArray());
        }

        @Test
        @DisplayName("Warning + confirmWarnings=true → 202 success")
        void mrnConflict_confirmed_shouldReturn202() throws Exception {
            Map<String, Object> request1 = makeRequest(
                    "John", "Doe", "123456", "1990-01-15",
                    "Dr. Smith", "1234567890", "Metformin", false);
            mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request1)));

            Map<String, Object> request2 = makeRequest(
                    "Wrong", "Name", "123456", "1990-01-15",
                    "Dr. Smith", "1234567890", "Aspirin", true);

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request2)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("Unified Error Format")
    class ErrorFormat {

        @Test
        @DisplayName("All errors have success, error.type, error.code, error.message, timestamp")
        void errorResponse_shouldHaveUnifiedFormat() throws Exception {
            Map<String, Object> request1 = makeRequest(
                    "John", "Doe", "123456", null,
                    "Dr. Smith", "1234567890", "Metformin", false);
            mockMvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request1)));

            Map<String, Object> request2 = makeRequest(
                    "Jane", "Doe", "654321", null,
                    "Dr. Fake", "1234567890", "Aspirin", false);

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request2)))
                    .andExpect(jsonPath("$.success").exists())
                    .andExpect(jsonPath("$.error.type").exists())
                    .andExpect(jsonPath("$.error.code").exists())
                    .andExpect(jsonPath("$.error.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }
}
