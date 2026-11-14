package com.payflow.otp;

import com.payflow.model.OtpPurpose;
import com.payflow.model.User;

/** Delivers an OTP to the user. Swap implementations via app.otp.delivery (sandbox | email). */
public interface OtpSender {
    void send(User user, OtpPurpose purpose, String code, String context);

    String channel();
}
