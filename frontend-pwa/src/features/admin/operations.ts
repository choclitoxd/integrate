import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useHttpClient } from '@/shared/api';
import type { Page } from './api';

type Filters = Readonly<Record<string, string>>;
type Module = 'incidents' | 'payout-batches';

export interface Incident {
  id: string;
  status: 'OPEN' | 'IN_REVIEW' | 'RESOLVED' | 'DISMISSED';
  type: string;
  severity: string;
  createdAt: string;
  bookingId: string;
  voucherId: string;
  financialImpactCop: number | null;
  resolutionNotes: string | null;
  resolvedBy: string | null;
  resolvedAt: string | null;
}

export interface Batch {
  id: string;
  associationName: string;
  status: 'OPEN' | 'SUBMITTED' | 'FAILED' | 'PAID';
  grossCop: number;
  gatewayFeeCop: number;
  platformFeeCop: number;
  netCommunityCop: number;
  disbursementCount: number;
  createdAt: string;
  submittedAt: string | null;
  paidAt: string | null;
  failureReason: string | null;
  receiptId: string | null;
}

export interface PayoutDetail {
  batch: Batch;
  disbursements: readonly { id: string; status: string; amountCop: number }[];
}

export interface Reconciliation {
  batch: Batch;
  lookupStatus: 'CHECKED' | 'UNAVAILABLE' | 'ALREADY_CLOSED';
}

const key = (module: Module) => ['me', 'admin', module] as const;
const path = (module: Module) => `/v1/admin/${module}`;

export function useOperationList<T>(module: Module, filters: Filters, page: number, size = 20) {
  const http = useHttpClient();
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  for (const [name, value] of Object.entries(filters)) {
    if (value) query.set(name, value);
  }
  return useQuery({
    queryKey: [...key(module), 'list', filters, page, size],
    queryFn: ({ signal }) => http.request<Page<T>>(`${path(module)}?${query}`, { signal }),
  });
}

export function useOperationDetail<T>(module: Module, id: string) {
  const http = useHttpClient();
  return useQuery({
    queryKey: [...key(module), 'detail', id],
    enabled: !!id,
    queryFn: ({ signal }) =>
      http.request<T>(`${path(module)}/${encodeURIComponent(id)}`, { signal }),
  });
}

export function useOperationAction<T>(module: Module, id: string) {
  const http = useHttpClient();
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({
      action,
      notes,
    }: {
      action: 'take' | 'resolve' | 'dismiss' | 'reconcile';
      notes?: string;
    }) =>
      http.request<T>(`${path(module)}/${encodeURIComponent(id)}/${action}`, {
        method: 'POST',
        ...(notes === undefined ? {} : { body: { resolutionNotes: notes } }),
      }),
    onSettled: () => client.invalidateQueries({ queryKey: key(module) }),
  });
}

export function useReceipt(id: string) {
  const http = useHttpClient();
  return useMutation({
    mutationFn: () =>
      http.request<{ url: string }>(`${path('payout-batches')}/${encodeURIComponent(id)}/receipt`),
  });
}
