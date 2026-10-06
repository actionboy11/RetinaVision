import request from '@/utils/request'
import type { Correction, Feedback, FeedbackVerdict, Report, Review, ReviewStatus } from '@/types/clinical'
export const listFeedback=(id:number)=>request.get<unknown,Feedback[]>(`/analysis-results/${id}/feedback`)
export const addFeedback=(id:number,data:{verdict:FeedbackVerdict;issueCodes:string[];comment:string})=>request.post<unknown,Feedback>(`/analysis-results/${id}/feedback`,data)
export const listCorrections=(id:number)=>request.get<unknown,Correction[]>(`/analysis-results/${id}/corrections`)
export const addCorrection=(id:number,data:FormData)=>request.post<unknown,Correction>(`/analysis-results/${id}/corrections`,data)
export const getReview=(id:number)=>request.get<unknown,Review|null>(`/analysis-results/${id}/review`)
export const submitReview=(id:number,data:{correctionVersion:number|null;status:ReviewStatus;findings:string;conclusion:string;recommendation:string;expectedVersion:number})=>request.post<unknown,Review>(`/analysis-results/${id}/review`,data)
export const getReportDraft=(id:number)=>request.get<unknown,Report>(`/analysis-results/${id}/report-draft`)
export const updateReportDraft=(id:number,data:{correctionVersion:number|null;draftJson:string})=>request.put<unknown,Report>(`/analysis-results/${id}/report-draft`,data)
export const generateReportDraft=(id:number)=>request.post<unknown,Report>(`/analysis-results/${id}/report-draft/ai-generate`)
export const signReport=(id:number)=>request.post<unknown,Report>(`/analysis-results/${id}/report-sign`)
export const listReports=(id:number)=>request.get<unknown,Report[]>(`/analysis-results/${id}/reports`)
export const downloadReportVersion=(id:number,version:number)=>request.get<unknown,Blob>(`/analysis-results/${id}/reports/${version}`,{responseType:'blob'})
