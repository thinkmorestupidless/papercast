import { useState } from 'react'
import { getPodcastAudioBlob } from '../api.js'

export default function AudioPlayer({ workflowId }) {
  const [audioUrl, setAudioUrl] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const loadAudio = async () => {
    setLoading(true)
    setError(null)
    try {
      const blob = await getPodcastAudioBlob(workflowId)
      setAudioUrl(URL.createObjectURL(blob))
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="audio-player">
      <h3>🔊 Audio</h3>
      {!audioUrl && (
        <>
          <p className="load-hint">
            Audio is generated on demand via ElevenLabs. Click below to generate and stream.
          </p>
          <button onClick={loadAudio} disabled={loading}>
            {loading ? 'Generating…' : 'Load audio'}
          </button>
        </>
      )}
      {error && <div className="error-banner" style={{ marginTop: '0.75rem' }}>{error}</div>}
      {audioUrl && (
        <audio controls autoPlay src={audioUrl}>
          Your browser does not support audio playback.
        </audio>
      )}
    </div>
  )
}
