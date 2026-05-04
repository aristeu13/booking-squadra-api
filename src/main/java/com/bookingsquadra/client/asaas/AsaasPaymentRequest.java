package com.bookingsquadra.client.asaas;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsaasPaymentRequest(
        String customer,
        String billingType,
        BigDecimal value,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate dueDate,
        String externalReference,
        List<AsaasSplitRequest> split
) {}
