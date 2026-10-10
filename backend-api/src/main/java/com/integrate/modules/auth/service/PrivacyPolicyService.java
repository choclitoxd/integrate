package com.integrate.modules.auth.service;

import com.integrate.modules.auth.service.model.PublishedPolicy;

/** Consulta pública de la política vigente (Ley 1581, art. 9: autorización informada; ADR-0013 §1). */
public interface PrivacyPolicyService {

    /** Política vigente; 404 {@code PRIVACY_POLICY_NOT_PUBLISHED} si no hay ninguna publicada. */
    PublishedPolicy current();
}
