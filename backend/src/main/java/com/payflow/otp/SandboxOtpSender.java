package com.payflow.otp;

import com.payflow.model.OtpPurpose;
import com.payflow.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default for local runs and demos: no SMS/email provider needed. The code is returned to the client
 * only when app.otp.sandbox-show-code=true, and shown in a clearly labelled "sandbox" notice.
 * The code itself is never written to the logs.
 */
@Component
@ConditionalOnProperty(name = "app.otp.delivery", havingValue = "sandbox", matchIfMissing = true)
public class SandboxOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(SandboxOtpSender.class);

    @Override
    public void send(User user, OtpPurpose purpose, String code, String context) {
        log.info("Sandbox OTP issued for user {} ({})", user.getId(), purpose);
    }

    @Override
    public String channel() { return "sandbox"; }
}
