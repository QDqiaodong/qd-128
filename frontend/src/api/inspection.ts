import axios from 'axios'
import type { PageResponse } from './locker'

export type { PageResponse }
import type { BuildingTreeDTO, UnitDTO } from './locker'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// ---------------- 类型定义 ----------------

export type InspectionCycleCode = 'ONCE' | 'DAILY' | 'WEEKLY' | 'MONTHLY'
export type InspectionTaskStatusCode = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED'
/** 列表快捷筛选：未完成（后端虚拟状态） */
export type InspectionTaskFilter = InspectionTaskStatusCode | 'INCOMPLETE'
export type CheckResultCode = 'NORMAL' | 'ABNORMAL' | 'NOT_APPLICABLE'
export type IssueStatusCode = 'PENDING' | 'PROCESSING' | 'RESOLVED'
export type CheckItemCode = 'compartment' | 'screen' | 'lock'

export interface InspectionTask {
  id: number
  taskName: string
  buildingId: number
  buildingName: string
  unitId: number | null
  unitName: string | null
  cycle: InspectionCycleCode
  cycleName: string
  assignee: string | null
  deadline: string | null
  status: InspectionTaskStatusCode
  statusName: string
  totalLockers: number
  completedLockers: number
  abnormalCount: number
  pendingIssueCount: number
  totalIssueCount: number
  creator: string | null
  createTime: string
  updateTime: string
  progress: number
  overdue: boolean
}

export interface InspectionRecord {
  id: number
  taskId: number
  lockerId: number
  lockerNo: string
  specTypeName: string
  compartmentCount: number
  buildingName: string
  unitName: string
  floor: string | null
  lockerExists: boolean
  compartmentResult: CheckResultCode | null
  screenResult: CheckResultCode | null
  lockResult: CheckResultCode | null
  remark: string | null
  inspector: string | null
  inspectTime: string | null
  pendingIssueCount: number
  totalIssueCount: number
}

export interface InspectionIssue {
  id: number
  taskId: number
  recordId: number
  lockerId: number
  lockerNo: string
  buildingName: string
  unitName: string
  checkItem: CheckItemCode
  checkItemName: string
  description: string
  status: IssueStatusCode
  statusName: string
  handler: string | null
  handleNote: string | null
  createTime: string
  handleTime: string | null
}

export interface RecordSubmitItem {
  lockerId: number
  compartmentResult: CheckResultCode | null
  screenResult: CheckResultCode | null
  lockResult: CheckResultCode | null
  remark?: string
  inspector?: string
}

export interface InspectionCreateRequest {
  taskName: string
  buildingId: number
  unitId?: number | null
  cycle: InspectionCycleCode
  assignee?: string
  deadline?: string | null
  creator?: string
}

export interface IssueHandleRequest {
  status: 'PROCESSING' | 'RESOLVED'
  handler?: string
  handleNote?: string
}

export interface TaskListParams {
  page?: number
  size?: number
  status?: InspectionTaskFilter | ''
  keyword?: string
  overdue?: boolean
  abnormal?: boolean
}

/** 巡检周期中文映射 */
export const CYCLE_NAME_MAP: Record<InspectionCycleCode, string> = {
  ONCE: '一次性',
  DAILY: '每日',
  WEEKLY: '每周',
  MONTHLY: '每月'
}

/** 任务状态中文映射 */
export const TASK_STATUS_NAME_MAP: Record<InspectionTaskStatusCode, string> = {
  PENDING: '待开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成'
}

/** 检查项中文映射 */
export const CHECK_ITEM_NAME_MAP: Record<CheckItemCode, string> = {
  compartment: '格口',
  screen: '屏幕',
  lock: '门锁'
}

/** 检查结果中文映射 */
export const CHECK_RESULT_NAME_MAP: Record<CheckResultCode, string> = {
  NORMAL: '正常',
  ABNORMAL: '异常',
  NOT_APPLICABLE: '不适用'
}

/** 异常处理状态中文映射 */
export const ISSUE_STATUS_NAME_MAP: Record<IssueStatusCode, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已解决'
}

export const inspectionApi = {
  createTask(data: InspectionCreateRequest) {
    return api.post<InspectionTask>('/inspections', data)
  },

  getTasks(params: TaskListParams) {
    return api.get<PageResponse<InspectionTask>>('/inspections', { params })
  },

  getTask(id: number) {
    return api.get<InspectionTask>(`/inspections/${id}`)
  },

  deleteTask(id: number) {
    return api.delete(`/inspections/${id}`)
  },

  getTaskRecords(id: number) {
    return api.get<InspectionRecord[]>(`/inspections/${id}/records`)
  },

  submitRecords(id: number, items: RecordSubmitItem[]) {
    return api.post<InspectionTask>(`/inspections/${id}/submit`, { items })
  },

  getTaskIssues(id: number, status?: IssueStatusCode | '') {
    return api.get<InspectionIssue[]>(`/inspections/${id}/issues`, {
      params: { status: status || undefined }
    })
  },

  handleIssue(issueId: number, data: IssueHandleRequest) {
    return api.put(`/inspections/issues/${issueId}`, data)
  },

  getCycles() {
    return api.get<Record<InspectionCycleCode, string>>('/inspections/cycles')
  },

  getTaskStatuses() {
    return api.get<Record<InspectionTaskStatusCode, string>>('/inspections/task-statuses')
  }
}

export type { BuildingTreeDTO, UnitDTO }
