import { ref } from 'vue'
import { ElMessage } from 'element-plus'

export function useChatStream() {
  const result = ref({
    streaming: false,
    intent: '',
    sql: '',
    rowCount: 0,
    chartOption: null,
    markdownReport: '',
    reportFileUrl: '',
    traces: [],
    failed: false,
    errorMessage: '',
    complete: false,
  })

  // Base URL of the backend, derived from the SSE endpoint URL.
  // Used to prefix the report file URL (e.g. /reports/xxx.html) for iframe display.
  let apiBase = 'http://localhost:8080'

  async function send(question, options = {}) {
    result.value = { streaming: true, intent: '', sql: '', rowCount: 0,
      chartOption: null, markdownReport: '', reportFileUrl: '', traces: [],
      failed: false, errorMessage: '', complete: false }

    try {
      // SSE streaming does NOT go through the Vite proxy because http-proxy
      // middleware buffers responses, which breaks SSE. Instead we POST directly
      // to the backend (CORS is enabled in CorsConfig.java).
      // Override with VITE_SSE_URL env var if the backend is on a different host/port.
      const sseUrl = import.meta.env.VITE_SSE_URL || 'http://localhost:8080/api/chat/stream'
      apiBase = sseUrl.replace(/\/api\/chat\/stream$/, '')

      const body = { question }
      if (options.agentId != null) body.agentId = options.agentId
      if (options.conversationId != null) body.conversationId = options.conversationId

      const res = await fetch(sseUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      if (!res.ok) throw new Error('HTTP ' + res.status)

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      // Accumulate raw bytes as text; split complete SSE events by \n\n boundary
      let rawBuffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          console.log('[SSE] stream ended, rawBuffer length:', rawBuffer.length)
          break
        }

        rawBuffer += decoder.decode(value, { stream: true })

        // SSE events are separated by double-newline
        const parts = rawBuffer.split('\n\n')
        // Last element is either empty (if buffer ends with \n\n) or an incomplete event
        rawBuffer = parts.pop() || ''

        for (const part of parts) {
          console.log('[SSE] received event:', part.substring(0, 120))
          parseSSEEvent(part)
        }
      }

      // Process any remaining event fragment after stream ends
      if (rawBuffer.trim()) {
        console.log('[SSE] final fragment:', rawBuffer.substring(0, 120))
        parseSSEEvent(rawBuffer)
      }

      // Ensure we mark complete even if no explicit 'complete' event was received
      if (!result.value.complete) {
        console.log('[SSE] no complete event received, marking as done')
        result.value.streaming = false
        result.value.complete = true
        if (result.value.traces.length > 0) {
          // Mark any still-running traces as done
          result.value.traces.forEach(t => {
            if (t.status === 'running') t.status = 'done'
          })
        }
      }
    } catch (e) {
      console.error('[SSE] fetch/stream error:', e)
      ElMessage.error('对话请求失败: ' + e.message)
      result.value.streaming = false
      result.value.failed = true
      result.value.errorMessage = e.message
      result.value.complete = true
    }
  }

  /**
   * Parse a single SSE event block (text between two \n\n separators).
   * Each block may contain event: and data: lines (order can vary).
   */
  function parseSSEEvent(text) {
    const lines = text.split('\n')
    let eventType = ''
    let dataPayload = ''

    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventType = line.substring(6).trim()
      } else if (line.startsWith('data:')) {
        // data: may span the rest of the line (including potentially nested JSON)
        dataPayload = line.substring(5)
      }
      // ignore comments (starting with ':') and empty lines within the block
    }

    if (eventType && dataPayload) {
      processEvent(eventType, dataPayload)
    }
  }

  function processEvent(event, data) {
    switch (event) {
      case 'connected':
        // SSE connection established; backend is ready
        if (!result.value.streaming) result.value.streaming = true
        break
      case 'node-start':
        try { const n = JSON.parse(data); result.value.traces.push({ node: n.node, status: 'running', message: '' }) } catch {}
        break
      case 'node-done':
        try {
          const d = JSON.parse(data)
          const idx = result.value.traces.findIndex(t => t.node === d.node && t.status === 'running')
          if (idx >= 0) {
            result.value.traces[idx].status = 'done'
            result.value.traces[idx].message = d.message || ''
            result.value.traces[idx].durationMs = d.durationMs || 0
          }
        } catch {}
        break
      case 'node-failed':
        try {
          const f = JSON.parse(data)
          const idx = result.value.traces.findIndex(t => t.node === f.node && t.status === 'running')
          if (idx >= 0) { result.value.traces[idx].status = 'failed'; result.value.traces[idx].message = f.error || '' }
        } catch {}
        break
      case 'complete':
        try {
          const c = JSON.parse(data)
          result.value.intent = c.intent || ''
          result.value.sql = c.sql || ''
          result.value.rowCount = c.rowCount || 0
          if (c.chartOption) {
            try { result.value.chartOption = typeof c.chartOption === 'string' ? JSON.parse(c.chartOption) : c.chartOption } catch { result.value.chartOption = c.chartOption }
          }
          result.value.markdownReport = c.markdownReport || ''
          if (c.reportFileUrl) {
            result.value.reportFileUrl = c.reportFileUrl.startsWith('http')
              ? c.reportFileUrl
              : apiBase + c.reportFileUrl
          }
          if (c.traces) result.value.traces = c.traces
          result.value.failed = c.failed || false
          result.value.errorMessage = c.errorMessage || ''
          result.value.streaming = false
          result.value.complete = true
        } catch {}
        break
      case 'error':
        result.value.streaming = false
        result.value.failed = true
        result.value.complete = true
        break
    }
  }

  return { result, send }
}
