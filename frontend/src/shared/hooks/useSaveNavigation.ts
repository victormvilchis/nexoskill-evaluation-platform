import { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useToast } from '../components/ToastProvider'

interface SaveNavigationOptions {
  title: string
  message?: string
  state?: unknown
}

export function useSaveNavigation(listPath: string) {
  const navigate = useNavigate()
  const toast = useToast()

  return useCallback(({ title, message, state }: SaveNavigationOptions) => {
    toast.success(title, message)
    navigate(listPath, { replace: true, state })
  }, [listPath, navigate, toast])
}
