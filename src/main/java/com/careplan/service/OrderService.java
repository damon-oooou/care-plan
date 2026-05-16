package com.careplan.service;

import com.careplan.dto.OrderRequest;
import com.careplan.entity.*;
import com.careplan.repository.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    @Autowired private PatientRepository patientRepo;
    @Autowired private ProviderRepository providerRepo;
    @Autowired private CareOrderRepository orderRepo;
    @Autowired private CarePlanRepository carePlanRepo;
    @Autowired private StringRedisTemplate redisTemplate;

    /**
     * 创建订单 + pending 状态的 care plan + 推入 Redis 队列
     * 返回 orderId 和 carePlanId
     */
    public Map<String, Long> createOrder(OrderRequest request) {
        // 1. 查找或创建 Patient
        Patient patient = patientRepo.findByMrn(request.mrn)
                .orElseGet(() -> {
                    Patient p = new Patient();
                    p.setFirstName(request.patientFirstName);
                    p.setLastName(request.patientLastName);
                    p.setMrn(request.mrn);
                    return patientRepo.save(p);
                });

        // 2. 查找或创建 Provider
        Provider provider = providerRepo.findByNpi(request.referringProviderNpi)
                .orElseGet(() -> {
                    Provider prov = new Provider();
                    prov.setName(request.referringProvider);
                    prov.setNpi(request.referringProviderNpi);
                    return providerRepo.save(prov);
                });

        // 3. 创建 Order
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

        // 4. 创建 Care Plan（status = pending）
        CarePlan carePlan = new CarePlan();
        carePlan.setOrder(order);
        carePlan.setStatus("pending");
        carePlanRepo.save(carePlan);

        // 5. 推入 Redis 队列
        redisTemplate.opsForList().rightPush("careplan:queue", carePlan.getId().toString());

        return Map.of("orderId", order.getId(), "carePlanId", carePlan.getId());
    }

    /**
     * 获取所有订单（按时间倒序）
     */
    public List<CareOrder> getAllOrders() {
        return orderRepo.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 按 ID 获取单个订单
     */
    public CareOrder getOrderById(Long id) {
        return orderRepo.findById(id).orElse(null);
    }

    /**
     * 获取所有患者
     */
    public List<Patient> getAllPatients() {
        return patientRepo.findAll();
    }

    /**
     * 获取所有 Provider
     */
    public List<Provider> getAllProviders() {
        return providerRepo.findAll();
    }

    /**
     * 获取 Care Plan 状态（轮询用）
     */
    public CarePlan getCarePlanById(Long id) {
        return carePlanRepo.findById(id).orElse(null);
    }
}
