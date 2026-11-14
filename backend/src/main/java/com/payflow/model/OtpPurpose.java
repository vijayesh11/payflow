package com.payflow.model;

public enum OtpPurpose {
    LOGIN,        // 2-step verification at sign-in
    SET_PIN,      // set or reset the UPI PIN
    CARD_TOPUP    // 3-D Secure style confirmation when adding money from a card
}
