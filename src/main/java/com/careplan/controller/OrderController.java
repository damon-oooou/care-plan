package com.careplan.controller;

import com.careplan.dto.OrderRequest;
import com.careplan.dto.OrderResponse;
import com.careplan.entity.CarePlan;
import com.careplan.entity.CareOrder;
import com.careplan.entity.Patient;
import com.careplan.entity.Provider;
import com.careplan.service.OrderService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class OrderController {

    @Autowired private OrderService orderService;

    /**
     * POST /api/orders — 创建订单，立刻返回
     */
    @PostMapping("/api/orders")
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest request) {
        Map<String, Long> result = orderService.createOrder(request);

        return ResponseEntity.accepted().body(Map.of(
                "message", "已收到，Care Plan 正在生成中",
                "orderId", result.get("orderId"),
                "carePlanId", result.get("carePlanId"),
                "status", "pending"
        ));
    }

    /**
     * GET /api/orders — 查看所有订单
     */
    @GetMapping("/api/orders")
    public List<OrderResponse> listOrders() {
        return orderService.getAllOrders().stream()
                .map(OrderResponse::new)
                .toList();
    }

    /**
     * GET /api/orders/{id} — 查看单个订单
     */
    @GetMapping("/api/orders/{id}")
    public ResponseEntity<?> getOrder(@PathVariable Long id) {
        CareOrder order = orderService.getOrderById(id);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new OrderResponse(order));
    }

    /**
     * GET /api/patients — 查看所有患者
     */
    @GetMapping("/api/patients")
    public List<Patient> listPatients() {
        return orderService.getAllPatients();
    }

    /**
     * GET /api/providers — 查看所有 Provider
     */
    @GetMapping("/api/providers")
    public List<Provider> listProviders() {
        return orderService.getAllProviders();
    }

    /**
     * GET /api/careplan/{id}/status — 轮询用
     */
    @GetMapping("/api/careplan/{id}/status")
    public ResponseEntity<?> getCarePlanStatus(@PathVariable Long id) {
        CarePlan carePlan = orderService.getCarePlanById(id);
        if (carePlan == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "carePlanId", carePlan.getId(),
                "status", carePlan.getStatus(),
                "content", carePlan.getContent() != null ? carePlan.getContent() : ""
        ));
    }
}
