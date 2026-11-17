package com.ispbilling.billing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateBillCycleRequest(
        @NotBlank(message = "Kod boş olamaz")
        @Pattern(regexp = "[A-Za-z0-9_-]{1,20}", message = "Kod en fazla 20 karakter; harf, rakam, - ve _")
        String code,
        @NotBlank(message = "Ad boş olamaz") String name,
        @NotNull(message = "Fatura günü girilmeli")
        @Min(value = 1, message = "Fatura günü 1-28 arasında olmalı")
        @Max(value = 28, message = "Fatura günü 1-28 arasında olmalı") Integer cycleDay,
        Boolean autoRun) {
}
