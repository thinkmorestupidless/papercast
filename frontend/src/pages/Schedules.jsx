import { useState, useEffect, useCallback } from 'react'
import { listSchedules, createSchedule, pauseSchedule, resumeSchedule, deleteSchedule } from '../api.js'
import ScheduleCard from '../components/ScheduleCard.jsx'
import CreateScheduleForm from '../components/CreateScheduleForm.jsx'

export default function Schedules() {
  const [schedules, setSchedules] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [formLoading, setFormLoading] = useState(false)
  const [formError, setFormError] = useState(null)

  const load = useCallback(async () => {
    try {
      const data = await listSchedules()
      setSchedules(data?.schedules ?? [])
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  const handleCreate = async (req) => {
    setFormLoading(true)
    setFormError(null)
    try {
      await createSchedule(req)
      setShowForm(false)
      await load()
    } catch (err) {
      setFormError(err.message)
    } finally {
      setFormLoading(false)
    }
  }

  const handlePause = async (id) => {
    try {
      await pauseSchedule(id)
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  const handleResume = async (id) => {
    try {
      await resumeSchedule(id)
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  const handleDelete = async (id) => {
    if (!confirm('Delete this schedule? This cannot be undone.')) return
    try {
      await deleteSchedule(id)
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  if (loading) return <div className="loading-spinner">Loading schedules…</div>

  return (
    <main className="page">
      <div className="page-toolbar">
        <h1>Podcast schedules</h1>
        <button className="primary" onClick={() => { setFormError(null); setShowForm(true) }}>
          + New schedule
        </button>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {schedules.length === 0 ? (
        <div className="empty-state">
          <div style={{ fontSize: '2.5rem' }}>🎙</div>
          <p>No schedules yet. Create one to start generating research podcasts.</p>
        </div>
      ) : (
        <div className="card-grid">
          {schedules.map(s => (
            <ScheduleCard
              key={s.scheduleId}
              schedule={s}
              onPause={handlePause}
              onResume={handleResume}
              onDelete={handleDelete}
            />
          ))}
        </div>
      )}

      {showForm && (
        <CreateScheduleForm
          onSubmit={handleCreate}
          onCancel={() => setShowForm(false)}
          loading={formLoading}
          error={formError}
        />
      )}
    </main>
  )
}
