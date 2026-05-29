package com.careplan.adapter;

import com.careplan.dto.InternalOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class HospitalAAdapterTest {

    private HospitalAAdapter adapter;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ObjectMapper objectMapper = new ObjectMapper();
        adapter = new HospitalAAdapter(validator, objectMapper);
    }

    // ── sourceSystem ──

    @Test
    void sourceSystem_returnsHospitalA() {
        assertEquals("hospital_a", adapter.sourceSystem());
    }

    // ── 正常流程 ──

    @Test
    void process_normalJson_returnsCorrectInternalOrder() {
        String json = """
                {
                  "order_number": "HA-2026-00451",
                  "ordering_physician": {
                    "first_name": "Michael",
                    "last_name": "Chen",
                    "national_provider_id": "3333344444"
                  },
                  "patient_demographics": {
                    "given_name": "Emily",
                    "family_name": "Wang",
                    "medical_record_number": "445566",
                    "date_of_birth": "1992-11-03",
                    "clinical_summary": "Asthma since childhood"
                  },
                  "prescription": {
                    "drug": "Montelukast",
                    "dose": "10mg",
                    "route": "oral",
                    "frequency": "once daily at bedtime",
                    "primary_dx": "Asthma",
                    "secondary_dx": ["Allergic Rhinitis", "GERD"],
                    "prior_medications": ["Albuterol 90mcg", "Fluticasone 250mcg"]
                  }
                }
                """;

        InternalOrder result = adapter.process(json);

        // patient
        assertEquals("Emily", result.patient().firstName());
        assertEquals("Wang", result.patient().lastName());
        assertEquals("445566", result.patient().mrn());
        assertEquals(LocalDate.of(1992, 11, 3), result.patient().dateOfBirth());
        assertEquals("Asthma since childhood", result.patient().patientRecords());

        // provider — Hospital A 名字是分开的，拼成 "Dr. Michael Chen"
        assertEquals("Dr. Michael Chen", result.provider().name());
        assertEquals("3333344444", result.provider().npi());

        // medication — 频率是完整文本，不需要展开
        assertEquals("Montelukast", result.medication().drugName());
        assertEquals("10mg", result.medication().dosage());
        assertEquals("once daily at bedtime", result.medication().frequency());
        assertEquals("Asthma", result.medication().diagnosis());
        assertEquals(2, result.medication().additionalDiagnoses().size());
        assertEquals("Allergic Rhinitis", result.medication().additionalDiagnoses().get(0));
        assertEquals("GERD", result.medication().additionalDiagnoses().get(1));
        assertEquals(2, result.medication().medicationHistory().size());
        assertEquals("Albuterol 90mcg", result.medication().medicationHistory().get(0));
        assertEquals("Fluticasone 250mcg", result.medication().medicationHistory().get(1));

        // metadata
        assertEquals("hospital_a", result.sourceSystem());
        assertEquals("HA-2026-00451", result.externalOrderId());
        assertFalse(result.confirmWarnings());
        assertNotNull(result.rawData());
    }

    // ── 医生名字拼接 ──

    @Test
    void process_physicianName_concatenatedWithDrPrefix() {
        String json = buildJson("Alice", "Zhang");
        InternalOrder result = adapter.process(json);
        assertEquals("Dr. Alice Zhang", result.provider().name());
    }

    // ── 日期格式 ──

    @Test
    void process_dateYyyyMmDd_parsesCorrectly() {
        String json = buildJsonWithDob("2000-12-25");
        InternalOrder result = adapter.process(json);
        assertEquals(LocalDate.of(2000, 12, 25), result.patient().dateOfBirth());
    }

    @Test
    void process_badDateFormat_throwsParseException() {
        // Hospital A 用 yyyy-MM-dd，给个 MM/dd/yyyy 应该报错
        String json = buildJsonWithDob("12/25/2000");
        AdapterParseException ex = assertThrows(AdapterParseException.class,
                () -> adapter.process(json));
        assertTrue(ex.getMessage().contains("Bad date format"));
    }

    // ── JSON 格式错误 ──

    @Test
    void process_invalidJson_throwsParseException() {
        assertThrows(AdapterParseException.class,
                () -> adapter.process("this is not json"));
    }

    @Test
    void process_missingPhysicianSection_throwsParseException() {
        String json = """
                {
                  "patient_demographics": { "given_name": "Test", "family_name": "User", "medical_record_number": "123456", "date_of_birth": "1990-01-01" },
                  "prescription": { "drug": "Test", "dose": "10mg", "frequency": "daily", "primary_dx": "Test" }
                }
                """;
        AdapterParseException ex = assertThrows(AdapterParseException.class,
                () -> adapter.process(json));
        assertTrue(ex.getMessage().contains("Missing required section: ordering_physician"));
    }

    @Test
    void process_missingPatientSection_throwsParseException() {
        String json = """
                {
                  "ordering_physician": { "first_name": "Dr", "last_name": "Test", "national_provider_id": "1234567890" },
                  "prescription": { "drug": "Test", "dose": "10mg", "frequency": "daily", "primary_dx": "Test" }
                }
                """;
        AdapterParseException ex = assertThrows(AdapterParseException.class,
                () -> adapter.process(json));
        assertTrue(ex.getMessage().contains("Missing required section: patient_demographics"));
    }

    @Test
    void process_missingPrescriptionSection_throwsParseException() {
        String json = """
                {
                  "ordering_physician": { "first_name": "Dr", "last_name": "Test", "national_provider_id": "1234567890" },
                  "patient_demographics": { "given_name": "Test", "family_name": "User", "medical_record_number": "123456", "date_of_birth": "1990-01-01" }
                }
                """;
        AdapterParseException ex = assertThrows(AdapterParseException.class,
                () -> adapter.process(json));
        assertTrue(ex.getMessage().contains("Missing required section: prescription"));
    }

    // ── Validation 失败 ──

    @Test
    void process_missingDrugName_throwsValidationException() {
        String json = """
                {
                  "ordering_physician": { "first_name": "Dr", "last_name": "Test", "national_provider_id": "1234567890" },
                  "patient_demographics": { "given_name": "Test", "family_name": "User", "medical_record_number": "123456", "date_of_birth": "1990-01-01" },
                  "prescription": { "dose": "10mg", "frequency": "daily", "primary_dx": "Test" }
                }
                """;
        assertThrows(AdapterValidationException.class,
                () -> adapter.process(json));
    }

    @Test
    void process_invalidNpiFormat_throwsValidationException() {
        String json = """
                {
                  "ordering_physician": { "first_name": "Dr", "last_name": "Test", "national_provider_id": "short" },
                  "patient_demographics": { "given_name": "Test", "family_name": "User", "medical_record_number": "123456", "date_of_birth": "1990-01-01" },
                  "prescription": { "drug": "Test", "dose": "10mg", "frequency": "daily", "primary_dx": "Test" }
                }
                """;
        assertThrows(AdapterValidationException.class,
                () -> adapter.process(json));
    }

    // ── 可选字段缺失不报错 ──

    @Test
    void process_noOptionalFields_stillSucceeds() {
        String json = """
                {
                  "ordering_physician": { "first_name": "Dr", "last_name": "Test", "national_provider_id": "1234567890" },
                  "patient_demographics": { "given_name": "Test", "family_name": "User", "medical_record_number": "123456", "date_of_birth": "1990-01-01" },
                  "prescription": { "drug": "Metformin", "dose": "500mg", "frequency": "daily", "primary_dx": "Diabetes" }
                }
                """;
        InternalOrder result = adapter.process(json);
        assertNull(result.patient().patientRecords());
        assertTrue(result.medication().additionalDiagnoses().isEmpty());
        assertTrue(result.medication().medicationHistory().isEmpty());
        assertNull(result.externalOrderId());
    }

    // ── 工具方法 ──

    private String buildJson(String physicianFirst, String physicianLast) {
        return """
                {
                  "ordering_physician": { "first_name": "%s", "last_name": "%s", "national_provider_id": "1234567890" },
                  "patient_demographics": { "given_name": "Test", "family_name": "User", "medical_record_number": "123456", "date_of_birth": "1990-01-01" },
                  "prescription": { "drug": "Metformin", "dose": "500mg", "frequency": "daily", "primary_dx": "Diabetes" }
                }
                """.formatted(physicianFirst, physicianLast);
    }

    private String buildJsonWithDob(String dob) {
        return """
                {
                  "ordering_physician": { "first_name": "Dr", "last_name": "Test", "national_provider_id": "1234567890" },
                  "patient_demographics": { "given_name": "Test", "family_name": "User", "medical_record_number": "123456", "date_of_birth": "%s" },
                  "prescription": { "drug": "Metformin", "dose": "500mg", "frequency": "daily", "primary_dx": "Diabetes" }
                }
                """.formatted(dob);
    }
}
