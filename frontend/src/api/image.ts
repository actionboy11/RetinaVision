import request from '@/utils/request'

import type { ImageFileItem } from '@/types/image'

export const getCaseImages = (caseId: number): Promise<ImageFileItem[]> => {
  return request.get<unknown, ImageFileItem[]>(`/cases/${caseId}/images`)
}

export const uploadCaseImage = (
  caseId: number,
  file: File,
): Promise<ImageFileItem> => {
  const formData = new FormData()
  formData.append('file', file)

  return request.post<unknown, ImageFileItem>(`/cases/${caseId}/images`, formData)
}

export const deleteImage = (imageId: number): Promise<boolean> => {
  return request.delete<unknown, boolean>(`/images/${imageId}`)
}

export const requestImageQualityCheck = (imageId: number): Promise<ImageFileItem> => {
  return request.post<unknown, ImageFileItem>(`/images/${imageId}/quality-check`)
}

export const getImagePreviewUrl = (imageId: number): string => {
  return `/images/${imageId}/preview`
}

export const getImagePreviewBlob = (imageId: number): Promise<Blob> => {
  return request.get<unknown, Blob>(getImagePreviewUrl(imageId), {
    responseType: 'blob',
  })
}
