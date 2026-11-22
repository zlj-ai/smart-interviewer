import type { AnswerResponse, PageResponse, QuestionResponse, Result, SessionResponse } from '@/types/api'
import { api } from './request'

export async function createSession(
  requestId: string,
  direction: string,
  difficulty: string,
  resumeId?: number,
): Promise<SessionResponse> {
  const res = await api.post<Result<SessionResponse>>('/interview/sessions', {
    requestId,
    direction,
    difficulty,
    resumeId,
  })
  return res.data.data as SessionResponse
}

export async function listSessions(page = 0, size = 10): Promise<PageResponse<SessionResponse>> {
  const res = await api.get<Result<PageResponse<SessionResponse>>>('/interview/sessions', {
    params: { page, size },
  })
  return res.data.data as PageResponse<SessionResponse>
}

export async function getSession(sessionId: number): Promise<SessionResponse> {
  const res = await api.get<Result<SessionResponse>>(`/interview/sessions/${sessionId}`)
  return res.data.data as SessionResponse
}

export async function listQuestions(sessionId: number): Promise<QuestionResponse[]> {
  const res = await api.get<Result<QuestionResponse[]>>(`/interview/sessions/${sessionId}/questions`)
  return res.data.data as QuestionResponse[]
}

export async function regenerateQuestions(sessionId: number): Promise<SessionResponse> {
  const res = await api.post<Result<SessionResponse>>(
    `/interview/sessions/${sessionId}/questions/regenerate`,
  )
  return res.data.data as SessionResponse
}

export async function submitAnswer(
  sessionId: number,
  questionId: number,
  answerText: string,
): Promise<AnswerResponse> {
  const res = await api.post<Result<AnswerResponse>>(
    `/interview/sessions/${sessionId}/questions/${questionId}/answer`,
    { answerText },
  )
  return res.data.data as AnswerResponse
}

export async function getAnswer(sessionId: number, questionId: number): Promise<AnswerResponse> {
  const res = await api.get<Result<AnswerResponse>>(
    `/interview/sessions/${sessionId}/questions/${questionId}/answer`,
  )
  return res.data.data as AnswerResponse
}
