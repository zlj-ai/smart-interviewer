import { defineStore } from 'pinia'

import type { UserResponse } from '@/types/api'

const USER_KEY = 'user'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null') as UserResponse | null,
  }),
  getters: {
    isLoggedIn: (state) => state.user !== null && localStorage.getItem('accessToken') !== null,
  },
  actions: {
    setUser(user: UserResponse) {
      this.user = user
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    },
    clear() {
      this.user = null
      localStorage.removeItem(USER_KEY)
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
    },
  },
})
