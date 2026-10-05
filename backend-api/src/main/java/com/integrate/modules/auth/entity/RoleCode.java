package com.integrate.modules.auth.entity;

/** Códigos de {@code auth.roles} (V2, V10). El claim del JWT es {@code ROLE_<code>}. */
public enum RoleCode {
    TOURIST,
    HOST,
    LINK_MANAGER,
    ADMIN;

    public static final String CLAIM_PREFIX = "ROLE_";

    public String claim() {
        return CLAIM_PREFIX + name();
    }
}
