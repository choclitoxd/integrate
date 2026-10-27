import { MyBookingsSection } from '../components/MyBookingsSection';

/**
 * "Mis experiencias" como pantalla propia, con su pestaña en la barra de navegación (ADR-0023 §2,
 * enmendado): las reservas del turista, próximas y realizadas, con acceso al voucher y al QR.
 */
export function MyExperiencesPage() {
  return (
    <>
      <title>Mis experiencias · Intégrate</title>
      <h1 className="text-3xl leading-tight font-bold text-balance text-brand">Mis experiencias</h1>
      <p className="mt-2 text-ink-muted">Tus reservas y sus vouchers, en un solo lugar.</p>
      <MyBookingsSection />
    </>
  );
}
