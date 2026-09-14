<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  collectionSuspensionApi,
  COLLECTION_SUSPENSION_STATUS_NAME_MAP
} from '@/api/collectionSuspension'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  CollectionSuspensionRecord,
  LockerCollectionSuspensionOverview,
  CollectionSuspensionStatusCode,
  CollectionSuspensionResumeRequest
} from '@/api/collectionSuspension'

const router = useRouter()

// ---------------- 停收台账列表 ----------------

const activeTab = ref<'records' | 'overview'>('records')
const records = ref<CollectionSuspensionRecord[]>([])
const recordsLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const statusFilter = ref<CollectionSuspensionStatusCode | ''>('SUSPENDED')
const keyword = ref('')

const statusOptions = Object.entries(COLLECTION_SUSPENSION_STATUS_NAME_MAP).map(([value, label]) => ({
  value: value as CollectionSuspensionStatusCode,
  label
}))

const fetchRecords = async () => {
  recordsLoading.value = true
  try {
    const res = await collectionSuspensionApi.getRecords({
      page: currentPage.value,
      size: pageSize.value,
      status: statusFilter.value,
      keyword: keyword.value.trim() || undefined
    })
    records.value = res.data.data
    total.value = res.data.total
  } catch (error) {
    console.error('获取夜间停收转投台账失败', error)
  } finally {
    recordsLoading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchRecords()
}

const handleReset = () => {
  statusFilter.value = ''
  keyword.value = ''
  currentPage.value = 1
  fetchRecords()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchRecords()
}

// ---------------- 按停收状态一览 ----------------

const overview = ref<LockerCollectionSuspensionOverview[]>([])
const overviewLoading = ref(false)
/** 一览是否已成功加载过：未加载前页头不显示具体停收中条数，避免把「加载中/加载失败」误判为全部已恢复 */
const overviewLoaded = ref(false)

const fetchOverview = async () => {
  overviewLoading.value = true
  try {
    const res = await collectionSuspensionApi.getLockerOverview()
    overview.value = res.data
    overviewLoaded.value = true
  } catch (error) {
    console.error('获取按停收状态一览失败', error)
    ElMessage.error('获取按停收状态一览失败，停收中条数暂未更新，请重新切换或刷新')
  } finally {
    overviewLoading.value = false
  }
}

/** 当前停收中总条数：与一览中各柜停收中条数同源，刷新后保持一致 */
const openRecordTotal = computed(() =>
  overview.value.reduce((sum, item) => sum + (item.openRecordCount || 0), 0)
)

/** 当前已过预计恢复时间仍未恢复的台数 */
const overdueTotal = computed(() =>
  overview.value.reduce((sum, item) => sum + (item.overdue ? 1 : 0), 0)
)

const handleTabChange = () => {
  if (activeTab.value === 'overview') {
    fetchOverview()
  } else {
    fetchRecords()
  }
}

// ---------------- 确认已恢复 ----------------

const resumeDialogVisible = ref(false)
const resuming = ref(false)
const currentRecord = ref<CollectionSuspensionRecord | null>(null)
const resumeForm = ref<CollectionSuspensionResumeRequest>({ resumeOperator: '', resumeNote: '' })

const openResumeDialog = (record: CollectionSuspensionRecord) => {
  currentRecord.value = record
  resumeForm.value = { resumeOperator: '', resumeNote: '' }
  resumeDialogVisible.value = true
}

/** 恢复内容只保存在本窗口内，未提交前关闭不会写入任何数据；已填写内容时关闭需二次确认 */
const resumeFormDirty = computed(
  () => !!((resumeForm.value.resumeOperator || '').trim() || (resumeForm.value.resumeNote || '').trim())
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
  if (!currentRecord.value) return
  resuming.value = true
  try {
    await collectionSuspensionApi.resume(currentRecord.value.id, {
      resumeOperator: resumeForm.value.resumeOperator?.trim() || undefined,
      resumeNote: resumeForm.value.resumeNote?.trim() || undefined
    })
    ElMessage.success('已确认恢复，柜体停收中标记恢复')
    resumeDialogVisible.value = false
    await fetchRecords()
    // 无论当前在哪个页签都同步刷新按柜一览，保证停收中条数与柜体页标记实时一致
    await fetchOverview()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '确认恢复失败')
  } finally {
    resuming.value = false
  }
}

// ---------------- 展示辅助 ----------------

const formatTime = (t?: string | null) => (t ? t.replace('T', ' ') : '-')

/** 持续时长展示：不足 1 小时显示分钟，否则显示小时+分钟 */
const formatElapsed = (minutes?: number | null) => {
  if (minutes === null || minutes === undefined) return '-'
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest > 0 ? `${hours} 小时 ${rest} 分` : `${hours} 小时`
}

const goLockerDetail = (lockerId: number) => {
  router.push(`/lockers/${lockerId}`)
}

onMounted(() => {
  fetchRecords()
  // 进入页面即预取按柜一览，保证第一次切到一览时停收中条数已与台账、柜体页标记一致
  fetchOverview()
})
</script>

<template>
  <div class="suspension-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="停收台账" name="records" />
      <el-tab-pane label="按停收状态" name="overview" />
    </el-tabs>

    <!-- ================= 停收台账列表 ================= -->
    <template v-if="activeTab === 'records'">
      <div class="list-header">
        <div class="search-box">
          <el-select
            v-model="statusFilter"
            clearable
            placeholder="按状态筛选"
            style="width: 160px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-input
            v-model="keyword"
            clearable
            placeholder="台账编号 / 值班人 / 柜体编号"
            style="width: 260px"
            @keyup.enter="handleSearch"
          />
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <el-alert type="info" :closable="false" class="header-tip">
          在「快递柜详情」页登记夜间停收转投
        </el-alert>
      </div>

      <el-table :data="records" border v-loading="recordsLoading">
        <el-table-column prop="recordNo" label="台账编号" width="190" />
        <el-table-column label="柜体" width="150">
          <template #default="{ row }">
            <el-tag type="info">{{ row.lockerNo }}</el-tag>
            <span class="locker-location">{{ row.buildingName }} {{ row.unitName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="开始停收时间" width="160">
          <template #default="{ row }">{{ formatTime(row.suspendStartTime) }}</template>
        </el-table-column>
        <el-table-column label="预计恢复时间" width="160">
          <template #default="{ row }">{{ formatTime(row.expectedResumeTime) }}</template>
        </el-table-column>
        <el-table-column label="已持续" width="120">
          <template #default="{ row }">{{ formatElapsed(row.elapsedMinutes) }}</template>
        </el-table-column>
        <el-table-column prop="dutyOfficer" label="值班人" width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.suspended" type="danger">停收中</el-tag>
            <el-tag v-else type="success">已恢复</el-tag>
            <el-tag v-if="row.overdue" type="danger" effect="dark" style="margin-left: 4px">
              逾时未恢复
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认恢复" min-width="180" show-overflow-tooltip>
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
        <el-table-column label="操作" width="130" fixed="right">
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

      <el-pagination
        v-if="total > 0"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        @current-change="handlePageChange"
        layout="total, prev, pager, next, jumper"
      />
      <div v-else-if="!recordsLoading" class="empty-tip">暂无夜间停收转投记录</div>
    </template>

    <!-- ================= 按停收状态一览 ================= -->
    <template v-else>
      <el-alert type="info" :closable="false" class="overview-tip">
        全部柜体（含停用柜）列出：存在停收中记录的柜体标记「停收中」，其余为「正常」，
        <template v-if="overviewLoaded">
          当前共有 <b>{{ openRecordTotal }}</b> 条停收中记录
          <template v-if="overdueTotal > 0">
            ，其中 <b>{{ overdueTotal }}</b> 台已过预计恢复时间
          </template>
          ；
        </template>
        <template v-else>停收中条数统计中…；</template>
        登记夜间停收转投请进入对应柜体详情页。
      </el-alert>
      <el-table :data="overview" border v-loading="overviewLoading">
        <el-table-column prop="lockerNo" label="柜体编号" width="120">
          <template #default="{ row }">
            <el-tag type="info">{{ row.lockerNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="buildingName" label="所属楼栋" width="110" />
        <el-table-column prop="unitName" label="所属单元" width="100" />
        <el-table-column label="楼层" width="80">
          <template #default="{ row }">{{ row.floor || '-' }}</template>
        </el-table-column>
        <el-table-column label="柜体状态" width="110">
          <template #default="{ row }">
            <el-tag
              :type="
                row.status === 'ACTIVE'
                  ? 'success'
                  : row.status === 'TEMPORARILY_DISABLED'
                    ? 'warning'
                    : 'info'
              "
            >{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="停收标记" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.suspended" type="danger">停收中</el-tag>
            <el-tag v-else type="success">正常</el-tag>
            <el-tag v-if="row.overdue" type="danger" effect="dark" style="margin-left: 4px">
              逾时未恢复
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预计恢复" width="160">
          <template #default="{ row }">{{ formatTime(row.expectedResumeTime) }}</template>
        </el-table-column>
        <el-table-column prop="openRecordCount" label="停收中条数" width="100" align="center" />
        <el-table-column prop="totalRecordCount" label="历史条数" width="90" align="center" />
        <el-table-column label="最近停收" width="160">
          <template #default="{ row }">{{ formatTime(row.lastSuspendStartTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="goLockerDetail(row.lockerId)">
              柜详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!overviewLoading && overview.length === 0" class="empty-tip">暂无柜体</div>
    </template>

    <!-- ================= 确认已恢复弹窗 ================= -->
    <el-dialog
      title="确认已恢复"
      v-model="resumeDialogVisible"
      width="520px"
      :close-on-click-modal="false"
      :before-close="handleResumeDialogClose"
    >
      <template v-if="currentRecord">
        <el-alert type="warning" :closable="false" class="dialog-tip">
          确认后该记录状态变为「已恢复」，柜体「停收中」标记恢复；台账中仍是同一条记录，按已恢复可查到。
        </el-alert>
        <el-descriptions :column="2" border class="dialog-tip">
          <el-descriptions-item label="台账编号">{{ currentRecord.recordNo }}</el-descriptions-item>
          <el-descriptions-item label="柜体">{{ currentRecord.lockerNo }}</el-descriptions-item>
          <el-descriptions-item label="开始停收时间">{{ formatTime(currentRecord.suspendStartTime) }}</el-descriptions-item>
          <el-descriptions-item label="预计恢复时间">{{ formatTime(currentRecord.expectedResumeTime) }}</el-descriptions-item>
          <el-descriptions-item label="值班人">{{ currentRecord.dutyOfficer }}</el-descriptions-item>
          <el-descriptions-item label="已持续">{{ formatElapsed(currentRecord.elapsedMinutes) }}</el-descriptions-item>
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
.suspension-page {
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

.header-tip {
  width: auto;
  padding: 0 12px;
}

.locker-location {
  margin-left: 8px;
  color: #999;
  font-size: 12px;
}

.overview-tip {
  margin-bottom: 16px;
}

.dialog-tip {
  margin-bottom: 16px;
}

.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px;
}

.el-pagination {
  margin-top: 16px;
}
</style>
