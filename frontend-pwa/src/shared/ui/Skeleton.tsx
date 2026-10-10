interface SkeletonProps {
  readonly className?: string;
}

/** Bloque de carga estático (sin shimmer); el estado se anuncia aparte con texto accesible. */
export function Skeleton({ className = '' }: SkeletonProps) {
  return <div aria-hidden="true" className={`rounded-md bg-surface-subtle ${className}`} />;
}
