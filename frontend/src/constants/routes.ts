export const routes = {
  login: '/login',
  register: '/register',
  sessions: '/sessions',
  sessionDetail: (id: number) => `/sessions/${id}`,
  knowledge: '/knowledge',
  resume: '/resume',
} as const
