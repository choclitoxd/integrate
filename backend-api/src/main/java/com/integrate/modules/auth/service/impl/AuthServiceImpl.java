package com.integrate.modules.auth.service.impl;

import com.integrate.modules.auth.dto.request.LoginRequest;
import com.integrate.modules.auth.entity.User;
import com.integrate.modules.auth.repository.UserRepository;
import com.integrate.modules.auth.service.AuthService;
import com.integrate.modules.auth.service.model.ClientInfo;
import com.integrate.modules.auth.service.model.Session;
import java.time.Clock;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Orquesta el login y la rotación (ADR-0005 §4-§5). La verificación Argon2id (decenas de ms) se
 * hace <b>fuera</b> de toda transacción para no retener conexiones ni locks; el registro del
 * intento y la emisión de la sesión ocurren después en {@link SessionTransactions}.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final SessionTransactions transactions;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    /** Hash ficticio: un correo inexistente cuesta lo mismo que uno real (no revela qué cuentas existen). */
    private final String dummyHash;

    public AuthServiceImpl(UserRepository userRepository, SessionTransactions transactions,
                           PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.transactions = transactions;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(RefreshTokenCodec.newToken());
    }

    @Override
    public Session login(LoginRequest request, ClientInfo client) {
        String email = Emails.normalize(request.email());
        Optional<User> found = userRepository.findByEmailIgnoreCase(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            return transactions.rejectUnknownUser(email, client);
        }
        User user = found.get();
        // Durante el bloqueo no se verifica la contraseña: no hay oráculo para seguir adivinando.
        if (user.isLockedAt(clock.instant())) {
            return transactions.rejectLocked(user, email, client);
        }
        boolean matches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        return transactions.completeLogin(user.getId(), email, matches, client);
    }

    @Override
    public Session refresh(String refreshToken, ClientInfo client) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw AuthErrors.refreshTokenInvalid();
        }
        return transactions.rotate(refreshToken, client);
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            transactions.revoke(refreshToken);
        }
    }
}
