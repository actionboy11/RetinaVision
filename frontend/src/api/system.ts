import request from '@/utils/request'

import type { SystemStatus } from '@/types/system'

export const getSystemStatus = (): Promise<SystemStatus> => {
  return request.get<unknown, SystemStatus>('/system/status')
}
