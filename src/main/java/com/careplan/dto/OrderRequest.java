package com.careplan.dto;

import java.util.List;

public class OrderRequest {
    public String patientFirstName;
    public String patientLastName;
    public String mrn;
    public String dateOfBirth;            // "1965-03-15" 格式
    public String referringProvider;
    public String referringProviderNpi;
    public String primaryDiagnosis;
    public List<String> additionalDiagnoses;
    public String medicationName;
    public List<String> medicationHistory;
    public String patientRecords;
    public boolean confirmWarnings;       // true = 用户确认跳过 warnings
}
