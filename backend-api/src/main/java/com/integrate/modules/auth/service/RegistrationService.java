package com.integrate.modules.auth.service;

import com.integrate.modules.auth.dto.request.RegisterRequest;
import com.integrate.modules.auth.entity.DocType;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.RegisteredUser;

/** Altas de identidades (RF-01, RF-02, ADR-0005 §6 y §9). */
public interface RegistrationService {

    /**
     * Autorregistro de un turista con su consentimiento {@code SELF_SERVICE}, en una sola transacción.
     *
     * @throws com.integrate.common.exception.BusinessRuleException {@code VALIDATION_FAILED} (422) si la
     *         contraseña o el consentimiento no son válidos, {@code AUTH_USER_ALREADY_EXISTS} (409) si el
     *         correo o el documento ya están registrados
     */
    RegisteredUser registerTourist(RegisterRequest request, ClientInfo client);

    /**
     * Crea el primer {@code ADMIN} si aún no existe ninguno vigente. Idempotente.
     *
     * @return el resultado, para que el invocador lo registre en el log
     */
    BootstrapOutcome bootstrapAdmin(AdminBootstrapCommand command);

    enum BootstrapOutcome {
        CREATED,
        /** Ya hay un administrador vigente: no se hace nada. */
        ADMIN_ALREADY_EXISTS,
        /** El correo o el documento ya pertenecen a otra cuenta: no se eleva en silencio. */
        IDENTITY_TAKEN
    }

    record AdminBootstrapCommand(String email, String password, String fullName, DocType docType,
                                 String docNumber, String phone) {

        @Override
        public String toString() {
            return "AdminBootstrapCommand[email=" + email + "]";
        }
    }
}
