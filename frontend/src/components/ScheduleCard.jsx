import { useNavigate } from 'react-router-dom'
import StatusBadge from './StatusBadge.jsx'

function formatCadence(schedule) {
  const { cadence, timeHour, timeMinute, dayOfWeek, dayOfMonth } = schedule
  const time = `${String(timeHour).padStart(2, '0')}:${String(timeMinute).padStart(2, '0')}`
  if (cadence === 'DAILY') return `Daily at ${time}`
  if (cadence === 'WEEKLY') return `Weekly on ${dayOfWeek} at ${time}`
  if (cadence === 'MONTHLY') return `Monthly on day ${dayOfMonth} at ${time}`
  return cadence
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(undefined, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
  })
}

export default function ScheduleCard({ schedule, onPause, onResume, onDelete }) {
  const navigate = useNavigate()
  const isPaused = schedule.status === 'PAUSED'

  return (
    <div className="schedule-card">
      <div className="schedule-card-header">
        <span className="schedule-card-title">{schedule.searchTerms}</span>
        <StatusBadge status={schedule.status} />
      </div>
      <div className="schedule-card-meta">
        <span>{formatCadence(schedule)}</span>
        {schedule.nextRunAt && !isPaused && (
          <span>Next run: {formatDate(schedule.nextRunAt)}</span>
        )}
        {schedule.recentRuns?.length > 0 && (
          <span>{schedule.recentRuns.length} run{schedule.recentRuns.length !== 1 ? 's' : ''}</span>
        )}
      </div>
      <div className="schedule-card-actions">
        <button onClick={() => navigate(`/schedules/${schedule.scheduleId}`)}>
          View podcasts
        </button>
        {isPaused
          ? <button onClick={() => onResume(schedule.scheduleId)}>Resume</button>
          : <button onClick={() => onPause(schedule.scheduleId)}>Pause</button>
        }
        <button className="danger" onClick={() => onDelete(schedule.scheduleId)}>
          Delete
        </button>
      </div>
    </div>
  )
}
