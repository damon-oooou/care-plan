package com.careplan.adapter;

import com.careplan.dto.InternalOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hospital A — 大型医院，通过 JSON 推送订单。
 *
 * 外部格式示例：
 * {
 *   "order_number": "HA-2026-00451",
 *   "ordering_physician": {
 *     "first_name": "Michael",
 *     "last_name": "Chen",
 *     "national_provider_id": "3333344444"
 *   },
 *   "patient_demographics": {
 *     "given_name": "Emily",
 *     "family_name": "Wang",
 *     "medical_record_number": "445566",
 *     "date_of_birth": "1992-11-03",
 *     "clinical_summary": "Asthma since childhood, uses rescue inhaler 2x/week"
 *   },
 *   "prescription": {
 *     "drug": "Montelukast",
 *     "dose": "10mg",
 *     "route": "oral",
 *     "frequency": "once daily at bedtime",
 *     "primary_dx": "Asthma",
 *     "secondary_dx": ["Allergic Rhinitis", "GERD"],
 *     "prior_medications": ["Albuterol 90mcg", "Fluticasone 250mcg"]
 *   }
 * }
 */
@Component
public class HospitalAAdapter extends BaseIntakeAdapter<String> {

    private final ObjectMapper objectMapper;

    public HospitalAAdapter(Validator validator, ObjectMapper objectMapper) {
        super(validator);
        this.objectMapper = objectMapper;
    }

    @Override
    public String sourceSystem() {
        return "hospital_a";
    }

    // ── Step 1: parse — 原始 JSON → 中间 Map ──

    @Override
    protected Map<String, Object> parse(String rawJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (JsonProcessingException e) {
            throw new AdapterParseException("hospital_a", "Invalid JSON: " + e.getMessage());
        }

        JsonNode physician = requireNode(root, "ordering_physician");
        JsonNode patient   = requireNode(root, "patient_demographics");
        JsonNode rx        = requireNode(root, "prescription");

        Map<String, Object> parsed = new LinkedHashMap<>();

        // provider — Hospital A 名字是分开的，拼起来
        String firstName = textOrNull(physician, "first_name");
        String lastName  = textOrNull(physician, "last_name");
        parsed.put("provider_name", "Dr. " + firstName + " " + lastName);
        parsed.put("provider_npi",  textOrNull(physician, "national_provider_id"));

        // patient
        parsed.put("patient_first",   textOrNull(patient, "given_name"));
        parsed.put("patient_last",    textOrNull(patient, "family_name"));
        parsed.put("patient_mrn",     textOrNull(patient, "medical_record_number"));
        parsed.put("patient_dob",     textOrNull(patient, "date_of_birth"));
        parsed.put("patient_records", textOrNull(patient, "clinical_summary"));

        // medication — Hospital A 频率已经是完整文本，不用转换
        parsed.put("drug_name",             textOrNull(rx, "drug"));
        parsed.put("dosage",                textOrNull(rx, "dose"));
        parsed.put("frequency",             textOrNull(rx, "frequency"));
        parsed.put("diagnosis",             textOrNull(rx, "primary_dx"));
        parsed.put("additional_diagnoses",  toStringList(rx, "secondary_dx"));
        parsed.put("medication_history",    toStringList(rx, "prior_medications"));

        // metadata
        parsed.put("external_order_id", textOrNull(root, "order_number"));

        // 保留原始 JSON
        parsed.put("_raw", rawJson);

        return parsed;
    }

    // ── Step 2: transform — 中间 Map → InternalOrder ──

    @Override
    @SuppressWarnings("unchecked")
    protected InternalOrder transform(Map<String, Object> parsed) {

        var patient = new InternalOrder.PatientInfo(
                (String) parsed.get("patient_first"),
                (String) parsed.get("patient_last"),
                (String) parsed.get("patient_mrn"),
                parseDob((String) parsed.get("patient_dob")),
                (String) parsed.get("patient_records")
        );

        var provider = new InternalOrder.ProviderInfo(
                (String) parsed.get("provider_name"),
                (String) parsed.get("provider_npi")
        );

        // Hospital A 频率已经是完整文本，直接用
        var medication = new InternalOrder.MedicationInfo(
                (String) parsed.get("drug_name"),
                (String) parsed.get("dosage"),
                (String) parsed.get("frequency"),
                (String) parsed.get("diagnosis"),
                (List<String>) parsed.get("additional_diagnoses"),
                (List<String>) parsed.get("medication_history")
        );

        return new InternalOrder(
                patient,
                provider,
                medication,
                "hospital_a",
                (String) parsed.get("external_order_id"),
                false,
                (String) parsed.get("_raw")
        );
    }

    // ── 内部工具方法 ──

    /** Hospital A 用标准格式 yyyy-MM-dd，直接 parse */
    private LocalDate parseDob(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new AdapterParseException("hospital_a",
                    "Bad date format: " + raw + " (expected yyyy-MM-dd)");
        }
    }

    private JsonNode requireNode(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            throw new AdapterParseException("hospital_a",
                    "Missing required section: " + field);
        }
        return node;
    }

    private String textOrNull(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        return (node != null && !node.isNull()) ? node.asText() : null;
    }

    private List<String> toStringList(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isArray()) return List.of();
        List<String> list = new ArrayList<>();
        node.forEach(item -> list.add(item.asText()));
        return list;
    }
}
