import { useEffect, useState, type PropsWithChildren } from 'react'
import {
  PLATFORM_REQUEST_FAILURE_EVENT,
  type PlatformRequestFailureDetail
} from '../api/apiClient'
import { ConfirmDialog } from './ConfirmDialog'
import { useToast } from './ToastProvider'

interface BlockingFailure {
  message: string
}

export function PlatformErrorProvider({ children }: PropsWithChildren) {
  const toast = useToast()
  const [blockingFailure, setBlockingFailure] = useState<BlockingFailure>()

  useEffect(() => {
    function handleFailure(event: Event) {
      const detail = (event as CustomEvent<PlatformRequestFailureDetail>).detail
      if (!detail) return
      if (detail.blocking) {
        setBlockingFailure({ message: detail.message })
        return
      }
      toast.error('No fue posible completar la operación', detail.message)
    }

    window.addEventListener(PLATFORM_REQUEST_FAILURE_EVENT, handleFailure)
    return () => window.removeEventListener(PLATFORM_REQUEST_FAILURE_EVENT, handleFailure)
  }, [toast])

  useEffect(() => {
    document.body.classList.toggle('platform-connection-blocked', Boolean(blockingFailure))
    return () => document.body.classList.remove('platform-connection-blocked')
  }, [blockingFailure])

  function goBack() {
    setBlockingFailure(undefined)
    if (window.history.length > 1) {
      window.history.back()
      return
    }
    window.location.assign(import.meta.env.BASE_URL)
  }

  return (
    <>
      {children}
      <ConfirmDialog
        open={Boolean(blockingFailure)}
        title="No fue posible conectar con la plataforma"
        description={blockingFailure?.message ?? ''}
        confirmLabel="Reintentar"
        cancelLabel="Regresar"
        onCancel={goBack}
        onConfirm={() => window.location.reload()}
      />
    </>
  )
}
