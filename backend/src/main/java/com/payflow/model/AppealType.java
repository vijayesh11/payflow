package com.payflow.model;

public enum AppealType {
    HOLD,               // "please release my held money early"
    BLOCKED_TRANSFER,   // "this blocked payment was really me"
    ACCOUNT_FREEZE      // "please review the freeze on my account"
}
