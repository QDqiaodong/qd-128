import axios from 'axios'
import type { PageResponse, LockerStatusCode } from './locker'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// ---------------- 类型定义 ----------------

export type DoorAlarmStatusCode = 'OPEN' | 'CLOSED'

export interface DoorAlarmRecord {
  id: number
  alarmNo: string
  lockerId: number
  lockerNo: string
  buildingName: string
  unitName: string
  floor: string | null
  lockerStatus: LockerStatusCode
  lockerStatusName: string
  /** 发现柜门未关时间 */
  doorOpenTime: string
  /** 约定关严分钟数 */
  thresholdMinutes: number
  /** 上报人 */
  reporter: string
  status: DoorAlarmStatusCode
  statusName: string
  /** 未处理 = 柜门仍未确认关严 */
  open: boolean
  /** 是否已超约定分钟仍未关严 */
  overtime: boolean
  /** 持续分钟数（未处理为距发现未关时长，已关闭为发现到确认关闭时长） */
  elapsedMinutes: number | null
  /** 确认关闭人 */
  closeOperator: string | null
  /** 确认关闭时间 */
  closeTime: string | null
  /** 关闭说明 */
  closeNote: string | null
  remark: string | null
  createTime: string
  updateTime: string
}

export interface LockerDoorAlarmOverview {
  lockerId: number
  lockerNo: string
  buildingName: string
  unitName: string
  floor: string | null
  status: LockerStatusCode
  statusName: string
  /** 是否存在未处理的柜门未关告警（存在则柜体标记「柜门未关」） */
  doorAjar: boolean
  /** 未处理告警条数 */
  openAlarmCount: number
  /** 未处理告警中是否有已超约定分钟的 */
  overtime: boolean
  /** 历史告警总条数（含已关闭） */
  totalAlarmCount: number
  lastDoorOpenTime: string | null
}

export interface DoorAlarmCreateRequest {
  lockerId: number | null
  /** 发现柜门未关时间（选填，默认当前时间） */
  doorOpenTime?: string
  /** 上报人（必填） */
  reporter: string
  /** 约定关严分钟数（选填，默认系统约定值 10 分钟） */
  thresholdMinutes?: number
  remark?: string
}

export interface DoorAlarmCloseRequest {
  /** 确认关闭人（选填，默认系统管理员） */
  closeOperator?: string
  /** 关闭说明（选填） */
  closeNote?: string
}

export interface DoorAlarmListParams {
  page?: number
  size?: number
  status?: DoorAlarmStatusCode | ''
  lockerId?: number
  keyword?: string
}

/** 告警状态中文映射（与后端 DoorAlarmStatus 保持一致） */
export const DOOR_ALARM_STATUS_NAME_MAP: Record<DoorAlarmStatusCode, string> = {
  OPEN: '未处理',
  CLOSED: '已关闭'
}

/** 系统约定关严分钟数默认值（与后端 DoorAlarmService.DEFAULT_THRESHOLD_MINUTES 保持一致） */
export const DEFAULT_THRESHOLD_MINUTES = 10

export const doorAlarmApi = {
  /** 登记柜门未关告警：上报人必填；同一柜已有未处理告警时后端拦截 */
  reportAlarm(data: DoorAlarmCreateRequest) {
    return api.post<DoorAlarmRecord>('/door-alarms', data)
  },

  getAlarms(params: DoorAlarmListParams) {
    return api.get<PageResponse<DoorAlarmRecord>>('/door-alarms', {
      params: {
        page: params.page,
        size: params.size,
        status: params.status || undefined,
        lockerId: params.lockerId || undefined,
        keyword: params.keyword || undefined
      }
    })
  },

  getAlarm(id: number) {
    return api.get<DoorAlarmRecord>(`/door-alarms/${id}`)
  },

  /** 确认柜门已关严：同一条记录状态变为已关闭，柜体未关标记随之恢复 */
  closeAlarm(id: number, data: DoorAlarmCloseRequest) {
    return api.post<DoorAlarmRecord>(`/door-alarms/${id}/close`, data)
  },

  /** 某台柜体的全部柜门未关告警（柜详情页） */
  getLockerAlarms(lockerId: number) {
    return api.get<DoorAlarmRecord[]>(`/door-alarms/locker/${lockerId}`)
  },

  /** 按柜门状态一览（含未处理条数与未关标记） */
  getLockerOverview() {
    return api.get<LockerDoorAlarmOverview[]>('/door-alarms/locker-overview')
  },

  getStatuses() {
    return api.get<Record<DoorAlarmStatusCode, string>>('/door-alarms/statuses')
  }
}
