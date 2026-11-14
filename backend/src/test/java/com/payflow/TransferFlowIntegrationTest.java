package com.payflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end through HTTP, security, service and (H2) database. */
@SpringBootTest
@AutoConfigureMockMvc
class TransferFlowIntegrationTest {

    static final String PASSWORD = "Password12345";
    static final String PIN = "583920";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String aliceToken;
    private String bobEmail;

    @BeforeEach
    void setUp() throws Exception {
        aliceToken = register(uniqueEmail("alice"));
        bobEmail = uniqueEmail("bob");
        register(bobEmail);
        addMoney(aliceToken, "1000");
        setPin(aliceToken, PIN);
    }

    // ---------- payments ----------

    @Test
    void sameIdempotencyKeyChargesOnlyOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = "{\"recipientEmail\":\"" + bobEmail + "\",\"amount\":100,\"note\":\"lunch\"}";

        mvc.perform(transfer(key, body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.note").value("lunch"));
        mvc.perform(transfer(key, body)).andExpect(status().isOk())          // the "retry"
                .andExpect(jsonPath("$.replayed").value(true));

        assertThat(balance(aliceToken)).isEqualByComparingTo("900.00");     // debited once, not twice
    }

    @Test
    void reusingKeyForDifferentPaymentIsRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        mvc.perform(transfer(key, "{\"recipientEmail\":\"" + bobEmail + "\",\"amount\":100}"))
                .andExpect(status().isCreated());
        mvc.perform(transfer(key, "{\"recipientEmail\":\"" + bobEmail + "\",\"amount\":999}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void insufficientFundsMovesNoMoney() throws Exception {
        mvc.perform(transfer(UUID.randomUUID().toString(),
                        "{\"recipientEmail\":\"" + bobEmail + "\",\"amount\":5000}"))
                .andExpect(status().isConflict());
        assertThat(balance(aliceToken)).isEqualByComparingTo("1000.00");
    }

    // ---------- fraud rules ----------

    @Test
    void velocityRuleBlocksBurstAcrossDifferentRecipients() throws Exception {
        for (int i = 0; i < 5; i++) {
            String other = uniqueEmail("r" + i);
            register(other);
            mvc.perform(transfer(UUID.randomUUID().toString(),
                    "{\"recipientEmail\":\"" + other + "\",\"amount\":10}")).andExpect(status().isCreated());
        }
        mvc.perform(transfer(UUID.randomUUID().toString(), "{\"recipientEmail\":\"" + bobEmail + "\",\"amount\":10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.fraudReason").value(containsString("VELOCITY")));
        assertThat(balance(aliceToken)).isEqualByComparingTo("950.00");
    }

    @Test
    void repeatRecipientRuleBlocksFourthQuickTransferToSamePerson() throws Exception {
        String body = "{\"recipientEmail\":\"" + bobEmail + "\",\"amount\":10}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(transfer(UUID.randomUUID().toString(), body)).andExpect(status().isCreated());
        }
        mvc.perform(transfer(UUID.randomUUID().toString(), body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.fraudReason").value(containsString("REPEAT_RECIPIENT")));
    }

    @Test
    void muleRuleBlocksEvenSmallTransfersToFlaggedRecipient() throws Exception {
        for (int i = 0; i < 3; i++) {   // three over-limit attempts into bob get blocked -> bob looks like a mule
            String t = register(uniqueEmail("v" + i));
            addMoney(t, "20000");
            setPin(t, PIN);
            mvc.perform(post("/api/transfers").header("Authorization", "Bearer " + t)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"recipientEmail\":\"" + bobEmail + "\",\"amount\":15000,\"pin\":\"" + PIN + "\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(transfer(UUID.randomUUID().toString(), "{\"recipientEmail\":\"" + bobEmail + "\",\"amount\":5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.fraudReason").value(containsString("MULE_BENEFICIARY")));
    }

    // ---------- security / PCI-DSS controls ----------

    @Test
    void userCannotAccessAdminApi() throws Exception {
        mvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/wallet")).andExpect(status().isUnauthorized());
    }

    @Test
    void weakPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail("weak") + "\",\"password\":\"short1\",\"fullName\":\"W\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accountLocksAfterFiveFailedLogins() throws Exception {
        String email = uniqueEmail("lock");
        register(email);
        String wrong = "{\"email\":\"" + email + "\",\"password\":\"WrongPassword99\"}";
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isLocked());
        // even the correct password is refused while locked
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isLocked());
    }

    @Test
    void cardIsStoredMaskedAndInvalidCardRejected() throws Exception {
        String res = mvc.perform(post("/api/cards").header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardNumber\":\"5555 5555 5555 4444\",\"expMonth\":12,\"expYear\":2035,\"cvv\":\"123\",\"holderName\":\"Alice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.brand").value("Mastercard"))
                .andExpect(jsonPath("$.last4").value("4444"))
                .andReturn().getResponse().getContentAsString();
        assertThat(res).doesNotContain("5555555555554444").doesNotContain("cvv");

        mvc.perform(post("/api/cards").header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardNumber\":\"4242424242424241\",\"expMonth\":12,\"expYear\":2035,\"cvv\":\"123\",\"holderName\":\"Alice\"}"))
                .andExpect(status().isBadRequest());   // fails Luhn check
    }

    // ---------- OTP & UPI PIN ----------

    @Test
    void loginRequiresOtpWhenTwoStepIsOn() throws Exception {
        String email = uniqueEmail("otp");
        register(email);
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpRequired").value(true))
                .andExpect(jsonPath("$.auth").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode challenge = json.readTree(res).get("challenge");
        String id = challenge.get("challengeId").asText();
        String code = challenge.get("sandboxCode").asText();
        String wrong = code.equals("000000") ? "111111" : "000000";

        mvc.perform(post("/api/auth/login/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + id + "\",\"code\":\"" + wrong + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + id + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
        // single use: the same code cannot be replayed
        mvc.perform(post("/api/auth/login/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + id + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cardTopUpIsOnlyCreditedAfterOtp() throws Exception {
        String t = register(uniqueEmail("topup"));
        long cardId = linkCard(t);
        mvc.perform(post("/api/wallet/deposit").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":" + cardId + ",\"amount\":500}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.purpose").value("CARD_TOPUP"));
        assertThat(balance(t)).isEqualByComparingTo("0.00");   // nothing credited before the OTP
    }

    @Test
    void transferNeedsPinAndWrongPinLocksAfterThreeAttempts() throws Exception {
        String noPin = register(uniqueEmail("nopin"));
        addMoney(noPin, "100");
        mvc.perform(post("/api/transfers").header("Authorization", "Bearer " + noPin)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientEmail\":\"" + bobEmail + "\",\"amount\":10,\"pin\":\"583920\"}"))
                .andExpect(status().isPreconditionRequired());

        String bad = "{\"recipientEmail\":\"" + bobEmail + "\",\"amount\":10,\"pin\":\"999999\"}";
        mvc.perform(transfer(UUID.randomUUID().toString(), bad)).andExpect(status().isForbidden());
        mvc.perform(transfer(UUID.randomUUID().toString(), bad)).andExpect(status().isForbidden());
        mvc.perform(transfer(UUID.randomUUID().toString(), bad)).andExpect(status().isLocked());
        // correct PIN is refused while locked, and no money moved
        mvc.perform(transfer(UUID.randomUUID().toString(), "{\"recipientEmail\":\"" + bobEmail + "\",\"amount\":10}"))
                .andExpect(status().isLocked());
        assertThat(balance(aliceToken)).isEqualByComparingTo("1000.00");

        // resetting the PIN with an OTP unlocks it
        setPin(aliceToken, "246810");
        mvc.perform(transfer(UUID.randomUUID().toString(),
                        "{\"recipientEmail\":\"" + bobEmail + "\",\"amount\":10,\"pin\":\"246810\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void weakPinIsRejected() throws Exception {
        String challenge = mvc.perform(post("/api/me/pin/otp").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode c = json.readTree(challenge);
        mvc.perform(post("/api/me/pin").header("Authorization", "Bearer " + aliceToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + c.get("challengeId").asText() + "\",\"code\":\""
                                + c.get("sandboxCode").asText() + "\",\"pin\":\"123456\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    /** Sends as Alice; adds her UPI PIN to the JSON body unless the body already has one. */
    private RequestBuilder transfer(String key, String body) {
        String withPin = body.contains("\"pin\"") ? body : body.substring(0, body.length() - 1) + ",\"pin\":\"" + PIN + "\"}";
        return post("/api/transfers").header("Authorization", "Bearer " + aliceToken)
                .header("Idempotency-Key", key).header("X-Country", "IN")
                .contentType(MediaType.APPLICATION_JSON).content(withPin);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
    }

    private String register(String email) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"fullName\":\"Test User\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    private long linkCard(String token) throws Exception {
        String card = mvc.perform(post("/api/cards").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardNumber\":\"4242424242424242\",\"expMonth\":12,\"expYear\":2035,\"cvv\":\"123\",\"holderName\":\"Test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(card).get("id").asLong();
    }

    /** Link a card, start a top-up, confirm it with the (sandbox) OTP. */
    private void addMoney(String token, String amount) throws Exception {
        long cardId = linkCard(token);
        String res = mvc.perform(post("/api/wallet/deposit").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardId\":" + cardId + ",\"amount\":" + amount + "}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        JsonNode c = json.readTree(res);
        mvc.perform(post("/api/wallet/deposit/confirm").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + c.get("challengeId").asText() + "\",\"code\":\"" + c.get("sandboxCode").asText() + "\"}"))
                .andExpect(status().isOk());
    }

    private void setPin(String token, String pin) throws Exception {
        String res = mvc.perform(post("/api/me/pin/otp").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode c = json.readTree(res);
        mvc.perform(post("/api/me/pin").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + c.get("challengeId").asText() + "\",\"code\":\""
                                + c.get("sandboxCode").asText() + "\",\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isNoContent());
    }

    private BigDecimal balance(String token) throws Exception {
        String res = mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(res);
        return n.get("balance").decimalValue();
    }
}
