package com.ispbilling.plan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePlanRequest(
        @NotBlank(message = "Tarife kodu boş olamaz") String code,
        @NotBlank(message = "Tarife adı boş olamaz") String name,
        @NotNull(message = "Aylık ücret girilmeli") @DecimalMin(value = "0", message = "Ücret negatif olamaz")
        @Digits(integer = 12, fraction = 2, message = "En fazla 2 ondalık hane") BigDecimal monthlyFee,
        @NotNull(message = "ÖİV oranı girilmeli") @DecimalMin(value = "0", message = "ÖİV negatif olamaz")
        BigDecimal oivRate,
        @NotNull(message = "KDV oranı girilmeli") @DecimalMin(value = "0", message = "KDV negatif olamaz")
        BigDecimal vatRate) {
}
