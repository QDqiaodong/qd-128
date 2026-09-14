<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { lockerApi, buildingApi, STATUS_NAME_MAP } from '@/api/locker'
import { clearanceApi } from '@/api/clearance'
import { keyBorrowApi, keyHandoverApi } from '@/api/keyBorrow'
import { meterReadingApi } from '@/api/meterReading'
import { repairApi } from '@/api/repair'
import { doorAlarmApi, DEFAULT_THRESHOLD_MINUTES } from '@/api/doorAlarm'
import { collectionSuspensionApi } from '@/api/collectionSuspension'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ClearanceOrder } from '@/api/clearance'
import type { KeyBorrowRecord, KeyHandover } from '@/api/keyBorrow'
import type { MeterReadingRecord } from '@/api/meterReading'
import type { RepairTicket, RepairTicketCreateRequest } from '@/api/repair'
import type { DoorAlarmRecord, DoorAlarmCreateRequest, DoorAlarmCloseRequest } from '@/api/doorAlarm'
import type {
  CollectionSuspensionRecord,
  CollectionSuspensionCreateRequest,
  CollectionSuspensionResumeRequest
} from '@/api/collectionSuspension'
import type {
  LockerDTO,
  AdjustmentRecord,
  StatusChangeRecord,
  BuildingTreeDTO,
  UnitDTO,
  AdjustRequest,
  LockerStatusCode,
  StatusChangeRequest
} from '@/api/locker'

const router = useRouter()
const route = useRoute()
const lockerId = ref(Number(route.params.id))
const locker = ref<LockerDTO | null>(null)
const adjustmentRecords = ref<AdjustmentRecord[]>([])
const statusRecords = ref<StatusChangeRecord[]>([])
const clearanceOrders = ref<ClearanceOrder[]>([])
const keyBorrowRecords = ref<KeyBorrowRecord[]>([])
const keyHandovers = ref<KeyHandover[]>([])
const meterReadings = ref<MeterReadingRecord[]>([])
const repairTickets = ref<RepairTicket[]>([])
const doorAlarms = ref<DoorAlarmRecord[]>([])
const collectionSuspensions = ref<CollectionSuspensionRecord[]>([])
const buildingTree = ref<BuildingTreeDTO[]>([])
const units = ref<UnitDTO[]>([])

const showAdjustDialog = ref(false)
const adjustForm = ref<AdjustRequest>({
  newBuildingId: 0,
  newUnitId: 0,
  reason: '',
  operator: ''
})

// ---------------- 状态 ----------------

const statusTagType = (status?: string) => {
  if (status === 'ACTIVE') return 'success'
  if (status === 'TEMPORARILY_DISABLED') return 'warning'
  if (status === 'PERMANENTLY_DISABLED') return 'info'
  return 'info'
}

const statusLabel = (status?: string) =>
  STATUS_NAME_MAP[status as LockerStatusCode] || '-'

const statusFilter = ref<LockerStatusCode | ''>('')
const statusFilterOptions = Object.entries(STATUS_NAME_MAP).map(([value, label]) => ({
  value: value as LockerStatusCode,
  label
}))

const filteredStatusRecords = computed(() => {
  if (!statusFilter.value) return statusRecords.value
  return statusRecords.value.filter((r) => r.newStatus === statusFilter.value)
})

const statusDialogVisible = ref(false)
const statusAction = ref<'TEMPORARILY_DISABLED' | 'PERMANENTLY_DISABLED' | 'ACTIVE'>(
  'TEMPORARILY_DISABLED'
)
const statusForm = ref<StatusChangeRequest>({
  targetStatus: 'TEMPORARILY_DISABLED',
  reason: '',
  operator: ''
})

const statusDialogTitle = computed(() => {
  if (statusAction.value === 'ACTIVE') return '恢复柜体'
  if (statusAction.value === 'TEMPORARILY_DISABLED') return '临时停用'
  return '永久停用'
})

const openStatusDialog = (
  action: 'TEMPORARILY_DISABLED' | 'PERMANENTLY_DISABLED' | 'ACTIVE'
) => {
  statusAction.value = action
  statusForm.value = { targetStatus: action, reason: '', operator: '' }
  statusDialogVisible.value = true
}

const confirmStatusChange = async () => {
  if (!statusForm.value.reason.trim()) {
    ElMessage.warning('请填写变更原因')
    return
  }
  if (statusAction.value === 'PERMANENTLY_DISABLED') {
    try {
      await ElMessageBox.confirm(
        '永久停用后该柜体将无法再恢复，确认继续？',
        '永久停用确认',
        { type: 'warning', confirmButtonText: '确认永久停用' }
      )
    } catch {
      return
    }
  }
  try {
    await lockerApi.changeLockerStatus(lockerId.value, {
      targetStatus: statusForm.value.targetStatus,
      reason: statusForm.value.reason.trim(),
      operator: statusForm.value.operator?.trim() || undefined
    })
    ElMessage.success('状态变更成功')
    statusDialogVisible.value = false
    await fetchData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '状态变更失败')
  }
}

// ---------------- 数据加载 ----------------

onMounted(async () => {
  await fetchData()
})

const fetchData = async () => {
  try {
    const [lockerRes, recordsRes, statusRes, treeRes, clearanceRes, keyBorrowRes, meterReadingRes, keyHandoverRes, repairRes, doorAlarmRes, suspensionRes] = await Promise.all([
      lockerApi.getLockerById(lockerId.value),
      lockerApi.getAdjustmentRecords(lockerId.value),
      lockerApi.getStatusChangeRecords(lockerId.value),
      buildingApi.getBuildingTree(),
      clearanceApi.getLockerOrders(lockerId.value),
      keyBorrowApi.getLockerRecords(lockerId.value),
      meterReadingApi.getLockerRecords(lockerId.value),
      keyHandoverApi.getLockerHandovers(lockerId.value),
      repairApi.getLockerTickets(lockerId.value),
      doorAlarmApi.getLockerAlarms(lockerId.value),
      collectionSuspensionApi.getLockerRecords(lockerId.value)
    ])
    locker.value = lockerRes.data
    adjustmentRecords.value = recordsRes.data
    statusRecords.value = statusRes.data
    buildingTree.value = treeRes.data
    clearanceOrders.value = clearanceRes.data
    keyBorrowRecords.value = keyBorrowRes.data
    meterReadings.value = meterReadingRes.data
    keyHandovers.value = keyHandoverRes.data
    repairTickets.value = repairRes.data
    doorAlarms.value = doorAlarmRes.data
    collectionSuspensions.value = suspensionRes.data
  } catch (error) {
    console.error('获取数据失败', error)
  }
}

// ---------------- 归属调整 ----------------

const handleBuildingChange = async (buildingId: number) => {
  try {
    const res = await buildingApi.getUnitsByBuilding(buildingId)
    units.value = res.data
    adjustForm.value.newUnitId = 0
  } catch (error) {
    console.error('获取单元失败', error)
  }
}

const handleAdjust = async () => {
  if (!adjustForm.value.newBuildingId || !adjustForm.value.newUnitId) {
    ElMessage.warning('请选择新的楼栋和单元')
    return
  }
  if (!adjustForm.value.reason.trim()) {
    ElMessage.warning('请填写调整原因')
    return
  }
  try {
    await lockerApi.adjustLocker(lockerId.value, {
      ...adjustForm.value,
      reason: adjustForm.value.reason.trim(),
      operator: adjustForm.value.operator?.trim() || undefined
    })
    ElMessage.success('调整成功')
    showAdjustDialog.value = false
    await fetchData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '调整失败')
  }
}

// ---------------- 格口报修 ----------------

const repairDialogVisible = ref(false)
const repairSubmitting = ref(false)
const repairForm = ref<RepairTicketCreateRequest>(emptyRepairForm())

function emptyRepairForm(): RepairTicketCreateRequest {
  return {
    lockerId: lockerId.value,
    compartmentNo: '',
    symptom: '',
    reporter: '',
    remark: ''
  }
}

/** 处理中的报修单占用的格口：同一格口处理中不能重复报修，与后端校验口径一致 */
const repairingCompartments = computed(() =>
  new Set(repairTickets.value.filter((t) => t.processing).map((t) => t.compartmentNo))
)

/** 该柜全部格口编号（1..格口数量），处理中的格口置灰 */
const compartmentOptions = computed(() => {
  const count = locker.value?.compartmentCount || 0
  return Array.from({ length: count }, (_, i) => {
    const no = String(i + 1)
    return { no, repairing: repairingCompartments.value.has(no) }
  })
})

/** 处理中报修条数：与柜体列表/详情的维修中标记同源，均由报修台账实时推导 */
const openRepairCount = computed(
  () => repairTickets.value.filter((t) => t.processing).length
)

const openRepairDialog = () => {
  repairForm.value = emptyRepairForm()
  repairDialogVisible.value = true
}

/** 表单已填写内容时，关闭窗口前确认，避免误关丢单 */
const repairFormDirty = computed(() => {
  const f = repairForm.value
  return !!(f.compartmentNo || f.symptom.trim() || f.reporter.trim() || (f.remark && f.remark.trim()))
})

/**
 * 登记内容只保存在本窗口内，未提交前关闭不会写入任何数据；
 * 已填写内容时关闭需二次确认，关掉窗口不会留下半条报修单。
 */
const handleRepairDialogClose = (done: () => void) => {
  if (!repairFormDirty.value) {
    done()
    return
  }
  ElMessageBox.confirm('关闭后本次填写的内容将丢弃，且不会生成报修单，确认关闭？', '提示', {
    type: 'warning',
    confirmButtonText: '丢弃并关闭',
    cancelButtonText: '继续填写'
  })
    .then(() => done())
    .catch(() => {})
}

const submitRepair = async () => {
  const f = repairForm.value
  if (!f.compartmentNo) {
    ElMessage.warning('请选择故障格口')
    return
  }
  if (!f.symptom.trim()) {
    ElMessage.warning('请填写故障现象')
    return
  }
  if (!f.reporter.trim()) {
    ElMessage.warning('请填写报修人')
    return
  }
  repairSubmitting.value = true
  try {
    await repairApi.createTicket({
      lockerId: lockerId.value,
      compartmentNo: f.compartmentNo,
      symptom: f.symptom.trim(),
      reporter: f.reporter.trim(),
      remark: f.remark?.trim() || undefined
    })
    ElMessage.success('报修登记成功，柜体已标记维修中')
    repairDialogVisible.value = false
    repairForm.value = emptyRepairForm()
    await fetchData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '报修登记失败')
  } finally {
    repairSubmitting.value = false
  }
}

// ---------------- 报修完工 ----------------

const completeDialogVisible = ref(false)
const completing = ref(false)
const completeTarget = ref<RepairTicket | null>(null)
const completeForm = ref({ handler: '', repairResult: '' })

const openCompleteDialog = (ticket: RepairTicket) => {
  completeTarget.value = ticket
  completeForm.value = { handler: '', repairResult: '' }
  completeDialogVisible.value = true
}

/** 完工内容只保存在本窗口内，未提交前关闭不会写入任何数据；已填写内容时关闭需二次确认 */
const completeFormDirty = computed(
  () => !!(completeForm.value.handler.trim() || completeForm.value.repairResult.trim())
)

const handleCompleteDialogClose = (done: () => void) => {
  if (!completeFormDirty.value) {
    done()
    return
  }
  ElMessageBox.confirm('关闭后本次填写的完工内容将丢弃，报修单仍为处理中，确认关闭？', '提示', {
    type: 'warning',
    confirmButtonText: '丢弃并关闭',
    cancelButtonText: '继续填写'
  })
    .then(() => done())
    .catch(() => {})
}

const submitComplete = async () => {
  if (!completeTarget.value) return
  if (!completeForm.value.handler.trim()) {
    ElMessage.warning('完工必须填写处理人')
    return
  }
  if (!completeForm.value.repairResult.trim()) {
    ElMessage.warning('完工必须填写处理结果')
    return
  }
  completing.value = true
  try {
    await repairApi.completeTicket(completeTarget.value.id, {
      handler: completeForm.value.handler.trim(),
      repairResult: completeForm.value.repairResult.trim()
    })
    ElMessage.success('完工登记成功，报修单已修好')
    completeDialogVisible.value = false
    await fetchData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '完工登记失败')
  } finally {
    completing.value = false
  }
}

const handleBack = () => {
  router.push('/lockers')
}

// ---------------- 柜门未关告警 ----------------

/** 未处理告警条数：与柜体列表/详情的柜门未关标记同源，均由告警台账实时推导 */
const openDoorAlarmCount = computed(
  () => doorAlarms.value.filter((a) => a.open).length
)

const doorAlarmDialogVisible = ref(false)
const doorAlarmSubmitting = ref(false)
const doorAlarmForm = ref<DoorAlarmCreateRequest>(emptyDoorAlarmForm())

function emptyDoorAlarmForm(): DoorAlarmCreateRequest {
  return {
    lockerId: lockerId.value,
    doorOpenTime: formatLocalDateTime(new Date()),
    reporter: '',
    thresholdMinutes: DEFAULT_THRESHOLD_MINUTES,
    remark: ''
  }
}

/** 当前时间按本地时区格式化为 yyyy-MM-ddTHH:mm:ss，供 el-date-picker value-format 使用 */
function formatLocalDateTime(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  )
}

const openDoorAlarmDialog = () => {
  doorAlarmForm.value = emptyDoorAlarmForm()
  doorAlarmDialogVisible.value = true
}

/** 表单已填写内容时，关闭窗口前确认，避免误关丢单 */
const doorAlarmFormDirty = computed(() => {
  const f = doorAlarmForm.value
  return !!(f.reporter.trim() || (f.remark && f.remark.trim()))
})

/**
 * 登记内容只保存在本窗口内，未提交前关闭不会写入任何数据；
 * 已填写内容时关闭需二次确认，关掉窗口不会留下半条告警。
 */
const handleDoorAlarmDialogClose = (done: () => void) => {
  if (!doorAlarmFormDirty.value) {
    done()
    return
  }
  ElMessageBox.confirm('关闭后本次填写的内容将丢弃，且不会生成告警记录，确认关闭？', '提示', {
    type: 'warning',
    confirmButtonText: '丢弃并关闭',
    cancelButtonText: '继续填写'
  })
    .then(() => done())
    .catch(() => {})
}

const submitDoorAlarm = async () => {
  const f = doorAlarmForm.value
  if (!f.reporter.trim()) {
    ElMessage.warning('请填写上报人')
    return
  }
  doorAlarmSubmitting.value = true
  try {
    await doorAlarmApi.reportAlarm({
      lockerId: lockerId.value,
      doorOpenTime: f.doorOpenTime || undefined,
      reporter: f.reporter.trim(),
      thresholdMinutes: f.thresholdMinutes || undefined,
      remark: f.remark?.trim() || undefined
    })
    ElMessage.success('柜门未关告警已登记，柜体已标记柜门未关')
    doorAlarmDialogVisible.value = false
    doorAlarmForm.value = emptyDoorAlarmForm()
    await fetchData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '登记柜门未关告警失败')
  } finally {
    doorAlarmSubmitting.value = false
  }
}

// ---------------- 确认已关闭 ----------------

const alarmCloseDialogVisible = ref(false)
const alarmClosing = ref(false)
const alarmCloseTarget = ref<DoorAlarmRecord | null>(null)
const alarmCloseForm = ref<DoorAlarmCloseRequest>({ closeOperator: '', closeNote: '' })

const openAlarmCloseDialog = (alarm: DoorAlarmRecord) => {
  alarmCloseTarget.value = alarm
  alarmCloseForm.value = { closeOperator: '', closeNote: '' }
  alarmCloseDialogVisible.value = true
}

/** 关闭内容只保存在本窗口内，未提交前关闭不会写入任何数据；已填写内容时关闭需二次确认 */
const alarmCloseFormDirty = computed(
  () =>
    !!(
      (alarmCloseForm.value.closeOperator || '').trim() ||
      (alarmCloseForm.value.closeNote || '').trim()
    )
)

const handleAlarmCloseDialogClose = (done: () => void) => {
  if (!alarmCloseFormDirty.value) {
    done()
    return
  }
  ElMessageBox.confirm('关闭后本次填写的内容将丢弃，告警仍为未处理，确认关闭？', '提示', {
    type: 'warning',
    confirmButtonText: '丢弃并关闭',
    cancelButtonText: '继续填写'
  })
    .then(() => done())
    .catch(() => {})
}

const submitAlarmClose = async () => {
  if (!alarmCloseTarget.value) return
  alarmClosing.value = true
  try {
    await doorAlarmApi.closeAlarm(alarmCloseTarget.value.id, {
      closeOperator: alarmCloseForm.value.closeOperator?.trim() || undefined,
      closeNote: alarmCloseForm.value.closeNote?.trim() || undefined
    })
    ElMessage.success('已确认柜门关严，柜体未关标记恢复')
    alarmCloseDialogVisible.value = false
    await fetchData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '确认关闭失败')
  } finally {
    alarmClosing.value = false
  }
}

/** 持续时长展示：不足 1 小时显示分钟，否则显示小时+分钟 */
const formatElapsed = (minutes?: number | null) => {
  if (minutes === null || minutes === undefined) return '-'
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest > 0 ? `${hours} 小时 ${rest} 分` : `${hours} 小时`
}

const formatTime = (t?: string | null) => (t ? t.replace('T', ' ') : '-')

// ---------------- 夜间停收转投 ----------------

/** 停收中记录条数：与柜体列表/详情的停收中标记同源，均由停收台账实时推导 */
const openSuspensionCount = computed(
  () => collectionSuspensions.value.filter((r) => r.suspended).length
)

const suspensionDialogVisible = ref(false)
const suspensionSubmitting = ref(false)
const suspensionForm = ref<CollectionSuspensionCreateRequest>(emptySuspensionForm())

function defaultExpectedResumeTime(): string {
  // 默认预计恢复时间：开始停收时间次日 07:00（夜间停收常见口径）
  const d = new Date()
  d.setDate(d.getDate() + 1)
  d.setHours(7, 0, 0, 0)
  return formatLocalDateTime(d)
}

function emptySuspensionForm(): CollectionSuspensionCreateRequest {
  return {
    lockerId: lockerId.value,
    suspendStartTime: formatLocalDateTime(new Date()),
    expectedResumeTime: defaultExpectedResumeTime(),
    dutyOfficer: '',
    remark: ''
  }
}

const openSuspensionDialog = () => {
  suspensionForm.value = emptySuspensionForm()
  suspensionDialogVisible.value = true
}

/** 表单已填写内容时，关闭窗口前确认，避免误关丢单 */
const suspensionFormDirty = computed(() => {
  const f = suspensionForm.value
  return !!(f.dutyOfficer.trim() || (f.remark && f.remark.trim()))
})

/**
 * 登记内容只保存在本窗口内，未提交前关闭不会写入任何数据；
 * 已填写内容时关闭需二次确认，关掉窗口不会留下半条停收记录。
 */
const handleSuspensionDialogClose = (done: () => void) => {
  if (!suspensionFormDirty.value) {
    done()
    return
  }
  ElMessageBox.confirm('关闭后本次填写的内容将丢弃，且不会生成停收记录，确认关闭？', '提示', {
    type: 'warning',
    confirmButtonText: '丢弃并关闭',
    cancelButtonText: '继续填写'
  })
    .then(() => done())
    .catch(() => {})
}

const submitSuspension = async () => {
  const f = suspensionForm.value
  if (!f.suspendStartTime) {
    ElMessage.warning('请选择开始停收时间')
    return
  }
  if (!f.expectedResumeTime) {
    ElMessage.warning('请选择预计恢复时间')
    return
  }
  if (f.expectedResumeTime <= f.suspendStartTime) {
    ElMessage.warning('预计恢复时间必须晚于开始停收时间')
    return
  }
  if (!f.dutyOfficer.trim()) {
    ElMessage.warning('请填写值班人')
    return
  }
  suspensionSubmitting.value = true
  try {
    await collectionSuspensionApi.register({
      lockerId: lockerId.value,
      suspendStartTime: f.suspendStartTime || undefined,
      expectedResumeTime: f.expectedResumeTime,
      dutyOfficer: f.dutyOfficer.trim(),
      remark: f.remark?.trim() || undefined
    })
    ElMessage.success('夜间停收转投已登记，柜体已标记停收中')
    suspensionDialogVisible.value = false
    suspensionForm.value = emptySuspensionForm()
    await fetchData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '登记夜间停收转投失败')
  } finally {
    suspensionSubmitting.value = false
  }
}

// ---------------- 确认已恢复 ----------------

const resumeDialogVisible = ref(false)
const resuming = ref(false)
const resumeTarget = ref<CollectionSuspensionRecord | null>(null)
const resumeForm = ref<CollectionSuspensionResumeRequest>({ resumeOperator: '', resumeNote: '' })

const openResumeDialog = (record: CollectionSuspensionRecord) => {
  resumeTarget.value = record
  resumeForm.value = { resumeOperator: '', resumeNote: '' }
  resumeDialogVisible.value = true
}

/** 恢复内容只保存在本窗口内，未提交前关闭不会写入任何数据；已填写内容时关闭需二次确认 */
const resumeFormDirty = computed(
  () =>
    !!(
      (resumeForm.value.resumeOperator || '').trim() ||
      (resumeForm.value.resumeNote || '').trim()
    )
)

const handleResumeDialogClose = (done: () => void) => {
  if (!resumeFormDirty.value) {
    done()
    return
  }
  ElMessageBox.confirm('关闭后本次填写的内容将丢弃，该柜仍为停收中，确认关闭？', '提示', {
    type: 'warning',
    confirmButtonText: '丢弃并关闭',
    cancelButtonText: '继续填写'
  })
    .then(() => done())
    .catch(() => {})
}

const submitResume = async () => {
  if (!resumeTarget.value) return
  resuming.value = true
  try {
    await collectionSuspensionApi.resume(resumeTarget.value.id, {
      resumeOperator: resumeForm.value.resumeOperator?.trim() || undefined,
      resumeNote: resumeForm.value.resumeNote?.trim() || undefined
    })
    ElMessage.success('已确认恢复，柜体停收中标记恢复')
    resumeDialogVisible.value = false
    await fetchData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '确认恢复失败')
  } finally {
    resuming.value = false
  }
}

/**
 * 本月有效抄表单（柜体页读数）：与抄表单列表、本月已抄台数同源，
 * 均由有效抄表单实时推导，刷新后保持一致
 */
const currentMonthReading = computed(() =>
  meterReadings.value.find((r) => r.currentMonth && r.active) || null
)
</script>

<template>
  <div class="locker-detail">
    <div class="detail-header">
      <el-button @click="handleBack">返回列表</el-button>
      <span class="detail-title">快递柜详情</span>
    </div>

    <el-card v-if="locker">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="柜体编号">
          <el-tag type="primary">{{ locker.lockerNo }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="规格类型">
          <el-tag>{{ locker.specTypeName || locker.specType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="格口数量">
          {{ locker.compartmentCount }}格
        </el-descriptions-item>
        <el-descriptions-item label="所属楼栋">
          {{ locker.buildingName }}
        </el-descriptions-item>
        <el-descriptions-item label="所属单元">
          {{ locker.unitName }}
        </el-descriptions-item>
        <el-descriptions-item label="楼层">
          {{ locker.floor || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="安装日期">
          {{ locker.installationDate || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag :type="statusTagType(locker.status)">{{ statusLabel(locker.status) }}</el-tag>
          <el-tag v-if="locker.doorAjar" type="danger" style="margin-left: 8px">
            柜门未关（{{ locker.openDoorAlarmCount }} 条）
          </el-tag>
          <el-tag v-if="locker.collectionSuspended" type="warning" style="margin-left: 8px">
            停收中（{{ locker.openCollectionSuspensionCount }} 条）
          </el-tag>
          <el-tag v-if="locker.overdue" type="danger" style="margin-left: 8px">
            滞留中（{{ locker.overduePackageCount }} 件）
          </el-tag>
          <el-tag v-if="locker.keyBorrowed" type="warning" style="margin-left: 8px">
            钥匙借用中
          </el-tag>
          <el-tag v-if="locker.repairing" type="danger" style="margin-left: 8px">
            维修中（{{ locker.openRepairCount }} 单）
          </el-tag>
          <el-tag v-else type="success" style="margin-left: 8px">可用</el-tag>
          <el-tag v-if="locker.meterReadThisMonth" type="success" style="margin-left: 8px">
            本月已抄
          </el-tag>
          <el-tag v-else type="info" style="margin-left: 8px">
            本月未抄
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="本月电表读数" :span="2">
          <template v-if="currentMonthReading">
            {{ currentMonthReading.readingValue }} kWh
            <span class="reading-meta">
              （{{ currentMonthReading.reader }} 抄于 {{ formatTime(currentMonthReading.readingTime) }}）
            </span>
          </template>
          <span v-else>本月未抄</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ formatTime(locker.createTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">
          {{ locker.remark || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <div class="action-bar">
        <el-button type="primary" @click="showAdjustDialog = true">调整归属</el-button>
        <el-button type="danger" plain @click="openRepairDialog">登记报修</el-button>
        <el-button type="warning" plain @click="openDoorAlarmDialog">登记柜门未关</el-button>
        <el-button type="warning" plain @click="openSuspensionDialog">登记停收转投</el-button>
        <template v-if="locker.status === 'ACTIVE'">
          <el-button type="warning" @click="openStatusDialog('TEMPORARILY_DISABLED')">临时停用</el-button>
          <el-button type="danger" @click="openStatusDialog('PERMANENTLY_DISABLED')">永久停用</el-button>
        </template>
        <el-button
          v-else-if="locker.status === 'TEMPORARILY_DISABLED'"
          type="success"
          @click="openStatusDialog('ACTIVE')"
        >恢复使用</el-button>
        <el-button
          v-if="locker.status === 'TEMPORARILY_DISABLED'"
          type="danger"
          @click="openStatusDialog('PERMANENTLY_DISABLED')"
        >永久停用</el-button>
        <el-tag v-if="locker.status === 'PERMANENTLY_DISABLED'" type="info">
          该柜体已永久停用，为终态，不可恢复
        </el-tag>
      </div>
    </el-card>

    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>状态变更记录</span>
          <el-select
            v-model="statusFilter"
            clearable
            placeholder="按变更后状态筛选"
            style="width: 180px"
          >
            <el-option
              v-for="opt in statusFilterOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </div>
      </template>
      <el-table :data="filteredStatusRecords" border v-if="filteredStatusRecords.length > 0">
        <el-table-column prop="id" label="记录ID" width="80" />
        <el-table-column label="变更前状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.oldStatus)">{{ statusLabel(row.oldStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变更后状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.newStatus)">{{ statusLabel(row.newStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="变更原因" min-width="200" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="110">
          <template #default="{ row }">{{ row.operator || '系统管理员' }}</template>
        </el-table-column>
        <el-table-column prop="changeTime" label="变更时间" width="180" />
      </el-table>
      <div v-else class="empty-tip">暂无状态变更记录</div>
    </el-card>

    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>滞留清柜记录</span>
          <el-tag v-if="locker?.overdue" type="danger" size="small">滞留中</el-tag>
        </div>
      </template>
      <el-table :data="clearanceOrders" border v-if="clearanceOrders.length > 0">
        <el-table-column prop="orderNo" label="清柜单号" width="190" />
        <el-table-column prop="overdueCompartments" label="滞留格口" min-width="110" show-overflow-tooltip />
        <el-table-column prop="packageCount" label="件数" width="70" align="center" />
        <el-table-column label="发现时间" width="160">
          <template #default="{ row }">{{ formatTime(row.foundTime) }}</template>
        </el-table-column>
        <el-table-column prop="handler" label="处理人" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.overdue" type="danger">滞留中</el-tag>
            <el-tag v-else type="success">已办结</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="催领" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.openUrge" type="warning" size="small">催领中</el-tag>
            <span v-else-if="row.urgeCount > 0">{{ row.urgeCount }} 次</span>
            <span v-else style="color: #999">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="handleResult" label="处理结果" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.handleResult || '-' }}</template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-tip">暂无滞留清柜记录</div>
    </el-card>

    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>钥匙借用记录</span>
          <el-tag v-if="locker?.keyBorrowed" type="warning" size="small">借用中</el-tag>
        </div>
      </template>
      <el-table :data="keyBorrowRecords" border v-if="keyBorrowRecords.length > 0">
        <el-table-column prop="recordNo" label="台账编号" width="190" />
        <el-table-column prop="borrower" label="借出人" width="100" />
        <el-table-column prop="reason" label="借用事由" min-width="140" show-overflow-tooltip />
        <el-table-column label="借出时间" width="160">
          <template #default="{ row }">{{ formatTime(row.borrowTime) }}</template>
        </el-table-column>
        <el-table-column label="预计归还" width="200">
          <template #default="{ row }">
            {{ formatTime(row.expectedReturnTime) }}
            <el-tooltip
              v-if="row.extendCount > 0"
              :content="`最近改期：${formatTime(row.lastExtendTime)}，原因：${row.lastExtendReason || '-'}`"
              placement="top"
            >
              <el-tag type="warning" size="small" style="margin-left: 4px">改期×{{ row.extendCount }}</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.onLoan" type="warning">借用中</el-tag>
            <el-tag v-else type="success">已归还</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="returner" label="归还人" width="100">
          <template #default="{ row }">{{ row.returner || '-' }}</template>
        </el-table-column>
        <el-table-column label="归还时间" width="160">
          <template #default="{ row }">{{ formatTime(row.returnTime) }}</template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-tip">暂无钥匙借用记录</div>
    </el-card>

    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>钥匙交接痕迹</span>
          <el-tag size="small" type="info">交接不改借用状态，未还条数不变</el-tag>
        </div>
      </template>
      <el-timeline v-if="keyHandovers.length > 0">
        <el-timeline-item
          v-for="h in keyHandovers"
          :key="h.id"
          :timestamp="formatTime(h.createTime)"
          placement="top"
          type="primary"
        >
          <el-card shadow="never">
            <div class="handover-line">
              <el-tag size="small">{{ h.handoverNo }}</el-tag>
              <b>{{ h.handoverFrom }}</b> 交班给 <b>{{ h.handoverTo }}</b>
              <el-tag size="small" type="info">本次点名 {{ h.itemCount }} 柜</el-tag>
            </div>
            <div class="handover-note">交接说明：{{ h.handoverNote }}</div>
            <div v-if="h.items && h.items.length" class="handover-items">
              <el-tag
                v-for="item in h.items.filter((i) => i.lockerId === lockerId)"
                :key="item.id"
                size="small"
                :type="item.onLoan ? 'danger' : 'success'"
                style="margin: 2px"
              >
                {{ item.lockerNo }}（{{ item.onLoan ? '仍借用中' : '已归还' }}，借出人：{{ item.borrower }}）
              </el-tag>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <div v-else class="empty-tip">暂无钥匙交接痕迹</div>
    </el-card>

    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>电表抄表记录</span>
          <el-tag v-if="locker?.meterReadThisMonth" type="success" size="small">本月已抄</el-tag>
          <el-tag v-else type="info" size="small">本月未抄</el-tag>
        </div>
      </template>
      <el-table :data="meterReadings" border v-if="meterReadings.length > 0">
        <el-table-column prop="recordNo" label="抄表单号" width="190" />
        <el-table-column prop="periodMonth" label="账期" width="90" align="center" />
        <el-table-column label="电表读数" width="110" align="right">
          <template #default="{ row }">{{ row.readingValue }} kWh</template>
        </el-table-column>
        <el-table-column prop="reader" label="抄表人" width="100" />
        <el-table-column label="抄表时间" width="160">
          <template #default="{ row }">{{ formatTime(row.readingTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.active" type="success">有效</el-tag>
            <el-tag v-else type="info">已作废</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="作废信息" min-width="180">
          <template #default="{ row }">
            <el-tooltip
              v-if="!row.active"
              :content="`作废人：${row.voidOperator || '-'}，作废时间：${formatTime(row.voidTime)}`"
              placement="top"
            >
              <span>{{ row.voidReason }}</span>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-tip">暂无电表抄表记录</div>
    </el-card>

    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>格口报修记录</span>
          <div class="card-header-tags">
            <el-tag v-if="openRepairCount > 0" type="danger" size="small">
              处理中 {{ openRepairCount }} 条
            </el-tag>
            <el-tag v-else type="success" size="small">可用</el-tag>
            <el-button type="danger" plain size="small" @click="openRepairDialog">登记报修</el-button>
          </div>
        </div>
      </template>
      <el-table :data="repairTickets" border v-if="repairTickets.length > 0">
        <el-table-column prop="ticketNo" label="报修单号" width="190" />
        <el-table-column label="故障格口" width="90" align="center">
          <template #default="{ row }">{{ row.compartmentNo }} 号</template>
        </el-table-column>
        <el-table-column prop="symptom" label="故障现象" min-width="150" show-overflow-tooltip />
        <el-table-column prop="reporter" label="报修人" width="100" />
        <el-table-column label="报修时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.processing" type="danger">处理中</el-tag>
            <el-tag v-else type="success">已修好</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handler" label="处理人" width="100">
          <template #default="{ row }">{{ row.handler || '-' }}</template>
        </el-table-column>
        <el-table-column label="处理结果" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip
              v-if="row.fixedTime"
              :content="`完工时间：${formatTime(row.fixedTime)}`"
              placement="top"
            >
              <span>{{ row.repairResult || '-' }}</span>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.processing"
              size="small"
              type="warning"
              @click="openCompleteDialog(row)"
            >完工</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-tip">暂无格口报修记录</div>
    </el-card>

    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>柜门未关告警记录</span>
          <div class="card-header-tags">
            <el-tag v-if="openDoorAlarmCount > 0" type="danger" size="small">
              未处理 {{ openDoorAlarmCount }} 条
            </el-tag>
            <el-tag v-else type="success" size="small">柜门正常</el-tag>
            <el-button type="warning" plain size="small" @click="openDoorAlarmDialog">
              登记柜门未关
            </el-button>
          </div>
        </div>
      </template>
      <el-table :data="doorAlarms" border v-if="doorAlarms.length > 0">
        <el-table-column prop="alarmNo" label="告警编号" width="190" />
        <el-table-column label="发现未关时间" width="160">
          <template #default="{ row }">{{ formatTime(row.doorOpenTime) }}</template>
        </el-table-column>
        <el-table-column label="约定分钟" width="90" align="center">
          <template #default="{ row }">{{ row.thresholdMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="已持续" width="110">
          <template #default="{ row }">{{ formatElapsed(row.elapsedMinutes) }}</template>
        </el-table-column>
        <el-table-column prop="reporter" label="上报人" width="110" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.open" type="danger">未处理</el-tag>
            <el-tag v-else type="success">已关闭</el-tag>
            <el-tag v-if="row.overtime" type="danger" effect="dark" style="margin-left: 4px">
              已超时
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认关闭" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip
              v-if="row.closeTime"
              :content="`确认关闭时间：${formatTime(row.closeTime)}`"
              placement="top"
            >
              <span>
                {{ row.closeOperator || '-' }}
                <template v-if="row.closeNote">（{{ row.closeNote }}）</template>
              </span>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.open"
              size="small"
              type="warning"
              @click="openAlarmCloseDialog(row)"
            >确认已关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-tip">暂无柜门未关告警记录</div>
    </el-card>

    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>夜间停收转投记录</span>
          <div class="card-header-tags">
            <el-tag v-if="openSuspensionCount > 0" type="warning" size="small">
              停收中 {{ openSuspensionCount }} 条
            </el-tag>
            <el-tag v-else type="success" size="small">收件正常</el-tag>
            <el-button type="warning" plain size="small" @click="openSuspensionDialog">
              登记停收转投
            </el-button>
          </div>
        </div>
      </template>
      <el-table :data="collectionSuspensions" border v-if="collectionSuspensions.length > 0">
        <el-table-column prop="recordNo" label="台账编号" width="190" />
        <el-table-column label="开始停收时间" width="160">
          <template #default="{ row }">{{ formatTime(row.suspendStartTime) }}</template>
        </el-table-column>
        <el-table-column label="预计恢复时间" width="160">
          <template #default="{ row }">{{ formatTime(row.expectedResumeTime) }}</template>
        </el-table-column>
        <el-table-column label="已持续" width="110">
          <template #default="{ row }">{{ formatElapsed(row.elapsedMinutes) }}</template>
        </el-table-column>
        <el-table-column prop="dutyOfficer" label="值班人" width="110" show-overflow-tooltip />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.suspended" type="warning">停收中</el-tag>
            <el-tag v-else type="success">已恢复</el-tag>
            <el-tag v-if="row.overdue" type="danger" effect="dark" style="margin-left: 4px">
              逾时未恢复
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认恢复" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip
              v-if="row.resumeTime"
              :content="`确认恢复时间：${formatTime(row.resumeTime)}`"
              placement="top"
            >
              <span>
                {{ row.resumeOperator || '-' }}
                <template v-if="row.resumeNote">（{{ row.resumeNote }}）</template>
              </span>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.suspended"
              size="small"
              type="warning"
              @click="openResumeDialog(row)"
            >确认已恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-tip">暂无夜间停收转投记录</div>
    </el-card>

    <el-card style="margin-top: 20px;">
      <template #header>归属调整历史</template>
      <el-table :data="adjustmentRecords" border v-if="adjustmentRecords.length > 0">
        <el-table-column prop="id" label="记录ID" width="80" />
        <el-table-column label="调整前" min-width="160">
          <template #default="{ row }">
            {{ row.oldBuildingName || '-' }} {{ row.oldUnitName || '' }}
          </template>
        </el-table-column>
        <el-table-column label="调整后" min-width="160">
          <template #default="{ row }">
            {{ row.newBuildingName }} {{ row.newUnitName }}
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="调整原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="110">
          <template #default="{ row }">{{ row.operator || '-' }}</template>
        </el-table-column>
        <el-table-column prop="adjustTime" label="调整时间" width="180" />
      </el-table>
      <div v-else class="empty-tip">暂无调整记录</div>
    </el-card>

    <!-- 状态变更弹窗 -->
    <el-dialog :title="statusDialogTitle" v-model="statusDialogVisible" width="480px">
      <el-form :model="statusForm" label-width="90px">
        <el-form-item label="当前状态">
          <el-tag :type="statusTagType(locker?.status)">{{ statusLabel(locker?.status) }}</el-tag>
          <span style="margin: 0 8px;">→</span>
          <el-tag :type="statusTagType(statusAction)">{{ statusLabel(statusAction) }}</el-tag>
        </el-form-item>
        <el-form-item label="变更原因" required>
          <el-input
            v-model="statusForm.reason"
            type="textarea"
            :rows="3"
            :placeholder="
              statusAction === 'ACTIVE'
                ? '请填写恢复原因'
                : statusAction === 'TEMPORARILY_DISABLED'
                  ? '请填写临时停用原因（如故障维修、停电等）'
                  : '请填写永久停用原因（如报废、拆除等）'
            "
          />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="statusForm.operator" placeholder="请填写操作人，默认系统管理员" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusDialogVisible = false">取消</el-button>
        <el-button
          :type="statusAction === 'ACTIVE' ? 'success' : statusAction === 'PERMANENTLY_DISABLED' ? 'danger' : 'warning'"
          @click="confirmStatusChange"
        >确认</el-button>
      </template>
    </el-dialog>

    <!-- 归属调整弹窗 -->
    <el-dialog title="调整归属" v-model="showAdjustDialog" width="500px">
      <el-form :model="adjustForm" label-width="100px">
        <el-form-item label="新楼栋">
          <el-select
            v-model="adjustForm.newBuildingId"
            placeholder="请选择楼栋"
            @change="handleBuildingChange"
          >
            <el-option
              v-for="building in buildingTree"
              :key="building.id"
              :label="building.name"
              :value="building.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="新单元">
          <el-select
            v-model="adjustForm.newUnitId"
            placeholder="请选择单元"
            :disabled="!adjustForm.newBuildingId"
          >
            <el-option
              v-for="unit in units"
              :key="unit.id"
              :label="unit.name"
              :value="unit.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input v-model="adjustForm.reason" type="textarea" placeholder="请输入调整原因" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="adjustForm.operator" placeholder="请填写操作人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdjustDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAdjust">确认调整</el-button>
      </template>
    </el-dialog>

    <!-- 登记报修弹窗：未提交前关闭仅丢弃草稿，不会留下半条报修单 -->
    <el-dialog
      title="登记格口报修"
      v-model="repairDialogVisible"
      width="520px"
      :close-on-click-modal="false"
      :before-close="handleRepairDialogClose"
    >
      <el-alert type="warning" :closable="false" class="dialog-tip">
        故障格口、故障现象、报修人必填，未填齐不能建单；提交后柜体标记「维修中」，同一格口处理中不能重复报修。
      </el-alert>
      <el-form :model="repairForm" label-width="90px">
        <el-form-item label="柜体">
          <el-tag type="info">{{ locker?.lockerNo }}</el-tag>
          <span class="repair-locker-meta">{{ locker?.buildingName }} {{ locker?.unitName }}</span>
        </el-form-item>
        <el-form-item label="故障格口" required>
          <el-select
            v-model="repairForm.compartmentNo"
            filterable
            placeholder="请选择故障格口"
            style="width: 100%"
          >
            <el-option
              v-for="opt in compartmentOptions"
              :key="opt.no"
              :value="opt.no"
              :disabled="opt.repairing"
              :label="`${opt.no} 号格口`"
            >
              <span>{{ opt.no }} 号格口</span>
              <el-tag v-if="opt.repairing" type="danger" size="small" class="repair-option-tag">
                处理中
              </el-tag>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="故障现象" required>
          <el-input
            v-model="repairForm.symptom"
            type="textarea"
            :rows="3"
            placeholder="必填，如：门磁失灵，关门后指示灯不亮"
          />
        </el-form-item>
        <el-form-item label="报修人" required>
          <el-input v-model="repairForm.reporter" placeholder="必填，请填写报修人" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="repairForm.remark"
            type="textarea"
            :rows="2"
            placeholder="选填，如联系方式、维保单位等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleRepairDialogClose(() => (repairDialogVisible = false))">取消</el-button>
        <el-button type="primary" :loading="repairSubmitting" @click="submitRepair">提交报修</el-button>
      </template>
    </el-dialog>

    <!-- 报修完工弹窗 -->
    <el-dialog
      title="报修完工"
      v-model="completeDialogVisible"
      width="520px"
      :close-on-click-modal="false"
      :before-close="handleCompleteDialogClose"
    >
      <template v-if="completeTarget">
        <el-alert type="warning" :closable="false" class="dialog-tip">
          完工后报修单状态变为「已修好」；该柜处理中条数相应减少，全部修好后柜体恢复「可用」。
        </el-alert>
        <el-descriptions :column="2" border class="dialog-tip">
          <el-descriptions-item label="报修单号">{{ completeTarget.ticketNo }}</el-descriptions-item>
          <el-descriptions-item label="故障格口">{{ completeTarget.compartmentNo }} 号</el-descriptions-item>
          <el-descriptions-item label="报修人">{{ completeTarget.reporter }}</el-descriptions-item>
          <el-descriptions-item label="报修时间">{{ formatTime(completeTarget.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="故障现象" :span="2">{{ completeTarget.symptom }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px">
          <el-form-item label="处理人" required>
            <el-input v-model="completeForm.handler" placeholder="必填，请填写处理人" />
          </el-form-item>
          <el-form-item label="处理结果" required>
            <el-input
              v-model="completeForm.repairResult"
              type="textarea"
              :rows="3"
              placeholder="必填，如：更换锁芯并调试，开关恢复正常"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="handleCompleteDialogClose(() => (completeDialogVisible = false))">取消</el-button>
        <el-button type="warning" :loading="completing" @click="submitComplete">确认完工</el-button>
      </template>
    </el-dialog>

    <!-- 登记柜门未关弹窗：未提交前关闭仅丢弃草稿，不会留下半条告警 -->
    <el-dialog
      title="登记柜门未关"
      v-model="doorAlarmDialogVisible"
      width="520px"
      :close-on-click-modal="false"
      :before-close="handleDoorAlarmDialogClose"
    >
      <el-alert type="warning" :closable="false" class="dialog-tip">
        上报人必填；提交后柜体标记「柜门未关」，超过约定分钟仍未关严将在告警台账标记超时；
        同一柜已有未处理告警时不能重复登记。
      </el-alert>
      <el-form :model="doorAlarmForm" label-width="110px">
        <el-form-item label="柜体">
          <el-tag type="info">{{ locker?.lockerNo }}</el-tag>
          <span class="repair-locker-meta">{{ locker?.buildingName }} {{ locker?.unitName }}</span>
        </el-form-item>
        <el-form-item label="发现未关时间" required>
          <el-date-picker
            v-model="doorAlarmForm.doorOpenTime"
            type="datetime"
            placeholder="默认当前时间，补登可选过去时间"
            style="width: 100%"
            value-format="YYYY-MM-DD[T]HH:mm:ss"
            :disabled-date="(d: Date) => d.getTime() > Date.now()"
          />
        </el-form-item>
        <el-form-item label="上报人" required>
          <el-input v-model="doorAlarmForm.reporter" placeholder="必填，如：物业巡柜-老周" />
        </el-form-item>
        <el-form-item label="约定关严分钟">
          <el-input-number
            v-model="doorAlarmForm.thresholdMinutes"
            :min="1"
            :max="1440"
            style="width: 160px"
          />
          <span class="repair-locker-meta">超过该时长仍未关严即标记超时，默认 10 分钟</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="doorAlarmForm.remark"
            type="textarea"
            :rows="2"
            placeholder="选填，如：早高峰取件后柜门虚掩"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDoorAlarmDialogClose(() => (doorAlarmDialogVisible = false))">取消</el-button>
        <el-button type="primary" :loading="doorAlarmSubmitting" @click="submitDoorAlarm">提交登记</el-button>
      </template>
    </el-dialog>

    <!-- 确认柜门已关闭弹窗 -->
    <el-dialog
      title="确认柜门已关闭"
      v-model="alarmCloseDialogVisible"
      width="520px"
      :close-on-click-modal="false"
      :before-close="handleAlarmCloseDialogClose"
    >
      <template v-if="alarmCloseTarget">
        <el-alert type="warning" :closable="false" class="dialog-tip">
          确认后该告警状态变为「已关闭」，柜体「柜门未关」标记恢复；台账中仍是同一条记录，按已关闭可查到。
        </el-alert>
        <el-descriptions :column="2" border class="dialog-tip">
          <el-descriptions-item label="告警编号">{{ alarmCloseTarget.alarmNo }}</el-descriptions-item>
          <el-descriptions-item label="发现未关时间">{{ formatTime(alarmCloseTarget.doorOpenTime) }}</el-descriptions-item>
          <el-descriptions-item label="约定分钟">{{ alarmCloseTarget.thresholdMinutes }} 分钟</el-descriptions-item>
          <el-descriptions-item label="上报人">{{ alarmCloseTarget.reporter }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px">
          <el-form-item label="确认关闭人">
            <el-input v-model="alarmCloseForm.closeOperator" placeholder="选填，默认系统管理员" />
          </el-form-item>
          <el-form-item label="关闭说明">
            <el-input
              v-model="alarmCloseForm.closeNote"
              type="textarea"
              :rows="3"
              placeholder="选填，如：现场核实柜门已关严，格口无遗留件"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="handleAlarmCloseDialogClose(() => (alarmCloseDialogVisible = false))">取消</el-button>
        <el-button type="warning" :loading="alarmClosing" @click="submitAlarmClose">确认已关闭</el-button>
      </template>
    </el-dialog>

    <!-- 登记夜间停收转投弹窗：未提交前关闭仅丢弃草稿，不会留下半条停收记录 -->
    <el-dialog
      title="登记夜间停收转投"
      v-model="suspensionDialogVisible"
      width="520px"
      :close-on-click-modal="false"
      :before-close="handleSuspensionDialogClose"
    >
      <el-alert type="warning" :closable="false" class="dialog-tip">
        开始停收时间、预计恢复时间、值班人必填；提交后柜体标记「停收中」，正在停收的柜会出现在停收台账；
        同一柜已有停收中记录时不能重复登记。
      </el-alert>
      <el-form :model="suspensionForm" label-width="110px">
        <el-form-item label="柜体">
          <el-tag type="info">{{ locker?.lockerNo }}</el-tag>
          <span class="repair-locker-meta">{{ locker?.buildingName }} {{ locker?.unitName }}</span>
        </el-form-item>
        <el-form-item label="开始停收时间" required>
          <el-date-picker
            v-model="suspensionForm.suspendStartTime"
            type="datetime"
            placeholder="默认当前时间，补登可选过去时间"
            style="width: 100%"
            value-format="YYYY-MM-DD[T]HH:mm:ss"
            :disabled-date="(d: Date) => d.getTime() > Date.now()"
          />
        </el-form-item>
        <el-form-item label="预计恢复时间" required>
          <el-date-picker
            v-model="suspensionForm.expectedResumeTime"
            type="datetime"
            placeholder="必填，如次日 07:00"
            style="width: 100%"
            value-format="YYYY-MM-DD[T]HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="值班人" required>
          <el-input v-model="suspensionForm.dutyOfficer" placeholder="必填，如：夜班-陈师傅" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="suspensionForm.remark"
            type="textarea"
            :rows="2"
            placeholder="选填，如转投柜位置、夜间联系方式等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleSuspensionDialogClose(() => (suspensionDialogVisible = false))">取消</el-button>
        <el-button type="primary" :loading="suspensionSubmitting" @click="submitSuspension">提交登记</el-button>
      </template>
    </el-dialog>

    <!-- 确认已恢复弹窗 -->
    <el-dialog
      title="确认已恢复"
      v-model="resumeDialogVisible"
      width="520px"
      :close-on-click-modal="false"
      :before-close="handleResumeDialogClose"
    >
      <template v-if="resumeTarget">
        <el-alert type="warning" :closable="false" class="dialog-tip">
          确认后该记录状态变为「已恢复」，柜体「停收中」标记恢复；台账中仍是同一条记录，按已恢复可查到。
        </el-alert>
        <el-descriptions :column="2" border class="dialog-tip">
          <el-descriptions-item label="台账编号">{{ resumeTarget.recordNo }}</el-descriptions-item>
          <el-descriptions-item label="开始停收时间">{{ formatTime(resumeTarget.suspendStartTime) }}</el-descriptions-item>
          <el-descriptions-item label="预计恢复时间">{{ formatTime(resumeTarget.expectedResumeTime) }}</el-descriptions-item>
          <el-descriptions-item label="值班人">{{ resumeTarget.dutyOfficer }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px">
          <el-form-item label="确认恢复人">
            <el-input v-model="resumeForm.resumeOperator" placeholder="选填，默认系统管理员" />
          </el-form-item>
          <el-form-item label="恢复说明">
            <el-input
              v-model="resumeForm.resumeNote"
              type="textarea"
              :rows="3"
              placeholder="选填，如：告示已撕，恢复正常收件"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="handleResumeDialogClose(() => (resumeDialogVisible = false))">取消</el-button>
        <el-button type="warning" :loading="resuming" @click="submitResume">确认已恢复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.locker-detail {
  padding: 16px 0;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.detail-title {
  font-size: 20px;
  font-weight: 600;
  color: #333;
}

.action-bar {
  margin-top: 20px;
  display: flex;
  gap: 12px;
  align-items: center;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header-tags {
  display: flex;
  align-items: center;
  gap: 8px;
}

.repair-locker-meta {
  margin-left: 8px;
  color: #999;
  font-size: 12px;
}

.repair-option-tag {
  float: right;
}

.dialog-tip {
  margin-bottom: 16px;
}

.reading-meta {
  color: #999;
  font-size: 12px;
}

.handover-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.handover-note {
  margin-top: 8px;
  color: #555;
  font-size: 13px;
}

.handover-items {
  margin-top: 8px;
}

.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px;
}
</style>
