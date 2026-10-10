import { useEffect, useState } from 'react';

/**
 * Cuenta regresiva anclada al servidor (ADR-0016 §2): `deadline` = instante monotónico en que llegó
 * la respuesta + `ttlSecondsRemaining`. Nunca se compara `expiresAt` con la hora del teléfono.
 */
export function remainingSeconds(deadline: number, now: number): number {
  return Math.max(0, Math.ceil((deadline - now) / 1000));
}

export type Milestone = 'running' | 'five' | 'one' | 'over';

/** Hitos que se anuncian al lector de pantalla; no cada segundo. */
export function milestoneOf(remaining: number): Milestone {
  if (remaining <= 0) return 'over';
  if (remaining <= 60) return 'one';
  if (remaining <= 300) return 'five';
  return 'running';
}

export function formatCountdown(remaining: number): string {
  const minutes = Math.floor(remaining / 60);
  const seconds = remaining % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

export const monotonicNow = (): number => performance.now();

/**
 * Segundos restantes, actualizados cada segundo. `receivedAt` es el instante monotónico en que llegó
 * la respuesta y `ttlSeconds` el `ttlSecondsRemaining` que traía.
 */
export function useServerCountdown(
  receivedAt: number,
  ttlSeconds: number,
  clock: () => number = monotonicNow,
): number {
  const deadline = receivedAt + ttlSeconds * 1000;
  const [now, setNow] = useState<number | null>(null);
  useEffect(() => {
    const id = setInterval(() => {
      setNow(clock());
    }, 1000);
    return () => {
      clearInterval(id);
    };
  }, [clock, deadline]);
  // Un `now` anterior a esta respuesta (de un ancla previa) no cuenta: se parte del plazo recibido.
  return remainingSeconds(deadline, now === null || now < receivedAt ? receivedAt : now);
}
