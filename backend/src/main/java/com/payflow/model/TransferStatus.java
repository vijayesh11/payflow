package com.payflow.model;

public enum TransferStatus {
    COMPLETED,  // money moved
    BLOCKED     // stopped by the fraud engine, no money moved
}
