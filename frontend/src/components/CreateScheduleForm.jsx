import { useState } from 'react'

const DAYS_OF_WEEK = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

const DEFAULTS = {
  searchTerms: '',
  cadence: 'DAILY',
  timeHour: 8,
  timeMinute: 0,
  dayOfWeek: 'MONDAY',
  dayOfMonth: 1,
}

export default function CreateScheduleForm({ onSubmit, onCancel, loading, error }) {
  const [form, setForm] = useState(DEFAULTS)

  const set = (field) => (e) => {
    const value = e.target.type === 'number' ? parseInt(e.target.value, 10) : e.target.value
    setForm(prev => ({ ...prev, [field]: value }))
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    const req = {
      searchTerms: form.searchTerms,
      cadence: form.cadence,
      timeHour: form.timeHour,
      timeMinute: form.timeMinute,
      dayOfWeek: form.cadence === 'WEEKLY' ? form.dayOfWeek : null,
      dayOfMonth: form.cadence === 'MONTHLY' ? form.dayOfMonth : null,
    }
    onSubmit(req)
  }

  return (
    <div className="modal-overlay" onClick={(e) => e.target === e.currentTarget && onCancel()}>
      <div className="modal">
        <h2>New podcast schedule</h2>
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="searchTerms">Search terms</label>
            <input
              id="searchTerms"
              type="text"
              value={form.searchTerms}
              onChange={set('searchTerms')}
              placeholder="e.g. black hole imaging, quantum computing"
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="cadence">Cadence</label>
            <select id="cadence" value={form.cadence} onChange={set('cadence')}>
              <option value="DAILY">Daily</option>
              <option value="WEEKLY">Weekly</option>
              <option value="MONTHLY">Monthly</option>
            </select>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label htmlFor="timeHour">Hour (0–23)</label>
              <input
                id="timeHour"
                type="number"
                min={0}
                max={23}
                value={form.timeHour}
                onChange={set('timeHour')}
              />
            </div>
            <div className="form-group">
              <label htmlFor="timeMinute">Minute (0–59)</label>
              <input
                id="timeMinute"
                type="number"
                min={0}
                max={59}
                value={form.timeMinute}
                onChange={set('timeMinute')}
              />
            </div>
          </div>

          {form.cadence === 'WEEKLY' && (
            <div className="form-group">
              <label htmlFor="dayOfWeek">Day of week</label>
              <select id="dayOfWeek" value={form.dayOfWeek} onChange={set('dayOfWeek')}>
                {DAYS_OF_WEEK.map(d => (
                  <option key={d} value={d}>{d.charAt(0) + d.slice(1).toLowerCase()}</option>
                ))}
              </select>
            </div>
          )}

          {form.cadence === 'MONTHLY' && (
            <div className="form-group">
              <label htmlFor="dayOfMonth">Day of month (1–28)</label>
              <input
                id="dayOfMonth"
                type="number"
                min={1}
                max={28}
                value={form.dayOfMonth}
                onChange={set('dayOfMonth')}
              />
            </div>
          )}

          {error && <div className="form-error">{error}</div>}

          <div className="modal-footer">
            <button type="button" onClick={onCancel}>Cancel</button>
            <button type="submit" className="primary" disabled={loading}>
              {loading ? 'Creating…' : 'Create schedule'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
