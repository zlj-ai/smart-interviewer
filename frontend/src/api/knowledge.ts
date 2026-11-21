import type { KbDocumentResponse, KbResponse, PageResponse, Result } from '@/types/api'
import { api } from './request'

export async function createKb(name: string, description: string): Promise<KbResponse> {
  const res = await api.post<Result<KbResponse>>('/kb', { name, description })
  return res.data.data as KbResponse
}

export async function listKbs(): Promise<KbResponse[]> {
  const res = await api.get<Result<PageResponse<KbResponse>>>('/kb', { params: { page: 0, size: 50 } })
  return (res.data.data as PageResponse<KbResponse>).content
}

export async function uploadDocument(kbId: number, file: File): Promise<KbDocumentResponse> {
  const form = new FormData()
  form.append('file', file)
  const res = await api.post<Result<KbDocumentResponse>>(`/kb/${kbId}/documents`, form)
  return res.data.data as KbDocumentResponse
}

export async function listDocuments(kbId: number): Promise<KbDocumentResponse[]> {
  const res = await api.get<Result<KbDocumentResponse[]>>(`/kb/${kbId}/documents`)
  return res.data.data as KbDocumentResponse[]
}
