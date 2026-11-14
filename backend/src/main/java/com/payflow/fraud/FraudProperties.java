package com.payflow.fraud;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** Bound from the "fraud:" block in application.yml — thresholds change without touching code. */
@ConfigurationProperties(prefix = "fraud")
public record FraudProperties(boolean enabled, Velocity velocity, RepeatRecipient repeatRecipient,
                              Beneficiary beneficiary, Threshold threshold, Geo geo, Batch batch) {

    public record RepeatRecipient(int maxTransfers, int windowSeconds) {}

    public record Beneficiary(int blockedThreshold, int windowHours) {}

    public record Velocity(int maxTransfers, int windowSeconds) {}

    public record Threshold(BigDecimal alertAmount, BigDecimal blockAmount) {}

    public record Geo(int windowMinutes) {}

    public record Batch(long intervalMs, int windowMinutes, BigDecimal maxOutflow) {}
}
