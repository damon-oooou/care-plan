package com.careplan.adapter;

import com.careplan.dto.InternalOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clinic B — 小型诊所，通过 JSON 推送订单。
 *
 * 外部格式示例：
 * {
 *   "ref_no": "CB-20260527-003",
 *   "doc": { "full_name": "Dr. Sarah Lin", "license": "1234567890" },
 *   "pt": {
 *     "fname": "John", "lname": "Doe",
 *     "record_id": "998877",
 *     "born": "03/15/1985",
 *     "records": "History of hypertension, well controlled"
 *   },
 *   "rx": {
 *     "med": "Metformin",
 *     "strength": "500mg",
 *     "sig": "BID",
 *     "condition": "Type 2 Diabetes",
 *     "other_conditions": ["Obesity"],
 *     "past_meds": ["Glipizide 5mg"]
 *   },
 *   "notes": "Patient prefers mail-order pharmacy"
 * }
 */
@Component
public class ClinicBAdapter extends BaseIntakeAdapter<String> {

    private final ObjectMapper objectMapper;

    public ClinicBAdapter(Validator validator, ObjectMapper objectMapper) {
        super(validator);
        this.objectMapper = objectMapper;
    }

    @Override
    public String sourceSystem() {
        return "clinic_b";
    }

    // ── Step 1: parse — 原始 JSON → 中间 Map ──

    @Override
    protected Map<String, Object> parse(String rawJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (JsonProcessingException e) {
            throw new AdapterParseException("clinic_b", "Invalid JSON: " + e.getMessage());
        }

        JsonNode doc = requireNode(root, "doc");
        JsonNode pt  = requireNode(root, "pt");
        JsonNode rx  = requireNode(root, "rx");

        Map<String, Object> parsed = new LinkedHashMap<>();

        // provider
        parsed.put("provider_name", textOrNull(doc, "full_name"));
        parsed.put("provider_npi",  textOrNull(doc, "license"));

        // patient
        parsed.put("patient_first",   textOrNull(pt, "fname"));
        parsed.put("patient_last",    textOrNull(pt, "lname"));
        parsed.put("patient_mrn",     textOrNull(pt, "record_id"));
        parsed.put("patient_dob",     textOrNull(pt, "born"));
        parsed.put("patient_records", textOrNull(pt, "records"));

        // medication
        parsed.put("drug_name",  textOrNull(rx, "med"));
        parsed.put("dosage",     textOrNull(rx, "strength"));
        parsed.put("frequency",  textOrNull(rx, "sig"));
        parsed.put("diagnosis",  textOrNull(rx, "condition"));
        parsed.put("additional_diagnoses", toStringList(rx, "other_conditions"));
        parsed.put("medication_history",   toStringList(rx, "past_meds"));

        // metadata
        parsed.put("external_order_id", textOrNull(root, "ref_no"));

        // 保留原始 JSON，排查时直接看
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

        // Clinic B 用缩写 "BID"/"TID"，转成完整描述
        String frequency = expandSig((String) parsed.get("frequency"));

        var medication = new InternalOrder.MedicationInfo(
                (String) parsed.get("drug_name"),
                (String) parsed.get("dosage"),
                frequency,
                (String) parsed.get("diagnosis"),
                (List<String>) parsed.get("additional_diagnoses"),
                (List<String>) parsed.get("medication_history")
        );

        return new InternalOrder(
                patient,
                provider,
                medication,
                "clinic_b",
                (String) parsed.get("external_order_id"),
                false,
                (String) parsed.get("_raw")
        );
    }

    // ── 内部工具方法 ──

    private static final DateTimeFormatter CLINIC_B_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private LocalDate parseDob(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw, CLINIC_B_DATE);
        } catch (DateTimeParseException e) {
            throw new AdapterParseException("clinic_b",
                    "Bad date format: " + raw + " (expected MM/dd/yyyy)");
        }
    }

    /** 处方缩写 → 可读文本 */
    private String expandSig(String sig) {
        if (sig == null) return null;
        return switch (sig.toUpperCase()) {
            case "QD"  -> "once daily";
            case "BID" -> "twice daily";
            case "TID" -> "three times daily";
            case "QID" -> "four times daily";
            case "PRN" -> "as needed";
            case "QHS" -> "at bedtime";
            default    -> sig;
        };
    }

    private JsonNode requireNode(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            throw new AdapterParseException("clinic_b",
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
