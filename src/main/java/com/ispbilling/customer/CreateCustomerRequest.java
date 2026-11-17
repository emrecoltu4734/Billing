package com.ispbilling.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateCustomerRequest(
        @NotNull(message = "Abone tipi seçilmeli") CustomerType type,
        @NotBlank(message = "Ad soyad / unvan boş olamaz") String name,
        @NotBlank(message = "TCKN/VKN boş olamaz")
        @Pattern(regexp = "\\d{10,11}", message = "TCKN 11, VKN 10 haneli olmalı") String taxId,
        String taxOffice,
        String phone,
        @Email(message = "Geçerli bir e-posta girin") String email,
        String address,
        /** Boşsa varsayılan fatura dönemi */
        String billCycleCode) {
}
