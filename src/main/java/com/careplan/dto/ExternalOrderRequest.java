package com.careplan.dto;

/**
 * 外部系统推送订单的请求体。
 * sourceSystem 标识来源（如 "clinic_b"），rawData 放原始 JSON。
 */
public class ExternalOrderRequest {
    public String sourceSystem;
    public String rawData;
}
