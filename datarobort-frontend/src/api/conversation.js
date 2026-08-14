import api from './index'

// Conversation CRUD + messages
export function listConversations(agentId) {
  return api.get('/conversations', { params: agentId ? { agentId } : {} })
}
export function getConversation(id) { return api.get(`/conversations/${id}`) }
export function listMessages(conversationId) { return api.get(`/conversations/${conversationId}/messages`) }
export function createConversation(data) { return api.post('/conversations', data) }
export function updateConversationTitle(id, title) { return api.put(`/conversations/${id}`, { title }) }
export function deleteConversation(id) { return api.delete(`/conversations/${id}`) }
