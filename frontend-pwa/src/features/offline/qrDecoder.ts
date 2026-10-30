/**
 * Decodificación de QR por software para navegadores sin `BarcodeDetector` (Safari/iOS, Firefox y muchos WebView de
 * Android): `jsqr` sobre los píxeles de un fotograma (ADR-0037 §2.1). Se importa de forma diferida para que solo se
 * descargue en el chunk del Gestor y únicamente cuando el navegador no trae el detector nativo.
 */
export async function decodeQrFromImageData(
  pixels: Uint8ClampedArray,
  width: number,
  height: number,
): Promise<string | null> {
  const { default: jsQR } = await import('jsqr');
  return jsQR(pixels, width, height, { inversionAttempts: 'dontInvert' })?.data ?? null;
}
