package com.rahul.ms.order.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.rahul.ms.order.dto.OrderRequest;
import com.rahul.ms.order.dto.OrderResponse;
import com.rahul.ms.order.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Order Management APIs")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
    
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Retrieve all orders")
    @ApiResponse(responseCode = "200", description = "List of orders")
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place a new order")
    @ApiResponse(responseCode = "201", description = "Order placed successfully")
    public OrderResponse placeOrder(@RequestBody @Valid OrderRequest orderRequest) {
        return orderService.placeOrder(orderRequest);
    }
}