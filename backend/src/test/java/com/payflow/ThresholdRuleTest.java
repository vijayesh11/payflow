package com.payflow;

import com.payflow.fraud.FraudProperties;
import com.payflow.fraud.ThresholdRule;
import com.payflow.fraud.TransferContext;
import com.payflow.model.Severity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ThresholdRuleTest {

    private final FraudProperties props = new FraudProperties(true,
            new FraudProperties.Velocity(5, 60),
            new FraudProperties.RepeatRecipient(3, 60),
            new FraudProperties.Beneficiary(3, 24),
            new FraudProperties.Threshold(new BigDecimal("5000"), new BigDecimal("10000")),
            new FraudProperties.Geo(30),
            new FraudProperties.Batch(30000, 60, new BigDecimal("8000")));

    private final ThresholdRule rule = new ThresholdRule(props);

    private TransferContext ctx(String amount) {
        return new TransferContext(null, null, new BigDecimal(amount), "IN");
    }

    @Test
    void smallAmountPasses() {
        assertThat(rule.evaluate(ctx("100.00"))).isEmpty();
    }

    @Test
    void amountAboveAlertLimitIsMedium() {
        assertThat(rule.evaluate(ctx("6000.00")).orElseThrow().severity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void amountAboveBlockLimitIsHigh() {
        assertThat(rule.evaluate(ctx("10000.01")).orElseThrow().severity()).isEqualTo(Severity.HIGH);
    }
}
