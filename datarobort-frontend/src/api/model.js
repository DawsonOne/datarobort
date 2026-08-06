import api from './index'

/** Get model list, optionally filtered by type (chat/embedding). */
export function listModels(type) {
  return api.get('/models', { params: type ? { type } : {} })
}

/** Get single model detail. */
export function getModel(id) {
  return api.get(`/models/${id}`)
}

/** Create a new model. */
export function createModel(data) {
  return api.post('/models', data)
}

/** Update an existing model. */
export function updateModel(id, data) {
  return api.put(`/models/${id}`, data)
}

/** Delete a model. */
export function deleteModel(id) {
  return api.delete(`/models/${id}`)
}

/** Set a model as default for its type. */
export function setDefaultModel(id) {
  return api.post(`/models/${id}/default`)
}

/** Test connectivity of a model. */
export function testModel(id) {
  return api.post(`/models/${id}/test`)
}
