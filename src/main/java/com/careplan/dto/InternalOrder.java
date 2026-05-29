package com.careplan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

/**
 * 内部标准订单格式。
 * 所有外部数据源经过 Adapter 转换后都变成这个格式，
 * 下游业务逻辑（重复检测、入库、Care Plan 生成）只认这一个类。
 */
public record InternalOrder(
        @NotNull @Valid PatientInfo patient,
        @NotNull @Valid ProviderInfo provider,
        @NotNull @Valid MedicationInfo medication,
        String sourceSystem,
        String externalOrderId,
        boolean confirmWarnings,
        String rawData
) {

    public record PatientInfo(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String mrn,
            @NotNull LocalDate dateOfBirth,
            String patientRecords          // 病历摘要，可选
    ) {}

    public record ProviderInfo(
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "\\d{10}") String npi
    ) {}

    public record MedicationInfo(
            @NotBlank String drugName,
            String dosage,                 // 可选，有些数据源不提供
            String frequency,              // 可选
            @NotBlank String diagnosis,
            List<String> additionalDiagnoses,  // 可选
            List<String> medicationHistory     // 可选
    ) {}
}
