package com.payflow.service;

import com.payflow.dto.Dtos.*;
import com.payflow.exception.ApiException;
import com.payflow.model.Card;
import com.payflow.model.User;
import com.payflow.repository.CardRepository;
import com.payflow.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.List;

/**
 * Card linking, PCI-DSS style:
 *  - the full card number (PAN) is validated, then reduced to brand + last 4 digits and discarded;
 *  - the CVV is only format-checked and never stored or logged (PCI-DSS 3.3: no sensitive auth data after authorization);
 *  - the card is referenced everywhere by a random token, as a payment processor's vault would return.
 */
@Service
public class CardService {

    private static final int MAX_CARDS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CardRepository cards;
    private final UserRepository users;
    private final AuditService audit;

    public CardService(CardRepository cards, UserRepository users, AuditService audit) {
        this.cards = cards;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    public CardResponse link(Long userId, CardRequest req) {
        String pan = req.cardNumber().replace(" ", "");
        if (pan.length() < 13 || pan.length() > 19 || !luhnValid(pan)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Card number is not valid");
        }
        if (YearMonth.of(req.expYear(), req.expMonth()).isBefore(YearMonth.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Card has expired");
        }
        if (cards.countByUserId(userId) >= MAX_CARDS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can link up to " + MAX_CARDS + " cards");
        }
        User user = users.findById(userId).orElseThrow();
        String brand = brandOf(pan);
        String last4 = pan.substring(pan.length() - 4);
        Card card = cards.save(new Card(user, newToken(), brand, last4, req.expMonth(), req.expYear(),
                req.holderName().trim()));
        audit.log(user.getEmail(), "CARD_LINKED", brand + " ending " + last4);
        return CardResponse.from(card);
    }

    @Transactional(readOnly = true)
    public List<CardResponse> list(Long userId) {
        return cards.findByUserIdOrderByCreatedAtDesc(userId).stream().map(CardResponse::from).toList();
    }

    @Transactional
    public void remove(Long userId, Long cardId) {
        Card card = cards.findByIdAndUserId(cardId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Card not found"));
        cards.delete(card);
        audit.log(card.getUser().getEmail(), "CARD_REMOVED", card.getBrand() + " ending " + card.getLast4());
    }

    /** Luhn checksum: catches typos in card numbers. */
    public static boolean luhnValid(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') return false;
            int d = c - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    static String brandOf(String pan) {
        if (pan.startsWith("4")) return "Visa";
        if (pan.matches("^(5[1-5]|2[2-7]).*")) return "Mastercard";
        if (pan.matches("^3[47].*")) return "Amex";
        if (pan.matches("^(60|65|81|82|508).*")) return "RuPay";
        return "Card";
    }

    private static String newToken() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return "tok_" + HexFormat.of().formatHex(b);
    }
}
