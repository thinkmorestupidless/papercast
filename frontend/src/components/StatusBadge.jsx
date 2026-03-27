const LABELS = {
  ACTIVE: 'Active',
  PAUSED: 'Paused',
  DELETED: 'Deleted',
  CREATED: 'Created',
  SEARCHING: 'Searching',
  SUMMARISING: 'Summarising',
  SCRIPTING: 'Scripting',
  COMPLETE: 'Complete',
  FAILED: 'Failed',
}

export default function StatusBadge({ status }) {
  const label = LABELS[status] ?? status
  const cls = status?.toLowerCase() ?? ''
  return <span className={`status-badge ${cls}`}>{label}</span>
}
