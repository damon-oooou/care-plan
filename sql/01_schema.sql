-- ============================================================
-- Care Plan Generator — Database Schema (v2)
-- PostgreSQL
-- 改动：Patient 加了 date_of_birth，Care Plan 加了 status
-- ============================================================

-- 1. Provider 表
CREATE TABLE IF NOT EXISTS provider (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200)  NOT NULL,
    npi         VARCHAR(10)   NOT NULL UNIQUE,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 2. Patient 表（加了 date_of_birth）
CREATE TABLE IF NOT EXISTS patient (
    id              BIGSERIAL PRIMARY KEY,
    first_name      VARCHAR(100)  NOT NULL,
    last_name       VARCHAR(100)  NOT NULL,
    mrn             VARCHAR(6)    NOT NULL UNIQUE,
    date_of_birth   DATE,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 3. Order 表
CREATE TABLE IF NOT EXISTS care_order (
    id                      BIGSERIAL PRIMARY KEY,
    patient_id              BIGINT        NOT NULL REFERENCES patient(id),
    provider_id             BIGINT        NOT NULL REFERENCES provider(id),
    primary_diagnosis       VARCHAR(20)   NOT NULL,
    additional_diagnoses    TEXT,
    medication_name         VARCHAR(200)  NOT NULL,
    medication_history      TEXT,
    patient_records         TEXT,
    created_at              TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 4. Care Plan 表（加了 status）
CREATE TABLE IF NOT EXISTS care_plan (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT       NOT NULL UNIQUE REFERENCES care_order(id),
    content     TEXT,                             -- LLM 生成后才有内容
    status      VARCHAR(20)  NOT NULL DEFAULT 'pending',
                                                  -- pending → processing → completed / failed
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Indexes
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_patient_mrn ON patient(mrn);
CREATE INDEX IF NOT EXISTS idx_patient_dob ON patient(date_of_birth);
CREATE INDEX IF NOT EXISTS idx_provider_npi ON provider(npi);
CREATE INDEX IF NOT EXISTS idx_care_order_patient ON care_order(patient_id);
CREATE INDEX IF NOT EXISTS idx_care_order_provider ON care_order(provider_id);
CREATE INDEX IF NOT EXISTS idx_care_plan_order ON care_plan(order_id);
CREATE INDEX IF NOT EXISTS idx_care_plan_status ON care_plan(status);
