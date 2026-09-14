<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { meterReadingApi, METER_READING_STATUS_NAME_MAP } from '@/api/meterReading'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  MeterReadingRecord,
  MeterReadingLockerOption,
  LockerMeterReadingOverview,
  MeterReadingStatusCode,
  MeterReadingCreateRequest
} from '@/api/meterReading'

// ---------------- 抄表单列表 ----------------

const activeTab = ref<'records' | 'overview'>('records')
const records = ref<MeterReadingRecord[]>([])
const recordsLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
/** 账期筛选（yyyy-MM），默认当前自然月，与按柜一览、本月已抄台数口径一致 */
const currentPeriod = () => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}
const periodFilter = ref<string>(currentPeriod())
const statusFilter = ref<MeterReadingStatusCode | ''>('')
const keyword = ref('')

const statusOptions = Object.entries(METER_READING_STATUS_NAME_MAP).map(([value, label]) => ({
  value: value as MeterReadingStatusCode,
  label
}))

const fetchRecords = async () => {
  recordsLoading.value = true
  try {
    const res = await meterReadingApi.getRecords({
      page: currentPage.value,
      size: pageSize.value,
      periodMonth: periodFilter.value || undefined,
      status: statusFilter.value,
      keyword: keyword.value.trim() || undefined
    })
    records.value = res.data.data
    total.value = res.data.total
  } catch (error) {
    console.error('获取抄表单列表失败', error)
  } finally {
    recordsLoading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchRecords()
}

const handleReset = () => {
  periodFilter.value = currentPeriod()
  statusFilter.value = ''
  keyword.value = ''
  currentPage.value = 1
  fetchRecords()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchRecords()
}

// ---------------- 按柜本月抄表状态一览 ----------------

const overview = ref<LockerMeterReadingOverview[]>([])
const overviewLoading = ref(false)
/** 一览是否已成功加载过：未加载前页头不显示具体已抄台数，避免把「加载中/加载失败」误判为本月全部未抄 */
const overviewLoaded = ref(false)
const readFilter = ref<'' | 'read' | 'unread'>('')

const fetchOverview = async () => {
  overviewLoading.value = true
  try {
    const res = await meterReadingApi.getLockerOverview()
    overview.value = res.data
    overviewLoaded.value = true
  } catch (error) {
    console.error('获取按柜抄表状态一览失败', error)
    ElMessage.error('获取按柜抄表状态一览失败，本月已抄台数暂未更新，请重新切换或刷新')
  } finally {
    overviewLoading.value = false
  }
}

/** 本月已抄台数：与一览中各柜已抄标记同源，刷新后与柜体页读数、筛选结果保持一致 */
const readCount = computed(() => overview.value.filter((item) => item.read).length)

const filteredOverview = computed(() => {
  if (readFilter.value === 'read') return overview.value.filter((item) => item.read)
  if (readFilter.value === 'unread') return overview.value.filter((item) => !item.read)
  return overview.value
})

const overviewPeriod = computed(() => overview.value[0]?.periodMonth || currentPeriod())

const handleTabChange = () => {
  if (activeTab.value === 'overview') {
    fetchOverview()
  } else {
    fetchRecords()
  }
}

// ---------------- 登记抄表 ----------------

const createDialogVisible = ref(false)
const lockerOptions = ref<MeterReadingLockerOption[]>([])
const submitting = ref(false)
const createForm = ref<MeterReadingCreateRequest>(emptyCreateForm())

function emptyCreateForm(): MeterReadingCreateRequest {
  return {
    lockerId: null,
    readingValue: null,
    reader: '',
    readingTime: null,
    remark: ''
  }
}

/** 表单已填写内容时，关闭窗口前确认，避免误关丢单 */
const createFormDirty = computed(() => {
  const f = createForm.value
  return !!(
    f.lockerId ||
    f.readingValue !== null ||
    f.reader.trim() ||
    f.readingTime ||
    (f.remark && f.remark.trim())
  )
})

const openCreateDialog = async (lockerId?: number) => {
  createForm.value = emptyCreateForm()
  if (lockerId) {
    createForm.value.lockerId = lockerId
  }
  createDialogVisible.value = true
  try {
    const res = await meterReadingApi.getLockerOptions()
    lockerOptions.value = res.data
  } catch (error) {
    console.error('获取可选柜体失败', error)
  }
}

/**
 * 抄表内容只保存在本窗口内，未提交前关闭不会写入任何数据；
 * 已填写内容时关闭需二次确认，关掉窗口不会留下半张抄表单。
 */
const handleCreateDialogClose = (done: () => void) => {
  if (!createFormDirty.value) {
    done()
    return
  }
  ElMessageBox.confirm('关闭后本次填写的内容将丢弃，且不会生成抄表单，确认关闭？', '提示', {
    type: 'warning',
    confirmButtonText: '丢弃并关闭',
    cancelButtonText: '继续填写'
  })
    .then(() => done())
    .catch(() => {})
}

const submitCreate = async () => {
  const f = createForm.value
  if (!f.lockerId) {
    ElMessage.warning('请选择抄表的柜体')
    return
  }
  if (f.readingValue === null || f.readingValue === undefined) {
    ElMessage.warning('请填写电表读数')
    return
  }
  if (f.readingValue < 0) {
    ElMessage.warning('电表读数不能为负数')
    return
  }
  if (!f.reader.trim()) {
    ElMessage.warning('请填写抄表人')
    return
  }
  if (!f.readingTime) {
    ElMessage.warning('请选择抄表时间')
    return
  }
  submitting.value = true
  try {
    await meterReadingApi.createRecord({
      lockerId: f.lockerId,
      readingValue: f.readingValue,
      reader: f.reader.trim(),
      readingTime: f.readingTime,
      remark: f.remark?.trim() || undefined
    })
    ElMessage.success('抄表登记成功')
    createDialogVisible.value = false
    createForm.value = emptyCreateForm()
    await fetchRecords()
    // 无论当前在哪个页签都同步刷新按柜一览，保证本月已抄台数与各柜状态实时一致
    await fetchOverview()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '登记失败')
  } finally {
    submitting.value = false
  }
}

// ---------------- 作废抄表单 ----------------

const voidDialogVisible = ref(false)
const voiding = ref(false)
const voidTarget = ref<MeterReadingRecord | null>(null)
const voidForm = ref<{ voidReason: string; voidOperator: string }>({
  voidReason: '',
  voidOperator: ''
})

const openVoidDialog = (record: MeterReadingRecord) => {
  voidTarget.value = record
  voidForm.value = { voidReason: '', voidOperator: '' }
  voidDialogVisible.value = true
}

const submitVoid = async () => {
  if (!voidTarget.value) return
  if (!voidForm.value.voidReason.trim()) {
    ElMessage.warning('作废必须填写作废原因')
    return
  }
  voiding.value = true
  try {
    await meterReadingApi.voidRecord(voidTarget.value.id, {
      voidReason: voidForm.value.voidReason.trim(),
      voidOperator: voidForm.value.voidOperator.trim() || undefined
    })
    ElMessage.success('抄表单已作废，该柜本月可重新登记')
    voidDialogVisible.value = false
    await fetchRecords()
    // 作废后本月已抄台数与各柜状态同步刷新，保证与列表、柜体页一致
    await fetchOverview()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '作废失败')
  } finally {
    voiding.value = false
  }
}

// ---------------- 抄表单详情 ----------------

const detailDialogVisible = ref(false)
const detailRecord = ref<MeterReadingRecord | null>(null)

const openDetailDialog = async (record: MeterReadingRecord) => {
  try {
    const res = await meterReadingApi.getRecord(record.id)
    detailRecord.value = res.data
    detailDialogVisible.value = true
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '获取抄表单详情失败')
  }
}

// ---------------- 展示辅助 ----------------

const formatTime = (t?: string | null) => (t ? t.replace('T', ' ') : '-')

const formatReading = (v?: number | null) => (v === null || v === undefined ? '-' : `${v} kWh`)

/** 抄表时间不能晚于当前时间（补登历史月份可选过去时间） */
const disableFutureDate = (date: Date) => date.getTime() > Date.now()

onMounted(() => {
  fetchRecords()
  // 进入页面即预取按柜一览，保证第一次切到一览时本月已抄台数已与各柜、柜体页标记一致
  fetchOverview()
})
</script>

<template>
  <div class="meter-reading-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="抄表单列表" name="records" />
      <el-tab-pane label="按柜本月抄表" name="overview" />
    </el-tabs>

    <!-- ================= 抄表单列表 ================= -->
    <template v-if="activeTab === 'records'">
      <div class="list-header">
        <div class="search-box">
          <el-date-picker
            v-model="periodFilter"
            type="month"
            clearable
            placeholder="按账期筛选"
            format="YYYY年MM月"
            value-format="YYYY-MM"
            style="width: 160px"
            @change="handleSearch"
          />
          <el-select
            v-model="statusFilter"
            clearable
            placeholder="按状态筛选"
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
          <el-input
            v-model="keyword"
            clearable
            placeholder="单号 / 抄表人 / 柜体编号"
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <el-button type="success" @click="openCreateDialog()">登记抄表</el-button>
      </div>

      <el-table :data="records" border v-loading="recordsLoading">
        <el-table-column prop="recordNo" label="抄表单号" width="190" />
        <el-table-column label="柜体" width="150">
          <template #default="{ row }">
            <el-tag type="info">{{ row.lockerNo }}</el-tag>
            <span class="locker-location">{{ row.buildingName }} {{ row.unitName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="periodMonth" label="账期" width="90" align="center" />
        <el-table-column label="电表读数" width="110" align="right">
          <template #default="{ row }">{{ formatReading(row.readingValue) }}</template>
        </el-table-column>
        <el-table-column prop="reader" label="抄表人" width="100" />
        <el-table-column label="抄表时间" width="160">
          <template #default="{ row }">{{ formatTime(row.readingTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.active" type="success">有效</el-tag>
            <el-tag v-else type="info">已作废</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="作废信息" min-width="180">
          <template #default="{ row }">
            <template v-if="!row.active">
              <el-tooltip
                :content="`作废人：${row.voidOperator || '-'}，作废时间：${formatTime(row.voidTime)}`"
                placement="top"
              >
                <span class="void-reason">{{ row.voidReason }}</span>
              </el-tooltip>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetailDialog(row)">详情</el-button>
            <el-button
              v-if="row.active"
              size="small"
              type="danger"
              plain
              @click="openVoidDialog(row)"
            >作废</el-button>
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
      <div v-else-if="!recordsLoading" class="empty-tip">暂无抄表单</div>
    </template>

    <!-- ================= 按柜本月抄表状态一览 ================= -->
    <template v-else>
      <el-alert type="info" :closable="false" class="overview-tip">
        {{ overviewPeriod }} 账期：全部柜体（含停用柜）列出，未抄的柜体置顶，
        <template v-if="overviewLoaded">
          本月已抄 <b>{{ readCount }}</b> 台 / 共 <b>{{ overview.length }}</b> 台；
        </template>
        <template v-else>本月已抄台数统计中…；</template>
        每行带出本账期前最近一次抄表的读数、抄表人和抄表日期（从未抄过显示「尚未抄过」），
        与已抄/未抄、柜体页读数实时同源，刷新后保持一致。
      </el-alert>
      <div class="list-header">
        <div class="search-box">
          <el-radio-group v-model="readFilter">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="read">已抄</el-radio-button>
            <el-radio-button value="unread">未抄</el-radio-button>
          </el-radio-group>
        </div>
        <el-button type="success" @click="openCreateDialog()">登记抄表</el-button>
      </div>
      <el-table :data="filteredOverview" border v-loading="overviewLoading">
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
        <el-table-column label="本月抄表" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.read" type="success">已抄</el-tag>
            <el-tag v-else type="danger">未抄</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="本月读数" width="110" align="right">
          <template #default="{ row }">{{ formatReading(row.readingValue) }}</template>
        </el-table-column>
        <el-table-column label="本月抄表人" width="100">
          <template #default="{ row }">{{ row.reader || '-' }}</template>
        </el-table-column>
        <el-table-column label="本月抄表时间" width="160">
          <template #default="{ row }">{{ formatTime(row.readingTime) }}</template>
        </el-table-column>
        <el-table-column label="上次读数" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.lastReadingTime">{{ formatReading(row.lastReadingValue) }}</span>
            <span v-else class="never-read">尚未抄过</span>
          </template>
        </el-table-column>
        <el-table-column label="上次抄表人" width="100">
          <template #default="{ row }">{{ row.lastReader || '-' }}</template>
        </el-table-column>
        <el-table-column label="上次抄表日期" width="160">
          <template #default="{ row }">{{ formatTime(row.lastReadingTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.read"
              content="本月已存在有效抄表单，如需更正请先作废原单"
              placement="top"
            >
              <span class="read-btn-wrap">
                <el-button size="small" type="success" disabled>登记抄表</el-button>
              </span>
            </el-tooltip>
            <el-button
              v-else
              size="small"
              type="success"
              @click="openCreateDialog(row.lockerId)"
            >登记抄表</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!overviewLoading && overview.length === 0" class="empty-tip">暂无柜体</div>
    </template>

    <!-- ================= 登记抄表弹窗 ================= -->
    <el-dialog
      title="登记电表抄表"
      v-model="createDialogVisible"
      width="560px"
      :close-on-click-modal="false"
      :before-close="handleCreateDialogClose"
    >
      <el-alert type="warning" :closable="false" class="dialog-tip">
        账期（自然月）由抄表时间自动推导；同一柜同一自然月只能存在一张有效抄表单，
        本月已抄的柜体不可重复登记，如需更正请先作废原单。
        登记读数必须大于该柜上次读数，否则无法保存，请现场核对电表后再提交。
      </el-alert>
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="柜体" required>
          <el-select
            v-model="createForm.lockerId"
            filterable
            placeholder="请选择柜体"
            style="width: 100%"
          >
            <el-option
              v-for="opt in lockerOptions"
              :key="opt.id"
              :value="opt.id"
              :disabled="opt.readThisMonth"
              :label="`${opt.lockerNo}（${opt.buildingName} ${opt.unitName}）`"
            >
              <span>{{ opt.lockerNo }}（{{ opt.buildingName }} {{ opt.unitName }}）</span>
              <span class="option-tags">
                <el-tag v-if="opt.readThisMonth" type="success" size="small">本月已抄</el-tag>
                <el-tag
                  v-if="opt.status !== 'ACTIVE'"
                  :type="opt.status === 'PERMANENTLY_DISABLED' ? 'info' : 'warning'"
                  size="small"
                >{{ opt.statusName }}</el-tag>
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="电表读数" required>
          <el-input-number
            v-model="createForm.readingValue"
            :min="0"
            :precision="2"
            :step="1"
            placeholder="请填写电表读数"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="抄表人" required>
          <el-input v-model="createForm.reader" placeholder="请填写抄表人" />
        </el-form-item>
        <el-form-item label="抄表时间" required>
          <el-date-picker
            v-model="createForm.readingTime"
            type="datetime"
            placeholder="请选择抄表时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD[T]HH:mm:ss"
            :disabled-date="disableFutureDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="createForm.remark"
            type="textarea"
            :rows="2"
            placeholder="选填，如表计更换说明等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleCreateDialogClose(() => (createDialogVisible = false))">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">提交登记</el-button>
      </template>
    </el-dialog>

    <!-- ================= 作废抄表单弹窗 ================= -->
    <el-dialog title="作废抄表单" v-model="voidDialogVisible" width="520px">
      <template v-if="voidTarget">
        <el-alert type="warning" :closable="false" class="dialog-tip">
          作废必须填写作废原因；作废后该柜 {{ voidTarget.periodMonth }} 不再占用有效单额度，可重新登记抄表。
        </el-alert>
        <el-descriptions :column="2" border class="dialog-tip">
          <el-descriptions-item label="抄表单号">{{ voidTarget.recordNo }}</el-descriptions-item>
          <el-descriptions-item label="柜体">{{ voidTarget.lockerNo }}</el-descriptions-item>
          <el-descriptions-item label="账期">{{ voidTarget.periodMonth }}</el-descriptions-item>
          <el-descriptions-item label="电表读数">{{ formatReading(voidTarget.readingValue) }}</el-descriptions-item>
          <el-descriptions-item label="抄表人">{{ voidTarget.reader }}</el-descriptions-item>
          <el-descriptions-item label="抄表时间">{{ formatTime(voidTarget.readingTime) }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px">
          <el-form-item label="作废原因" required>
            <el-input
              v-model="voidForm.voidReason"
              type="textarea"
              :rows="2"
              placeholder="必填，如：读数录入错误，重新抄表"
            />
          </el-form-item>
          <el-form-item label="作废人">
            <el-input v-model="voidForm.voidOperator" placeholder="选填，默认系统管理员" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="voidDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="voiding" @click="submitVoid">确认作废</el-button>
      </template>
    </el-dialog>

    <!-- ================= 抄表单详情弹窗 ================= -->
    <el-dialog title="抄表单详情" v-model="detailDialogVisible" width="640px">
      <template v-if="detailRecord">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="抄表单号">{{ detailRecord.recordNo }}</el-descriptions-item>
          <el-descriptions-item label="单据状态">
            <el-tag v-if="detailRecord.active" type="success">有效</el-tag>
            <el-tag v-else type="info">已作废</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="柜体">
            {{ detailRecord.lockerNo }}（{{ detailRecord.buildingName }} {{ detailRecord.unitName }}）
          </el-descriptions-item>
          <el-descriptions-item label="柜体状态">{{ detailRecord.lockerStatusName }}</el-descriptions-item>
          <el-descriptions-item label="账期">{{ detailRecord.periodMonth }}</el-descriptions-item>
          <el-descriptions-item label="电表读数">{{ formatReading(detailRecord.readingValue) }}</el-descriptions-item>
          <el-descriptions-item label="抄表人">{{ detailRecord.reader }}</el-descriptions-item>
          <el-descriptions-item label="抄表时间">{{ formatTime(detailRecord.readingTime) }}</el-descriptions-item>
          <el-descriptions-item label="本月抄表">
            <el-tag v-if="detailRecord.currentMonth" type="success" size="small">本月已抄</el-tag>
            <el-tag v-else type="info" size="small">非本月有效单</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="登记时间">{{ formatTime(detailRecord.createTime) }}</el-descriptions-item>
          <template v-if="!detailRecord.active">
            <el-descriptions-item label="作废原因">{{ detailRecord.voidReason }}</el-descriptions-item>
            <el-descriptions-item label="作废人">{{ detailRecord.voidOperator || '-' }}</el-descriptions-item>
            <el-descriptions-item label="作废时间">{{ formatTime(detailRecord.voidTime) }}</el-descriptions-item>
          </template>
          <el-descriptions-item label="备注" :span="2">{{ detailRecord.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.meter-reading-page {
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
  align-items: center;
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

.option-tags {
  float: right;
  display: flex;
  gap: 4px;
}

.void-reason {
  color: #909399;
  font-size: 13px;
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.read-btn-wrap {
  display: inline-block;
}

.never-read {
  color: #909399;
  font-size: 13px;
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
