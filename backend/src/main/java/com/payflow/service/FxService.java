package com.payflow.service;

import com.payflow.dto.Dtos.FxQuote;
import com.payflow.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Transparent FX: we convert at the mid-market rate and charge one visible fee — no markup hidden in the rate.
 * Every quote also shows what a typical provider would pay out (published-style 3–4% FX markup plus a
 * percentage fee), so the saving is concrete.
 *
 * Rates are SIMULATED reference rates (base value plus a small drift) — a production system would pull
 * live mid-market rates from a provider such as an FX data API.
 */
@Service
public class FxService {

    private static final Map<String, Double> BASE_INR = Map.of(
            "USD", 83.20, "EUR", 90.10, "GBP", 105.30, "AED", 22.65, "SGD", 61.90);

    private final BigDecimal feePercent;
    private final BigDecimal typicalMarkupPercent;
    private final BigDecimal typicalFeePercent;
    private final int lockMinutes;

    public FxService(@Value("${app.fx.fee-percent}") BigDecimal feePercent,
                     @Value("${app.fx.typical-markup-percent}") BigDecimal typicalMarkupPercent,
                     @Value("${app.fx.typical-fee-percent}") BigDecimal typicalFeePercent,
                     @Value("${app.fx.quote-lock-minutes}") int lockMinutes) {
        this.feePercent = feePercent;
        this.typicalMarkupPercent = typicalMarkupPercent;
        this.typicalFeePercent = typicalFeePercent;
        this.lockMinutes = lockMinutes;
    }

    /** Current simulated mid-market rate: INR per 1 unit of currency. */
    public BigDecimal midRate(String currency) {
        Double base = BASE_INR.get(currency);
        if (base == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported currency " + currency);
        long minute = Instant.now().getEpochSecond() / 60;
        double drift = Math.sin(minute / 7.0) * 0.004;       // ±0.4%, changes minute to minute
        return BigDecimal.valueOf(base * (1 + drift)).setScale(4, RoundingMode.HALF_UP);
    }

    public Instant newLockExpiry() {
        return Instant.now().plus(lockMinutes, ChronoUnit.MINUTES);
    }

    public FxQuote quote(String currency, BigDecimal amount, BigDecimal rate, Instant lockedUntil) {
        BigDecimal gross = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = pct(gross, feePercent);
        BigDecimal net = gross.subtract(fee);
        // typical provider: converts at a marked-up rate, then takes a percentage fee
        BigDecimal typicalGross = gross.subtract(pct(gross, typicalMarkupPercent));
        BigDecimal typicalNet = typicalGross.subtract(pct(typicalGross, typicalFeePercent));
        return new FxQuote(currency, amount, rate, gross, feePercent, fee, net, typicalMarkupPercent, typicalFeePercent,
                typicalNet, net.subtract(typicalNet), lockedUntil, "Simulated mid-market reference rate");
    }

    private static BigDecimal pct(BigDecimal v, BigDecimal percent) {
        return v.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
