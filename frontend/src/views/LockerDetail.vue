<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { lockerApi, buildingApi, STATUS_NAME_MAP } from '@/api/locker'
import { clearanceApi } from '@/api/clearance'
import { keyBorrowApi, keyHandoverApi } from '@/api/keyBorrow'
import { meterReadingApi } from '@/api/meterReading'
import { repairApi } from '@/api/repair'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ClearanceOrder } from '@/api/clearance'
import type { KeyBorrowRecord, KeyHandover } from '@/api/keyBorrow'
import type { MeterReadingRecord } from '@/api/meterReading'
import type { RepairTicket, RepairTicketCreateRequest } from '@/api/repair'
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
    const [lockerRes, recordsRes, statusRes, treeRes, clearanceRes, keyBorrowRes, meterReadingRes, keyHandoverRes, repairRes] = await Promise.all([
      lockerApi.getLockerById(lockerId.value),
      lockerApi.getAdjustmentRecords(lockerId.value),
      lockerApi.getStatusChangeRecords(lockerId.value),
      buildingApi.getBuildingTree(),
      clearanceApi.getLockerOrders(lockerId.value),
      keyBorrowApi.getLockerRecords(lockerId.value),
      meterReadingApi.getLockerRecords(lockerId.value),
      keyHandoverApi.getLockerHandovers(lockerId.value),
      repairApi.getLockerTickets(lockerId.value)
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

const formatTime = (t?: string | null) => (t ? t.replace('T', ' ') : '-')

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
