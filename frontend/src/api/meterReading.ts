import axios from 'axios'
import type { PageResponse, LockerStatusCode } from './locker'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// ---------------- 类型定义 ----------------

export type MeterReadingStatusCode = 'ACTIVE' | 'VOIDED'

export interface MeterReadingRecord {
  id: number
  recordNo: string
  lockerId: number
  lockerNo: string
  buildingName: string
  unitName: string
  floor: string | null
  lockerStatus: LockerStatusCode
  lockerStatusName: string
  /** 账期（自然月，格式 yyyy-MM），由抄表时间推导 */
  periodMonth: string
  /** 电表读数（kWh） */
  readingValue: number
  reader: string
  readingTime: string
  status: MeterReadingStatusCode
  statusName: string
  /** 有效 = 未作废 */
  active: boolean
  /** 当前自然月的有效单：柜体页「本月已抄」与本月已抄台数的统计口径 */
  currentMonth: boolean
  voidReason: string | null
  voidOperator: string | null
  voidTime: string | null
  remark: string | null
  createTime: string
  updateTime: string
}

export interface LockerMeterReadingOverview {
  lockerId: number
  lockerNo: string
  buildingName: string
  unitName: string
  floor: string | null
  status: LockerStatusCode
  statusName: string
  /** 账期（自然月，格式 yyyy-MM） */
  periodMonth: string
  /** 该账期是否已抄（存在有效抄表单） */
  read: boolean
  recordId: number | null
  /** 本月电表读数（kWh），未抄为空 */
  readingValue: number | null
  reader: string | null
  readingTime: string | null
}

export interface MeterReadingLockerOption {
  id: number
  lockerNo: string
  buildingName: string
  unitName: string
  floor: string | null
  status: LockerStatusCode
  statusName: string
  /** 本月已抄的柜体在登记下拉框中置灰 */
  readThisMonth: boolean
}

export interface MeterReadingCreateRequest {
  lockerId: number | null
  /** 电表读数（kWh） */
  readingValue: number | null
  reader: string
  /** 本地时间 ISO（不含时区），后端按 LocalDateTime 解析；账期由该时间推导 */
  readingTime: string | null
  remark?: string
}

export interface MeterReadingVoidRequest {
  /** 作废原因（必填） */
  voidReason: string
  voidOperator?: string
}

export interface MeterReadingListParams {
  page?: number
  size?: number
  /** 账期过滤（yyyy-MM），不传返回全部账期 */
  periodMonth?: string
  status?: MeterReadingStatusCode | ''
  lockerId?: number
  keyword?: string
}

/** 抄表单状态中文映射（与后端 MeterReadingStatus 保持一致） */
export const METER_READING_STATUS_NAME_MAP: Record<MeterReadingStatusCode, string> = {
  ACTIVE: '有效',
  VOIDED: '已作废'
}

export const meterReadingApi = {
  createRecord(data: MeterReadingCreateRequest) {
    return api.post<MeterReadingRecord>('/meter-readings', data)
  },

  getRecords(params: MeterReadingListParams) {
    return api.get<PageResponse<MeterReadingRecord>>('/meter-readings', {
      params: {
        page: params.page,
        size: params.size,
        periodMonth: params.periodMonth || undefined,
        status: params.status || undefined,
        lockerId: params.lockerId || undefined,
        keyword: params.keyword || undefined
      }
    })
  },

  getRecord(id: number) {
    return api.get<MeterReadingRecord>(`/meter-readings/${id}`)
  },

  /** 作废抄表单：必须填写作废原因 */
  voidRecord(id: number, data: MeterReadingVoidRequest) {
    return api.post<MeterReadingRecord>(`/meter-readings/${id}/void`, data)
  },

  getLockerRecords(lockerId: number) {
    return api.get<MeterReadingRecord[]>(`/meter-readings/locker/${lockerId}`)
  },

  /** 按柜本月抄表状态一览（periodMonth 不传默认当前自然月） */
  getLockerOverview(periodMonth?: string) {
    return api.get<LockerMeterReadingOverview[]>('/meter-readings/locker-overview', {
      params: { periodMonth: periodMonth || undefined }
    })
  },

  getLockerOptions() {
    return api.get<MeterReadingLockerOption[]>('/meter-readings/locker-options')
  },

  getStatuses() {
    return api.get<Record<MeterReadingStatusCode, string>>('/meter-readings/statuses')
  }
}
