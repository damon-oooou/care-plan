package com.careplan.dto;

import java.util.List;

public class OrderRequest {
    public String patientFirstName;
    public String patientLastName;
    public String mrn;
    public String referringProvider;
    public String referringProviderNpi;
    public String primaryDiagnosis;
    public List<String> additionalDiagnoses;
    public String medicationName;
    public List<String> medicationHistory;
    public String patientRecords;
}
