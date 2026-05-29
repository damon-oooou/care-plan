package com.careplan.adapter;

import com.careplan.dto.InternalOrder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 所有数据源 Adapter 的抽象基类。
 * 子类只需实现 parse() 和 transform()，validate() 已有默认实现。
 *
 * 使用流程：adapter.process(rawData)
 *   1. parse()      — 原始数据 → 结构化中间 Map
 *   2. transform()  — 中间 Map → InternalOrder
 *   3. validate()   — 校验 InternalOrder 字段
 *
 * @param <T> 原始输入类型（String for JSON/CSV, byte[] for HL7, etc.）
 */
public abstract class BaseIntakeAdapter<T> {

    private final Validator validator;

    protected BaseIntakeAdapter(Validator validator) {
        this.validator = validator;
    }

    /** 子类声明自己负责哪个来源 */
    public abstract String sourceSystem();

    /**
     * 把原始数据解析成 key-value 中间结构。
     * 这一步只做格式解析，不做业务映射。
     * 如果格式不合法（JSON 坏了、缺必填字段），直接抛 AdapterParseException。
     */
    protected abstract Map<String, Object> parse(T rawData);

    /**
     * 把中间 Map 映射成 InternalOrder。
     * 字段重命名、类型转换、默认值填充都在这里。
     */
    protected abstract InternalOrder transform(Map<String, Object> parsed);

    /**
     * 校验 InternalOrder。默认用 Jakarta Validation 注解校验，
     * 子类可以 override 追加业务规则。
     */
    protected List<String> validate(InternalOrder order) {
        Set<ConstraintViolation<InternalOrder>> violations = validator.validate(order);
        return violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
    }

    /**
     * 对外唯一入口：parse → transform → validate，一步到位。
     * final 不允许子类覆盖，保证所有数据源走同一条流程。
     */
    public final InternalOrder process(T rawData) {
        Map<String, Object> parsed = parse(rawData);
        InternalOrder order = transform(parsed);

        List<String> errors = validate(order);
        if (!errors.isEmpty()) {
            throw new AdapterValidationException(sourceSystem(), errors);
        }

        return order;
    }
}
