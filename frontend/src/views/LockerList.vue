<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { lockerApi, STATUS_NAME_MAP } from '@/api/locker'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { LockerDTO, PageResponse, LockerStatusCode, StatusChangeRequest } from '@/api/locker'

const router = useRouter()
const tableData = ref<LockerDTO[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const statusFilter = ref<LockerStatusCode[]>([])

const statusOptions = Object.entries(STATUS_NAME_MAP).map(([value, label]) => ({
  value: value as LockerStatusCode,
  label
}))

const statusTagType = (status?: string) => {
  if (status === 'ACTIVE') return 'success'
  if (status === 'TEMPORARILY_DISABLED') return 'warning'
  if (status === 'PERMANENTLY_DISABLED') return 'info'
  return 'info'
}

const statusLabel = (status?: string) =>
  STATUS_NAME_MAP[status as LockerStatusCode] || '-'

onMounted(async () => {
  await fetchLockers()
})

const fetchLockers = async () => {
  try {
    const res = await lockerApi.getLockers(currentPage.value, pageSize.value, statusFilter.value)
    const data = res.data as PageResponse<LockerDTO>
    tableData.value = data.data
    total.value = data.total
  } catch (error) {
    console.error('获取快递柜列表失败', error)
  }
}

const handleFilter = () => {
  currentPage.value = 1
  fetchLockers()
}

const handleReset = () => {
  statusFilter.value = []
  currentPage.value = 1
  fetchLockers()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchLockers()
}

const handleEdit = (id: number) => {
  router.push(`/lockers/${id}/edit`)
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认删除该快递柜？删除后不可恢复。', '提示', {
      type: 'warning'
    })
    await lockerApi.deleteLocker(id)
    ElMessage.success('删除成功')
    fetchLockers()
  } catch (error: any) {
    if (error !== 'cancel') ElMessage.error('删除失败')
  }
}

const handleView = (id: number) => {
  router.push(`/lockers/${id}`)
}

// ---------------- 状态变更 ----------------

const statusDialogVisible = ref(false)
const currentLocker = ref<LockerDTO | null>(null)
const statusAction = ref<'TEMPORARILY_DISABLED' | 'PERMANENTLY_DISABLED' | 'ACTIVE'>(
  'TEMPORARILY_DISABLED'
)
const statusForm = ref<StatusChangeRequest>({
  targetStatus: 'TEMPORARILY_DISABLED',
  reason: '',
  operator: ''
})

const dialogTitle = () => {
  if (statusAction.value === 'ACTIVE') return '恢复柜体'
  if (statusAction.value === 'TEMPORARILY_DISABLED') return '临时停用'
  return '永久停用'
}

const openStatusDialog = (
  locker: LockerDTO,
  action: 'TEMPORARILY_DISABLED' | 'PERMANENTLY_DISABLED' | 'ACTIVE'
) => {
  currentLocker.value = locker
  statusAction.value = action
  statusForm.value = { targetStatus: action, reason: '', operator: '' }
  statusDialogVisible.value = true
}

const confirmStatusChange = async () => {
  if (!currentLocker.value) return
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
    await lockerApi.changeLockerStatus(currentLocker.value.id, {
      targetStatus: statusForm.value.targetStatus,
      reason: statusForm.value.reason.trim(),
      operator: statusForm.value.operator?.trim() || undefined
    })
    ElMessage.success('状态变更成功')
    statusDialogVisible.value = false
    await fetchLockers()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '状态变更失败')
  }
}
</script>

<template>
  <div class="locker-list">
    <div class="list-header">
      <div class="search-box">
        <el-select
          v-model="statusFilter"
          multiple
          collapse-tags
          collapse-tags-tooltip
          clearable
          placeholder="按状态筛选"
          style="width: 240px"
          @change="handleFilter"
        >
          <el-option
            v-for="opt in statusOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <el-button type="success" @click="router.push('/lockers/create')">新增快递柜</el-button>
    </div>

    <el-table :data="tableData" border>
      <el-table-column prop="lockerNo" label="柜体编号" width="120">
        <template #default="{ row }">
          <el-tag type="info">{{ row.lockerNo }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="规格类型" width="120">
        <template #default="{ row }">
          <el-tag>{{ row.specTypeName || row.specType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="compartmentCount" label="格口数量" width="90" />
      <el-table-column prop="buildingName" label="所属楼栋" width="100" />
      <el-table-column prop="unitName" label="所属单元" width="90" />
      <el-table-column prop="floor" label="楼层" width="80">
        <template #default="{ row }">{{ row.floor || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="200">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          <el-tag v-if="row.overdue" type="danger" style="margin-left: 4px">滞留中</el-tag>
          <el-tag v-if="row.keyBorrowed" type="warning" style="margin-left: 4px">借用中</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="170">
        <template #default="{ row }">{{ row.updateTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleView(row.id)">详情</el-button>
          <el-button size="small" @click="handleEdit(row.id)">编辑</el-button>
          <el-button
            v-if="row.status === 'ACTIVE'"
            size="small"
            type="warning"
            @click="openStatusDialog(row, 'TEMPORARILY_DISABLED')"
          >临时停用</el-button>
          <el-button
            v-if="row.status === 'TEMPORARILY_DISABLED'"
            size="small"
            type="success"
            @click="openStatusDialog(row, 'ACTIVE')"
          >恢复</el-button>
          <el-button
            v-if="row.status !== 'PERMANENTLY_DISABLED'"
            size="small"
            type="danger"
            @click="openStatusDialog(row, 'PERMANENTLY_DISABLED')"
          >永久停用</el-button>
          <el-button size="small" type="danger" plain @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      :current-page="currentPage"
      :page-size="pageSize"
      :total="total"
      @current-change="handlePageChange"
      layout="total, prev, pager, next, jumper"
    />
    <div v-else class="empty-tip">暂无数据</div>

    <el-dialog :title="dialogTitle()" v-model="statusDialogVisible" width="480px">
      <el-form :model="statusForm" label-width="90px">
        <el-form-item label="柜体编号">
          <el-tag type="info">{{ currentLocker?.lockerNo }}</el-tag>
          <span style="margin-left: 12px; color: #999">
            当前状态：{{ statusLabel(currentLocker?.status) }}
          </span>
        </el-form-item>
        <el-form-item label="目标状态">
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
  </div>
</template>

<style scoped>
.locker-list {
  padding: 16px 0;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.search-box {
  display: flex;
  gap: 12px;
}

.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px;
}
</style>
