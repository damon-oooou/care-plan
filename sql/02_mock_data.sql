-- ============================================================
-- Care Plan Generator — Mock Data (v2)
-- 加了 date_of_birth 和 care_plan.status
-- ============================================================

-- ======================== Providers ========================
INSERT INTO provider (id, name, npi) VALUES
(1, 'Dr. Sarah Chen',       '1234567890'),
(2, 'Dr. Michael Rodriguez','2345678901'),
(3, 'Dr. Emily Watson',     '3456789012'),
(4, 'Dr. James Kim',        '4567890123'),
(5, 'Dr. Lisa Thompson',    '5678901234');

-- ======================== Patients（加了 DOB）========================
INSERT INTO patient (id, first_name, last_name, mrn, date_of_birth) VALUES
(1,  'John',    'Doe',       '100001', '1965-03-15'),
(2,  'Jane',    'Smith',     '100002', '1978-07-22'),
(3,  'Robert',  'Johnson',   '100003', '1952-11-08'),
(4,  'Maria',   'Garcia',    '100004', '1985-01-30'),
(5,  'David',   'Williams',  '100005', '1970-09-12'),
(6,  'Susan',   'Brown',     '100006', '1960-04-18'),
(7,  'James',   'Wilson',    '100007', '1992-06-05'),
(8,  'Patricia','Martinez',  '100008', '1988-12-20'),
(9,  'Thomas',  'Anderson',  '100009', '1995-08-14'),
(10, 'Linda',   'Taylor',    '100010', '1955-02-28');

-- ======================== Orders ========================
INSERT INTO care_order (id, patient_id, provider_id, primary_diagnosis, additional_diagnoses, medication_name, medication_history, patient_records, created_at) VALUES
(1, 1, 1, 'E11.65', 'I10,E78.5', 'Metformin 500mg', 'Lisinopril 10mg,Atorvastatin 20mg', 'Patient has Type 2 diabetes with poor glycemic control. Recent HbA1c 8.2%. History of hypertension. BMI 31.2. Non-smoker.', '2026-05-01 09:15:00'),
(2, 2, 2, 'M05.79', 'M06.09', 'Humira 40mg', 'Methotrexate 15mg weekly,Prednisone 5mg', 'RA diagnosed 3 years ago. Failed methotrexate monotherapy. Moderate disease activity. DAS28 score 4.8. No TB history.', '2026-05-02 10:30:00'),
(3, 3, 3, 'N18.3', 'I10,E11.22', 'Farxiga 10mg', 'Losartan 100mg,Amlodipine 5mg,Insulin Glargine 20u', 'Stage 3 CKD with eGFR 42. Diabetic nephropathy suspected. Proteinuria 850mg/day. BP consistently 145/92.', '2026-05-03 14:00:00'),
(4, 4, 4, 'G35', NULL, 'Ocrevus 300mg', 'Copaxone (discontinued due to injection site reactions)', 'Relapsing-remitting MS. Two relapses in past 12 months. MRI shows 3 new T2 lesions. EDSS 2.5.', '2026-05-05 08:45:00'),
(5, 5, 5, 'I50.22', 'I10,I25.10', 'Entresto 49/51mg', 'Lisinopril 20mg (switching to Entresto),Carvedilol 12.5mg,Furosemide 40mg', 'HFrEF with EF 30%. NYHA Class III. Recent hospitalization for acute decompensation. Weight gain 4kg in 2 weeks.', '2026-05-06 11:20:00'),
(6, 6, 1, 'C50.911', 'Z85.3', 'Tamoxifen 20mg', 'Anastrozole (switched due to joint pain)', 'ER+/PR+/HER2- breast cancer. Stage IIA. Post-lumpectomy and radiation. Started adjuvant hormonal therapy.', '2026-05-07 09:00:00'),
(7, 7, 2, 'J45.40', 'J30.1,J45.50', 'Dupixent 200mg', 'Fluticasone/Salmeterol 250/50,Montelukast 10mg,Prednisone bursts x3/year', 'Severe persistent asthma. 3 ED visits in past year. Eosinophil count 620. IgE 450. Failed step 4 therapy.', '2026-05-08 13:30:00'),
(8, 8, 3, 'B18.2', 'K74.60', 'Epclusa 400/100mg', NULL, 'Chronic HCV genotype 1a. Treatment-naive. Fibroscan 12.1 kPa (F3 fibrosis). Viral load 2.1M IU/mL. No decompensation.', '2026-05-09 10:15:00'),
(9, 9, 4, 'B20', 'Z21', 'Biktarvy', 'Descovy + Tivicay (simplifying regimen)', 'HIV diagnosed 2020. Virologically suppressed on current regimen. CD4 count 580. No OIs. Switching to single-tablet regimen for adherence.', '2026-05-10 15:45:00'),
(10, 10, 5, 'M81.0', 'M80.08XA,E55.9', 'Prolia 60mg', 'Alendronate 70mg weekly (GI intolerance),Calcium 1200mg,Vitamin D3 2000IU', 'Postmenopausal osteoporosis. T-score -3.1 lumbar spine. History of vertebral compression fracture. Cannot tolerate oral bisphosphonates.', '2026-05-10 16:30:00'),
(11, 1, 1, 'E78.5', 'E11.65,I10', 'Repatha 140mg', 'Atorvastatin 40mg (LDL still elevated),Ezetimibe 10mg', 'LDL 145 despite max statin + ezetimibe. ASCVD risk score high. Considering PCSK9 inhibitor.', '2026-05-11 09:00:00'),
(12, 3, 3, 'D63.1', 'N18.3,E11.22', 'Aranesp 40mcg', 'Ferrous sulfate 325mg TID,IV iron sucrose x3', 'Anemia of CKD. Hgb 9.2. Iron studies adequate after IV repletion. Starting ESA therapy.', '2026-05-12 11:00:00');

-- ======================== Care Plans（加了 status）========================
-- 10 个 completed，1 个 pending（刚创建还没生成），1 个 failed（LLM 调用失败）
INSERT INTO care_plan (id, order_id, content, status, created_at) VALUES

(1, 1, '# Care Plan — John Doe (MRN: 100001)

## 1. Problem List
- Type 2 Diabetes Mellitus with hyperglycemia (E11.65) — HbA1c 8.2%
- Essential Hypertension (I10)
- Hyperlipidemia (E78.5)
- Obesity (BMI 31.2)

## 2. Goals
- Reduce HbA1c to <7.5% within 3 months
- BP <130/80 mmHg
- LDL <100 mg/dL

## 3. Pharmacist Interventions
- Initiate Metformin 500mg BID, titrate to 1000mg BID over 4 weeks
- Educate on hypoglycemia signs and GI side effects
- Assess renal function before initiation
- Review diet and exercise; refer to diabetes educator

## 4. Monitoring Plan
- HbA1c: every 3 months
- Fasting glucose: monthly for first 3 months
- Renal function: baseline, then every 6 months
- BP: each visit', 'completed', '2026-05-01 09:20:00'),

(2, 2, '# Care Plan — Jane Smith (MRN: 100002)

## 1. Problem List
- Rheumatoid Arthritis, seropositive (M05.79) — moderate activity, DAS28 4.8
- Failed methotrexate monotherapy

## 2. Goals
- Achieve low disease activity (DAS28 <3.2) within 12 weeks
- Taper Prednisone to discontinuation within 8 weeks

## 3. Pharmacist Interventions
- Initiate Humira 40mg SC every other week
- Pre-treatment: TB test, Hep B/C, CBC, LFTs
- Patient education on injection technique and infection signs

## 4. Monitoring Plan
- CBC and LFTs: baseline, 4 weeks, then every 3 months
- DAS28: every 12 weeks
- TB screening: annually', 'completed', '2026-05-02 10:45:00'),

(3, 3, '# Care Plan — Robert Johnson (MRN: 100003)

## 1. Problem List
- CKD Stage 3 (N18.3) — eGFR 42, proteinuria 850mg/day
- Hypertension (I10) — BP 145/92
- Type 2 DM with diabetic CKD (E11.22)

## 2. Goals
- Reduce proteinuria by 30% in 6 months
- BP <130/80

## 3. Pharmacist Interventions
- Initiate Farxiga 10mg daily
- Educate on genital mycotic infections and volume depletion
- Optimize Losartan to max tolerated dose

## 4. Monitoring Plan
- eGFR and SCr: baseline, 2 weeks, 1 month, then every 3 months
- UACR: every 6 months
- Potassium: 1 week, 2 weeks, then every 3 months', 'completed', '2026-05-03 14:15:00'),

(4, 4, '# Care Plan — Maria Garcia (MRN: 100004)

## 1. Problem List
- Relapsing-remitting MS (G35) — 2 relapses/12 months, EDSS 2.5
- Prior Copaxone discontinued

## 2. Goals
- Achieve NEDA within 12 months
- Maintain EDSS ≤2.5

## 3. Pharmacist Interventions
- Initiate Ocrevus 300mg IV (split first dose)
- Pre-medication: methylprednisolone, acetaminophen, diphenhydramine
- Screen for Hep B, ensure vaccinations current

## 4. Monitoring Plan
- Immunoglobulin levels: every 6 months
- MRI brain: 6 months, then annually
- JCV antibody: every 6 months', 'completed', '2026-05-05 09:00:00'),

(5, 5, '# Care Plan — David Williams (MRN: 100005)

## 1. Problem List
- HFrEF (I50.22) — EF 30%, NYHA Class III
- Recent hospitalization, fluid overload

## 2. Goals
- Stabilize fluid status within 2 weeks
- Achieve target dose Entresto within 8 weeks

## 3. Pharmacist Interventions
- Switch Lisinopril to Entresto 49/51mg BID (36-hour washout)
- Titrate to 97/103mg BID over 4-8 weeks
- Educate on daily weight monitoring, Na <2g/day

## 4. Monitoring Plan
- Daily weights by patient
- Renal function + K: weekly during titration
- BNP: baseline, 4 weeks, then every 3 months
- Echo: 3 months after optimization', 'completed', '2026-05-06 11:35:00'),

(6, 6, '# Care Plan — Susan Brown (MRN: 100006)

## 1. Problem List
- ER+/PR+/HER2- Breast Cancer (C50.911) — Stage IIA, post-surgery
- Prior Anastrozole discontinued due to arthralgias

## 2. Goals
- Complete 5-10 years adjuvant hormonal therapy
- Maintain bone health

## 3. Pharmacist Interventions
- Initiate Tamoxifen 20mg daily
- Screen for DVT/PE risk factors
- Avoid strong CYP2D6 inhibitors

## 4. Monitoring Plan
- CBC and LFTs: every 6 months for year 1
- Gynecological exam: annually
- DEXA: baseline, then every 2 years', 'completed', '2026-05-07 09:15:00'),

(7, 7, '# Care Plan — James Wilson (MRN: 100007)

## 1. Problem List
- Severe persistent asthma (J45.40) — uncontrolled on Step 4
- Elevated eosinophils 620, IgE 450

## 2. Goals
- Zero exacerbations in 12 months
- Eliminate oral steroid bursts

## 3. Pharmacist Interventions
- Initiate Dupixent 600mg loading, then 200mg every other week
- Continue ICS/LABA and Montelukast
- Develop asthma action plan

## 4. Monitoring Plan
- Eosinophil count: baseline, 4 weeks, then every 3 months
- ACT score and spirometry: every visit', 'completed', '2026-05-08 13:45:00'),

(8, 8, '# Care Plan — Patricia Martinez (MRN: 100008)

## 1. Problem List
- Chronic HCV genotype 1a (B18.2) — treatment-naive
- Advanced fibrosis F3

## 2. Goals
- Achieve SVR12
- ≥95% adherence to 12-week course

## 3. Pharmacist Interventions
- Initiate Epclusa 400/100mg daily x 12 weeks
- Drug interaction screening
- Verify insurance prior authorization

## 4. Monitoring Plan
- HCV RNA: week 4, EOT, SVR12
- CBC, CMP, LFTs: baseline, week 4, EOT
- HCC screening: every 6 months', 'completed', '2026-05-09 10:30:00'),

(9, 9, '# Care Plan — Thomas Anderson (MRN: 100009)

## 1. Problem List
- HIV (B20) — virologically suppressed, CD4 580
- Simplifying regimen

## 2. Goals
- Maintain viral suppression after switch
- Improve adherence via single-tablet regimen

## 3. Pharmacist Interventions
- Switch to Biktarvy 1 tablet daily
- Drug interaction review: avoid rifampin, separate antacids
- Assess renal and bone health

## 4. Monitoring Plan
- HIV viral load: 4 weeks post-switch, then every 3-6 months
- CD4: every 6 months
- Renal function: baseline, 4 weeks, then every 6 months', 'completed', '2026-05-10 16:00:00'),

(10, 10, '# Care Plan — Linda Taylor (MRN: 100010)

## 1. Problem List
- Postmenopausal osteoporosis (M81.0) — T-score -3.1
- History of vertebral compression fracture
- Vitamin D deficiency

## 2. Goals
- Improve T-score by ≥3% within 2 years
- Prevent future fractures

## 3. Pharmacist Interventions
- Initiate Prolia 60mg SC every 6 months
- Ensure adequate calcium and Vitamin D
- Dental exam before starting

## 4. Monitoring Plan
- Calcium and Vitamin D: baseline, 2 weeks, then every 6 months
- DEXA: 2 years after initiation
- Dental exam: annually', 'completed', '2026-05-10 16:45:00'),

-- Order 11: pending（刚提交，还没开始生成）
(11, 11, NULL, 'pending', '2026-05-11 09:00:00'),

-- Order 12: failed（LLM 调用失败了）
(12, 12, NULL, 'failed', '2026-05-12 11:00:00');

-- Reset sequences
SELECT setval('provider_id_seq', (SELECT MAX(id) FROM provider));
SELECT setval('patient_id_seq', (SELECT MAX(id) FROM patient));
SELECT setval('care_order_id_seq', (SELECT MAX(id) FROM care_order));
SELECT setval('care_plan_id_seq', (SELECT MAX(id) FROM care_plan));
