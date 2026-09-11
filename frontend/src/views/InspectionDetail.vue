<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  inspectionApi,
  TASK_STATUS_NAME_MAP,
  CHECK_RESULT_NAME_MAP,
  ISSUE_STATUS_NAME_MAP
} from '@/api/inspection'
import type {
  InspectionTask,
  InspectionRecord,
  InspectionIssue,
  CheckResultCode,
  IssueStatusCode,
  RecordSubmitItem
} from '@/api/inspection'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const taskId = Number(route.params.id)

const task = ref<InspectionTask | null>(null)
const records = ref<InspectionRecord[]>([])
const issues = ref<InspectionIssue[]>([])
const issueStatusFilter = ref<IssueStatusCode | ''>('')
const savingLockerId = ref<number | null>(null)

// 逐台填报的本地草稿，key 为 lockerId
const drafts = reactive<Record<number, RecordSubmitItem>>({})

const resultOptions = (Object.entries(CHECK_RESULT_NAME_MAP) as [CheckResultCode, string][]).map(
  ([value, label]) => ({ value, label })
)

const issueStatusOptions = (Object.entries(ISSUE_STATUS_NAME_MAP) as [IssueStatusCode, string][]).map(
  ([value, label]) => ({ value, label })
)

onMounted(() => {
  fetchAll()
})

const fetchAll = async () => {
  await Promise.all([fetchTask(), fetchRecords(), fetchIssues()])
}

const fetchTask = async () => {
  try {
    const res = await inspectionApi.getTask(taskId)
    task.value = res.data
  } catch (error) {
    console.error('获取巡检任务失败', error)
  }
}

const fetchRecords = async () => {
  try {
    const res = await inspectionApi.getTaskRecords(taskId)
    records.value = res.data
    // 以服务端数据为准初始化/刷新草稿，保证刷新后进度与已填结果一致
    res.data.forEach((r) => {
      drafts[r.lockerId] = {
        lockerId: r.lockerId,
        compartmentResult: r.compartmentResult,
        screenResult: r.screenResult,
        lockResult: r.lockResult,
        remark: r.remark || '',
        inspector: r.inspector || ''
      }
    })
  } catch (error) {
    console.error('获取巡检明细失败', error)
  }
}

const fetchIssues = async () => {
  try {
    const res = await inspectionApi.getTaskIssues(taskId, issueStatusFilter.value || undefined)
    issues.value = res.data
  } catch (error) {
    console.error('获取异常记录失败', error)
  }
}

const handleIssueFilterChange = () => {
  fetchIssues()
}

// ---------------- 填报 ----------------

const isRowFilled = (lockerId: number) => {
  const d = drafts[lockerId]
  return d && (d.compartmentResult || d.screenResult || d.lockResult)
}

const hasAbnormal = (lockerId: number) => {
  const d = drafts[lockerId]
  return d && Object.values(d).some((v) => v === 'ABNORMAL')
}

const saveRecord = async (record: InspectionRecord) => {
  const draft = drafts[record.lockerId]
  if (!draft) return
  if (!draft.compartmentResult && !draft.screenResult && !draft.lockResult) {
    ElMessage.warning('请至少完成一个检查项（格口 / 屏幕 / 门锁）')
    return
  }
  savingLockerId.value = record.lockerId
  try {
    await inspectionApi.submitRecords(taskId, [
      {
        lockerId: record.lockerId,
        compartmentResult: draft.compartmentResult,
        screenResult: draft.screenResult,
        lockResult: draft.lockResult,
        remark: draft.remark?.trim() || undefined,
        inspector: draft.inspector?.trim() || undefined
      }
    ])
    ElMessage.success('巡检结果已保存')
    await fetchAll()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    savingLockerId.value = null
  }
}

// ---------------- 异常处理 ----------------

const issueDialogVisible = ref(false)
const currentIssue = ref<InspectionIssue | null>(null)
const issueForm = ref<{ status: 'PROCESSING' | 'RESOLVED'; handler: string; handleNote: string }>({
  status: 'PROCESSING',
  handler: '',
  handleNote: ''
})

const openIssueDialog = (issue: InspectionIssue, target: 'PROCESSING' | 'RESOLVED') => {
  currentIssue.value = issue
  issueForm.value = {
    status: target,
    handler: issue.handler || '',
    handleNote: issue.handleNote || ''
  }
  issueDialogVisible.value = true
}

const confirmHandleIssue = async () => {
  if (!currentIssue.value) return
  if (issueForm.value.status === 'RESOLVED' && !issueForm.value.handleNote.trim()) {
    ElMessage.warning('请填写处理说明')
    return
  }
  try {
    await inspectionApi.handleIssue(currentIssue.value.id, {
      status: issueForm.value.status,
      handler: issueForm.value.handler.trim() || undefined,
      handleNote: issueForm.value.handleNote.trim() || undefined
    })
    ElMessage.success(issueForm.value.status === 'RESOLVED' ? '异常已标记为已解决' : '已标记为处理中')
    issueDialogVisible.value = false
    await fetchAll()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '处理失败')
  }
}

// ---------------- 展示辅助 ----------------

const taskTagType = () => {
  if (!task.value) return 'info'
  if (task.value.status === 'COMPLETED') return 'success'
  if (task.value.overdue) return 'danger'
  if (task.value.status === 'IN_PROGRESS') return 'warning'
  return 'info'
}

const taskStatusLabel = () => {
  if (!task.value) return ''
  if (task.value.overdue && task.value.status !== 'COMPLETED') return '已逾期'
  return TASK_STATUS_NAME_MAP[task.value.status]
}

const issueTagType = (s: IssueStatusCode) => {
  if (s === 'PENDING') return 'danger'
  if (s === 'PROCESSING') return 'warning'
  return 'success'
}

const formatTime = (t?: string | null) => t || '-'

const handleBack = () => {
  router.push('/inspections')
}
</script>

<template>
  <div class="inspection-detail">
    <div class="detail-header">
      <el-button @click="handleBack">返回列表</el-button>
      <span class="detail-title">巡检任务详情</span>
    </div>

    <el-card v-if="task" v-loading="!task">
      <template #header>
        <div class="card-header">
          <span class="task-name">{{ task.taskName }}</span>
          <el-tag :type="taskTagType()" size="large">{{ taskStatusLabel() }}</el-tag>
        </div>
      </template>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="任务ID">{{ task.id }}</el-descriptions-item>
        <el-descriptions-item label="巡检范围">
          {{ task.buildingName }}<span v-if="task.unitName"> / {{ task.unitName }}</span>
          <el-tag v-else size="small" type="info" effect="plain">整栋</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="巡检周期">{{ task.cycleName }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ task.assignee || '-' }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">
          <span :class="{ 'overdue-text': task.overdue }">{{ formatTime(task.deadline) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建人">{{ task.creator || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发起时间">{{ formatTime(task.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="异常柜体">
          <el-tag :type="task.abnormalCount > 0 ? 'danger' : 'success'" size="small">
            {{ task.abnormalCount }} 台
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="待处理异常">
          <el-tag :type="task.pendingIssueCount > 0 ? 'danger' : 'info'" size="small">
            {{ task.pendingIssueCount }} 条
          </el-tag>
          <span v-if="task.totalIssueCount > 0" class="sub-text">
            （累计 {{ task.totalIssueCount }} 条）
          </span>
        </el-descriptions-item>
      </el-descriptions>

      <div class="progress-block">
        <span class="progress-label">巡检进度</span>
        <el-progress
          :percentage="task.progress"
          :status="task.status === 'COMPLETED' ? 'success' : task.overdue ? 'exception' : undefined"
          style="flex: 1;"
        />
        <span class="progress-count">{{ task.completedLockers }} / {{ task.totalLockers }} 台</span>
      </div>
    </el-card>

    <!-- 逐台巡检填报 -->
    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>逐台巡检填报（格口 / 屏幕 / 门锁）</span>
          <el-tag type="info" size="small">选择「异常」将自动生成待处理记录</el-tag>
        </div>
      </template>

      <el-table :data="records" border row-key="lockerId">
        <el-table-column label="快递柜" min-width="180">
          <template #default="{ row }">
            <div class="locker-cell">
              <el-tag type="info">{{ row.lockerNo }}</el-tag>
              <div class="locker-meta">
                {{ row.buildingName || '-' }} {{ row.unitName || '' }}
                <span v-if="row.floor"> · {{ row.floor }}</span>
              </div>
              <el-tag v-if="!row.lockerExists" type="danger" size="small">柜体已删除</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="格口" width="230">
          <template #default="{ row }">
            <el-radio-group
              v-model="drafts[row.lockerId].compartmentResult"
              :disabled="!row.lockerExists"
              size="small"
            >
              <el-radio-button
                v-for="opt in resultOptions"
                :key="'c-' + opt.value"
                :value="opt.value"
              >{{ opt.label }}</el-radio-button>
            </el-radio-group>
          </template>
        </el-table-column>

        <el-table-column label="屏幕" width="230">
          <template #default="{ row }">
            <el-radio-group
              v-model="drafts[row.lockerId].screenResult"
              :disabled="!row.lockerExists"
              size="small"
            >
              <el-radio-button
                v-for="opt in resultOptions"
                :key="'s-' + opt.value"
                :value="opt.value"
              >{{ opt.label }}</el-radio-button>
            </el-radio-group>
          </template>
        </el-table-column>

        <el-table-column label="门锁" width="230">
          <template #default="{ row }">
            <el-radio-group
              v-model="drafts[row.lockerId].lockResult"
              :disabled="!row.lockerExists"
              size="small"
            >
              <el-radio-button
                v-for="opt in resultOptions"
                :key="'l-' + opt.value"
                :value="opt.value"
              >{{ opt.label }}</el-radio-button>
            </el-radio-group>
          </template>
        </el-table-column>

        <el-table-column label="备注 / 巡检人" min-width="200">
          <template #default="{ row }">
            <el-input
              v-model="drafts[row.lockerId].remark"
              :disabled="!row.lockerExists"
              size="small"
              placeholder="备注（选填）"
              class="remark-input"
            />
            <el-input
              v-model="drafts[row.lockerId].inspector"
              :disabled="!row.lockerExists"
              size="small"
              placeholder="巡检人（选填）"
              class="remark-input"
              style="margin-top: 4px;"
            />
          </template>
        </el-table-column>

        <el-table-column label="异常" width="120">
          <template #default="{ row }">
            <el-badge v-if="row.pendingIssueCount > 0" :value="row.pendingIssueCount" type="danger">
              <el-tag type="danger" size="small">待处理</el-tag>
            </el-badge>
            <el-tag v-else-if="row.totalIssueCount > 0" type="success" size="small">已处理</el-tag>
            <el-tag v-else-if="isRowFilled(row.lockerId) && !hasAbnormal(row.lockerId)" type="success" size="small">
              正常
            </el-tag>
            <span v-else class="sub-text">未检</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              :disabled="!row.lockerExists"
              :loading="savingLockerId === row.lockerId"
              @click="saveRecord(row)"
            >保存填报</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="records.length === 0" class="empty-tip">该任务暂未关联快递柜</div>
    </el-card>

    <!-- 异常待处理记录 -->
    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>异常待处理记录</span>
          <el-select
            v-model="issueStatusFilter"
            clearable
            placeholder="全部处理状态"
            style="width: 160px"
            @change="handleIssueFilterChange"
          >
            <el-option
              v-for="opt in issueStatusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </div>
      </template>

      <el-table :data="issues" border>
        <el-table-column prop="id" label="记录ID" width="80" />
        <el-table-column label="快递柜" min-width="160">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.lockerNo || `#${row.lockerId}` }}</el-tag>
            <div class="locker-meta">{{ row.buildingName }} {{ row.unitName }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="checkItemName" label="异常项" width="90" />
        <el-table-column prop="description" label="异常描述" min-width="160" show-overflow-tooltip />
        <el-table-column label="处理状态" width="100">
          <template #default="{ row }">
            <el-tag :type="issueTagType(row.status)" size="small">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handler" label="处理人" width="100">
          <template #default="{ row }">{{ row.handler || '-' }}</template>
        </el-table-column>
        <el-table-column prop="handleNote" label="处理说明" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.handleNote || '-' }}</template>
        </el-table-column>
        <el-table-column label="上报时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="处理时间" width="170">
          <template #default="{ row }">{{ formatTime(row.handleTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              size="small"
              type="warning"
              @click="openIssueDialog(row, 'PROCESSING')"
            >开始处理</el-button>
            <el-button
              v-if="row.status !== 'RESOLVED'"
              size="small"
              type="success"
              @click="openIssueDialog(row, 'RESOLVED')"
            >标记解决</el-button>
            <el-tag v-else type="success" size="small">已闭环</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="issues.length === 0" class="empty-tip">暂无异常记录</div>
    </el-card>

    <!-- 异常处理弹窗 -->
    <el-dialog
      :title="issueForm.status === 'RESOLVED' ? '标记异常已解决' : '开始处理异常'"
      v-model="issueDialogVisible"
      width="480px"
    >
      <el-form :model="issueForm" label-width="90px">
        <el-form-item label="快递柜">
          <el-tag type="info" size="small">{{ currentIssue?.lockerNo }}</el-tag>
          <span style="margin-left: 8px;">{{ currentIssue?.checkItemName }}异常</span>
        </el-form-item>
        <el-form-item label="处理人">
          <el-input v-model="issueForm.handler" maxlength="50" placeholder="请填写处理人" />
        </el-form-item>
        <el-form-item label="处理说明" :required="issueForm.status === 'RESOLVED'">
          <el-input
            v-model="issueForm.handleNote"
            type="textarea"
            :rows="3"
            placeholder="请填写处理过程 / 结果说明"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueDialogVisible = false">取消</el-button>
        <el-button
          :type="issueForm.status === 'RESOLVED' ? 'success' : 'warning'"
          @click="confirmHandleIssue"
        >确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.inspection-detail {
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-name {
  font-size: 16px;
  font-weight: 600;
}

.progress-block {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
}

.progress-label {
  color: #666;
  white-space: nowrap;
}

.progress-count {
  font-weight: 600;
  color: #1e3a5f;
  white-space: nowrap;
}

.locker-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.locker-meta {
  font-size: 12px;
  color: #999;
}

.remark-input {
  width: 100%;
}

.overdue-text {
  color: #f56c6c;
  font-weight: 600;
}

.sub-text {
  font-size: 12px;
  color: #999;
  margin-left: 6px;
}

.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px;
}
</style>
