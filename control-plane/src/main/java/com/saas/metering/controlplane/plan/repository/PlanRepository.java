package com.saas.metering.controlplane.plan.repository;

import com.saas.metering.controlplane.plan.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    boolean existsByCode(String code);

    Optional<Plan> findByCode(String code);

    List<Plan> findByActiveOrderByCodeAsc(boolean active);

    List<Plan> findAllByOrderByCodeAsc();
}
