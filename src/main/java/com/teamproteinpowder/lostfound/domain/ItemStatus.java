package com.teamproteinpowder.lostfound.domain;

/**
 * The lifecycle of a post.
 *
 * OPEN      nobody has claimed it yet
 * PENDING   a claim is in progress and the two people are talking
 * RESOLVED  the item is back with its owner
 */
public enum ItemStatus {
    OPEN,
    PENDING,
    RESOLVED
}
