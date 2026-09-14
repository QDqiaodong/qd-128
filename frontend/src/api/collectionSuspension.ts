import axios from 'axios'
import type { PageResponse, LockerStatusCode } from './locker'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// ---------------- 类型定义 ----------------

export type CollectionSuspensionStatusCode = 'SUSPENDED' | 'RESUMED'

export interface CollectionSuspensionRecord {
  id: number
  recordNo: string
  lockerId: number
  lockerNo: string
  buildingName: string
  unitName: string
  floor: string | null
  lockerStatus: LockerStatusCode
  lockerStatusName: string
  /** 开始停收时间 */
  suspendStartTime: string
  /** 预计恢复时间 */
  expectedResumeTime: string
  /** 值班人 */
  dutyOfficer: string
  status: CollectionSuspensionStatusCode
  statusName: string
  /** 停收中 = 告示未撕、尚未确认恢复 */
  suspended: boolean
  /** 是否已过预计恢复时间仍未确认恢复 */
  overdue: boolean
  /** 持续分钟数（停收中为距开始停收时长，已恢复为开始停收到确认恢复时长） */
  elapsedMinutes: number | null
  /** 确认恢复人 */
  resumeOperator: string | null
  /** 确认恢复时间 */
  resumeTime: string | null
  /** 恢复说明 */
  resumeNote: string | null
  remark: string | null
  createTime: string
  updateTime: string
}

export interface LockerCollectionSuspensionOverview {
  lockerId: number
  lockerNo: string
  buildingName: string
  unitName: string
  floor: string | null
  status: LockerStatusCode
  statusName: string
  /** 是否存在停收中的记录（存在则柜体标记「停收中」） */
  suspended: boolean
  /** 停收中记录条数 */
  openRecordCount: number
  /** 停收中记录是否有已过预计恢复时间的 */
  overdue: boolean
  /** 历史记录总条数（含已恢复） */
  totalRecordCount: number
  lastSuspendStartTime: string | null
  expectedResumeTime: string | null
}

export interface CollectionSuspensionCreateRequest {
  lockerId: number | null
  /** 开始停收时间（选填，默认当前时间） */
  suspendStartTime?: string
  /** 预计恢复时间（必填，必须晚于开始停收时间） */
  expectedResumeTime: string
  /** 值班人（必填） */
  dutyOfficer: string
  remark?: string
}

export interface CollectionSuspensionResumeRequest {
  /** 确认恢复人（选填，默认系统管理员） */
  resumeOperator?: string
  /** 恢复说明（选填） */
  resumeNote?: string
}

export interface CollectionSuspensionListParams {
  page?: number
  size?: number
  status?: CollectionSuspensionStatusCode | ''
  lockerId?: number
  keyword?: string
}

/** 停收状态中文映射（与后端 CollectionSuspensionStatus 保持一致） */
export const COLLECTION_SUSPENSION_STATUS_NAME_MAP: Record<CollectionSuspensionStatusCode, string> = {
  SUSPENDED: '停收中',
  RESUMED: '已恢复'
}

export const collectionSuspensionApi = {
  /** 登记夜间停收转投：开始停收时间、预计恢复时间、值班人必填；同一柜已有停收中记录时后端拦截 */
  register(data: CollectionSuspensionCreateRequest) {
    return api.post<CollectionSuspensionRecord>('/collection-suspensions', data)
  },

  getRecords(params: CollectionSuspensionListParams) {
    return api.get<PageResponse<CollectionSuspensionRecord>>('/collection-suspensions', {
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
    return api.get<CollectionSuspensionRecord>(`/collection-suspensions/${id}`)
  },

  /** 确认已恢复：同一条记录状态变为已恢复，柜体停收标记随之恢复 */
  resume(id: number, data: CollectionSuspensionResumeRequest) {
    return api.post<CollectionSuspensionRecord>(`/collection-suspensions/${id}/resume`, data)
  },

  /** 某台柜体的全部停收转投记录（柜详情页） */
  getLockerRecords(lockerId: number) {
    return api.get<CollectionSuspensionRecord[]>(`/collection-suspensions/locker/${lockerId}`)
  },

  /** 按停收状态一览（含停收中条数与停收标记） */
  getLockerOverview() {
    return api.get<LockerCollectionSuspensionOverview[]>('/collection-suspensions/locker-overview')
  },

  getStatuses() {
    return api.get<Record<CollectionSuspensionStatusCode, string>>('/collection-suspensions/statuses')
  }
}
