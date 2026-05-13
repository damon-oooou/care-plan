package com.careplan.repository;

import com.careplan.entity.CareOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CareOrderRepository extends JpaRepository<CareOrder, Long> {
    List<CareOrder> findByPatientId(Long patientId);
    List<CareOrder> findAllByOrderByCreatedAtDesc();
}
