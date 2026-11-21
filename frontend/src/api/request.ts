import axios, { AxiosError } from 'axios'

/**
 * 统一 API 客户端：携带 Bearer token，401 时用 refreshToken 静默刷新后重试一次。
 * token 直接读写 localStorage，避免与 Pinia store 产生循环依赖。
 */

const TOKEN_KEY = 'accessToken'
const REFRESH_KEY = 'refreshToken'

export function saveTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_KEY, refreshToken)
}

export function clearTokens(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

export function getAccessToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshing: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = localStorage.getItem(REFRESH_KEY)
  if (!refreshToken) {
    return null
  }
  try {
    const res = await axios.post('/api/auth/refresh', { refreshToken })
    const body = res.data as { success: boolean; data: { accessToken: string; refreshToken: string } | null }
    if (body.success && body.data) {
      saveTokens(body.data.accessToken, body.data.refreshToken)
      return body.data.accessToken
    }
    clearTokens()
    return null
  } catch {
    clearTokens()
    return null
  }
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (typeof error.config & { _retried?: boolean }) | undefined
    if (error.response?.status === 401 && original && !original._retried) {
      original._retried = true
      refreshing ??= refreshAccessToken().finally(() => {
        refreshing = null
      })
      const newToken = await refreshing
      if (newToken) {
        original.headers = original.headers ?? {}
        ;(original.headers as Record<string, string>).Authorization = `Bearer ${newToken}`
        return api.request(original)
      }
    }
    return Promise.reject(error)
  },
)
