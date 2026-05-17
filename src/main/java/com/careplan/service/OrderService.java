package com.careplan.service;

import com.careplan.dto.OrderRequest;
import com.careplan.entity.*;
import com.careplan.exception.*;
import com.careplan.repository.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OrderService {

    @Autowired private PatientRepository patientRepo;
    @Autowired private ProviderRepository providerRepo;
    @Autowired private CareOrderRepository orderRepo;
    @Autowired private CarePlanRepository carePlanRepo;
    @Autowired private StringRedisTemplate redisTemplate;

    /**
     * 创建订单（含重复检测）
     * 不正常的情况全部 throw，GlobalExceptionHandler 统一处理
     */
    public Map<String, Object> createOrder(OrderRequest request) {

        List<String> warnings = new ArrayList<>();

        // ============================================================
        // 1. Provider 重复检测
        // ============================================================
        Optional<Provider> existingProvider = providerRepo.findByNpi(request.referringProviderNpi);

        if (existingProvider.isPresent()
                && !existingProvider.get().getName().equalsIgnoreCase(request.referringProvider)) {
            throw new BlockError("npi_conflict",
                    "NPI " + request.referringProviderNpi + " 已属于 "
                    + existingProvider.get().getName() + "，不能用于 " + request.referringProvider,
                    "NPI 是国家执照号，全国唯一，一个 NPI 只能对应一个 Provider");
        }

        // ============================================================
        // 2. Patient 重复检测
        // ============================================================
        LocalDate dob = null;
        if (request.dateOfBirth != null && !request.dateOfBirth.isBlank()) {
            dob = LocalDate.parse(request.dateOfBirth);
        }

        Optional<Patient> existingPatient = patientRepo.findByMrn(request.mrn);

        if (existingPatient.isPresent()) {
            Patient patient = existingPatient.get();
            boolean nameMatch = patient.getFirstName().equalsIgnoreCase(request.patientFirstName)
                    && patient.getLastName().equalsIgnoreCase(request.patientLastName);
            boolean dobMatch = (patient.getDateOfBirth() == null && dob == null)
                    || (patient.getDateOfBirth() != null && patient.getDateOfBirth().equals(dob));

            if (!nameMatch || !dobMatch) {
                warnings.add("MRN " + request.mrn + " 已存在，属于 "
                        + patient.getFirstName() + " " + patient.getLastName()
                        + " (DOB: " + patient.getDateOfBirth() + ")"
                        + "，但你输入的是 " + request.patientFirstName + " " + request.patientLastName
                        + " (DOB: " + dob + ")。可能是录入错误。");
            }
        } else if (dob != null) {
            List<Patient> sameName = patientRepo.findByFirstNameAndLastNameAndDateOfBirth(
                    request.patientFirstName, request.patientLastName, dob);
            if (!sameName.isEmpty()) {
                warnings.add("患者 " + request.patientFirstName + " " + request.patientLastName
                        + " (DOB: " + dob + ") 已存在，MRN 为 " + sameName.get(0).getMrn()
                        + "，但你输入的 MRN 是 " + request.mrn + "。可能是同一人。");
            }
        }

        // ============================================================
        // 3. Order 重复检测
        // ============================================================
        if (existingPatient.isPresent()) {
            Long patientId = existingPatient.get().getId();
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            LocalDateTime todayEnd = todayStart.plusDays(1);

            // 同患者 + 同药物 + 同一天 → 阻止
            List<CareOrder> sameDayOrders = orderRepo.findByPatientIdAndMedicationNameAndCreatedAtBetween(
                    patientId, request.medicationName, todayStart, todayEnd);
            if (!sameDayOrders.isEmpty()) {
                throw new BlockError("duplicate_order",
                        "患者今天已经有一个 " + request.medicationName + " 的订单",
                        "同一患者 + 同一药物 + 同一天，确定是重复提交");
            }

            // 同患者 + 同药物 + 不同天 → 警告
            List<CareOrder> previousOrders = orderRepo.findByPatientIdAndMedicationName(
                    patientId, request.medicationName);
            if (!previousOrders.isEmpty()) {
                CareOrder lastOrder = previousOrders.get(previousOrders.size() - 1);
                warnings.add("患者之前已有 " + request.medicationName + " 的订单"
                        + " (创建于 " + lastOrder.getCreatedAt().toLocalDate() + ")。可能是续方。");
            }
        }

        // ============================================================
        // 4. 有警告且用户没确认 → throw WarningException
        // ============================================================
        if (!warnings.isEmpty() && !request.confirmWarnings) {
            throw new WarningException("needs_confirmation",
                    "检测到以下问题，请确认后继续", warnings);
        }

        // ============================================================
        // 5. 通过所有检测，正常创建
        // ============================================================
        final LocalDate finalDob = dob;

        Patient patient = existingPatient.orElseGet(() -> {
            Patient p = new Patient();
            p.setFirstName(request.patientFirstName);
            p.setLastName(request.patientLastName);
            p.setMrn(request.mrn);
            return p;
        });
        if (finalDob != null) {
            patient.setDateOfBirth(finalDob);
        }
        patient = patientRepo.save(patient);

        Provider provider = existingProvider.orElseGet(() -> {
            Provider prov = new Provider();
            prov.setName(request.referringProvider);
            prov.setNpi(request.referringProviderNpi);
            return prov;
        });
        provider = providerRepo.save(provider);

        CareOrder order = new CareOrder();
        order.setPatient(patient);
        order.setProvider(provider);
        order.setPrimaryDiagnosis(request.primaryDiagnosis);
        order.setMedicationName(request.medicationName);
        order.setPatientRecords(request.patientRecords);

        if (request.additionalDiagnoses != null && !request.additionalDiagnoses.isEmpty()) {
            order.setAdditionalDiagnoses(String.join(",", request.additionalDiagnoses));
        }
        if (request.medicationHistory != null && !request.medicationHistory.isEmpty()) {
            order.setMedicationHistory(String.join(",", request.medicationHistory));
        }

        order = orderRepo.save(order);

        CarePlan carePlan = new CarePlan();
        carePlan.setOrder(order);
        carePlan.setStatus("pending");
        carePlanRepo.save(carePlan);

        redisTemplate.opsForList().rightPush("careplan:queue", carePlan.getId().toString());

        return Map.of(
                "orderId", order.getId(),
                "carePlanId", carePlan.getId()
        );
    }

    public List<CareOrder> getAllOrders() {
        return orderRepo.findAllByOrderByCreatedAtDesc();
    }

    public CareOrder getOrderById(Long id) {
        return orderRepo.findById(id).orElse(null);
    }

    public List<Patient> getAllPatients() {
        return patientRepo.findAll();
    }

    public List<Provider> getAllProviders() {
        return providerRepo.findAll();
    }

    public CarePlan getCarePlanById(Long id) {
        return carePlanRepo.findById(id).orElse(null);
    }
}
