package com.careplan.service;

import com.careplan.dto.OrderRequest;
import com.careplan.entity.*;
import com.careplan.exception.BlockError;
import com.careplan.exception.WarningException;
import com.careplan.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private PatientRepository patientRepo;
    @Mock private ProviderRepository providerRepo;
    @Mock private CareOrderRepository orderRepo;
    @Mock private CarePlanRepository carePlanRepo;
    @Mock private RedisQueueService redisQueue;

    @InjectMocks private OrderService orderService;

    private OrderRequest baseRequest;

    @BeforeEach
    void setUp() {
        baseRequest = new OrderRequest();
        baseRequest.patientFirstName = "John";
        baseRequest.patientLastName = "Doe";
        baseRequest.mrn = "123456";
        baseRequest.dateOfBirth = "1990-01-15";
        baseRequest.referringProvider = "Dr. Smith";
        baseRequest.referringProviderNpi = "1234567890";
        baseRequest.primaryDiagnosis = "E11.65";
        baseRequest.medicationName = "Metformin";
        baseRequest.patientRecords = "test records";
        baseRequest.confirmWarnings = false;
    }

    private Patient makePatient(Long id, String firstName, String lastName, String mrn, LocalDate dob) {
        Patient p = new Patient();
        p.setId(id);
        p.setFirstName(firstName);
        p.setLastName(lastName);
        p.setMrn(mrn);
        p.setDateOfBirth(dob);
        return p;
    }

    private Provider makeProvider(Long id, String name, String npi) {
        Provider p = new Provider();
        p.setId(id);
        p.setName(name);
        p.setNpi(npi);
        return p;
    }

    private CareOrder makeOrder(Long id, Patient patient, String medicationName, LocalDateTime createdAt) {
        CareOrder o = new CareOrder();
        o.setId(id);
        o.setPatient(patient);
        o.setMedicationName(medicationName);
        o.setCreatedAt(createdAt);
        return o;
    }

    private void mockSuccessfulCreation() {
        Patient savedPatient = makePatient(1L, "John", "Doe", "123456", LocalDate.of(1990, 1, 15));
        Provider savedProvider = makeProvider(1L, "Dr. Smith", "1234567890");
        CareOrder savedOrder = new CareOrder();
        savedOrder.setId(1L);
        CarePlan savedPlan = new CarePlan();
        savedPlan.setId(1L);

        when(patientRepo.save(any(Patient.class))).thenReturn(savedPatient);
        when(providerRepo.save(any(Provider.class))).thenReturn(savedProvider);
        when(orderRepo.save(any(CareOrder.class))).thenReturn(savedOrder);
        when(carePlanRepo.save(any(CarePlan.class))).thenAnswer(invocation -> {
    CarePlan cp = invocation.getArgument(0);
    cp.setId(1L);  // or use ReflectionTestUtils if there's no setter
    return cp;
});
        doNothing().when(redisQueue).pushToQueue(anyString());
    }

    // ================================================================
    // Provider Duplicate Detection
    // ================================================================
    @Nested
    @DisplayName("Provider Duplicate Detection")
    class ProviderTests {

        @Test
        @DisplayName("NPI exists + same name → reuse provider, no error")
        void npiExists_sameName_shouldSucceed() {
            Provider existing = makeProvider(1L, "Dr. Smith", "1234567890");
            when(providerRepo.findByNpi("1234567890")).thenReturn(Optional.of(existing));
            when(patientRepo.findByMrn("123456")).thenReturn(Optional.empty());
            when(patientRepo.findByFirstNameAndLastNameAndDateOfBirth(any(), any(), any())).thenReturn(List.of());
            mockSuccessfulCreation();

            Map<String, Object> result = orderService.createOrder(baseRequest);
            assertNotNull(result.get("orderId"));
        }

        @Test
        @DisplayName("NPI exists + same name case insensitive → reuse provider")
        void npiExists_sameName_caseInsensitive_shouldSucceed() {
            Provider existing = makeProvider(1L, "dr. smith", "1234567890");
            when(providerRepo.findByNpi("1234567890")).thenReturn(Optional.of(existing));
            when(patientRepo.findByMrn("123456")).thenReturn(Optional.empty());
            when(patientRepo.findByFirstNameAndLastNameAndDateOfBirth(any(), any(), any())).thenReturn(List.of());
            mockSuccessfulCreation();

            Map<String, Object> result = orderService.createOrder(baseRequest);
            assertNotNull(result.get("orderId"));
        }

        @Test
        @DisplayName("NPI exists + different name → BlockError")
        void npiExists_differentName_shouldBlock() {
            Provider existing = makeProvider(1L, "Dr. Chen", "1234567890");
            when(providerRepo.findByNpi("1234567890")).thenReturn(Optional.of(existing));

            BlockError error = assertThrows(BlockError.class, () -> orderService.createOrder(baseRequest));
            assertEquals("npi_conflict", error.getCode());
            assertTrue(error.getMessage().contains("Dr. Chen"));
        }

        @Test
        @DisplayName("NPI does not exist → create new provider")
        void npiNotExists_shouldCreateNew() {
            when(providerRepo.findByNpi("1234567890")).thenReturn(Optional.empty());
            when(patientRepo.findByMrn("123456")).thenReturn(Optional.empty());
            when(patientRepo.findByFirstNameAndLastNameAndDateOfBirth(any(), any(), any())).thenReturn(List.of());
            mockSuccessfulCreation();

            Map<String, Object> result = orderService.createOrder(baseRequest);
            assertNotNull(result.get("orderId"));
            verify(providerRepo).save(any(Provider.class));
        }
    }

    // ================================================================
    // Patient Duplicate Detection
    // ================================================================
    @Nested
    @DisplayName("Patient Duplicate Detection")
    class PatientTests {

        @BeforeEach
        void setUpProvider() {
            when(providerRepo.findByNpi("1234567890")).thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("MRN exists + same name + same DOB → reuse patient, no warning")
        void mrnExists_sameNameDob_shouldReuse() {
            Patient existing = makePatient(1L, "John", "Doe", "123456", LocalDate.of(1990, 1, 15));
            when(patientRepo.findByMrn("123456")).thenReturn(Optional.of(existing));
            when(orderRepo.findByPatientIdAndMedicationNameAndCreatedAtBetween(anyLong(), anyString(), any(), any()))
                    .thenReturn(List.of());
            when(orderRepo.findByPatientIdAndMedicationName(anyLong(), anyString()))
                    .thenReturn(List.of());
            mockSuccessfulCreation();

            Map<String, Object> result = orderService.createOrder(baseRequest);
            assertNotNull(result.get("orderId"));
        }

        @Test
        @DisplayName("MRN exists + different name → WarningException")
        void mrnExists_differentName_shouldWarn() {
            Patient existing = makePatient(1L, "Jane", "Smith", "123456", LocalDate.of(1990, 1, 15));
            when(patientRepo.findByMrn("123456")).thenReturn(Optional.of(existing));
            when(orderRepo.findByPatientIdAndMedicationNameAndCreatedAtBetween(anyLong(), anyString(), any(), any()))
                    .thenReturn(List.of());
            when(orderRepo.findByPatientIdAndMedicationName(anyLong(), anyString()))
                    .thenReturn(List.of());

            WarningException warning = assertThrows(WarningException.class, () -> orderService.createOrder(baseRequest));
            assertEquals("needs_confirmation", warning.getCode());
            assertFalse(warning.getWarnings().isEmpty());
        }

        @Test
        @DisplayName("MRN exists + different DOB → WarningException")
        void mrnExists_differentDob_shouldWarn() {
            Patient existing = makePatient(1L, "John", "Doe", "123456", LocalDate.of(1985, 5, 20));
            when(patientRepo.findByMrn("123456")).thenReturn(Optional.of(existing));
            when(orderRepo.findByPatientIdAndMedicationNameAndCreatedAtBetween(anyLong(), anyString(), any(), any()))
                    .thenReturn(List.of());
            when(orderRepo.findByPatientIdAndMedicationName(anyLong(), anyString()))
                    .thenReturn(List.of());

            WarningException warning = assertThrows(WarningException.class, () -> orderService.createOrder(baseRequest));
            assertEquals("needs_confirmation", warning.getCode());
        }

        @Test
        @DisplayName("MRN exists + different name + confirmWarnings=true → skip warning")
        void mrnExists_differentName_confirmed_shouldSucceed() {
            baseRequest.confirmWarnings = true;
            Patient existing = makePatient(1L, "Jane", "Smith", "123456", LocalDate.of(1990, 1, 15));
            when(patientRepo.findByMrn("123456")).thenReturn(Optional.of(existing));
            when(orderRepo.findByPatientIdAndMedicationNameAndCreatedAtBetween(anyLong(), anyString(), any(), any()))
                    .thenReturn(List.of());
            when(orderRepo.findByPatientIdAndMedicationName(anyLong(), anyString()))
                    .thenReturn(List.of());
            mockSuccessfulCreation();

            Map<String, Object> result = orderService.createOrder(baseRequest);
            assertNotNull(result.get("orderId"));
        }

        @Test
        @DisplayName("MRN not exists + same name+DOB found → WarningException")
        void mrnNotExists_sameNameDob_shouldWarn() {
            when(patientRepo.findByMrn("123456")).thenReturn(Optional.empty());
            Patient samePerson = makePatient(5L, "John", "Doe", "999999", LocalDate.of(1990, 1, 15));
            when(patientRepo.findByFirstNameAndLastNameAndDateOfBirth("John", "Doe", LocalDate.of(1990, 1, 15)))
                    .thenReturn(List.of(samePerson));

            WarningException warning = assertThrows(WarningException.class, () -> orderService.createOrder(baseRequest));
            assertEquals("needs_confirmation", warning.getCode());
            assertTrue(warning.getWarnings().get(0).contains("999999"));
        }

        @Test
        @DisplayName("MRN not exists + no name match → create new patient")
        void mrnNotExists_noMatch_shouldCreateNew() {
            when(patientRepo.findByMrn("123456")).thenReturn(Optional.empty());
            when(patientRepo.findByFirstNameAndLastNameAndDateOfBirth("John", "Doe", LocalDate.of(1990, 1, 15)))
                    .thenReturn(List.of());
            mockSuccessfulCreation();

            Map<String, Object> result = orderService.createOrder(baseRequest);
            assertNotNull(result.get("orderId"));
            verify(patientRepo).save(any(Patient.class));
        }

        @Test
        @DisplayName("No DOB provided → skip DOB check")
        void noDob_mrnNotExists_shouldSkipDobCheck() {
            baseRequest.dateOfBirth = null;
            when(patientRepo.findByMrn("123456")).thenReturn(Optional.empty());
            mockSuccessfulCreation();

            Map<String, Object> result = orderService.createOrder(baseRequest);
            assertNotNull(result.get("orderId"));
            verify(patientRepo, never()).findByFirstNameAndLastNameAndDateOfBirth(any(), any(), any());
        }
    }

    // ================================================================
    // Order Duplicate Detection
    // ================================================================
    @Nested
    @DisplayName("Order Duplicate Detection")
    class OrderTests {

        private Patient existingPatient;

        @BeforeEach
        void setUpExistingPatient() {
            when(providerRepo.findByNpi("1234567890")).thenReturn(Optional.empty());
            existingPatient = makePatient(1L, "John", "Doe", "123456", LocalDate.of(1990, 1, 15));
            when(patientRepo.findByMrn("123456")).thenReturn(Optional.of(existingPatient));
        }

        @Test
        @DisplayName("Same patient + same medication + same day → BlockError")
        void samePatient_sameMed_sameDay_shouldBlock() {
            CareOrder todayOrder = makeOrder(1L, existingPatient, "Metformin", LocalDateTime.now());
            when(orderRepo.findByPatientIdAndMedicationNameAndCreatedAtBetween(eq(1L), eq("Metformin"), any(), any()))
                    .thenReturn(List.of(todayOrder));

            BlockError error = assertThrows(BlockError.class, () -> orderService.createOrder(baseRequest));
            assertEquals("duplicate_order", error.getCode());
        }

        @Test
        @DisplayName("Same patient + same medication + different day → WarningException")
        void samePatient_sameMed_differentDay_shouldWarn() {
            when(orderRepo.findByPatientIdAndMedicationNameAndCreatedAtBetween(eq(1L), eq("Metformin"), any(), any()))
                    .thenReturn(List.of());
            CareOrder oldOrder = makeOrder(1L, existingPatient, "Metformin", LocalDateTime.now().minusDays(30));
            when(orderRepo.findByPatientIdAndMedicationName(1L, "Metformin"))
                    .thenReturn(List.of(oldOrder));

            WarningException warning = assertThrows(WarningException.class, () -> orderService.createOrder(baseRequest));
            assertEquals("needs_confirmation", warning.getCode());
        }

        @Test
        @DisplayName("Same patient + same medication + different day + confirmed → succeed")
        void samePatient_sameMed_differentDay_confirmed_shouldSucceed() {
            baseRequest.confirmWarnings = true;
            when(orderRepo.findByPatientIdAndMedicationNameAndCreatedAtBetween(eq(1L), eq("Metformin"), any(), any()))
                    .thenReturn(List.of());
            CareOrder oldOrder = makeOrder(1L, existingPatient, "Metformin", LocalDateTime.now().minusDays(30));
            when(orderRepo.findByPatientIdAndMedicationName(1L, "Metformin"))
                    .thenReturn(List.of(oldOrder));
            mockSuccessfulCreation();

            Map<String, Object> result = orderService.createOrder(baseRequest);
            assertNotNull(result.get("orderId"));
        }

        @Test
        @DisplayName("Same patient + different medication → succeed")
        void samePatient_differentMed_shouldSucceed() {
            when(orderRepo.findByPatientIdAndMedicationNameAndCreatedAtBetween(eq(1L), eq("Metformin"), any(), any()))
                    .thenReturn(List.of());
            when(orderRepo.findByPatientIdAndMedicationName(1L, "Metformin"))
                    .thenReturn(List.of());
            mockSuccessfulCreation();

            Map<String, Object> result = orderService.createOrder(baseRequest);
            assertNotNull(result.get("orderId"));
        }
    }

    // ================================================================
    // Successful Creation
    // ================================================================
    @Nested
    @DisplayName("Successful Order Creation")
    class SuccessTests {

        @Test
        @DisplayName("New patient + new provider → create all, push to Redis")
        void newPatientNewProvider_shouldCreateAndPush() {
            when(providerRepo.findByNpi("1234567890")).thenReturn(Optional.empty());
            when(patientRepo.findByMrn("123456")).thenReturn(Optional.empty());
            when(patientRepo.findByFirstNameAndLastNameAndDateOfBirth("John", "Doe", LocalDate.of(1990, 1, 15)))
                    .thenReturn(List.of());
            mockSuccessfulCreation();

            Map<String, Object> result = orderService.createOrder(baseRequest);

            assertNotNull(result.get("orderId"));
            assertNotNull(result.get("carePlanId"));
            verify(patientRepo).save(any(Patient.class));
            verify(providerRepo).save(any(Provider.class));
            verify(orderRepo).save(any(CareOrder.class));
            verify(carePlanRepo).save(any(CarePlan.class));
            verify(redisQueue).pushToQueue(anyString());
        }

        @Test
        @DisplayName("CarePlan is created with status=pending")
        void carePlan_shouldBeCreatedAsPending() {
            when(providerRepo.findByNpi("1234567890")).thenReturn(Optional.empty());
            when(patientRepo.findByMrn("123456")).thenReturn(Optional.empty());
            when(patientRepo.findByFirstNameAndLastNameAndDateOfBirth("John", "Doe", LocalDate.of(1990, 1, 15)))
                    .thenReturn(List.of());
            mockSuccessfulCreation();

            orderService.createOrder(baseRequest);
            verify(carePlanRepo).save(argThat(plan -> "pending".equals(plan.getStatus())));
        }
    }
}
