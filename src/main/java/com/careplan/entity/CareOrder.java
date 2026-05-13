package com.careplan.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "care_order")
public class CareOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "primary_diagnosis", nullable = false, length = 20)
    private String primaryDiagnosis;

    @Column(name = "additional_diagnoses")
    private String additionalDiagnoses;  // 逗号分隔

    @Column(name = "medication_name", nullable = false)
    private String medicationName;

    @Column(name = "medication_history")
    private String medicationHistory;  // 逗号分隔

    @Column(name = "patient_records", columnDefinition = "TEXT")
    private String patientRecords;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // 一对一关联 CarePlan（可以为空，生成后才有）
    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private CarePlan carePlan;

    // ---- Getters & Setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }

    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }

    public String getPrimaryDiagnosis() { return primaryDiagnosis; }
    public void setPrimaryDiagnosis(String primaryDiagnosis) { this.primaryDiagnosis = primaryDiagnosis; }

    public String getAdditionalDiagnoses() { return additionalDiagnoses; }
    public void setAdditionalDiagnoses(String additionalDiagnoses) { this.additionalDiagnoses = additionalDiagnoses; }

    public String getMedicationName() { return medicationName; }
    public void setMedicationName(String medicationName) { this.medicationName = medicationName; }

    public String getMedicationHistory() { return medicationHistory; }
    public void setMedicationHistory(String medicationHistory) { this.medicationHistory = medicationHistory; }

    public String getPatientRecords() { return patientRecords; }
    public void setPatientRecords(String patientRecords) { this.patientRecords = patientRecords; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public CarePlan getCarePlan() { return carePlan; }
    public void setCarePlan(CarePlan carePlan) { this.carePlan = carePlan; }
}
