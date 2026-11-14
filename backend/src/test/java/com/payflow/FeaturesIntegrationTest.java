package com.payflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Trust Score + progressive holds, appeals, escrow and FX invoices, end to end. */
@SpringBootTest
@AutoConfigureMockMvc
class FeaturesIntegrationTest {

    static final String PASSWORD = "Password12345";
    static final String PIN = "583920";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void largePaymentToNewAccountIsPartlyHeldWithExplanation() throws Exception {
        String payer = register(email("payer"));
        addMoney(payer, "10000");
        setPin(payer);
        String newbieEmail = email("newbie");
        String newbie = register(newbieEmail);

        mvc.perform(pay(payer, newbieEmail, "5000")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.heldAmount").isNumber());

        JsonNode wallet = getJson(newbie, "/api/wallet");
        // new account: Trust Score < 40 -> 60% held
        assertThat(wallet.get("heldBalance").decimalValue()).isEqualByComparingTo("3000.00");
        assertThat(wallet.get("balance").decimalValue()).isEqualByComparingTo("2000.00");

        JsonNode res = getJson(newbie, "/api/resolution");
        assertThat(res.get("trust").get("tier").asText()).isEqualTo("New");
        JsonNode hold = res.get("holds").get(0);
        assertThat(hold.get("explanation").asText()).contains("Trust Score");
        assertThat(hold.get("status").asText()).isEqualTo("HELD");
    }

    @Test
    void blockedPaymentCanBeAppealedAndApprovedByAdmin() throws Exception {
        String sender = register(email("appealer"));
        addMoney(sender, "20000");
        setPin(sender);
        String to = email("target");
        register(to);
        String body = mvc.perform(pay(sender, to, "12000"))       // over the block limit -> BLOCKED
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.explanations[0].title").value("Unusually large amount"))
                .andReturn().getResponse().getContentAsString();
        long transferId = json.readTree(body).get("id").asLong();

        String appeal = mvc.perform(post("/api/appeals").header("Authorization", "Bearer " + sender)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"BLOCKED_TRANSFER\",\"targetId\":" + transferId
                                + ",\"message\":\"This is my rent deposit for my new flat.\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long appealId = json.readTree(appeal).get("id").asLong();

        String admin = adminToken();
        mvc.perform(post("/api/admin/appeals/" + appealId + "/decide").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true,\"note\":\"Verified with the customer by phone.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(getJson(sender, "/api/wallet").get("balance").decimalValue()).isEqualByComparingTo("8000.00");
        JsonNode mine = getJson(sender, "/api/appeals").get(0);
        assertThat(mine.get("decisionNote").asText()).contains("Verified");
    }

    @Test
    void escrowFundSubmitApproveAndDisputeRefund() throws Exception {
        String client = register(email("client"));
        addMoney(client, "30000");
        setPin(client);
        String freelancerEmail = email("freelancer");
        String freelancer = register(freelancerEmail);

        String created = mvc.perform(post("/api/escrow").header("Authorization", "Bearer " + client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"freelancerEmail\":\"" + freelancerEmail + "\",\"title\":\"Logo\",\"milestones\":["
                                + "{\"title\":\"Concepts\",\"amount\":5000},{\"title\":\"Final\",\"amount\":7000}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode ms = json.readTree(created).get("milestones");
        long m1 = ms.get(0).get("id").asLong(), m2 = ms.get(1).get("id").asLong();

        // wrong PIN: nothing funded
        mvc.perform(post("/api/escrow/milestones/" + m1 + "/fund").header("Authorization", "Bearer " + client)
                .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"999999\"}")).andExpect(status().isForbidden());
        for (long m : new long[]{m1, m2}) {
            mvc.perform(post("/api/escrow/milestones/" + m + "/fund").header("Authorization", "Bearer " + client)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"" + PIN + "\"}")).andExpect(status().isOk());
        }
        assertThat(getJson(client, "/api/wallet").get("balance").decimalValue()).isEqualByComparingTo("18000.00");

        for (long m : new long[]{m1, m2}) {
            mvc.perform(post("/api/escrow/milestones/" + m + "/submit").header("Authorization", "Bearer " + freelancer)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Delivered, see link\"}")).andExpect(status().isOk());
        }
        mvc.perform(post("/api/escrow/milestones/" + m1 + "/approve").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk());
        assertThat(getJson(freelancer, "/api/wallet").get("balance").decimalValue()).isEqualByComparingTo("5000.00");

        mvc.perform(post("/api/escrow/milestones/" + m2 + "/dispute").header("Authorization", "Bearer " + client)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Final files were never delivered\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/escrow/milestones/" + m2 + "/evidence").header("Authorization", "Bearer " + freelancer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Here is the upload receipt\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/disputes/" + m2 + "/resolve").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"REFUND\",\"note\":\"No proof of final delivery\"}"))
                .andExpect(status().isOk());
        assertThat(getJson(client, "/api/wallet").get("balance").decimalValue()).isEqualByComparingTo("25000.00");
    }

    @Test
    void internationalInvoiceSettlesAtLockedRateWithBreakdown() throws Exception {
        String freelancer = register(email("intl"));
        String inv = mvc.perform(post("/api/invoices").header("Authorization", "Bearer " + freelancer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientName\":\"Acme\",\"description\":\"Design work\",\"currency\":\"USD\",\"amount\":100}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(inv).get("token").asText();

        JsonNode view = json.readTree(mvc.perform(get("/api/public/invoices/" + token))     // no login needed
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        BigDecimal net = view.get("quote").get("netInr").decimalValue();
        assertThat(view.get("quote").get("savingsInr").decimalValue()).isPositive();

        mvc.perform(post("/api/public/invoices/" + token + "/pay").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payerName\":\"Acme Inc\",\"cardNumber\":\"4242424242424242\",\"expMonth\":12,\"expYear\":2035,\"cvv\":\"123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        assertThat(getJson(freelancer, "/api/wallet").get("balance").decimalValue()).isEqualByComparingTo(net);
        // can't be paid twice
        mvc.perform(post("/api/public/invoices/" + token + "/pay").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payerName\":\"Acme Inc\",\"cardNumber\":\"4242424242424242\",\"expMonth\":12,\"expYear\":2035,\"cvv\":\"123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void frozenUserCanSignInSeeReasonAndAppeal() throws Exception {
        String e = email("frozen");
        String t = register(e);
        String admin = adminToken();
        JsonNode users = getJson(admin, "/api/admin/users");
        long id = -1;
        for (JsonNode u : users) if (u.get("email").asText().equals(e)) id = u.get("id").asLong();
        mvc.perform(post("/api/admin/users/" + id + "/freeze").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Unusual sign-in from a new device\"}"))
                .andExpect(status().isNoContent());

        JsonNode me = getJson(t, "/api/me");
        assertThat(me.get("freezeReason").asText()).contains("Unusual");
        mvc.perform(post("/api/appeals").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ACCOUNT_FREEZE\",\"message\":\"That was me on my new phone.\"}"))
                .andExpect(status().isCreated());
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.RequestBuilder pay(String token, String to, String amount) {
        return post("/api/transfers").header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString()).header("X-Country", "IN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientEmail\":\"" + to + "\",\"amount\":" + amount + ",\"pin\":\"" + PIN + "\"}");
    }

    private static String email(String p) {
        return p + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
    }

    private JsonNode getJson(String token, String path) throws Exception {
        return json.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String register(String email) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"fullName\":\"Test User\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    private String adminToken() throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@payflow.dev\",\"password\":\"PayFlow@Admin2026\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode c = json.readTree(res).get("challenge");
        String auth = mvc.perform(post("/api/auth/login/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + c.get("challengeId").asText() + "\",\"code\":\"" + c.get("sandboxCode").asText() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(auth).get("token").asText();
    }

    private void addMoney(String token, String amount) throws Exception {
        String card = mvc.perform(post("/api/cards").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardNumber\":\"4242424242424242\",\"expMonth\":12,\"expYear\":2035,\"cvv\":\"123\",\"holderName\":\"T\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String res = mvc.perform(post("/api/wallet/deposit").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardId\":" + json.readTree(card).get("id").asLong() + ",\"amount\":" + amount + "}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        JsonNode c = json.readTree(res);
        mvc.perform(post("/api/wallet/deposit/confirm").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + c.get("challengeId").asText() + "\",\"code\":\"" + c.get("sandboxCode").asText() + "\"}"))
                .andExpect(status().isOk());
    }

    private void setPin(String token) throws Exception {
        JsonNode c = json.readTree(mvc.perform(post("/api/me/pin/otp").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/me/pin").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + c.get("challengeId").asText() + "\",\"code\":\"" + c.get("sandboxCode").asText()
                                + "\",\"pin\":\"" + PIN + "\"}"))
                .andExpect(status().isNoContent());
    }
}
