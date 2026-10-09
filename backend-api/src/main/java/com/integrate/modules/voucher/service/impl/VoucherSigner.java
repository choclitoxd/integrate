package com.integrate.modules.voucher.service.impl;

import com.integrate.modules.voucher.service.impl.SigningKeyRegistrar.ActiveSigningKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * JWS compacto RS256 (ADR-0010 §2-§3). La firma RSASSA-PKCS1-v1_5 es determinista y la serialización se controla
 * aquí (orden fijo de campos), así que los mismos datos y la misma llave producen siempre el mismo token: por eso el
 * token no se guarda y se puede volver a derivar y verificar contra {@code token_sha256}.
 */
@Component
@RequiredArgsConstructor
class VoucherSigner {

    static final String TYPE = "voucher+jwt";
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    private final JsonMapper jsonMapper;

    String sign(VoucherClaims claims, ActiveSigningKey key) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", TYPE);
        header.put("kid", key.kid());
        String signingInput = encode(header) + "." + encode(claims.asOrderedMap());
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key.privateKey());
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + B64URL.encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Voucher signature failed", e);
        }
    }

    static String sha256Hex(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is mandatory in every JRE", e);
        }
    }

    private String encode(Map<String, Object> json) {
        return B64URL.encodeToString(jsonMapper.writeValueAsBytes(json));
    }
}
