package com.ispbilling.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @Test
    void normalizesTurkishMobileFormats() {
        assertThat(PhoneNumbers.normalizeTrMobile("0532 123 45 67")).isEqualTo("905321234567");
        assertThat(PhoneNumbers.normalizeTrMobile("5321234567")).isEqualTo("905321234567");
        assertThat(PhoneNumbers.normalizeTrMobile("+90 (532) 123-45-67")).isEqualTo("905321234567");
        assertThat(PhoneNumbers.normalizeTrMobile("0090 532 123 45 67")).isEqualTo("905321234567");
    }

    @Test
    void rejectsLandlinesAndGarbage() {
        assertThat(PhoneNumbers.normalizeTrMobile("0212 123 45 67")).isNull();   // sabit hat SMS alamaz
        assertThat(PhoneNumbers.normalizeTrMobile("123")).isNull();
        assertThat(PhoneNumbers.normalizeTrMobile(null)).isNull();
    }
}
