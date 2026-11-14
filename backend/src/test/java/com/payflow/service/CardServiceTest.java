package com.payflow.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardServiceTest {

    @Test
    void luhnAcceptsKnownTestCardsAndRejectsTypos() {
        assertThat(CardService.luhnValid("4242424242424242")).isTrue();
        assertThat(CardService.luhnValid("5555555555554444")).isTrue();
        assertThat(CardService.luhnValid("4242424242424241")).isFalse();
    }

    @Test
    void detectsBrand() {
        assertThat(CardService.brandOf("4242424242424242")).isEqualTo("Visa");
        assertThat(CardService.brandOf("5555555555554444")).isEqualTo("Mastercard");
        assertThat(CardService.brandOf("378282246310005")).isEqualTo("Amex");
        assertThat(CardService.brandOf("6080123456789012")).isEqualTo("RuPay");
    }
}
