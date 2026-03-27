const API_URL = import.meta.env.VITE_API_URL ?? '/api'

let _getToken = null

export const setTokenProvider = (fn) => {
  _getToken = fn
}

async function authFetch(path, options = {}) {
  const token = await _getToken()
  const res = await fetch(API_URL + path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
      Authorization: `Bearer ${token}`,
    },
  })
  return res
}

async function json(res) {
  if (!res.ok) {
    let msg = res.statusText
    try {
      const body = await res.json()
      msg = body.error ?? body.message ?? msg
    } catch {}
    const err = new Error(msg)
    err.status = res.status
    throw err
  }
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

// ─── Podcast API ─────────────────────────────────────────────────────────────

export async function createPodcast(query) {
  return json(await authFetch('/podcast', {
    method: 'POST',
    body: JSON.stringify({ query }),
  }))
}

export async function getPodcastStatus(workflowId) {
  return json(await authFetch(`/podcast/${workflowId}/status`))
}

export async function getPodcastScript(workflowId) {
  return json(await authFetch(`/podcast/${workflowId}/script`))
}

export async function getPodcastAudioBlob(workflowId) {
  const token = await _getToken()
  const res = await fetch(API_URL + `/podcast/${workflowId}/audio`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error('Audio not available: ' + res.status)
  return res.blob()
}

// ─── Schedules API ───────────────────────────────────────────────────────────

export async function listSchedules() {
  return json(await authFetch('/schedules'))
}

export async function getSchedule(scheduleId) {
  return json(await authFetch(`/schedules/${scheduleId}`))
}

export async function createSchedule(req) {
  return json(await authFetch('/schedules', {
    method: 'POST',
    body: JSON.stringify(req),
  }))
}

export async function updateSchedule(scheduleId, req) {
  return json(await authFetch(`/schedules/${scheduleId}`, {
    method: 'PUT',
    body: JSON.stringify(req),
  }))
}

export async function pauseSchedule(scheduleId) {
  return json(await authFetch(`/schedules/${scheduleId}/pause`, { method: 'PATCH' }))
}

export async function resumeSchedule(scheduleId) {
  return json(await authFetch(`/schedules/${scheduleId}/resume`, { method: 'PATCH' }))
}

export async function deleteSchedule(scheduleId) {
  return json(await authFetch(`/schedules/${scheduleId}`, { method: 'DELETE' }))
}

export async function getScheduleRuns(scheduleId) {
  return json(await authFetch(`/schedules/${scheduleId}/runs`))
}
