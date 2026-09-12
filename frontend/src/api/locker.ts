import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

export interface LockerDTO {
  id: number
  lockerNo: string
  compartmentCount: number
  specType: string
  specTypeName: string
  buildingId: number
  buildingName: string
  unitId: number
  unitName: string
  floor: string
  installationDate: string
  status: LockerStatusCode
  statusName: string
  /** 归档快照时刻的状态（可能与当前 status 不同） */
  snapshotStatus?: LockerStatusCode
  snapshotStatusName?: string
  fromSnapshot?: boolean
  /** 归档详情中冗余的当前实时状态 */
  currentStatus?: LockerStatusCode
  currentStatusName?: string
  /** 是否有办理中的滞留清柜单（滞留中标记） */
  overdue?: boolean
  /** 办理中的清柜单数 */
  openClearanceCount?: number
  /** 办理中清柜单的滞留件数合计 */
  overduePackageCount?: number
  /** 是否有借用中的钥匙借用记录（借用中标记） */
  keyBorrowed?: boolean
  /** 未还借用条数（借用中记录数） */
  openKeyBorrowCount?: number
  /** 本月是否已抄电表（已抄/未抄标记，由有效抄表单实时推导） */
  meterReadThisMonth?: boolean
  remark: string
  createTime: string
  updateTime: string
}

export type LockerStatusCode = 'ACTIVE' | 'TEMPORARILY_DISABLED' | 'PERMANENTLY_DISABLED'

export interface PageResponse<T> {
  data: T[]
  total: number
  page: number
  size: number
}

export interface FilterRequest {
  buildingIds?: number[]
  unitIds?: number[]
  specTypes?: string[]
  /** 生命周期状态过滤；不传时多条件筛选默认只返回正常柜体 */
  statuses?: LockerStatusCode[]
  startDate?: string
  endDate?: string
  page?: number
  size?: number
}

export interface LockerCreateRequest {
  lockerNo: string
  compartmentCount: number
  specType: string
  buildingId: number
  unitId: number
  floor?: string
  installationDate?: string
  remark?: string
}

export interface LockerUpdateRequest {
  lockerNo?: string
  compartmentCount?: number
  specType?: string
  buildingId?: number
  unitId?: number
  floor?: string
  installationDate?: string
  remark?: string
}

export interface AdjustRequest {
  newBuildingId: number
  newUnitId: number
  reason: string
  operator?: string
}

export interface AdjustmentRecord {
  id: number
  lockerId: number
  oldBuildingId: number
  oldUnitId: number
  oldBuildingName: string
  oldUnitName: string
  newBuildingId: number
  newUnitId: number
  newBuildingName: string
  newUnitName: string
  reason: string
  operator: string
  adjustTime: string
}

export interface StatusChangeRequest {
  targetStatus: LockerStatusCode
  reason: string
  operator?: string
}

export interface StatusChangeRecord {
  id: number
  lockerId: number
  oldStatus: LockerStatusCode
  newStatus: LockerStatusCode
  oldStatusName?: string
  newStatusName?: string
  reason: string
  operator: string
  changeTime: string
}

export interface BuildingTreeDTO {
  id: number
  name: string
  code: string
  children: UnitDTO[]
}

export interface UnitDTO {
  id: number
  name: string
  code: string
  buildingId: number
}

/** 物业层级删除前的关联影响统计 */
export interface HierarchyReference {
  id: number
  type: 'BUILDING' | 'UNIT'
  /** 关联快递柜数量 */
  lockerCount: number
  /** 关联归档快照数量（去重） */
  archiveCount: number
  /** 楼栋下的单元数量（仅楼栋） */
  unitCount: number
  /** 是否允许删除 */
  deletable: boolean
}

export interface Archive {
  id: number
  archiveName: string
  filterConditions: string
  resultCount: number
  operator: string
  createTime: string
}

/** 状态码 -> 中文名称（与后端 LockerStatus 保持一致） */
export const STATUS_NAME_MAP: Record<LockerStatusCode, string> = {
  ACTIVE: '正常',
  TEMPORARILY_DISABLED: '临时停用',
  PERMANENTLY_DISABLED: '永久停用'
}

export const lockerApi = {
  getLockers(page: number = 1, size: number = 20, statuses?: LockerStatusCode[]) {
    return api.get<PageResponse<LockerDTO>>('/lockers', {
      params: { page, size, statuses: statuses && statuses.length ? statuses.join(',') : undefined },
      paramsSerializer: {
        serialize: (p: Record<string, unknown>) => {
          const sp = new URLSearchParams()
          Object.entries(p).forEach(([k, v]) => {
            if (v === undefined || v === null || v === '') return
            if (k === 'statuses' && typeof v === 'string') {
              v.split(',').forEach((s) => sp.append(k, s))
            } else {
              sp.append(k, String(v))
            }
          })
          return sp.toString()
        }
      }
    })
  },

  getLockerById(id: number) {
    return api.get<LockerDTO>(`/lockers/${id}`)
  },

  createLocker(data: LockerCreateRequest) {
    return api.post<LockerDTO>('/lockers', data)
  },

  updateLocker(id: number, data: LockerUpdateRequest) {
    return api.put<LockerDTO>(`/lockers/${id}`, data)
  },

  deleteLocker(id: number) {
    return api.delete(`/lockers/${id}`)
  },

  filterLockers(data: FilterRequest) {
    return api.post<PageResponse<LockerDTO>>('/lockers/filter', data)
  },

  adjustLocker(id: number, data: AdjustRequest) {
    return api.post<AdjustmentRecord>(`/lockers/${id}/adjust`, data)
  },

  getAdjustmentRecords(id: number) {
    return api.get<AdjustmentRecord[]>(`/lockers/${id}/adjustments`)
  },

  changeLockerStatus(id: number, data: StatusChangeRequest) {
    return api.post<StatusChangeRecord>(`/lockers/${id}/status`, data)
  },

  getStatusChangeRecords(id: number, status?: LockerStatusCode) {
    return api.get<StatusChangeRecord[]>(`/lockers/${id}/status-changes`, {
      params: { status }
    })
  },

  getSpecTypes() {
    return api.get<Record<string, string>>('/lockers/spec-types')
  },

  getStatuses() {
    return api.get<Record<LockerStatusCode, string>>('/lockers/statuses')
  },

  countLockers() {
    return api.get<number>('/lockers/count')
  },

  countLockersByBuilding(buildingId: number) {
    return api.get<number>(`/lockers/count/building/${buildingId}`)
  }
}

export const buildingApi = {
  getBuildingTree() {
    return api.get<BuildingTreeDTO[]>('/buildings')
  },

  getUnitsByBuilding(buildingId: number) {
    return api.get<UnitDTO[]>(`/buildings/${buildingId}/units`)
  },

  createBuilding(data: { name: string; code?: string; sortOrder?: number }) {
    return api.post('/buildings', data)
  },

  updateBuilding(id: number, data: { name: string; code?: string; sortOrder?: number }) {
    return api.put(`/buildings/${id}`, data)
  },

  deleteBuilding(id: number) {
    return api.delete(`/buildings/${id}`)
  },

  getBuildingReferences(id: number) {
    return api.get<HierarchyReference>(`/buildings/${id}/references`)
  },

  createUnit(data: { buildingId: number; name: string; code?: string; sortOrder?: number }) {
    return api.post('/units', data)
  },

  updateUnit(id: number, data: { name: string; code?: string; sortOrder?: number }) {
    return api.put(`/units/${id}`, data)
  },

  deleteUnit(id: number) {
    return api.delete(`/units/${id}`)
  },

  getUnitReferences(id: number) {
    return api.get<HierarchyReference>(`/units/${id}/references`)
  }
}

export const archiveApi = {
  createArchive(data: {
    archiveName: string
    filterConditions?: string
    resultCount: number
    operator?: string
    lockerIds: number[]
  }) {
    return api.post<Archive>('/archives', data)
  },

  createArchiveFromFilter(data: {
    filterRequest: FilterRequest
    archiveName: string
    operator?: string
  }) {
    return api.post<Archive>('/archives/from-filter', data)
  },

  getArchives() {
    return api.get<PageResponse<Archive>>('/archives')
  },

  getArchiveById(id: number) {
    return api.get<Archive>(`/archives/${id}`)
  },

  deleteArchive(id: number) {
    return api.delete(`/archives/${id}`)
  },

  getArchiveLockers(id: number) {
    return api.get<LockerDTO[]>(`/archives/${id}/lockers`)
  }
}
