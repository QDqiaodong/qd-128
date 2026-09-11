<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  inspectionApi,
  TASK_STATUS_NAME_MAP
} from '@/api/inspection'
import type {
  InspectionTask,
  InspectionTaskFilter,
  PageResponse
} from '@/api/inspection'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()

const tableData = ref<InspectionTask[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 筛选：任务状态（含 INCOMPLETE 未完成）、逾期、异常、关键字
const statusFilter = ref<InspectionTaskFilter | ''>('')
const overdueOnly = ref(false)
const abnormalOnly = ref(false)
const keyword = ref('')

const statusOptions = [
  { value: 'INCOMPLETE', label: '未完成' },
  ...Object.entries(TASK_STATUS_NAME_MAP).map(([value, label]) => ({ value, label }))
]

const statusTagType = (row: InspectionTask) => {
  if (row.status === 'COMPLETED') return 'success'
  if (row.overdue) return 'danger'
  if (row.status === 'IN_PROGRESS') return 'warning'
  return 'info'
}

const statusLabel = (row: InspectionTask) => {
  if (row.overdue && row.status !== 'COMPLETED') return '已逾期'
  return TASK_STATUS_NAME_MAP[row.status] || '-'
}

onMounted(() => {
  fetchTasks()
})

const fetchTasks = async () => {
  try {
    const res = await inspectionApi.getTasks({
      page: currentPage.value,
      size: pageSize.value,
      status: statusFilter.value || undefined,
      overdue: overdueOnly.value || undefined,
      abnormal: abnormalOnly.value || undefined,
      keyword: keyword.value.trim() || undefined
    })
    const data = res.data as PageResponse<InspectionTask>
    tableData.value = data.data
    total.value = Number(data.total)
  } catch (error) {
    console.error('获取巡检任务列表失败', error)
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchTasks()
}

const handleReset = () => {
  statusFilter.value = ''
  overdueOnly.value = false
  abnormalOnly.value = false
  keyword.value = ''
  currentPage.value = 1
  fetchTasks()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchTasks()
}

const handleView = (id: number) => {
  router.push(`/inspections/${id}`)
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认删除该巡检任务？任务下的巡检明细与异常记录将一并删除。', '提示', {
      type: 'warning'
    })
    await inspectionApi.deleteTask(id)
    ElMessage.success('删除成功')
    await fetchTasks()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  }
}

const formatDeadline = (t: string | null) => t || '-'
</script>

<template>
  <div class="inspection-list">
    <div class="list-header">
      <div class="search-box">
        <el-select
          v-model="statusFilter"
          clearable
          placeholder="任务状态"
          style="width: 140px"
          @change="handleSearch"
        >
          <el-option
            v-for="opt in statusOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-checkbox v-model="overdueOnly" @change="handleSearch">仅看逾期</el-checkbox>
        <el-checkbox v-model="abnormalOnly" @change="handleSearch">仅看异常</el-checkbox>
        <el-input
          v-model="keyword"
          clearable
          placeholder="任务名称 / 负责人"
          style="width: 200px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <el-button type="success" @click="router.push('/inspections/create')">发起巡检任务</el-button>
    </div>

    <el-table :data="tableData" border>
      <el-table-column prop="id" label="任务ID" width="80" />
      <el-table-column prop="taskName" label="任务名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="巡检范围" min-width="150">
        <template #default="{ row }">
          {{ row.buildingName }}<span v-if="row.unitName"> / {{ row.unitName }}</span>
          <el-tag v-else size="small" type="info" effect="plain">整栋</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="cycleName" label="周期" width="80" />
      <el-table-column prop="assignee" label="负责人" width="100">
        <template #default="{ row }">{{ row.assignee || '-' }}</template>
      </el-table-column>
      <el-table-column label="截止时间" width="170">
        <template #default="{ row }">
          <span :class="{ 'overdue-text': row.overdue }">{{ formatDeadline(row.deadline) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="巡检进度" width="170">
        <template #default="{ row }">
          <el-progress
            :percentage="row.progress"
            :status="row.status === 'COMPLETED' ? 'success' : row.overdue ? 'exception' : undefined"
          />
          <div class="progress-text">{{ row.completedLockers }}/{{ row.totalLockers }} 台</div>
        </template>
      </el-table-column>
      <el-table-column label="异常" width="110">
        <template #default="{ row }">
          <el-badge v-if="row.pendingIssueCount > 0" :value="row.pendingIssueCount" type="danger">
            <el-tag type="danger" size="small">异常 {{ row.abnormalCount }}</el-tag>
          </el-badge>
          <el-tag v-else-if="row.abnormalCount > 0" type="success" size="small">
            异常 {{ row.abnormalCount }}（已处理）
          </el-tag>
          <span v-else class="normal-text">无异常</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row)">{{ statusLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="handleView(row.id)">详情/巡检</el-button>
          <el-button size="small" type="danger" plain @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      :current-page="currentPage"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next, jumper"
      @current-change="handlePageChange"
    />
    <div v-else class="empty-tip">暂无巡检任务，点击右上角「发起巡检任务」</div>
  </div>
</template>

<style scoped>
.inspection-list {
  padding: 16px 0;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.search-box {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.progress-text {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.overdue-text {
  color: #f56c6c;
  font-weight: 600;
}

.normal-text {
  color: #67c23a;
  font-size: 13px;
}

.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px;
}
</style>
