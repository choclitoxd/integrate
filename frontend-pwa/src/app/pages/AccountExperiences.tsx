import { lazy, Suspense } from 'react';

import { Skeleton } from '@/shared/ui';

/** Diferida: el módulo de reservas (con el QR) no viaja en el bundle inicial (ADR-0018 §1). */
const MyBookingsSection = lazy(async () => {
  const { MyBookingsSection: section } = await import('@/features/booking');
  return { default: section };
});

/**
 * "Mis experiencias" dentro de Mi cuenta (ADR-0023 §2). Se compone aquí, en `app/`, porque `booking` ya depende de
 * `auth`: que `auth` importara `booking` crearía un ciclo entre features.
 */
export function AccountExperiences() {
  return (
    <Suspense fallback={<Skeleton className="mt-8 h-48 w-full" />}>
      <MyBookingsSection />
    </Suspense>
  );
}
