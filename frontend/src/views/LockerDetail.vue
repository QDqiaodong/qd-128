<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { lockerApi, buildingApi, STATUS_NAME_MAP } from '@/api/locker'
import { clearanceApi } from '@/api/clearance'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ClearanceOrder } from '@/api/clearance'
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
    const [lockerRes, recordsRes, statusRes, treeRes, clearanceRes] = await Promise.all([
      lockerApi.getLockerById(lockerId.value),
      lockerApi.getAdjustmentRecords(lockerId.value),
      lockerApi.getStatusChangeRecords(lockerId.value),
      buildingApi.getBuildingTree(),
      clearanceApi.getLockerOrders(lockerId.value)
    ])
    locker.value = lockerRes.data
    adjustmentRecords.value = recordsRes.data
    statusRecords.value = statusRes.data
    buildingTree.value = treeRes.data
    clearanceOrders.value = clearanceRes.data
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

const handleBack = () => {
  router.push('/lockers')
}

const formatTime = (t?: string | null) => (t ? t.replace('T', ' ') : '-')
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
        <el-table-column prop="handleResult" label="处理结果" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.handleResult || '-' }}</template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-tip">暂无滞留清柜记录</div>
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

.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px;
}
</style>
