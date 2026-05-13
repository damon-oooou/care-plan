package com.careplan.repository;

import com.careplan.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
    Optional<Provider> findByNpi(String npi);
}
