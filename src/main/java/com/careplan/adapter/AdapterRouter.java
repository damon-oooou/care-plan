package com.careplan.adapter;

import com.careplan.dto.InternalOrder;
import com.careplan.exception.ValidationError;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Adapter 路由器。
 * Spring 自动注入所有 BaseIntakeAdapter 实现，
 * 根据 sourceSystem 找到对应的 Adapter 执行转换。
 *
 * 新增数据源时只需要加一个 Adapter @Component，这里不用改。
 */
@Service
public class AdapterRouter {

    private final List<BaseIntakeAdapter<?>> adapters;

    public AdapterRouter(List<BaseIntakeAdapter<?>> adapters) {
        this.adapters = adapters;
    }

    @SuppressWarnings("unchecked")
    public InternalOrder route(String sourceSystem, String rawData) {
        BaseIntakeAdapter<String> adapter = (BaseIntakeAdapter<String>) adapters.stream()
                .filter(a -> a.sourceSystem().equals(sourceSystem))
                .findFirst()
                .orElseThrow(() -> new ValidationError(
                        "unsupported_source",
                        "Unknown source system: " + sourceSystem
                ));

        return adapter.process(rawData);
    }
}
