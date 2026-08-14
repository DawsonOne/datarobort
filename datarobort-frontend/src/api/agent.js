import api from './index'

// Agent CRUD + publish
export function listAgents() { return api.get('/agents') }
export function getAgent(id) { return api.get(`/agents/${id}`) }
export function createAgent(data) { return api.post('/agents', data) }
export function updateAgent(id, data) { return api.put(`/agents/${id}`, data) }
export function deleteAgent(id) { return api.delete(`/agents/${id}`) }
export function publishAgent(id, status) { return api.put(`/agents/${id}/publish`, { status }) }
