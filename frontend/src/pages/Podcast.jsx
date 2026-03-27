import { useState, useEffect, useRef } from 'react'
import { useParams, Link } from 'react-router-dom'
import { getPodcastStatus, getPodcastScript } from '../api.js'
import StatusBadge from '../components/StatusBadge.jsx'
import AudioPlayer from '../components/AudioPlayer.jsx'

const STEPS = [
  { key: 'CREATED',     label: 'Created' },
  { key: 'SEARCHING',   label: 'Searching' },
  { key: 'SUMMARISING', label: 'Summarising' },
  { key: 'SCRIPTING',   label: 'Scripting' },
  { key: 'COMPLETE',    label: 'Complete' },
]

const STEP_INDEX = Object.fromEntries(STEPS.map((s, i) => [s.key, i]))

function ProgressSteps({ status }) {
  const current = STEP_INDEX[status] ?? 0
  return (
    <div className="progress-steps">
      {STEPS.map((step, i) => {
        const isDone = i < current || status === 'COMPLETE'
        const isActive = i === current && status !== 'COMPLETE' && status !== 'FAILED'
        return (
          <div
            key={step.key}
            className={`progress-step ${isDone ? 'done' : ''} ${isActive ? 'active' : ''}`}
          >
            <div className="progress-dot">
              {isDone ? '✓' : i + 1}
            </div>
            <div className="progress-label">{step.label}</div>
          </div>
        )
      })}
    </div>
  )
}

export default function Podcast() {
  const { id } = useParams()
  const [status, setStatus] = useState(null)
  const [script, setScript] = useState(null)
  const [error, setError] = useState(null)
  const pollRef = useRef(null)

  useEffect(() => {
    async function poll() {
      try {
        const s = await getPodcastStatus(id)
        setStatus(s)
        if (s.status === 'COMPLETE') {
          clearInterval(pollRef.current)
          const sc = await getPodcastScript(id)
          setScript(sc.script)
        } else if (s.status === 'FAILED') {
          clearInterval(pollRef.current)
        }
      } catch (err) {
        setError(err.message)
        clearInterval(pollRef.current)
      }
    }

    poll()
    pollRef.current = setInterval(poll, 3000)
    return () => clearInterval(pollRef.current)
  }, [id])

  return (
    <main className="page">
      <Link to="/schedules" className="back-link">← Back to schedules</Link>

      <div className="page-toolbar">
        <div>
          <h1 style={{ marginBottom: '0.375rem', fontFamily: 'var(--font-mono)', fontSize: '1rem' }}>
            {id}
          </h1>
          {status && <StatusBadge status={status.status} />}
        </div>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {status && (
        <>
          <ProgressSteps status={status.status} />

          {status.status !== 'CREATED' && (
            <div style={{ fontSize: '0.875rem', opacity: 0.7, marginBottom: '1rem', display: 'flex', gap: '1.5rem' }}>
              {status.papersFound > 0 && (
                <span>📄 {status.papersFound} paper{status.papersFound !== 1 ? 's' : ''} found</span>
              )}
              {status.summariesGenerated > 0 && (
                <span>✍️ {status.summariesGenerated} summar{status.summariesGenerated !== 1 ? 'ies' : 'y'}</span>
              )}
            </div>
          )}

          {status.status === 'FAILED' && (
            <div className="error-banner">
              {status.errorMessage ?? 'Podcast generation failed.'}
            </div>
          )}
        </>
      )}

      {script && (
        <>
          <AudioPlayer workflowId={id} />

          <div className="script-section">
            <h2>Podcast script</h2>
            {script.topic && (
              <p style={{ opacity: 0.6, fontSize: '0.875rem', marginBottom: '1rem' }}>
                Topic: <strong>{script.topic}</strong>
              </p>
            )}

            <div className="script-block">
              <h3>Introduction</h3>
              <p>{script.introduction}</p>
            </div>

            {script.segments?.map((seg, i) => (
              <div key={i} className="script-block">
                <div className="script-segment-title">{seg.paperId}</div>
                <h3>{seg.paperTitle}</h3>
                <p>{seg.narrative}</p>
              </div>
            ))}

            <div className="script-block">
              <h3>Conclusion</h3>
              <p>{script.conclusion}</p>
            </div>
          </div>
        </>
      )}

      {!status && !error && (
        <div className="loading-spinner">Loading podcast status…</div>
      )}
    </main>
  )
}
