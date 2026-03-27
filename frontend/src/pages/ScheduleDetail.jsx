import { useState, useEffect } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { getSchedule, getScheduleRuns } from '../api.js'
import StatusBadge from '../components/StatusBadge.jsx'

function formatDateTime(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit'
  })
}

export default function ScheduleDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [schedule, setSchedule] = useState(null)
  const [runs, setRuns] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    async function load() {
      try {
        const [sched, runsData] = await Promise.all([
          getSchedule(id),
          getScheduleRuns(id),
        ])
        setSchedule(sched)
        const sorted = [...(runsData?.runs ?? [])].sort(
          (a, b) => new Date(b.triggeredAt) - new Date(a.triggeredAt)
        )
        setRuns(sorted)
      } catch (err) {
        if (err.status === 404) navigate('/schedules', { replace: true })
        else setError(err.message)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [id, navigate])

  if (loading) return <div className="loading-spinner">Loading…</div>
  if (!schedule) return null

  return (
    <main className="page">
      <Link to="/schedules" className="back-link">← Back to schedules</Link>

      <div className="page-toolbar">
        <div>
          <h1 style={{ marginBottom: '0.375rem' }}>{schedule.searchTerms}</h1>
          <StatusBadge status={schedule.status} />
        </div>
      </div>

      {error && <div className="error-banner">{error}</div>}

      <h2 style={{ marginBottom: '0.5rem', fontSize: '1rem' }}>
        Podcast runs {runs.length > 0 && `(${runs.length})`}
      </h2>

      {runs.length === 0 ? (
        <div className="empty-state">
          <p>No runs yet. Podcasts are created when the schedule fires.</p>
        </div>
      ) : (
        <div className="runs-list">
          {runs.map(run => (
            <div key={run.workflowId} className="run-item">
              <div>
                <Link to={`/podcast/${run.workflowId}`}>
                  Podcast {run.workflowId.slice(0, 8)}…
                </Link>
                <div className="run-item-time">{formatDateTime(run.triggeredAt)}</div>
              </div>
              <Link to={`/podcast/${run.workflowId}`}>
                <button style={{ fontSize: '0.8125rem', height: '2rem' }}>View →</button>
              </Link>
            </div>
          ))}
        </div>
      )}
    </main>
  )
}
