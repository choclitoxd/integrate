package com.integrate.modules.auth;

import com.integrate.modules.auth.dto.request.ConsentAcceptance;
import com.integrate.modules.auth.dto.request.RegisterRequest;
import com.integrate.modules.auth.entity.ConsentPurpose;
import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.service.model.ClientInfo;
import java.net.InetAddress;
import java.util.Set;
import java.util.UUID;

/** Datos de prueba del módulo {@code auth}. Contraseñas generadas en memoria; nada reutilizable fuera de las pruebas. */
public final class AuthTestData {

    public static final String DEV_POLICY = "v0.1";
    public static final String PASSWORD = "clave-de-prueba-" + UUID.randomUUID().toString().substring(0, 8);
    public static final ClientInfo CLIENT = new ClientInfo(InetAddress.getLoopbackAddress(), "JUnit");

    private AuthTestData() {
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID().toString().substring(0, 8) + "@ejemplo.com";
    }

    public static String uniqueDocNumber() {
        return "D" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static RegisterRequest tourist(String email) {
        return tourist(email, uniqueDocNumber(), new ConsentAcceptance(DEV_POLICY, Set.of(ConsentPurpose.PERSONAL_DATA)));
    }

    public static RegisterRequest tourist(String email, String docNumber, ConsentAcceptance consent) {
        return new RegisterRequest(email, PASSWORD, "Carlos Restrepo", DocType.CC, docNumber, "+573001234567",
                consent);
    }
}
