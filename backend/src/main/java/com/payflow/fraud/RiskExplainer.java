package com.payflow.fraud;

import com.payflow.dto.Dtos.Explanation;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns raw rule hits ("VELOCITY: 6 transfers in last 60s (limit 5)") into language a customer understands,
 * plus what they can do about it. A risk decision the customer can't understand is a support ticket;
 * one they can understand is often resolved on the spot.
 */
public final class RiskExplainer {

    private RiskExplainer() {}

    public static List<Explanation> explain(String fraudReason) {
        List<Explanation> out = new ArrayList<>();
        if (fraudReason == null || fraudReason.isBlank()) return out;
        for (String part : fraudReason.split(" \\| ")) {
            int colon = part.indexOf(':');
            String rule = colon > 0 ? part.substring(0, colon).trim() : part.trim();
            String detail = colon > 0 ? part.substring(colon + 1).trim() : "";
            out.add(describe(rule, detail));
        }
        return out;
    }

    private static Explanation describe(String rule, String detail) {
        return switch (rule) {
            case "VELOCITY" -> new Explanation(rule, "Too many payments in a short time",
                    "Your account sent " + detail + ". Sudden bursts like this are how stolen accounts are usually emptied.",
                    "If this was you, wait a minute and try again — or appeal and a reviewer will release it.");
            case "REPEAT_RECIPIENT" -> new Explanation(rule, "Several quick payments to the same person",
                    "We saw " + detail + ". Scams often pressure people into paying the same account again and again.",
                    "Pause and make sure nobody is rushing you. If it's genuine, appeal with a short explanation.");
            case "MULE_BENEFICIARY" -> new Explanation(rule, "This recipient is under review",
                    "Other payments to this account were recently stopped for suspected fraud (" + detail + ").",
                    "Only continue if you know this person well. If you do, appeal and tell us how you know them.");
            case "GEO_ANOMALY" -> new Explanation(rule, "Payment from an unusual location",
                    "The location " + detail.replace("country changed ", "changed from ") + " — faster than anyone can travel.",
                    "Using a VPN or travelling? Appeal and we'll release it after a quick check.");
            case "THRESHOLD" -> new Explanation(rule, "Unusually large amount",
                    "The " + detail + ".",
                    "Large payments may need a review. Appeal with the purpose of the payment to speed it up.");
            case "HIGH_OUTFLOW" -> new Explanation(rule, "High total outflow",
                    "Your account " + detail + ".",
                    "No action needed unless we contact you.");
            default -> new Explanation(rule, rule, detail, "Appeal if you think this is a mistake.");
        };
    }
}
