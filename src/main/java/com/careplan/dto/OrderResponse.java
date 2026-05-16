package com.careplan.dto;

import com.careplan.entity.CareOrder;

public class OrderResponse {
    public Long id;
    public String patientName;
    public String mrn;
    public String providerName;
    public String providerNpi;
    public String primaryDiagnosis;
    public String medicationName;
    public String carePlan;
    public String status;
    public String createdAt;

    public OrderResponse(CareOrder order) {
        this.id = order.getId();
        this.patientName = order.getPatient().getFirstName() + " " + order.getPatient().getLastName();
        this.mrn = order.getPatient().getMrn();
        this.providerName = order.getProvider().getName();
        this.providerNpi = order.getProvider().getNpi();
        this.primaryDiagnosis = order.getPrimaryDiagnosis();
        this.medicationName = order.getMedicationName();
        this.carePlan = order.getCarePlan() != null ? order.getCarePlan().getContent() : null;
        this.status = order.getCarePlan() != null ? order.getCarePlan().getStatus() : "no_plan";
        this.createdAt = order.getCreatedAt().toString();
    }
}
