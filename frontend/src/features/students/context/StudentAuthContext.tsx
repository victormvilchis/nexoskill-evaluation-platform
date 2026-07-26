import { createContext, useCallback, useContext, useEffect, useMemo, useState, type PropsWithChildren } from 'react'
import { getCurrentStudent, studentLogin, studentLogout } from '../api/studentApi'
import { STUDENT_AUTH_INVALID_EVENT } from '../../../shared/api/apiClient'
import type { StudentIdentity } from '../../../shared/types/students'

interface StudentAuthValue {
  student: StudentIdentity | null
  loading: boolean
  login: (organizationCode: string, email: string, password: string) => Promise<StudentIdentity>
  logout: () => Promise<void>
  refresh: () => Promise<void>
}

const StudentAuthContext = createContext<StudentAuthValue | undefined>(undefined)
const SESSION_CHECK_INTERVAL = 60_000

export function StudentAuthProvider({ children }: PropsWithChildren) {
  const [student, setStudent] = useState<StudentIdentity | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      const response = await getCurrentStudent()
      setStudent(response.student)
    } catch {
      setStudent(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  useEffect(() => {
    const invalidate = () => setStudent(null)
    window.addEventListener(STUDENT_AUTH_INVALID_EVENT, invalidate)
    return () => window.removeEventListener(STUDENT_AUTH_INVALID_EVENT, invalidate)
  }, [])

  useEffect(() => {
    if (!student) return
    const verify = () => { void refresh() }
    const interval = window.setInterval(verify, SESSION_CHECK_INTERVAL)
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') verify()
    }
    document.addEventListener('visibilitychange', handleVisibility)
    return () => {
      window.clearInterval(interval)
      document.removeEventListener('visibilitychange', handleVisibility)
    }
  }, [refresh, student?.publicId])

  const login = useCallback(async (organizationCode: string, email: string, password: string) => {
    const response = await studentLogin(organizationCode, email, password)
    setStudent(response.student)
    return response.student
  }, [])

  const logout = useCallback(async () => {
    try { await studentLogout() } finally { setStudent(null) }
  }, [])

  const value = useMemo(() => ({ student, loading, login, logout, refresh }), [student, loading, login, logout, refresh])
  return <StudentAuthContext.Provider value={value}>{children}</StudentAuthContext.Provider>
}

export function useStudentAuth() {
  const context = useContext(StudentAuthContext)
  if (!context) throw new Error('useStudentAuth debe utilizarse dentro de StudentAuthProvider')
  return context
}
