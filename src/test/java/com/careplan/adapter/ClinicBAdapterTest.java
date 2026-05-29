package com.careplan.adapter;

import com.careplan.dto.InternalOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ClinicBAdapterTest {

    private ClinicBAdapter adapter;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ObjectMapper objectMapper = new ObjectMapper();
        adapter = new ClinicBAdapter(validator, objectMapper);
    }

    // ── sourceSystem ──

    @Test
    void sourceSystem_returnsClinicB() {
        assertEquals("clinic_b", adapter.sourceSystem());
    }

    // ── 正常流程 ──

    @Test
    void process_normalJson_returnsCorrectInternalOrder() {
        String json = """
                {
                  "ref_no": "CB-001",
                  "doc": { "full_name": "Dr. Sarah Lin", "license": "1234567890" },
                  "pt": {
                    "fname": "John", "lname": "Doe",
                    "record_id": "998877",
                    "born": "03/15/1985",
                    "records": "History of hypertension"
                  },
                  "rx": {
                    "med": "Metformin",
                    "strength": "500mg",
                    "sig": "BID",
                    "condition": "Type 2 Diabetes",
                    "other_conditions": ["Obesity"],
                    "past_meds": ["Glipizide 5mg"]
                  }
                }
                """;

        InternalOrder result = adapter.process(json);

        // patient
        assertEquals("John", result.patient().firstName());
        assertEquals("Doe", result.patient().lastName());
        assertEquals("998877", result.patient().mrn());
        assertEquals(LocalDate.of(1985, 3, 15), result.patient().dateOfBirth());
        assertEquals("History of hypertension", result.patient().patientRecords());

        // provider
        assertEquals("Dr. Sarah Lin", result.provider().name());
        assertEquals("1234567890", result.provider().npi());

        // medication
        assertEquals("Metformin", result.medication().drugName());
        assertEquals("500mg", result.medication().dosage());
        assertEquals("Type 2 Diabetes", result.medication().diagnosis());
        assertEquals(1, result.medication().additionalDiagnoses().size());
        assertEquals("Obesity", result.medication().additionalDiagnoses().get(0));
        assertEquals(1, result.medication().medicationHistory().size());
        assertEquals("Glipizide 5mg", result.medication().medicationHistory().get(0));

        // metadata
        assertEquals("clinic_b", result.sourceSystem());
        assertEquals("CB-001", result.externalOrderId());
        assertFalse(result.confirmWarnings());
        assertNotNull(result.rawData());
    }

    // ── SIG 缩写展开 ──

    @Test
    void process_sigBID_expandsToTwiceDaily() {
        String json = buildJson("BID");
        InternalOrder result = adapter.process(json);
        assertEquals("twice daily", result.medication().frequency());
    }

    @Test
    void process_sigQD_expandsToOnceDaily() {
        String json = buildJson("QD");
        InternalOrder result = adapter.process(json);
        assertEquals("once daily", result.medication().frequency());
    }

    @Test
    void process_sigTID_expandsToThreeTimesDaily() {
        String json = buildJson("TID");
        InternalOrder result = adapter.process(json);
        assertEquals("three times daily", result.medication().frequency());
    }

    @Test
    void process_sigQID_expandsToFourTimesDaily() {
        String json = buildJson("QID");
        InternalOrder result = adapter.process(json);
        assertEquals("four times daily", result.medication().frequency());
    }

    @Test
    void process_sigPRN_expandsToAsNeeded() {
        String json = buildJson("PRN");
        InternalOrder result = adapter.process(json);
        assertEquals("as needed", result.medication().frequency());
    }

    @Test
    void process_unknownSig_keepsOriginal() {
        String json = buildJson("STAT");
        InternalOrder result = adapter.process(json);
        assertEquals("STAT", result.medication().frequency());
    }

    // ── 日期格式 ──

    @Test
    void process_dateMMddyyyy_parsesCorrectly() {
        String json = buildJsonWithDob("12/25/2000");
        InternalOrder result = adapter.process(json);
        assertEquals(LocalDate.of(2000, 12, 25), result.patient().dateOfBirth());
    }

    @Test
    void process_badDateFormat_throwsParseException() {
        String json = buildJsonWithDob("1985-03-15");
        AdapterParseException ex = assertThrows(AdapterParseException.class,
                () -> adapter.process(json));
        assertTrue(ex.getMessage().contains("Bad date format"));
    }

    // ── JSON 格式错误 ──

    @Test
    void process_invalidJson_throwsParseException() {
        assertThrows(AdapterParseException.class,
                () -> adapter.process("not json at all"));
    }

    @Test
    void process_missingDocSection_throwsParseException() {
        String json = """
                {
                  "pt": { "fname": "John", "lname": "Doe", "record_id": "998877", "born": "01/01/1990" },
                  "rx": { "med": "Test", "strength": "10mg", "sig": "QD", "condition": "Test" }
                }
                """;
        AdapterParseException ex = assertThrows(AdapterParseException.class,
                () -> adapter.process(json));
        assertTrue(ex.getMessage().contains("Missing required section: doc"));
    }

    @Test
    void process_missingPtSection_throwsParseException() {
        String json = """
                {
                  "doc": { "full_name": "Dr. Test", "license": "1234567890" },
                  "rx": { "med": "Test", "strength": "10mg", "sig": "QD", "condition": "Test" }
                }
                """;
        AdapterParseException ex = assertThrows(AdapterParseException.class,
                () -> adapter.process(json));
        assertTrue(ex.getMessage().contains("Missing required section: pt"));
    }

    @Test
    void process_missingRxSection_throwsParseException() {
        String json = """
                {
                  "doc": { "full_name": "Dr. Test", "license": "1234567890" },
                  "pt": { "fname": "John", "lname": "Doe", "record_id": "998877", "born": "01/01/1990" }
                }
                """;
        AdapterParseException ex = assertThrows(AdapterParseException.class,
                () -> adapter.process(json));
        assertTrue(ex.getMessage().contains("Missing required section: rx"));
    }

    // ── Validation 失败 ──

    @Test
    void process_missingRequiredField_throwsValidationException() {
        // drugName 为空 → @NotBlank 校验失败
        String json = """
                {
                  "doc": { "full_name": "Dr. Test", "license": "1234567890" },
                  "pt": { "fname": "John", "lname": "Doe", "record_id": "998877", "born": "01/01/1990" },
                  "rx": { "strength": "10mg", "sig": "QD", "condition": "Test" }
                }
                """;
        assertThrows(AdapterValidationException.class,
                () -> adapter.process(json));
    }

    @Test
    void process_invalidNpiFormat_throwsValidationException() {
        // NPI 不是 10 位数字 → @Pattern 校验失败
        String json = """
                {
                  "doc": { "full_name": "Dr. Test", "license": "123" },
                  "pt": { "fname": "John", "lname": "Doe", "record_id": "998877", "born": "01/01/1990" },
                  "rx": { "med": "Test", "strength": "10mg", "sig": "QD", "condition": "Test" }
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
                  "doc": { "full_name": "Dr. Test", "license": "1234567890" },
                  "pt": { "fname": "John", "lname": "Doe", "record_id": "998877", "born": "01/01/1990" },
                  "rx": { "med": "Metformin", "strength": "500mg", "sig": "QD", "condition": "Diabetes" }
                }
                """;
        InternalOrder result = adapter.process(json);
        assertNull(result.patient().patientRecords());
        assertTrue(result.medication().additionalDiagnoses().isEmpty());
        assertTrue(result.medication().medicationHistory().isEmpty());
        assertNull(result.externalOrderId());
    }

    // ── 工具方法：构造测试 JSON ──

    private String buildJson(String sig) {
        return """
                {
                  "doc": { "full_name": "Dr. Test", "license": "1234567890" },
                  "pt": { "fname": "John", "lname": "Doe", "record_id": "998877", "born": "01/01/1990" },
                  "rx": { "med": "Metformin", "strength": "500mg", "sig": "%s", "condition": "Diabetes" }
                }
                """.formatted(sig);
    }

    private String buildJsonWithDob(String dob) {
        return """
                {
                  "doc": { "full_name": "Dr. Test", "license": "1234567890" },
                  "pt": { "fname": "John", "lname": "Doe", "record_id": "998877", "born": "%s" },
                  "rx": { "med": "Metformin", "strength": "500mg", "sig": "QD", "condition": "Diabetes" }
                }
                """.formatted(dob);
    }
}
