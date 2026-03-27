import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'

export default function Callback() {
  const { isAuthenticated, isLoading, appState } = useAuth0()
  const navigate = useNavigate()

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      const returnTo = appState?.returnTo ?? '/schedules'
      navigate(returnTo, { replace: true })
    }
  }, [isAuthenticated, isLoading, appState, navigate])

  return (
    <div className="callback-page">
      Authenticating…
    </div>
  )
}
