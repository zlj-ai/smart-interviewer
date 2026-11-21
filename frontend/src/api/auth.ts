import axios from 'axios'

import type { LoginResponse, Result, UserResponse } from '@/types/api'
import { api, saveTokens } from './request'

export async function register(username: string, password: string, email?: string): Promise<UserResponse> {
  const res = await api.post<Result<UserResponse>>('/auth/register', { username, password, email })
  return res.data.data as UserResponse
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  const res = await api.post<Result<LoginResponse>>('/auth/login', { username, password })
  const data = res.data.data as LoginResponse
  saveTokens(data.accessToken, data.refreshToken)
  return data
}

export async function logout(): Promise<void> {
  const refreshToken = localStorage.getItem('refreshToken')
  if (refreshToken) {
    await axios.post('/api/auth/logout', { refreshToken }).catch(() => undefined)
  }
}

export async function me(): Promise<UserResponse> {
  const res = await api.get<Result<UserResponse>>('/users/me')
  return res.data.data as UserResponse
}
