import { useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuth0 } from '@auth0/auth0-react'
import { setTokenProvider } from './api.js'
import Header from './components/Header.jsx'
import Login from './pages/Login.jsx'
import Callback from './pages/Callback.jsx'
import Schedules from './pages/Schedules.jsx'
import ScheduleDetail from './pages/ScheduleDetail.jsx'
import Podcast from './pages/Podcast.jsx'

function ProtectedRoute({ children }) {
  const { isAuthenticated, isLoading, loginWithRedirect } = useAuth0()

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      loginWithRedirect({ appState: { returnTo: window.location.pathname } })
    }
  }, [isLoading, isAuthenticated, loginWithRedirect])

  if (isLoading) return <div className="loading-spinner">Loading…</div>
  if (!isAuthenticated) return null
  return children
}

export default function App() {
  const { getIdTokenClaims } = useAuth0()

  useEffect(() => {
    setTokenProvider(() => getIdTokenClaims().then(c => c?.__raw ?? ''))
  }, [getIdTokenClaims])

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/callback" element={<Callback />} />
        <Route
          path="/*"
          element={
            <ProtectedRoute>
              <div className="app-shell">
                <Header />
                <Routes>
                  <Route path="/" element={<Navigate to="/schedules" replace />} />
                  <Route path="/schedules" element={<Schedules />} />
                  <Route path="/schedules/:id" element={<ScheduleDetail />} />
                  <Route path="/podcast/:id" element={<Podcast />} />
                  <Route path="*" element={<Navigate to="/schedules" replace />} />
                </Routes>
              </div>
            </ProtectedRoute>
          }
        />
      </Routes>
    </BrowserRouter>
  )
}
