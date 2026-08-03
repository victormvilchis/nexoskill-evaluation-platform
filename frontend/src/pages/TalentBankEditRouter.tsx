import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getTalent } from '../features/talent-bank/api/talentBankApi'
import { LoadingScreen } from '../shared/components/LoadingScreen'
import type { TalentType } from '../shared/types/talentBank'
import { AcademyTalentEditorPage } from './AcademyTalentEditorPage'
import { StudentEditorPage } from './StudentEditorPage'

export function TalentBankEditRouter() {
  const { publicId } = useParams()
  const [type, setType] = useState<TalentType>()
  const [error, setError] = useState<string>()
  useEffect(() => {
    if (!publicId) return
    getTalent(publicId).then((value) => setType(value.talentType)).catch(() => setError('No fue posible cargar el talento.'))
  }, [publicId])
  if (error) return <main className="content-page"><div className="error-message">{error}</div></main>
  if (!type) return <LoadingScreen />
  return type === 'ACADEMY' ? <AcademyTalentEditorPage mode="edit" /> : <StudentEditorPage mode="edit" workspace="talent-bank" />
}
