package com.example.demo.controller;

import com.example.demo.domain.Order;
import com.example.demo.domain.OrderRequest;
import com.example.demo.domain.OrderShippingUpdateRequest;
import com.example.demo.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/** {@code orders} 리소스에 대한 REST API. 기본 경로는 {@code /api/orders}. */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public List<Order> findAll() {
        return orderService.findAll();
    }

    @GetMapping("/{id}")
    public Order findById(@PathVariable Long id) {
        return orderService.findById(id);
    }

    /** users와 JOIN한 고객 표시 정보(customer_display_name/email)를 포함해 조회한다. */
    @GetMapping("/{id}/with-contact")
    public Map<String, Object> findWithUserContact(@PathVariable Long id) {
        return orderService.findWithUserContact(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(@Valid @RequestBody OrderRequest request) {
        return orderService.create(request);
    }

    /** customerPhone/shippingAddress만 부분 갱신한다 ({@code <set>/<if>} 동적 SQL 데모). */
    @PatchMapping("/{id}/shipping")
    public Order updateShippingInfo(@PathVariable Long id, @RequestBody OrderShippingUpdateRequest request) {
        return orderService.updateShippingInfo(id, request);
    }

}
