package com.payflow.fraud;

import com.payflow.model.Severity;

public record RuleHit(String rule, Severity severity, String details) {}
