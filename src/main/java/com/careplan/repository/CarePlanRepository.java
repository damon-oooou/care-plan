package com.careplan.repository;

import com.careplan.entity.CarePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CarePlanRepository extends JpaRepository<CarePlan, Long> {
    Optional<CarePlan> findByOrderId(Long orderId);
}
