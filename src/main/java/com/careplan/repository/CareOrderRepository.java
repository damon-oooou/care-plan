package com.careplan.repository;

import com.careplan.entity.CareOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface CareOrderRepository extends JpaRepository<CareOrder, Long> {
    List<CareOrder> findByPatientId(Long patientId);
    List<CareOrder> findAllByOrderByCreatedAtDesc();
    List<CareOrder> findByPatientIdAndMedicationName(Long patientId, String medicationName);
    List<CareOrder> findByPatientIdAndMedicationNameAndCreatedAtBetween(
            Long patientId, String medicationName, LocalDateTime start, LocalDateTime end);
}
