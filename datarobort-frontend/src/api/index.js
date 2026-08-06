import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// Response interceptor: unwrap Result.data, or show error message
api.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code !== undefined) {
      if (body.code === '0') {
        return body.data
      }
      // Business error
      const msg = body.message || '未知错误'
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }
    return body
  },
  (error) => {
    const msg = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default api
