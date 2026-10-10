import type { DocType } from '@/features/auth';
import type { HttpClient } from '@/shared/api';

/** DTO de reserva desde `docs/api/openapi.yaml` (ADR-0008, ADR-0015). */

export interface BookingParticipant {
  readonly fullName: string;
  readonly docType: DocType;
  readonly docNumber: string;
  readonly medicalEps: string;
  readonly isHolder: boolean;
}

export interface BookingLockRequest {
  readonly scheduleId: string;
  readonly requestedSlots: number;
  readonly emergencyContact: { readonly fullName: string; readonly phone: string };
  readonly participants: readonly BookingParticipant[];
}

export type BookingStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'PAYMENT_FAILED'
  | 'EXPIRED'
  | 'CANCELLED_REFUNDED'
  | 'CANCELLED_NO_REFUND'
  | 'CANCELLED_BY_PLATFORM'
  | 'EXECUTED'
  | 'NO_SHOW'
  | 'SYNC_CONFLICT';

/** Respuesta del lock y de `GET /bookings/{id}`: sin datos de los viajeros. */
export interface Booking {
  readonly bookingId: string;
  readonly status: BookingStatus;
  readonly scheduleId: string;
  readonly experienceId: string;
  readonly lockedSlots: number;
  readonly unitPriceCop: number;
  readonly totalAmountCop: number;
  readonly currency: 'COP';
  readonly createdAt: string;
  readonly expiresAt: string;
  readonly ttlSecondsRemaining: number;
}

export function lockSlots(
  http: HttpClient,
  body: BookingLockRequest,
  idempotencyKey: string,
): Promise<Booking> {
  return http.request<Booking>('/v1/bookings/lock', {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

export function getBooking(http: HttpClient, id: string, signal?: AbortSignal): Promise<Booking> {
  return http.request<Booking>(`/v1/bookings/${encodeURIComponent(id)}`, signal ? { signal } : {});
}

export type PaymentMethod = 'PSE' | 'CARD' | 'NEQUI' | 'DAVIPLATA';

export type CheckoutData =
  | { readonly type: 'MOCK'; readonly reference: string }
  | {
      readonly type: 'WOMPI';
      readonly publicKey: string;
      readonly currency: 'COP';
      readonly amountInCents: number;
      readonly reference: string;
      readonly integritySignature: string;
    };

/** `PaymentCheckoutResponse`: un intento abierto por reserva (ADR-0009 §3). */
export interface PaymentCheckout {
  readonly paymentId: string;
  readonly bookingId: string;
  readonly provider: 'MOCK_GATEWAY' | 'WOMPI';
  readonly paymentMethod: PaymentMethod;
  readonly status: 'INITIATED' | 'PENDING';
  readonly amountCop: number;
  readonly currency: 'COP';
  readonly bookingExpiresAt: string;
  readonly checkout: CheckoutData;
}

export function startPayment(
  http: HttpClient,
  bookingId: string,
  paymentMethod: PaymentMethod,
): Promise<PaymentCheckout> {
  return http.request<PaymentCheckout>(`/v1/bookings/${encodeURIComponent(bookingId)}/payment`, {
    method: 'POST',
    body: { paymentMethod },
  });
}

/** Solo `dev`/`test`: pasa el evento Mock por el procesador del webhook (ADR-0009, punto C). */
export function simulatePayment(
  http: HttpClient,
  paymentId: string,
  status: 'APPROVED' | 'DECLINED',
): Promise<unknown> {
  return http.request(`/v1/dev/payments/${encodeURIComponent(paymentId)}/simulate`, {
    method: 'POST',
    body: { status },
  });
}

/** `VoucherResponse` (ADR-0010): el `token` es una credencial al portador; no se persiste. */
export interface Voucher {
  readonly voucherId: string;
  readonly jti: string;
  readonly bookingId: string;
  readonly shortCode: string;
  readonly status: 'ISSUED' | 'REVOKED' | 'CONSUMED' | 'EXPIRED';
  readonly issuedAt: string;
  readonly expiresAt: string;
  /** Fecha local (America/Bogota) en que el QR es válido. */
  readonly validDate: string;
  readonly token: string | null;
  readonly pdfStatus: 'READY' | 'GENERATING';
  /** Prefirmada por 5 min: se pide de nuevo al descargar (ADR-0017 §5). */
  readonly pdfUrl: string | null;
  readonly pdfUrlExpiresAt?: string | null;
}

export function getVoucher(
  http: HttpClient,
  bookingId: string,
  signal?: AbortSignal,
): Promise<Voucher> {
  return http.request<Voucher>(
    `/v1/bookings/${encodeURIComponent(bookingId)}/voucher`,
    signal ? { signal } : {},
  );
}
