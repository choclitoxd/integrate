import type { CheckoutData } from './api';

const WOMPI_CHECKOUT = 'https://checkout.wompi.co/p/';

/**
 * URL del Web Checkout de Wompi (ADR-0016 §3): la pasarela corre en su dominio y no se carga
 * JavaScript de terceros en el origen de la PWA. `redirect-url` vuelve a la reserva.
 */
export function wompiCheckoutUrl(
  checkout: Extract<CheckoutData, { type: 'WOMPI' }>,
  redirectUrl: string,
): string {
  const params = new URLSearchParams({
    'public-key': checkout.publicKey,
    currency: checkout.currency,
    'amount-in-cents': String(checkout.amountInCents),
    reference: checkout.reference,
    'signature:integrity': checkout.integritySignature,
    'redirect-url': redirectUrl,
  });
  return `${WOMPI_CHECKOUT}?${params.toString()}`;
}

/** Navegación fuera de la PWA, aislada en un objeto para poder observarla en las pruebas. */
export const externalNavigation = {
  assign(url: string): void {
    window.location.assign(url);
  },
};
