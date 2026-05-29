package com.careplan.service;

import com.careplan.dto.InternalOrder;
import com.careplan.dto.OrderRequest;
import com.careplan.entity.*;
import com.careplan.exception.*;
import com.careplan.repository.*;

import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired private RedisQueueService redisQueue;

    /**
     * 创建订单（含重复检测）— 前端表单用
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
                    "NPI " + request.referringProviderNpi + " already belongs to "
                    + existingProvider.get().getName() + ", cannot be used for " + request.referringProvider,
                    "NPI is a national license number, one NPI can only belong to one provider");
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
                warnings.add("MRN " + request.mrn + " already exists, belongs to "
                        + patient.getFirstName() + " " + patient.getLastName()
                        + " (DOB: " + patient.getDateOfBirth() + ")"
                        + ", but you entered  " + request.patientFirstName + " " + request.patientLastName
                        + " (DOB: " + dob + "). Possible data entry error.");
            }
        } else if (dob != null) {
            List<Patient> sameName = patientRepo.findByFirstNameAndLastNameAndDateOfBirth(
                    request.patientFirstName, request.patientLastName, dob);
            if (!sameName.isEmpty()) {
                warnings.add("Patient " + request.patientFirstName + " " + request.patientLastName
                        + " (DOB: " + dob + ") already exists, MRN is " + sameName.get(0).getMrn()
                        + ", but you entered MRN " + request.mrn + ". This might be the same patient.");
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
                        "Patient has already placed an order for " + request.medicationName + " today",
                        "Same patient + same medication + same day, confirmed as duplicate submission");
            }

            // 同患者 + 同药物 + 不同天 → 警告
            List<CareOrder> previousOrders = orderRepo.findByPatientIdAndMedicationName(
                    patientId, request.medicationName);
            if (!previousOrders.isEmpty()) {
                CareOrder lastOrder = previousOrders.get(previousOrders.size() - 1);
                warnings.add("Patient already has a previous " + request.medicationName + " order"
                        + " (created on " + lastOrder.getCreatedAt().toLocalDate() + "). This might be a refill.");
            }
        }

        // ============================================================
        // 4. 有警告且用户没确认 → throw WarningException
        // ============================================================
        if (!warnings.isEmpty() && !request.confirmWarnings) {
            throw new WarningException("needs_confirmation",
                    "The following issues were detected, please confirm to proceed", warnings);
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

        redisQueue.pushToQueue(carePlan.getId().toString());

        return Map.of(
                "orderId", order.getId(),
                "carePlanId", carePlan.getId()
        );
    }

    /**
     * 从 InternalOrder 创建订单 — 外部数据源用
     * 重复检测逻辑和 createOrder 完全一致
     */
    public Map<String, Object> createFromInternal(InternalOrder req) {

        List<String> warnings = new ArrayList<>();

        // ============================================================
        // 1. Provider 重复检测
        // ============================================================
        Optional<Provider> existingProvider = providerRepo.findByNpi(req.provider().npi());

        if (existingProvider.isPresent()
                && !existingProvider.get().getName().equalsIgnoreCase(req.provider().name())) {
            throw new BlockError("npi_conflict",
                    "NPI " + req.provider().npi() + " already belongs to "
                    + existingProvider.get().getName(),
                    "NPI is a national license number, one NPI can only belong to one provider");
        }

        // ============================================================
        // 2. Patient 重复检测
        // ============================================================
        LocalDate dob = req.patient().dateOfBirth();
        Optional<Patient> existingPatient = patientRepo.findByMrn(req.patient().mrn());

        if (existingPatient.isPresent()) {
            Patient patient = existingPatient.get();
            boolean nameMatch = patient.getFirstName().equalsIgnoreCase(req.patient().firstName())
                    && patient.getLastName().equalsIgnoreCase(req.patient().lastName());
            boolean dobMatch = (patient.getDateOfBirth() == null && dob == null)
                    || (patient.getDateOfBirth() != null && patient.getDateOfBirth().equals(dob));

            if (!nameMatch || !dobMatch) {
                warnings.add("MRN " + req.patient().mrn() + " already exists, belongs to "
                        + patient.getFirstName() + " " + patient.getLastName()
                        + " (DOB: " + patient.getDateOfBirth() + ")"
                        + ", but incoming data has " + req.patient().firstName() + " " + req.patient().lastName()
                        + " (DOB: " + dob + ").");
            }
        } else if (dob != null) {
            List<Patient> sameName = patientRepo.findByFirstNameAndLastNameAndDateOfBirth(
                    req.patient().firstName(), req.patient().lastName(), dob);
            if (!sameName.isEmpty()) {
                warnings.add("Patient " + req.patient().firstName() + " " + req.patient().lastName()
                        + " (DOB: " + dob + ") already exists with MRN " + sameName.get(0).getMrn()
                        + ", but incoming MRN is " + req.patient().mrn());
            }
        }

        // ============================================================
        // 3. Order 重复检测
        // ============================================================
        if (existingPatient.isPresent()) {
            Long patientId = existingPatient.get().getId();
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            LocalDateTime todayEnd = todayStart.plusDays(1);

            List<CareOrder> sameDayOrders = orderRepo.findByPatientIdAndMedicationNameAndCreatedAtBetween(
                    patientId, req.medication().drugName(), todayStart, todayEnd);
            if (!sameDayOrders.isEmpty()) {
                throw new BlockError("duplicate_order",
                        "Patient has already placed an order for " + req.medication().drugName() + " today",
                        "Same patient + same medication + same day");
            }

            List<CareOrder> previousOrders = orderRepo.findByPatientIdAndMedicationName(
                    patientId, req.medication().drugName());
            if (!previousOrders.isEmpty()) {
                CareOrder lastOrder = previousOrders.get(previousOrders.size() - 1);
                warnings.add("Patient already has a previous " + req.medication().drugName() + " order"
                        + " (created on " + lastOrder.getCreatedAt().toLocalDate() + ").");
            }
        }

        // ============================================================
        // 4. 有警告且未确认
        // ============================================================
        if (!warnings.isEmpty() && !req.confirmWarnings()) {
            throw new WarningException("needs_confirmation",
                    "Issues detected, please confirm to proceed", warnings);
        }

        // ============================================================
        // 5. 创建记录
        // ============================================================
        Patient patient = existingPatient.orElseGet(() -> {
            Patient p = new Patient();
            p.setFirstName(req.patient().firstName());
            p.setLastName(req.patient().lastName());
            p.setMrn(req.patient().mrn());
            return p;
        });
        if (dob != null) {
            patient.setDateOfBirth(dob);
        }
        patient = patientRepo.save(patient);

        Provider provider = existingProvider.orElseGet(() -> {
            Provider prov = new Provider();
            prov.setName(req.provider().name());
            prov.setNpi(req.provider().npi());
            return prov;
        });
        provider = providerRepo.save(provider);

        CareOrder order = new CareOrder();
        order.setPatient(patient);
        order.setProvider(provider);
        order.setPrimaryDiagnosis(req.medication().diagnosis());
        order.setMedicationName(req.medication().drugName());
        order.setPatientRecords(req.patient().patientRecords());
        order.setSourceSystem(req.sourceSystem());
        order.setExternalOrderId(req.externalOrderId());
        order.setRawData(req.rawData());

        if (req.medication().additionalDiagnoses() != null && !req.medication().additionalDiagnoses().isEmpty()) {
            order.setAdditionalDiagnoses(String.join(",", req.medication().additionalDiagnoses()));
        }
        if (req.medication().medicationHistory() != null && !req.medication().medicationHistory().isEmpty()) {
            order.setMedicationHistory(String.join(",", req.medication().medicationHistory()));
        }

        order = orderRepo.save(order);

        CarePlan carePlan = new CarePlan();
        carePlan.setOrder(order);
        carePlan.setStatus("pending");
        carePlanRepo.save(carePlan);

        redisQueue.pushToQueue(carePlan.getId().toString());

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
