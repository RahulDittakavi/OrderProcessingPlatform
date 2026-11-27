package com.rahul.ms.order.entity;

public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
