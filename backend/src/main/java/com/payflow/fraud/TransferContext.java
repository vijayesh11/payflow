package com.payflow.fraud;

import com.payflow.model.Wallet;
import java.math.BigDecimal;

/** Everything a rule needs to judge one transfer attempt. */
public record TransferContext(Wallet senderWallet, Wallet receiverWallet, BigDecimal amount, String country) {}
