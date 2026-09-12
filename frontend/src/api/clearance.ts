import axios from 'axios'
import type { PageResponse, LockerStatusCode } from './locker'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// ---------------- 类型定义 ----------------

export type ClearanceStatusCode = 'PROCESSING' | 'COMPLETED'

export interface ClearanceOrder {
  id: number
  orderNo: string
  lockerId: number
  lockerNo: string
  buildingName: string
  unitName: string
  floor: string | null
  lockerStatus: LockerStatusCode
  lockerStatusName: string
  overdueCompartments: string
  packageCount: number
  foundTime: string
  handler: string
  status: ClearanceStatusCode
  statusName: string
  /** 办理中 = 滞留中 */
  overdue: boolean
  handleResult: string | null
  completeTime: string | null
  remark: string | null
  createTime: string
  updateTime: string
}

export interface ClearanceLockerOption {
  id: number
  lockerNo: string
  buildingName: string
  unitName: string
  floor: string | null
  status: LockerStatusCode
  statusName: string
  overdue: boolean
}

export interface LockerClearanceOverview {
  lockerId: number
  lockerNo: string
  buildingName: string
  unitName: string
  floor: string | null
  overdue: boolean
  openOrderCount: number
  openPackageCount: number
  totalOrderCount: number
  lastFoundTime: string | null
}

export interface ClearanceOrderCreateRequest {
  lockerId: number | null
  overdueCompartments: string
  packageCount: number
  /** 本地时间 ISO（不含时区），后端按 LocalDateTime 解析 */
  foundTime: string | null
  handler: string
  remark?: string
}

export interface ClearanceListParams {
  page?: number
  size?: number
  status?: ClearanceStatusCode | ''
  lockerId?: number
  keyword?: string
}

/** 清柜单状态中文映射（与后端 ClearanceStatus 保持一致） */
export const CLEARANCE_STATUS_NAME_MAP: Record<ClearanceStatusCode, string> = {
  PROCESSING: '办理中',
  COMPLETED: '已办结'
}

export const clearanceApi = {
  createOrder(data: ClearanceOrderCreateRequest) {
    return api.post<ClearanceOrder>('/clearances', data)
  },

  getOrders(params: ClearanceListParams) {
    return api.get<PageResponse<ClearanceOrder>>('/clearances', {
      params: {
        page: params.page,
        size: params.size,
        status: params.status || undefined,
        lockerId: params.lockerId || undefined,
        keyword: params.keyword || undefined
      }
    })
  },

  getOrder(id: number) {
    return api.get<ClearanceOrder>(`/clearances/${id}`)
  },

  completeOrder(id: number, handleResult: string) {
    return api.post<ClearanceOrder>(`/clearances/${id}/complete`, { handleResult })
  },

  getLockerOrders(lockerId: number) {
    return api.get<ClearanceOrder[]>(`/clearances/locker/${lockerId}`)
  },

  getLockerOverview() {
    return api.get<LockerClearanceOverview[]>('/clearances/locker-overview')
  },

  getLockerOptions() {
    return api.get<ClearanceLockerOption[]>('/clearances/locker-options')
  },

  getStatuses() {
    return api.get<Record<ClearanceStatusCode, string>>('/clearances/statuses')
  }
}
