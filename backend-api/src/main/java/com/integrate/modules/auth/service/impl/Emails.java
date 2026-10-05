package com.integrate.modules.auth.service.impl;

import java.util.Locale;

/** Normalización única del correo: se guarda y se busca en minúsculas, sin espacios externos. */
final class Emails {

    private Emails() {
    }

    static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
