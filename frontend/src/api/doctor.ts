import request from '@/utils/request'
import type { DoctorReviewReminder } from '@/types/doctor'

export const getDoctorReviewReminders = () => {
  return request.get<unknown, DoctorReviewReminder>('/doctor/reviews/reminders')
}
