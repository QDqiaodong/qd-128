<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { keyBorrowApi, KEY_BORROW_STATUS_NAME_MAP } from '@/api/keyBorrow'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  KeyBorrowRecord,
  KeyBorrowLockerOption,
  LockerKeyBorrowOverview,
  KeyBorrowStatusCode,
  KeyBorrowCreateRequest
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

const fetchOverview = async () => {
  overviewLoading.value = true
  try {
    const res = await keyBorrowApi.getLockerOverview()
    overview.value = res.data
  } catch (error) {
    console.error('获取柜体钥匙状态一览失败', error)
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
    if (activeTab.value === 'overview') {
      await fetchOverview()
    }
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
    if (activeTab.value === 'overview') {
      await fetchOverview()
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '归还登记失败')
  } finally {
    returning.value = false
  }
}

// ---------------- 展示辅助 ----------------

const formatTime = (t?: string | null) => (t ? t.replace('T', ' ') : '-')

/** 借出时间不能晚于当前时间（历史补登可选过去时间） */
const disableFutureDate = (date: Date) => date.getTime() > Date.now()

onMounted(() => {
  fetchRecords()
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
        <el-button type="success" @click="openCreateDialog()">登记借用</el-button>
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
        <el-table-column label="预计归还" width="160">
          <template #default="{ row }">{{ formatTime(row.expectedReturnTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.onLoan" type="danger">借用中</el-tag>
            <el-tag v-else type="success">已归还</el-tag>
            <el-tag v-if="row.returnOverdue" type="warning" style="margin-left: 4px">逾期未还</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="returner" label="归还人" width="100">
          <template #default="{ row }">{{ row.returner || '-' }}</template>
        </el-table-column>
        <el-table-column label="归还时间" width="160">
          <template #default="{ row }">{{ formatTime(row.returnTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.onLoan"
              size="small"
              type="warning"
              @click="openReturnDialog(row)"
            >归还</el-button>
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
        全部柜体（含停用柜）列出：钥匙未还的柜体标记「借用中」，当前共有
        <b>{{ openRecordTotal }}</b> 条未还记录；永久停用柜也可补登历史借用。
      </el-alert>
      <div class="list-header">
        <span />
        <el-button type="success" @click="openCreateDialog()">登记借用</el-button>
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

.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px;
}

.el-pagination {
  margin-top: 16px;
}
</style>
