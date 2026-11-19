package dev.peluci.doceenccoreapi.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record DashboardResponse(
        @Schema(requiredMode = REQUIRED) Today today,
        @Schema(requiredMode = REQUIRED) Week thisWeek,
        @Schema(requiredMode = REQUIRED) long overdueCount,
        @Schema(requiredMode = REQUIRED) Month month,
        @Schema(requiredMode = REQUIRED) Receivable receivable) {

    public record Today(
            @Schema(requiredMode = REQUIRED) LocalDate date,
            @Schema(requiredMode = REQUIRED) List<TodayOrder> orders) {
    }

    public record TodayOrder(
            @Schema(requiredMode = REQUIRED) UUID id,
            @Schema(requiredMode = REQUIRED) long orderNumber,
            @Schema(requiredMode = REQUIRED) String customerName,
            @Schema(requiredMode = REQUIRED) String fulfillmentType,
            @Schema(requiredMode = REQUIRED) BigDecimal totalAmount,
            @Schema(requiredMode = REQUIRED) BigDecimal balance) {
    }

    public record Week(
            @Schema(requiredMode = REQUIRED) LocalDate from,
            @Schema(requiredMode = REQUIRED) LocalDate to,
            @Schema(requiredMode = REQUIRED) long ordersCount) {
    }

    public record Month(
            @Schema(requiredMode = REQUIRED) LocalDate from,
            @Schema(requiredMode = REQUIRED) LocalDate to,
            @Schema(requiredMode = REQUIRED) BigDecimal soldAmount,
            @Schema(requiredMode = REQUIRED) BigDecimal receivedAmount) {
    }

    public record Receivable(
            @Schema(requiredMode = REQUIRED) BigDecimal amount,
            @Schema(requiredMode = REQUIRED) long ordersCount,
            @Schema(requiredMode = REQUIRED) Bucket dueOnDelivery,
            @Schema(requiredMode = REQUIRED) Bucket unpaidDelivered) {
    }

    public record Bucket(
            @Schema(requiredMode = REQUIRED) BigDecimal amount,
            @Schema(requiredMode = REQUIRED) long ordersCount) {
    }

}
