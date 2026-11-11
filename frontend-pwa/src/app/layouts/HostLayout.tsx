import { useSession } from '@/shared/session';

import { AppShell } from './AppShell';
import { roleNavigation } from './roleNavigation';

export function HostLayout() {
  const session = useSession();
  return <AppShell navLabel="Navegación principal" navItems={roleNavigation(session)} homeTo="/" />;
}
