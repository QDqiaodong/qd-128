<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { keyBorrowApi, keyHandoverApi, KEY_BORROW_STATUS_NAME_MAP } from '@/api/keyBorrow'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  KeyBorrowRecord,
  KeyBorrowLockerOption,
  LockerKeyBorrowOverview,
  KeyBorrowStatusCode,
  KeyBorrowCreateRequest,
  KeyHandover,
  KeyHandoverPendingItem
} from '@/api/keyBorrow'

// ---------------- 借用台账列表 ----------------

const activeTab = ref<'records' | 'overview'>('records')
const records = ref<KeyBorrowRecord[]>([])
const recordsLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const statusFilter = ref<KeyBorrowStatusCode | ''>('ON_LOAN')
const keyword = ref('')

const statusOptions = Object.entries(KEY_BORROW_STATUS_NAME_MAP).map(([value, label]) => ({
  value: value as KeyBorrowStatusCode,
  label
}))

const fetchRecords = async () => {
  recordsLoading.value = true
  try {
    const res = await keyBorrowApi.getRecords({
      page: currentPage.value,
      size: pageSize.value,
      status: statusFilter.value,
      keyword: keyword.value.trim() || undefined
    })
    records.value = res.data.data
    total.value = res.data.total
  } catch (error) {
    console.error('获取钥匙借用台账失败', error)
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

// ---------------- 按柜钥匙状态一览 ----------------

const overview = ref<LockerKeyBorrowOverview[]>([])
const overviewLoading = ref(false)
/** 一览是否已成功加载过：未加载前页头不显示具体未还条数，避免把「加载中/加载失败」误判为钥匙已全部还清 */
const overviewLoaded = ref(false)

const fetchOverview = async () => {
  overviewLoading.value = true
  try {
    const res = await keyBorrowApi.getLockerOverview()
    overview.value = res.data
    overviewLoaded.value = true
  } catch (error) {
    console.error('获取柜体钥匙状态一览失败', error)
    ElMessage.error('获取柜体钥匙状态一览失败，未还条数暂未更新，请重新切换或刷新')
  } finally {
    overviewLoading.value = false
  }
}

/** 当前未还总条数：与一览中各柜未还条数同源，刷新后保持一致 */
const openRecordTotal = computed(() =>
  overview.value.reduce((sum, item) => sum + (item.openRecordCount || 0), 0)
)

const handleTabChange = () => {
  if (activeTab.value === 'overview') {
    fetchOverview()
  } else {
    fetchRecords()
  }
}

// ---------------- 登记借用 ----------------

const createDialogVisible = ref(false)
const lockerOptions = ref<KeyBorrowLockerOption[]>([])
const submitting = ref(false)
const createForm = ref<KeyBorrowCreateRequest>(emptyCreateForm())

function emptyCreateForm(): KeyBorrowCreateRequest {
  return {
    lockerId: null,
    borrower: '',
    reason: '',
    borrowTime: null,
    expectedReturnTime: null,
    remark: ''
  }
}

/** 表单已填写内容时，关闭窗口前确认，避免误关丢单 */
const createFormDirty = computed(() => {
  const f = createForm.value
  return !!(
    f.lockerId ||
    f.borrower.trim() ||
    f.reason.trim() ||
    f.borrowTime ||
    f.expectedReturnTime ||
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
    const res = await keyBorrowApi.getLockerOptions()
    lockerOptions.value = res.data
  } catch (error) {
    console.error('获取可选柜体失败', error)
  }
}

/**
 * 登记内容只保存在本窗口内，未提交前关闭不会写入任何数据；
 * 已填写内容时关闭需二次确认，关掉窗口不会留下半条台账。
 */
const handleCreateDialogClose = (done: () => void) => {
  if (!createFormDirty.value) {
    done()
    return
  }
  ElMessageBox.confirm('关闭后本次填写的内容将丢弃，且不会生成借用记录，确认关闭？', '提示', {
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
    ElMessage.warning('请选择借用钥匙的柜体')
    return
  }
  if (!f.borrower.trim()) {
    ElMessage.warning('请填写借出人')
    return
  }
  if (!f.reason.trim()) {
    ElMessage.warning('请填写借用事由')
    return
  }
  if (!f.borrowTime) {
    ElMessage.warning('请选择借出时间')
    return
  }
  if (!f.expectedReturnTime) {
    ElMessage.warning('请选择预计归还时间')
    return
  }
  if (f.expectedReturnTime < f.borrowTime) {
    ElMessage.warning('预计归还时间不能早于借出时间')
    return
  }
  submitting.value = true
  try {
    await keyBorrowApi.createRecord({
      lockerId: f.lockerId,
      borrower: f.borrower.trim(),
      reason: f.reason.trim(),
      borrowTime: f.borrowTime,
      expectedReturnTime: f.expectedReturnTime,
      remark: f.remark?.trim() || undefined
    })
    ElMessage.success('钥匙借用登记成功')
    createDialogVisible.value = false
    createForm.value = emptyCreateForm()
    await fetchRecords()
    // 无论当前在哪个页签都同步刷新按柜一览，保证页头未还条数与台账、各柜状态实时一致
    await fetchOverview()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '登记失败')
  } finally {
    submitting.value = false
  }
}

// ---------------- 归还 ----------------

const returnDialogVisible = ref(false)
const returning = ref(false)
const currentRecord = ref<KeyBorrowRecord | null>(null)
const returner = ref('')
const returnTime = ref<string | null>(null)

const openReturnDialog = (record: KeyBorrowRecord) => {
  currentRecord.value = record
  returner.value = ''
  returnTime.value = null
  returnDialogVisible.value = true
}

const submitReturn = async () => {
  if (!currentRecord.value) return
  if (!returner.value.trim()) {
    ElMessage.warning('归还必须填写归还人')
    return
  }
  returning.value = true
  try {
    await keyBorrowApi.returnRecord(
      currentRecord.value.id,
      returner.value.trim(),
      returnTime.value || undefined
    )
    ElMessage.success('钥匙归还登记成功')
    returnDialogVisible.value = false
    await fetchRecords()
    // 无论当前在哪个页签都同步刷新按柜一览，保证页头未还条数与台账、各柜状态实时一致
    await fetchOverview()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '归还登记失败')
  } finally {
    returning.value = false
  }
}

// ---------------- 借用改期 ----------------

const extendDialogVisible = ref(false)
const extending = ref(false)
const extendTarget = ref<KeyBorrowRecord | null>(null)
const extendForm = ref<{ expectedReturnTime: string | null; extendReason: string }>({
  expectedReturnTime: null,
  extendReason: ''
})

const openExtendDialog = (record: KeyBorrowRecord) => {
  // 还没到预计归还时间不能改期，刚好到点或已过点放行；前端先拦下，最终以后端校验为准
  if (!record.extendable) {
    ElMessage.warning('还没到预计归还时间，不能改期')
    return
  }
  extendTarget.value = record
  extendForm.value = { expectedReturnTime: null, extendReason: '' }
  extendDialogVisible.value = true
}

/** 改期表单已填写内容时，关闭窗口前确认，避免误关留下误解 */
const extendFormDirty = computed(() =>
  !!(extendForm.value.expectedReturnTime || extendForm.value.extendReason.trim())
)

/**
 * 改期内容只保存在本窗口内，未提交前关闭不会写入任何数据；
 * 已填写内容时关闭需二次确认，关掉窗口不会留下半条改期。
 */
const handleExtendDialogClose = (done: () => void) => {
  if (!extendFormDirty.value) {
    done()
    return
  }
  ElMessageBox.confirm('关闭后本次填写的改期内容将丢弃，原预计归还时间不变，确认关闭？', '提示', {
    type: 'warning',
    confirmButtonText: '丢弃并关闭',
    cancelButtonText: '继续填写'
  })
    .then(() => done())
    .catch(() => {})
}

const submitExtend = async () => {
  const record = extendTarget.value
  if (!record) return
  if (!record.extendable) {
    ElMessage.warning('还没到预计归还时间，不能改期')
    return
  }
  const f = extendForm.value
  if (!f.expectedReturnTime) {
    ElMessage.warning('请选择新的预计归还时间')
    return
  }
  if (f.expectedReturnTime <= record.expectedReturnTime) {
    ElMessage.warning('新的预计归还时间必须晚于原预计归还时间')
    return
  }
  if (!f.extendReason.trim()) {
    ElMessage.warning('请填写改期原因')
    return
  }
  extending.value = true
  try {
    await keyBorrowApi.extendRecord(record.id, {
      expectedReturnTime: f.expectedReturnTime,
      extendReason: f.extendReason.trim()
    })
    ElMessage.success('改期成功，预计归还时间已更新')
    extendDialogVisible.value = false
    await fetchRecords()
    // 无论当前在哪个页签都同步刷新按柜一览，保证页头未还条数与台账、各柜状态实时一致
    await fetchOverview()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '改期失败')
  } finally {
    extending.value = false
  }
}

// ---------------- 交接班 ----------------

const handoverDialogVisible = ref(false)
const pendingItems = ref<KeyHandoverPendingItem[]>([])
const pendingLoading = ref(false)
const pendingLoaded = ref(false)
const handoverTableRef = ref<{
  toggleRowSelection: (row: KeyHandoverPendingItem, selected?: boolean) => void
  clearSelection: () => void
} | null>(null)
const submittingHandover = ref(false)
/** 勾选的未还记录 ID，默认不勾，由交班人逐一点名（也可一键勾齐后再逐柜核对） */
const checkedRecordIds = ref<number[]>([])
const handoverForm = ref({ handoverFrom: '', handoverTo: '', handoverNote: '' })

/** 待点名未还总数：与按柜一览未还总条数同源，都是实时推导的借用中记录 */
const pendingTotal = computed(() => pendingItems.value.length)
const checkedTotal = computed(() => checkedRecordIds.value.length)
const allChecked = computed(
  () => pendingTotal.value > 0 && checkedTotal.value === pendingTotal.value
)

const handoverFormDirty = computed(() =>
  !!(
    checkedRecordIds.value.length ||
    handoverForm.value.handoverFrom.trim() ||
    handoverForm.value.handoverTo.trim() ||
    handoverForm.value.handoverNote.trim()
  )
)

const openHandoverDialog = async () => {
  checkedRecordIds.value = []
  handoverForm.value = { handoverFrom: '', handoverTo: '', handoverNote: '' }
  handoverDialogVisible.value = true
  pendingLoaded.value = false
  pendingLoading.value = true
  try {
    const res = await keyHandoverApi.getPendingItems()
    pendingItems.value = res.data
    pendingLoaded.value = true
    if (pendingItems.value.length === 0) {
      ElMessage.info('当前没有借用中的柜，无需交接')
    }
  } catch (error) {
    console.error('获取待点名未还柜失败', error)
    pendingItems.value = []
  } finally {
    pendingLoading.value = false
  }
}

const handleCheckAllChange = (val: boolean | string | number) => {
  // 点表头复选框：勾齐或清空，具体选中行仍以表格 selection 为准并触发 selection-change
  if (val) {
    pendingItems.value.forEach((row) => handoverTableRef.value?.toggleRowSelection(row, true))
  } else {
    handoverTableRef.value?.clearSelection()
  }
}

/**
 * 交接内容只保存在本窗口内，未提交关闭不写任何数据；
 * 已勾选/填写时关闭需二次确认，关掉窗口不会留下半次交接。
 */
const handleHandoverDialogClose = (done: () => void) => {
  if (!handoverFormDirty.value) {
    done()
    return
  }
  ElMessageBox.confirm('关闭后本次点名与交接内容将丢弃，且不会生成交接记录，确认关闭？', '提示', {
    type: 'warning',
    confirmButtonText: '丢弃并关闭',
    cancelButtonText: '继续交接'
  })
    .then(() => done())
    .catch(() => {})
}

const submitHandover = async () => {
  if (checkedTotal.value === 0) {
    ElMessage.warning('请勾选当前借用中的柜逐一点名')
    return
  }
  if (checkedTotal.value !== pendingTotal.value) {
    ElMessage.warning(`还有 ${pendingTotal.value - checkedTotal.value} 个未还柜未点名，勾齐全部未还柜才能交班`)
    return
  }
  if (!handoverForm.value.handoverFrom.trim()) {
    ElMessage.warning('请填写交班人')
    return
  }
  if (!handoverForm.value.handoverTo.trim()) {
    ElMessage.warning('请填写接班人')
    return
  }
  if (!handoverForm.value.handoverNote.trim()) {
    ElMessage.warning('请填写交接说明')
    return
  }
  submittingHandover.value = true
  try {
    await keyHandoverApi.submit({
      handoverFrom: handoverForm.value.handoverFrom.trim(),
      handoverTo: handoverForm.value.handoverTo.trim(),
      handoverNote: handoverForm.value.handoverNote.trim(),
      recordIds: [...checkedRecordIds.value]
    })
    ElMessage.success('交接成功，交接痕迹已记入台账，未还柜仍为借用中')
    handoverDialogVisible.value = false
    await fetchRecords()
    // 交接不改借用状态：刷新一览与页头未还条数，条数应与提交前一致
    await fetchOverview()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '交接失败')
  } finally {
    submittingHandover.value = false
  }
}

// ---------------- 交接痕迹 ----------------

const handoverTraceVisible = ref(false)
const traceLoading = ref(false)
const traceRecords = ref<KeyHandover[]>([])
const traceTarget = ref<KeyBorrowRecord | null>(null)

const openHandoverTrace = async (record: KeyBorrowRecord) => {
  traceTarget.value = record
  traceRecords.value = []
  handoverTraceVisible.value = true
  traceLoading.value = true
  try {
    const res = await keyHandoverApi.getRecordHandovers(record.id)
    traceRecords.value = res.data
  } catch (error) {
    console.error('获取交接痕迹失败', error)
  } finally {
    traceLoading.value = false
  }
}

// ---------------- 展示辅助 ----------------

const formatTime = (t?: string | null) => (t ? t.replace('T', ' ') : '-')

/** 借出时间不能晚于当前时间（历史补登可选过去时间） */
const disableFutureDate = (date: Date) => date.getTime() > Date.now()

onMounted(() => {
  fetchRecords()
  // 进入页面即预取按柜一览，保证第一次切到一览时页头未还条数已与各柜、柜体页标记一致
  fetchOverview()
})
</script>

<template>
  <div class="key-borrow-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="借用台账" name="records" />
      <el-tab-pane label="按柜钥匙状态" name="overview" />
    </el-tabs>

    <!-- ================= 借用台账列表 ================= -->
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
            placeholder="单号 / 借出人 / 事由 / 柜体编号"
            style="width: 240px"
            @keyup.enter="handleSearch"
          />
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <div class="header-actions">
          <el-button type="primary" @click="openHandoverDialog">交接班</el-button>
          <el-button type="success" @click="openCreateDialog()">登记借用</el-button>
        </div>
      </div>

      <el-table :data="records" border v-loading="recordsLoading">
        <el-table-column prop="recordNo" label="台账编号" width="190" />
        <el-table-column label="柜体" width="150">
          <template #default="{ row }">
            <el-tag type="info">{{ row.lockerNo }}</el-tag>
            <span class="locker-location">{{ row.buildingName }} {{ row.unitName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="borrower" label="借出人" width="100" />
        <el-table-column prop="reason" label="借用事由" min-width="150" show-overflow-tooltip />
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
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.onLoan" type="danger">借用中</el-tag>
            <el-tag v-else type="success">已归还</el-tag>
            <el-tag v-if="row.returnOverdue" type="warning" style="margin-left: 4px">逾期未还</el-tag>
            <el-button
              v-if="row.handoverCount > 0"
              link
              type="primary"
              size="small"
              style="margin-left: 4px"
              @click="openHandoverTrace(row)"
            >已交接×{{ row.handoverCount }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="returner" label="归还人" width="100">
          <template #default="{ row }">{{ row.returner || '-' }}</template>
        </el-table-column>
        <el-table-column label="归还时间" width="160">
          <template #default="{ row }">{{ formatTime(row.returnTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="row.onLoan">
              <el-tooltip
                v-if="!row.extendable"
                content="还没到预计归还时间，不能改期"
                placement="top"
              >
                <span class="extend-btn-wrap">
                  <el-button
                    size="small"
                    type="primary"
                    plain
                    disabled
                  >改期</el-button>
                </span>
              </el-tooltip>
              <el-button
                v-else
                size="small"
                type="primary"
                plain
                @click="openExtendDialog(row)"
              >改期</el-button>
              <el-button
                size="small"
                type="warning"
                @click="openReturnDialog(row)"
              >归还</el-button>
            </template>
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
      <div v-else-if="!recordsLoading" class="empty-tip">暂无借用记录</div>
    </template>

    <!-- ================= 按柜钥匙状态一览 ================= -->
    <template v-else>
      <el-alert type="info" :closable="false" class="overview-tip">
        全部柜体（含停用柜）列出：钥匙未还的柜体标记「借用中」，
        <template v-if="overviewLoaded">当前共有 <b>{{ openRecordTotal }}</b> 条未还记录；</template>
        <template v-else>未还条数统计中…；</template>
        永久停用柜也可补登历史借用。
      </el-alert>
      <div class="list-header">
        <span />
        <div class="header-actions">
          <el-button type="primary" @click="openHandoverDialog">交接班</el-button>
          <el-button type="success" @click="openCreateDialog()">登记借用</el-button>
        </div>
      </div>
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
        <el-table-column label="钥匙状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.onLoan" type="danger">借用中</el-tag>
            <el-tag v-else type="info">在柜</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="openRecordCount" label="未还条数" width="90" align="center" />
        <el-table-column prop="totalRecordCount" label="历史条数" width="90" align="center" />
        <el-table-column label="最近借出时间" width="160">
          <template #default="{ row }">{{ formatTime(row.lastBorrowTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="success"
              :disabled="row.onLoan"
              @click="openCreateDialog(row.lockerId)"
            >登记借用</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!overviewLoading && overview.length === 0" class="empty-tip">暂无柜体</div>
    </template>

    <!-- ================= 登记借用弹窗 ================= -->
    <el-dialog
      title="登记钥匙借用"
      v-model="createDialogVisible"
      width="560px"
      :close-on-click-modal="false"
      :before-close="handleCreateDialogClose"
    >
      <el-alert type="warning" :closable="false" class="dialog-tip">
        提交后柜体将标记为「借用中」，同一柜钥匙未还清前不能再借出；永久停用柜可在此补登历史借用。
      </el-alert>
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="柜体" required>
          <el-select
            v-model="createForm.lockerId"
            filterable
            placeholder="请选择柜体（含永久停用柜）"
            style="width: 100%"
          >
            <el-option
              v-for="opt in lockerOptions"
              :key="opt.id"
              :value="opt.id"
              :disabled="opt.onLoan"
              :label="`${opt.lockerNo}（${opt.buildingName} ${opt.unitName}）`"
            >
              <span>{{ opt.lockerNo }}（{{ opt.buildingName }} {{ opt.unitName }}）</span>
              <span class="option-tags">
                <el-tag v-if="opt.onLoan" type="danger" size="small">借用中</el-tag>
                <el-tag
                  v-if="opt.status !== 'ACTIVE'"
                  :type="opt.status === 'PERMANENTLY_DISABLED' ? 'info' : 'warning'"
                  size="small"
                >{{ opt.statusName }}</el-tag>
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="借出人" required>
          <el-input v-model="createForm.borrower" placeholder="请填写借出人" />
        </el-form-item>
        <el-form-item label="借用事由" required>
          <el-input
            v-model="createForm.reason"
            placeholder="如：柜门检修、批量投件临时借用"
          />
        </el-form-item>
        <el-form-item label="借出时间" required>
          <el-date-picker
            v-model="createForm.borrowTime"
            type="datetime"
            placeholder="请选择借出时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD[T]HH:mm:ss"
            :disabled-date="disableFutureDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="预计归还" required>
          <el-date-picker
            v-model="createForm.expectedReturnTime"
            type="datetime"
            placeholder="请选择预计归还时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD[T]HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="createForm.remark"
            type="textarea"
            :rows="2"
            placeholder="选填，如钥匙保管说明等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleCreateDialogClose(() => (createDialogVisible = false))">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">提交登记</el-button>
      </template>
    </el-dialog>

    <!-- ================= 借用改期弹窗 ================= -->
    <el-dialog
      title="借用改期"
      v-model="extendDialogVisible"
      width="520px"
      :close-on-click-modal="false"
      :before-close="handleExtendDialogClose"
    >
      <template v-if="extendTarget">
        <el-alert type="warning" :closable="false" class="dialog-tip">
          仅借用中且预计归还刚好到点或已过点的记录可改期；已归还的单、还没到预计归还时间的单不能改。
          改期后柜体仍标记「借用中」，未还条数不变。
        </el-alert>
        <el-descriptions :column="2" border class="dialog-tip">
          <el-descriptions-item label="台账编号">{{ extendTarget.recordNo }}</el-descriptions-item>
          <el-descriptions-item label="柜体">{{ extendTarget.lockerNo }}</el-descriptions-item>
          <el-descriptions-item label="借出人">{{ extendTarget.borrower }}</el-descriptions-item>
          <el-descriptions-item label="原预计归还">
            {{ formatTime(extendTarget.expectedReturnTime) }}
            <el-tag v-if="extendTarget.returnOverdue" type="danger" size="small" style="margin-left: 4px">已逾期</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px">
          <el-form-item label="新预计归还" required>
            <el-date-picker
              v-model="extendForm.expectedReturnTime"
              type="datetime"
              placeholder="选择一个更晚的预计归还时间"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DD[T]HH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="改期原因" required>
            <el-input
              v-model="extendForm.extendReason"
              type="textarea"
              :rows="2"
              placeholder="必填，如：维修配件未到，顺延三天"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="handleExtendDialogClose(() => (extendDialogVisible = false))">取消</el-button>
        <el-button type="primary" :loading="extending" @click="submitExtend">确认改期</el-button>
      </template>
    </el-dialog>

    <!-- ================= 归还弹窗 ================= -->
    <el-dialog title="归还钥匙" v-model="returnDialogVisible" width="520px">
      <template v-if="currentRecord">
        <el-descriptions :column="2" border class="dialog-tip">
          <el-descriptions-item label="台账编号">{{ currentRecord.recordNo }}</el-descriptions-item>
          <el-descriptions-item label="柜体">{{ currentRecord.lockerNo }}</el-descriptions-item>
          <el-descriptions-item label="借出人">{{ currentRecord.borrower }}</el-descriptions-item>
          <el-descriptions-item label="借出时间">{{ formatTime(currentRecord.borrowTime) }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px">
          <el-form-item label="归还人" required>
            <el-input v-model="returner" placeholder="必填，请填写归还人" />
          </el-form-item>
          <el-form-item label="归还时间">
            <el-date-picker
              v-model="returnTime"
              type="datetime"
              placeholder="默认当前时间，补登历史归还可选过去时间"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DD[T]HH:mm:ss"
              :disabled-date="disableFutureDate"
              style="width: 100%"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="returnDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="returning" @click="submitReturn">确认归还</el-button>
      </template>
    </el-dialog>

    <!-- ================= 交接班弹窗 ================= -->
    <el-dialog
      title="钥匙交接班"
      v-model="handoverDialogVisible"
      width="760px"
      :close-on-click-modal="false"
      :before-close="handleHandoverDialogClose"
    >
      <el-alert type="info" :closable="false" class="dialog-tip">
        交班人需勾选当前全部借用中的柜逐一点名，并填写接班人和交接说明。
        <template v-if="pendingLoaded">当前共 <b>{{ pendingTotal }}</b> 个未还柜，已点名 <b>{{ checkedTotal }}</b> 个；</template>
        交接只转移保管责任，各柜仍为「借用中」，未还条数不变。
      </el-alert>

      <el-table
        ref="handoverTableRef"
        :data="pendingItems"
        border
        height="300"
        v-loading="pendingLoading"
        row-key="recordId"
        @selection-change="(sel: KeyHandoverPendingItem[]) => (checkedRecordIds = sel.map((i) => i.recordId))"
      >
        <el-table-column type="selection" width="55" align="center">
          <template #header>
            <el-tooltip content="一键勾齐后仍请逐柜核对" placement="top">
              <el-checkbox
                :model-value="allChecked"
                :indeterminate="checkedTotal > 0 && !allChecked"
                @change="handleCheckAllChange"
              />
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="lockerNo" label="柜体编号" width="110">
          <template #default="{ row }">
            <el-tag type="info">{{ row.lockerNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="位置" width="150">
          <template #default="{ row }">{{ row.buildingName }} {{ row.unitName }}</template>
        </el-table-column>
        <el-table-column prop="borrower" label="借出人" width="90" />
        <el-table-column prop="reason" label="借用事由" min-width="140" show-overflow-tooltip />
        <el-table-column label="预计归还" width="170">
          <template #default="{ row }">
            {{ formatTime(row.expectedReturnTime) }}
            <el-tag v-if="row.overdue" type="danger" size="small" style="margin-left: 4px">逾期</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!pendingLoading && pendingItems.length === 0" class="empty-tip">
        当前没有借用中的柜，无需交接
      </div>

      <el-form :model="handoverForm" label-width="90px" style="margin-top: 16px">
        <el-form-item label="交班人" required>
          <el-input v-model="handoverForm.handoverFrom" placeholder="请填写交班人" />
        </el-form-item>
        <el-form-item label="接班人" required>
          <el-input v-model="handoverForm.handoverTo" placeholder="请填写接班人" />
        </el-form-item>
        <el-form-item label="交接说明" required>
          <el-input
            v-model="handoverForm.handoverNote"
            type="textarea"
            :rows="2"
            placeholder="必填，如钥匙存放位置、逾期柜跟进事项等"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="handleHandoverDialogClose(() => (handoverDialogVisible = false))">取消</el-button>
        <el-button
          type="primary"
          :loading="submittingHandover"
          :disabled="pendingTotal === 0"
          @click="submitHandover"
        >
          提交交接（{{ checkedTotal }}/{{ pendingTotal }}）
        </el-button>
      </template>
    </el-dialog>

    <!-- ================= 交接痕迹弹窗 ================= -->
    <el-dialog title="交接痕迹" v-model="handoverTraceVisible" width="720px">
      <div v-if="traceTarget" class="trace-head">
        <el-tag type="info">{{ traceTarget.lockerNo }}</el-tag>
        <span>台账编号 {{ traceTarget.recordNo }}</span>
        <span>借出人 {{ traceTarget.borrower }}</span>
      </div>
      <el-timeline v-loading="traceLoading" style="margin-top: 16px">
        <el-timeline-item
          v-for="h in traceRecords"
          :key="h.id"
          :timestamp="formatTime(h.createTime)"
          placement="top"
          type="primary"
        >
          <el-card shadow="never">
            <div class="trace-line">
              <el-tag size="small">{{ h.handoverNo }}</el-tag>
              <b>{{ h.handoverFrom }}</b> 交班给 <b>{{ h.handoverTo }}</b>
              <el-tag size="small" type="info">点名 {{ h.itemCount }} 柜</el-tag>
            </div>
            <div class="trace-note">交接说明：{{ h.handoverNote }}</div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <div v-if="!traceLoading && traceRecords.length === 0" class="empty-tip">暂无交接痕迹</div>
    </el-dialog>
  </div>
</template>

<style scoped>
.key-borrow-page {
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

.header-actions {
  display: flex;
  gap: 12px;
}

.trace-head {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #666;
  font-size: 13px;
}

.trace-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.trace-note {
  margin-top: 8px;
  color: #555;
  font-size: 13px;
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

.extend-btn-wrap {
  display: inline-block;
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
