import type { PageResponse, ResumeDetailResponse, ResumeResponse, Result } from '@/types/api'
import { api } from './request'

export async function uploadResume(file: File): Promise<ResumeResponse> {
  const form = new FormData()
  form.append('file', file)
  const res = await api.post<Result<ResumeResponse>>('/resume', form)
  return res.data.data as ResumeResponse
}

export async function listResumes(): Promise<PageResponse<ResumeResponse>> {
  const res = await api.get<Result<PageResponse<ResumeResponse>>>('/resume', {
    params: { page: 0, size: 20 },
  })
  return res.data.data as PageResponse<ResumeResponse>
}

export async function getResumeDetail(id: number): Promise<ResumeDetailResponse> {
  const res = await api.get<Result<ResumeDetailResponse>>(`/resume/${id}`)
  return res.data.data as ResumeDetailResponse
}

export async function deleteResume(id: number): Promise<void> {
  await api.delete(`/resume/${id}`)
}
