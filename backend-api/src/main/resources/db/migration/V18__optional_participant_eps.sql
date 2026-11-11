-- ADR-0054: no se exige información de salud para reservar.
ALTER TABLE booking.booking_participants ALTER COLUMN eps_enc DROP NOT NULL;
COMMENT ON COLUMN booking.booking_participants.eps_enc IS
    'EPS voluntaria del titular, cifrada AES-256-GCM; NULL si no se suministra.';
