import api from './index'

// Knowledge Base CRUD
export function listKBs() { return api.get('/knowledge-bases') }
export function getKB(id) { return api.get(`/knowledge-bases/${id}`) }
export function createKB(data) { return api.post('/knowledge-bases', data) }
export function updateKB(id, data) { return api.put(`/knowledge-bases/${id}`, data) }
export function deleteKB(id) { return api.delete(`/knowledge-bases/${id}`) }

// Documents within a KB
export function listDocuments(kbId) { return api.get(`/knowledge-bases/${kbId}/documents`) }
export async function uploadDocument(kbId, file) {
  const fd = new FormData()
  fd.append('file', file)
  const res = await fetch(`/api/knowledge-bases/${kbId}/documents`, {
    method: 'POST',
    body: fd,
  })
  if (!res.ok) throw new Error(`上传失败: HTTP ${res.status}`)
  const json = await res.json()
  if (json.code !== '0') throw new Error(json.message || '上传失败')
  return json.data
}
export function deleteDocument(kbId, docId) { return api.delete(`/knowledge-bases/${kbId}/documents/${docId}`) }

// Business Knowledge CRUD
export function listBK() { return api.get('/business-knowledge') }
export function getBK(id) { return api.get(`/business-knowledge/${id}`) }
export function createBK(data) { return api.post('/business-knowledge', data) }
export function updateBK(id, data) { return api.put(`/business-knowledge/${id}`, data) }
export function deleteBK(id) { return api.delete(`/business-knowledge/${id}`) }

// Semantic Model CRUD
export function listSM(dsId) { return api.get('/semantic-models', { params: { dsId } }) }
export function getSM(id) { return api.get(`/semantic-models/${id}`) }
export function createSM(data) { return api.post('/semantic-models', data) }
export function updateSM(id, data) { return api.put(`/semantic-models/${id}`, data) }
export function deleteSM(id) { return api.delete(`/semantic-models/${id}`) }

// Recall test
export function recall(query, topK = 10, threshold = 0.3) {
  return api.post('/recall', { query, topK, threshold })
}
