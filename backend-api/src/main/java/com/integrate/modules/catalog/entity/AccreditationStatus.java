package com.integrate.modules.catalog.entity;

/** Estado de la acreditación de un gestor de enlace ({@code ck_link_manager_status}, V3). {@code REVOKED} es terminal. */
public enum AccreditationStatus {
    ACTIVE,
    SUSPENDED,
    REVOKED
}
