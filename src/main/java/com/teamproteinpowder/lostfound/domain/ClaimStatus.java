package com.teamproteinpowder.lostfound.domain;

/**
 * The lifecycle of one claim.
 *
 * OPEN       the claimant has asked; the poster has not answered yet
 * ACCEPTED   the poster agrees it is theirs — the item becomes RESOLVED
 * DECLINED   the poster says no; the item goes back to OPEN if no other
 *            claim is still outstanding
 * WITHDRAWN  the claimant changed their mind
 */
public enum ClaimStatus {
    OPEN,
    ACCEPTED,
    DECLINED,
    WITHDRAWN;

    public boolean isClosed() {
        return this != OPEN;
    }
}
