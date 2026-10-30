package cn.mythicland.dreamshop.api;

/**
 * Runtime state of a shop record.
 */
public enum ShopStatus {

    /** The shop can be traded with. */
    ACTIVE,

    /** The product cannot currently be materialized or matched. */
    INVALID,

    /** The record is retained for audit purposes but no longer active. */
    DELETED
}
