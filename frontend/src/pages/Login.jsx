import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'

export default function Login() {
  const { loginWithRedirect, isAuthenticated, isLoading } = useAuth0()
  const navigate = useNavigate()

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      navigate('/schedules', { replace: true })
    }
  }, [isAuthenticated, isLoading, navigate])

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">🎙</div>
        <h1>Papercast</h1>
        <p>
          Search scientific papers, generate AI summaries, and create audio podcasts —
          all on a schedule.
        </p>
        <div className="login-actions">
          <button
            className="primary"
            onClick={() => loginWithRedirect()}
          >
            Log in
          </button>
          <button
            onClick={() => loginWithRedirect({ authorizationParams: { screen_hint: 'signup' } })}
          >
            Sign up
          </button>
        </div>
      </div>
    </div>
  )
}
