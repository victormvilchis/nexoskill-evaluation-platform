import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type PropsWithChildren
} from 'react'
import { useNavigate } from 'react-router-dom'
import {
  AUTH_INVALID_EVENT,
  PASSWORD_CHANGE_REQUIRED_EVENT,
  ORGANIZATION_CONTEXT_KEY,
  ORGANIZATION_CONTEXT_CHANGED_EVENT,
  type AuthInvalidEventDetail
} from '../../../shared/api/apiClient'
import type { CurrentUser } from '../../../shared/types/auth'
import {
  getCurrentUser,
  login as loginRequest,
  logout as logoutRequest
} from '../api/authApi'

interface AuthContextValue {
  user: CurrentUser | null
  loading: boolean
  login: (email: string, password: string) => Promise<CurrentUser>
  logout: () => Promise<void>
  refresh: () => Promise<void>
}

const MAX_TIMEOUT = 2_147_000_000
const SESSION_CHECK_INTERVAL = 30_000
const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: PropsWithChildren) {
  const navigate = useNavigate()
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(true)
  const expirationTimer = useRef<number | null>(null)

  const clearOrganizationContext = useCallback(() => {
    if (!window.localStorage.getItem(ORGANIZATION_CONTEXT_KEY)) return
    window.localStorage.removeItem(ORGANIZATION_CONTEXT_KEY)
    window.dispatchEvent(new Event(ORGANIZATION_CONTEXT_CHANGED_EVENT))
  }, [])

  const normalizeOrganizationContext = useCallback((current: CurrentUser | null) => {
    if (current?.roles.includes('ADMINISTRATOR')) return
    clearOrganizationContext()
  }, [clearOrganizationContext])

  const redirectToLogin = useCallback((reason: string) => {
    clearOrganizationContext()
    setUser(null)
    navigate(`/login?reason=${encodeURIComponent(reason)}`, { replace: true })
  }, [clearOrganizationContext, navigate])

  const refresh = useCallback(async () => {
    try {
      const response = await getCurrentUser()
      normalizeOrganizationContext(response.user)
      setUser(response.user)
    } catch {
      clearOrganizationContext()
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [clearOrganizationContext, normalizeOrganizationContext])

  useEffect(() => {
    function handleInvalidAuthentication(event: Event) {
      const detail = (event as CustomEvent<AuthInvalidEventDetail>).detail
      if (detail?.code === 'ACCESS_EXPIRED') {
        redirectToLogin('expired')
        return
      }
      if (detail?.code === 'TEMP_PASSWORD_EXPIRED') {
        redirectToLogin('temporary-password-expired')
        return
      }
      if (detail?.code === 'ACCOUNT_INACTIVE') {
        redirectToLogin('inactive')
        return
      }
      if (detail?.code === 'ACCOUNT_SUSPENDED') {
        redirectToLogin('suspended')
        return
      }
      if (detail?.code === 'ORGANIZATION_INACTIVE') {
        redirectToLogin('organization-inactive')
        return
      }
      if (detail?.code === 'ORGANIZATION_EXPIRED') {
        redirectToLogin('organization-expired')
        return
      }
      redirectToLogin('session')
    }

    function handlePasswordChangeRequired() {
      navigate('/change-password', { replace: true })
    }

    window.addEventListener(AUTH_INVALID_EVENT, handleInvalidAuthentication)
    window.addEventListener(
      PASSWORD_CHANGE_REQUIRED_EVENT,
      handlePasswordChangeRequired
    )
    return () => {
      window.removeEventListener(AUTH_INVALID_EVENT, handleInvalidAuthentication)
      window.removeEventListener(
        PASSWORD_CHANGE_REQUIRED_EVENT,
        handlePasswordChangeRequired
      )
    }
  }, [navigate, redirectToLogin])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    if (expirationTimer.current !== null) {
      window.clearTimeout(expirationTimer.current)
      expirationTimer.current = null
    }

    if (!user?.accessExpiresAt) return

    let cancelled = false
    const schedule = () => {
      if (cancelled) return
      const remaining = new Date(user.accessExpiresAt!).getTime() - Date.now()
      if (remaining <= 0) {
        redirectToLogin('expired')
        void getCurrentUser().catch(() => undefined)
        return
      }
      expirationTimer.current = window.setTimeout(
        schedule,
        Math.min(remaining, MAX_TIMEOUT)
      )
    }

    schedule()
    return () => {
      cancelled = true
      if (expirationTimer.current !== null) {
        window.clearTimeout(expirationTimer.current)
      }
    }
  }, [redirectToLogin, user?.accessExpiresAt])

  useEffect(() => {
    if (!user) return

    const verifySession = () => {
      void getCurrentUser()
        .then((response) => { normalizeOrganizationContext(response.user); setUser(response.user) })
        .catch(() => undefined)
    }

    const interval = window.setInterval(verifySession, SESSION_CHECK_INTERVAL)
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') verifySession()
    }
    document.addEventListener('visibilitychange', handleVisibility)

    return () => {
      window.clearInterval(interval)
      document.removeEventListener('visibilitychange', handleVisibility)
    }
  }, [normalizeOrganizationContext, user?.publicId])

  const login = useCallback(async (email: string, password: string) => {
    const response = await loginRequest(email, password)
    normalizeOrganizationContext(response.user)
    setUser(response.user)
    return response.user
  }, [normalizeOrganizationContext])

  const logout = useCallback(async () => {
    try {
      await logoutRequest()
    } finally {
      clearOrganizationContext()
      setUser(null)
    }
  }, [clearOrganizationContext])

  const value = useMemo(
    () => ({ user, loading, login, logout, refresh }),
    [user, loading, login, logout, refresh]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth debe utilizarse dentro de AuthProvider')
  }
  return context
}
