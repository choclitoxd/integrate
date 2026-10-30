import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';

import { useOfflineStatus } from '@/shared/offline';
import { useSession } from '@/shared/session';
import { Button, Icon } from '@/shared/ui';

import { VerdictBanner } from '../components/VerdictBanner';
import { decodeQrFromImageData } from '../qrDecoder';
import { validateAndRedeemQrToken, type RedemptionResult } from '../redemptionEngine';

/** Ancho máximo del fotograma que se decodifica por software: acota el costo en teléfonos de gama baja. */
const SOFTWARE_DECODE_MAX_WIDTH = 640;
/** Pausa entre lecturas de la cámara (≈ 4 fotogramas por segundo). */
const SCAN_INTERVAL_MS = 250;

interface BarcodeDetectorLike {
  detect(source: HTMLVideoElement): Promise<readonly { readonly rawValue?: string }[]>;
}

type BarcodeDetectorConstructor = new (options: {
  readonly formats: readonly string[];
}) => BarcodeDetectorLike;

/**
 * Vista de escaneo QR en campo con verificación criptográfica RS256/Ed25519 en Web Crypto API
 * y bloqueo atómico anti-doble uso (T-2.10, T-2.11, ADR-0037 §2-§3, RNF-05, RNF-14, RNF-18).
 */
export function QrScannerPage() {
  const session = useSession();
  const validatedBy = session.status === 'authenticated' ? session.user.id : 'link-manager';
  const { online, rosterCount } = useOfflineStatus();
  const [tokenInput, setTokenInput] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [cameraActive, setCameraActive] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [result, setResult] = useState<RedemptionResult | null>(null);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const stopCamera = () => {
    if (streamRef.current) {
      for (const track of streamRef.current.getTracks()) {
        track.stop();
      }
      streamRef.current = null;
    }
    setCameraActive(false);
  };

  useEffect(() => {
    return () => {
      if (streamRef.current) {
        for (const track of streamRef.current.getTracks()) {
          track.stop();
        }
        streamRef.current = null;
      }
    };
  }, []);

  const handleVerifyToken = async (rawToken: string) => {
    const trimmed = rawToken.trim();
    if (!trimmed || submitting) return;
    setSubmitting(true);
    try {
      const verdict = await validateAndRedeemQrToken(trimmed, validatedBy);
      setResult(verdict);
      stopCamera();
    } finally {
      setSubmitting(false);
    }
  };

  const startCamera = async () => {
    setCameraError(null);
    setResult(null);
    const mediaDevices = (globalThis.navigator as { mediaDevices?: MediaDevices }).mediaDevices;
    if (!mediaDevices || typeof mediaDevices.getUserMedia !== 'function') {
      setCameraError(
        'La cámara no está disponible en este navegador. Usa el canje manual por código de 8 caracteres.',
      );
      return;
    }

    try {
      const stream = await mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      });
      streamRef.current = stream;
      setCameraActive(true);
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play().catch(() => undefined);
      }

      // `BarcodeDetector` nativo cuando existe; si no (Safari/iOS, Firefox), `jsqr` sobre un canvas de trabajo.
      const DetectorCtor = (
        globalThis as unknown as { BarcodeDetector?: BarcodeDetectorConstructor }
      ).BarcodeDetector;
      const detector = DetectorCtor ? new DetectorCtor({ formats: ['qr_code'] }) : null;
      const canvas = detector ? null : document.createElement('canvas');
      const context = canvas?.getContext('2d', { willReadFrequently: true }) ?? null;
      if (!detector && !context) {
        setCameraError(
          'Este navegador no puede leer códigos QR con la cámara. Pega el token o usa la contingencia manual.',
        );
        return;
      }

      const readFrame = async (video: HTMLVideoElement): Promise<string | null> => {
        if (detector) {
          return (await detector.detect(video))[0]?.rawValue ?? null;
        }
        if (!canvas || !context || video.videoWidth === 0) return null;
        const scale = Math.min(1, SOFTWARE_DECODE_MAX_WIDTH / video.videoWidth);
        canvas.width = Math.round(video.videoWidth * scale);
        canvas.height = Math.round(video.videoHeight * scale);
        context.drawImage(video, 0, 0, canvas.width, canvas.height);
        const frame = context.getImageData(0, 0, canvas.width, canvas.height);
        return decodeQrFromImageData(frame.data, frame.width, frame.height);
      };

      const scanLoop = async () => {
        const video = videoRef.current;
        if (!streamRef.current || !video) return;
        try {
          const value = await readFrame(video);
          if (value) {
            await handleVerifyToken(value);
            return;
          }
        } catch {
          // Continúa el ciclo de lectura óptica mientras la cámara siga activa.
        }
        window.setTimeout(() => {
          void scanLoop();
        }, SCAN_INTERVAL_MS);
      };
      void scanLoop();
    } catch {
      setCameraError(
        'No se pudo acceder a la cámara trasera. Verifica los permisos o utiliza la contingencia manual.',
      );
      stopCamera();
    }
  };

  return (
    <>
      <title>Validar QR en campo · Intégrate</title>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-3xl font-bold text-brand">Validar QR en campo</h1>
        <span
          role="status"
          className={`inline-flex min-h-9 items-center gap-1.5 rounded-full border-2 px-3 text-sm font-bold ${
            online
              ? 'border-success-border bg-success-bg text-success-text'
              : 'border-warning-border bg-warning-bg text-warning-text'
          }`}
        >
          <Icon name={online ? 'wifi' : 'wifiOff'} size={18} />
          {online ? 'En línea' : 'Validación local sin red'}
        </span>
      </div>

      <p className="mt-2 text-ink-muted">
        Verifica la firma digital del voucher del visitante y registra su asistencia en este
        teléfono ({rosterCount} {rosterCount === 1 ? 'voucher cargado' : 'vouchers cargados'} en el
        padrón).
      </p>

      {rosterCount === 0 && (
        <p
          role="status"
          className="mt-4 flex items-start gap-2 rounded-xl border-2 border-warning-border bg-warning-bg px-4 py-3 text-sm font-semibold text-warning-text"
        >
          <Icon name="alert" size={20} className="mt-0.5 shrink-0" />
          <span>
            Aún no has descargado el padrón de la jornada en este dispositivo. Regresa al panel de
            jornada para descargarlo antes de salir de cobertura.
          </span>
        </p>
      )}

      {result !== null && (
        <VerdictBanner
          result={result}
          onReset={() => {
            setResult(null);
            setTokenInput('');
          }}
        />
      )}

      <section
        aria-labelledby="camara-qr-heading"
        className="mt-6 rounded-xl border-2 border-border-subtle bg-surface px-4 py-4"
      >
        <div className="flex items-center justify-between gap-2">
          <h2 id="camara-qr-heading" className="text-lg font-bold text-ink">
            Lector óptico de cámara
          </h2>
          <Icon name="qr" size={22} className="text-brand" />
        </div>
        <p className="mt-1 text-sm text-ink-muted">
          Apunta la cámara trasera al código QR impreso o en la pantalla del visitante.
        </p>

        {cameraActive && (
          <div className="mt-3 overflow-hidden rounded-lg border-2 border-brand bg-canvas">
            <video
              ref={videoRef}
              playsInline
              muted
              aria-label="Vista previa de cámara para escanear código QR"
              className="aspect-square w-full object-cover"
            />
          </div>
        )}

        {cameraError !== null && (
          <p
            role="alert"
            className="mt-3 rounded-lg border-2 border-warning-border bg-warning-bg px-3 py-2 text-sm font-semibold text-warning-text"
          >
            {cameraError}
          </p>
        )}

        <div className="mt-4">
          {!cameraActive ? (
            <Button type="button" onClick={() => void startCamera()}>
              Activar cámara trasera
            </Button>
          ) : (
            <Button type="button" variant="secondary" onClick={stopCamera}>
              Detener cámara
            </Button>
          )}
        </div>
      </section>

      <section
        aria-labelledby="token-qr-heading"
        className="mt-6 rounded-xl border-2 border-border-subtle bg-surface px-4 py-4"
      >
        <h2 id="token-qr-heading" className="text-lg font-bold text-ink">
          Verificación criptográfica de token QR
        </h2>
        <p className="mt-1 text-sm text-ink-muted">
          Si utilizas un lector externo o copias el contenido del QR, pégalo aquí para verificar su
          firma digital RS256.
        </p>

        <form
          className="mt-3 space-y-3"
          onSubmit={(event) => {
            event.preventDefault();
            void handleVerifyToken(tokenInput);
          }}
        >
          <div>
            <label htmlFor="qr-token-input" className="block text-base font-semibold text-ink">
              Token QR (JWS compacto)
            </label>
            <textarea
              id="qr-token-input"
              rows={3}
              value={tokenInput}
              onChange={(event) => {
                setTokenInput(event.target.value);
              }}
              placeholder="eyJhbGciOiJSUzI1NiIsImtpZCI6..."
              className="mt-1.5 w-full rounded-lg border-2 border-border-strong bg-canvas px-3.5 py-2.5 font-mono text-sm text-ink"
            />
          </div>
          <Button type="submit" disabled={submitting || tokenInput.trim().length === 0}>
            {submitting ? 'Verificando firma…' : 'Verificar firma y registrar ingreso'}
          </Button>
        </form>
      </section>

      <div className="mt-6 rounded-xl border-2 border-border-subtle bg-canvas px-4 py-4">
        <h2 className="text-base font-bold text-ink">
          ¿El código QR está dañado o no se puede leer?
        </h2>
        <p className="mt-1 text-sm text-ink-muted">
          Utiliza la contingencia manual por código alfanumérico de 8 caracteres o busca al titular
          en el padrón descargado.
        </p>
        <Link
          to="/gestor/contingencia"
          className="mt-3 inline-flex min-h-11 w-full items-center justify-center rounded-lg border-2 border-brand bg-canvas px-5 py-3 text-base font-bold text-brand hover:bg-brand-soft"
        >
          Ir a contingencia manual (código de 8 caracteres)
        </Link>
      </div>
    </>
  );
}
