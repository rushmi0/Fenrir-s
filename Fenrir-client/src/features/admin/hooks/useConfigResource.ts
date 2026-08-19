import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '@/features/admin/api/adminApi'

interface ConfigResource<T> {
  value: T | null
  loading: boolean
  saving: boolean
  error: string | null
  saved: boolean
  setValue: (value: T) => void
  save: () => Promise<void>
}

function describeError(err: unknown): string {
  return err instanceof ApiError || err instanceof Error ? err.message : 'something went wrong'
}

/** Shared fetch-edit-save lifecycle for the Relay/Policy/System config panels. */
export function useConfigResource<T>(load: () => Promise<T>, persist: (value: T) => Promise<T>): ConfigResource<T> {
  const [value, setValue] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    load()
      .then(setValue)
      .catch((err: unknown) => setError(describeError(err)))
      .finally(() => setLoading(false))
    // Intentionally runs once on mount only - `load` is a fresh closure per render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const save = useCallback(async () => {
    if (!value) return
    setSaving(true)
    setError(null)
    setSaved(false)
    try {
      const result = await persist(value)
      setValue(result)
      setSaved(true)
    } catch (err) {
      setError(describeError(err))
    } finally {
      setSaving(false)
    }
  }, [value, persist])

  return { value, loading, saving, error, saved, setValue, save }
}
