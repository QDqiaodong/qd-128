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
  /** 借用中且预计归还刚好到点或已过点：可改期（与后端改期校验口径一致） */
  extendable: boolean
  returner: string | null
  returnTime: string | null
  remark: string | null
  /** 改期次数：借用中延后预计归还的累计次数 */
  extendCount: number
  /** 最近一次改期原因 */
  lastExtendReason: string | null
  /** 最近一次改期时间 */
  lastExtendTime: string | null
  /** 被交接班点名的次数：交接只留痕迹不改状态 */
  handoverCount: number
  createTime: string
  updateTime: string
}

/** 交接窗口待点名的一条未还借用记录 */
export interface KeyHandoverPendingItem {
  recordId: number
  recordNo: string
  lockerId: number
  lockerNo: string
  buildingName: string | null
  unitName: string | null
  floor: string | null
  borrower: string
  reason: string
  borrowTime: string
  expectedReturnTime: string
  /** 预计归还已过 */
  overdue: boolean
}

/** 交接点名明细 */
export interface KeyHandoverItem {
  id: number
  recordId: number
  lockerId: number
  lockerNo: string | null
  recordNo: string | null
  buildingName: string | null
  unitName: string | null
  floor: string | null
  borrower: string | null
  reason: string | null
  borrowTime: string | null
  expectedReturnTime: string | null
  /** 该单当前是否仍未还（交接不改状态） */
  onLoan: boolean
}

/** 钥匙交接班记录（台账中的交接痕迹） */
export interface KeyHandover {
  id: number
  handoverNo: string
  handoverFrom: string
  handoverTo: string
  handoverNote: string
  itemCount: number
  createTime: string
  items: KeyHandoverItem[]
}

export interface KeyHandoverCreateRequest {
  handoverFrom: string
  handoverTo: string
  handoverNote: string
  /** 点名勾选的未还记录 ID，必须勾齐全部未还柜 */
  recordIds: number[]
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

export interface KeyBorrowExtendRequest {
  /** 新的预计归还时间，必须晚于原预计归还时间（预计归还刚好到点或已过点时允许提交） */
  expectedReturnTime: string
  /** 改期原因（必填） */
  extendReason: string
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

  /** 借用改期：借用中且预计归还刚好到点或已过点可改，新预计归还必须更晚，改期原因必填 */
  extendRecord(id: number, data: KeyBorrowExtendRequest) {
    return api.post<KeyBorrowRecord>(`/key-borrows/${id}/extend`, data)
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

export const keyHandoverApi = {
  /** 交接窗口待点名清单：当前全部借用中记录 */
  getPendingItems() {
    return api.get<KeyHandoverPendingItem[]>('/key-handovers/pending')
  },

  /** 提交交接：必须勾齐全部未还柜，交班人/接班人/交接说明必填 */
  submit(data: KeyHandoverCreateRequest) {
    return api.post<KeyHandover>('/key-handovers', data)
  },

  /** 交接痕迹分页 */
  getHandovers(params: { page?: number; size?: number }) {
    return api.get<PageResponse<KeyHandover>>('/key-handovers', {
      params: { page: params.page, size: params.size }
    })
  },

  getHandover(id: number) {
    return api.get<KeyHandover>(`/key-handovers/${id}`)
  },

  /** 某台柜体相关的全部交接痕迹（柜详情页） */
  getLockerHandovers(lockerId: number) {
    return api.get<KeyHandover[]>(`/key-handovers/by-locker/${lockerId}`)
  },

  /** 某条借用记录被点名过的交接痕迹（台账列表） */
  getRecordHandovers(recordId: number) {
    return api.get<KeyHandover[]>(`/key-handovers/by-record/${recordId}`)
  }
}
