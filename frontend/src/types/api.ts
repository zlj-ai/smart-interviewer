// 与后端 DTO 对齐的类型定义（禁止在页面里重复定义）

export interface Result<T> {
  success: boolean
  code: string
  message: string
  data: T | null
}

export interface UserResponse {
  id: number
  username: string
  email: string | null
  role: string
  createdAt: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserResponse
}

export interface SessionResponse {
  id: number
  direction: string
  difficulty: string
  status: string
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface QuestionResponse {
  id: number
  seq: number
  question: string
  referenceAnswer: string | null
  skillTag: string | null
}

export interface AnswerResponse {
  id: number
  questionId: number
  sessionId: number
  answerText: string
  score: number | null
  feedback: string | null
  strengths: string | null
  improvements: string | null
  status: string
  attempt: number
  createdAt: string
}

export interface KbResponse {
  id: number
  name: string
  description: string | null
  createdAt: string
}

export interface KbDocumentResponse {
  id: number
  kbId: number
  filename: string
  sizeBytes: number
  charCount: number
  chunkCount: number
  status: string
  createdAt: string
}

export interface ResumeResponse {
  id: number
  filename: string
  sizeBytes: number
  charCount: number
  status: string
  attempt: number
  createdAt: string
}

export interface ResumeDetailResponse extends ResumeResponse {
  analysisJson: string | null
}

export interface SourceRef {
  index: number
  docId: number | null
  filename: string
  score: number | null
}
