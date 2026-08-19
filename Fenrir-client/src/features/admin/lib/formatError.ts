import { ApiError } from '@/features/admin/api/adminApi'

export function formatError(err: unknown, fallback: string): string {
  return err instanceof ApiError || err instanceof Error ? err.message : fallback
}
