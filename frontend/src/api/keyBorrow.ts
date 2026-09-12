import axios from 'axios'
import type { PageResponse, LockerStatusCode } from './locker'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// ---------------- 类型定义 ----------------

export type KeyBorrowStatusCode = 'ON_LOAN' | 'RETURNED'

export interface KeyBorrowRecord {
  id: number
  recordNo: string
  lockerId: number
  lockerNo: string
  buildingName: string
  unitName: string
  floor: string | null
  lockerStatus: LockerStatusCode
  lockerStatusName: string
  borrower: string
  reason: string
  borrowTime: string
  expectedReturnTime: string
  status: KeyBorrowStatusCode
  statusName: string
  /** 借用中 = 钥匙未归还 */
  onLoan: boolean
  /** 借用中且预计归还时间已过 */
  returnOverdue: boolean
  returner: string | null
  returnTime: string | null
  remark: string | null
  createTime: string
  updateTime: string
}

export interface KeyBorrowLockerOption {
  id: number
  lockerNo: string
  buildingName: string
  unitName: string
  floor: string | null
  status: LockerStatusCode
  statusName: string
  onLoan: boolean
}

export interface LockerKeyBorrowOverview {
  lockerId: number
  lockerNo: string
  buildingName: string
  unitName: string
  floor: string | null
  status: LockerStatusCode
  statusName: string
  onLoan: boolean
  openRecordCount: number
  totalRecordCount: number
  lastBorrowTime: string | null
}

export interface KeyBorrowCreateRequest {
  lockerId: number | null
  borrower: string
  reason: string
  /** 本地时间 ISO（不含时区），后端按 LocalDateTime 解析 */
  borrowTime: string | null
  expectedReturnTime: string | null
  remark?: string
}

export interface KeyBorrowListParams {
  page?: number
  size?: number
  status?: KeyBorrowStatusCode | ''
  lockerId?: number
  keyword?: string
}

/** 借用状态中文映射（与后端 KeyBorrowStatus 保持一致） */
export const KEY_BORROW_STATUS_NAME_MAP: Record<KeyBorrowStatusCode, string> = {
  ON_LOAN: '借用中',
  RETURNED: '已归还'
}

export const keyBorrowApi = {
  createRecord(data: KeyBorrowCreateRequest) {
    return api.post<KeyBorrowRecord>('/key-borrows', data)
  },

  getRecords(params: KeyBorrowListParams) {
    return api.get<PageResponse<KeyBorrowRecord>>('/key-borrows', {
      params: {
        page: params.page,
        size: params.size,
        status: params.status || undefined,
        lockerId: params.lockerId || undefined,
        keyword: params.keyword || undefined
      }
    })
  },

  getRecord(id: number) {
    return api.get<KeyBorrowRecord>(`/key-borrows/${id}`)
  },

  returnRecord(id: number, returner: string, returnTime?: string | null) {
    return api.post<KeyBorrowRecord>(`/key-borrows/${id}/return`, { returner, returnTime: returnTime || undefined })
  },

  getLockerRecords(lockerId: number) {
    return api.get<KeyBorrowRecord[]>(`/key-borrows/locker/${lockerId}`)
  },

  getLockerOverview() {
    return api.get<LockerKeyBorrowOverview[]>('/key-borrows/locker-overview')
  },

  getLockerOptions() {
    return api.get<KeyBorrowLockerOption[]>('/key-borrows/locker-options')
  },

  getStatuses() {
    return api.get<Record<KeyBorrowStatusCode, string>>('/key-borrows/statuses')
  }
}
