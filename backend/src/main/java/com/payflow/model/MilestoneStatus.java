package com.payflow.model;

/**
 * PENDING_FUNDING -> FUNDED (client paid into escrow, PIN-authorised)
 *   -> SUBMITTED (freelancer delivered; review window starts)
 *      -> RELEASED (client approved, or review window expired: auto-release)
 *      -> DISPUTED (client disputed within the window) -> RELEASED or REFUNDED by an admin, with reasons
 *   -> REFUNDED (freelancer cancelled before delivering)
 */
public enum MilestoneStatus { PENDING_FUNDING, FUNDED, SUBMITTED, DISPUTED, RELEASED, REFUNDED }
