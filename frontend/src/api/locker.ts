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
  installLocation: string
  installationDate: string
  status: string
  remark: string
  createdAt: string
  updatedAt: string
}

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
  createdAt: string
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

export interface Archive {
  id: number
  name: string
  filterParams: string
  lockerCount: number
  createdBy: string
  createdAt: string
}

export const lockerApi = {
  getLockers(page: number = 1, size: number = 20, keyword?: string) {
    return api.get<PageResponse<LockerDTO>>('/lockers', { params: { page, size, keyword } })
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

  getSpecTypes() {
    return api.get<Record<string, string>>('/lockers/spec-types')
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

  createBuilding(data: { name: string; code: string; sortOrder?: number }) {
    return api.post('/buildings', data)
  },

  updateBuilding(id: number, data: { name: string; code: string; sortOrder?: number }) {
    return api.put(`/buildings/${id}`, data)
  },

  deleteBuilding(id: number) {
    return api.delete(`/buildings/${id}`)
  },

  createUnit(data: { buildingId: number; name: string; code?: string; sortOrder?: number }) {
    return api.post('/units', data)
  },

  updateUnit(id: number, data: { name: string; code?: string; sortOrder?: number }) {
    return api.put(`/units/${id}`, data)
  },

  deleteUnit(id: number) {
    return api.delete(`/units/${id}`)
  }
}

export const archiveApi = {
  createArchive(data: { name: string; filterParams: string; lockerCount: number; createdBy: string; lockerIds: number[] }) {
    return api.post<Archive>('/archives', data)
  },

  createArchiveFromFilter(data: { filterRequest: FilterRequest; name: string; createdBy: string }) {
    return api.post<Archive>('/archives/from-filter', data)
  },

  getArchives() {
    return api.get<Archive[]>('/archives')
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
