import api from './index'

/** Get datasource list. */
export function listDatasources() {
  return api.get('/datasources')
}

/** Get single datasource detail. */
export function getDatasource(id) {
  return api.get(`/datasources/${id}`)
}

/** Create a new datasource. */
export function createDatasource(data) {
  return api.post('/datasources', data)
}

/** Update an existing datasource. */
export function updateDatasource(id, data) {
  return api.put(`/datasources/${id}`, data)
}

/** Delete a datasource. */
export function deleteDatasource(id) {
  return api.delete(`/datasources/${id}`)
}

/** Test connectivity of a datasource. */
export function testDatasource(id) {
  return api.post(`/datasources/${id}/test`)
}

/** Re-crawl table/column metadata. */
export function refreshSchema(id) {
  return api.post(`/datasources/${id}/refresh-schema`)
}

/** Get schema tree (tables with columns). */
export function getSchema(id) {
  return api.get(`/datasources/${id}/schema`)
}
