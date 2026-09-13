import axios from 'axios'
import type { PageResponse, LockerStatusCode } from './locker'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// ---------------- 类型定义 ----------------

export type RepairStatusCode = 'PROCESSING' | 'FIXED'

export interface RepairTicket {
  id: number
  ticketNo: string
  lockerId: number
  lockerNo: string
  buildingName: string
  unitName: string
  floor: string | null
  lockerStatus: LockerStatusCode
  lockerStatusName: string
  /** 故障格口编号 */
  compartmentNo: string
  /** 故障现象 */
  symptom: string
  /** 报修人 */
  reporter: string
  status: RepairStatusCode
  statusName: string
  /** 处理中 = 尚未完工 */
  processing: boolean
  /** 处理人（完工时填写） */
  handler: string | null
  /** 处理结果（完工时填写） */
  repairResult: string | null
  /** 完工时间 */
  fixedTime: string | null
  remark: string | null
  createTime: string
  updateTime: string
}

export interface LockerRepairOverview {
  lockerId: number
  lockerNo: string
  buildingName: string
  unitName: string
  floor: string | null
  status: LockerStatusCode
  statusName: string
  /** 是否存在处理中的报修单（存在则柜体标记维修中，否则可用） */
  repairing: boolean
  /** 处理中报修条数 */
  openTicketCount: number
  /** 历史报修总条数（含已修好） */
  totalTicketCount: number
  lastReportTime: string | null
}

export interface RepairTicketCreateRequest {
  lockerId: number | null
  /** 故障格口编号（必填） */
  compartmentNo: string
  /** 故障现象（必填） */
  symptom: string
  /** 报修人（必填） */
  reporter: string
  remark?: string
}

export interface RepairTicketCompleteRequest {
  /** 处理人（完工必填） */
  handler: string
  /** 处理结果（完工必填） */
  repairResult: string
}

export interface RepairTicketListParams {
  page?: number
  size?: number
  status?: RepairStatusCode | ''
  lockerId?: number
  keyword?: string
}

/** 报修状态中文映射（与后端 RepairStatus 保持一致） */
export const REPAIR_STATUS_NAME_MAP: Record<RepairStatusCode, string> = {
  PROCESSING: '处理中',
  FIXED: '已修好'
}

export const repairApi = {
  /** 登记报修：故障格口、故障现象、报修人必填，未填齐不能建单 */
  createTicket(data: RepairTicketCreateRequest) {
    return api.post<RepairTicket>('/repairs', data)
  },

  getTickets(params: RepairTicketListParams) {
    return api.get<PageResponse<RepairTicket>>('/repairs', {
      params: {
        page: params.page,
        size: params.size,
        status: params.status || undefined,
        lockerId: params.lockerId || undefined,
        keyword: params.keyword || undefined
      }
    })
  },

  getTicket(id: number) {
    return api.get<RepairTicket>(`/repairs/${id}`)
  },

  /** 完工：处理人、处理结果必填，状态变为已修好 */
  completeTicket(id: number, data: RepairTicketCompleteRequest) {
    return api.post<RepairTicket>(`/repairs/${id}/complete`, data)
  },

  /** 某台柜体的全部报修单（柜详情页） */
  getLockerTickets(lockerId: number) {
    return api.get<RepairTicket[]>(`/repairs/locker/${lockerId}`)
  },

  /** 按柜报修状态一览（含处理中条数与可用标记） */
  getLockerOverview() {
    return api.get<LockerRepairOverview[]>('/repairs/locker-overview')
  },

  getStatuses() {
    return api.get<Record<RepairStatusCode, string>>('/repairs/statuses')
  }
}
