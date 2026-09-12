<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { clearanceApi, CLEARANCE_STATUS_NAME_MAP } from '@/api/clearance'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  ClearanceOrder,
  ClearanceLockerOption,
  LockerClearanceOverview,
  ClearanceStatusCode,
  ClearanceOrderCreateRequest,
  ClearanceUrgeRecord
} from '@/api/clearance'

// ---------------- 清柜单列表 ----------------

const activeTab = ref<'orders' | 'overview'>('orders')
const orders = ref<ClearanceOrder[]>([])
const ordersLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const statusFilter = ref<ClearanceStatusCode | ''>('PROCESSING')
const keyword = ref('')

const statusOptions = Object.entries(CLEARANCE_STATUS_NAME_MAP).map(([value, label]) => ({
  value: value as ClearanceStatusCode,
  label
}))

const fetchOrders = async () => {
  ordersLoading.value = true
  try {
    const res = await clearanceApi.getOrders({
      page: currentPage.value,
      size: pageSize.value,
      status: statusFilter.value,
      keyword: keyword.value.trim() || undefined
    })
    orders.value = res.data.data
    total.value = res.data.total
  } catch (error) {
    console.error('获取清柜单列表失败', error)
  } finally {
    ordersLoading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchOrders()
}

const handleReset = () => {
  statusFilter.value = ''
  keyword.value = ''
  currentPage.value = 1
  fetchOrders()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchOrders()
}

// ---------------- 按柜滞留一览 ----------------

const overview = ref<LockerClearanceOverview[]>([])
const overviewLoading = ref(false)

const fetchOverview = async () => {
  overviewLoading.value = true
  try {
    const res = await clearanceApi.getLockerOverview()
    overview.value = res.data
  } catch (error) {
    console.error('获取柜体滞留一览失败', error)
  } finally {
    overviewLoading.value = false
  }
}

const handleTabChange = () => {
  if (activeTab.value === 'overview') {
    fetchOverview()
  } else {
    fetchOrders()
  }
}

// ---------------- 登记清柜单 ----------------

const createDialogVisible = ref(false)
const lockerOptions = ref<ClearanceLockerOption[]>([])
const submitting = ref(false)
const createForm = ref<ClearanceOrderCreateRequest>(emptyCreateForm())

function emptyCreateForm(): ClearanceOrderCreateRequest {
  return {
    lockerId: null,
    overdueCompartments: '',
    packageCount: 1,
    foundTime: null,
    handler: '',
    remark: ''
  }
}

/** 表单已填写内容时，关闭窗口前确认，避免误关丢单 */
const createFormDirty = computed(() => {
  const f = createForm.value
  return !!(
    f.lockerId ||
    f.overdueCompartments.trim() ||
    f.foundTime ||
    f.handler.trim() ||
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
    const res = await clearanceApi.getLockerOptions()
    lockerOptions.value = res.data
  } catch (error) {
    console.error('获取可选柜体失败', error)
  }
}

/**
 * 登记内容只保存在本窗口内，未提交前关闭不会写入任何数据；
 * 已填写内容时关闭需二次确认，关掉窗口不会留下半单。
 */
const handleCreateDialogClose = (done: () => void) => {
  if (!createFormDirty.value) {
    done()
    return
  }
  ElMessageBox.confirm('关闭后本次填写的内容将丢弃，且不会生成清柜单，确认关闭？', '提示', {
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
    ElMessage.warning('请选择滞留件所在柜体')
    return
  }
  if (!f.overdueCompartments.trim()) {
    ElMessage.warning('请填写滞留格口')
    return
  }
  if (!f.packageCount || f.packageCount < 1) {
    ElMessage.warning('滞留件数必须大于 0')
    return
  }
  if (!f.foundTime) {
    ElMessage.warning('请选择发现时间')
    return
  }
  if (!f.handler.trim()) {
    ElMessage.warning('请填写处理人')
    return
  }
  submitting.value = true
  try {
    await clearanceApi.createOrder({
      lockerId: f.lockerId,
      overdueCompartments: f.overdueCompartments.trim(),
      packageCount: f.packageCount,
      foundTime: f.foundTime,
      handler: f.handler.trim(),
      remark: f.remark?.trim() || undefined
    })
    ElMessage.success('清柜单登记成功')
    createDialogVisible.value = false
    createForm.value = emptyCreateForm()
    await fetchOrders()
    if (activeTab.value === 'overview') {
      await fetchOverview()
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '登记失败')
  } finally {
    submitting.value = false
  }
}

// ---------------- 办结 ----------------

const completeDialogVisible = ref(false)
const completing = ref(false)
const currentOrder = ref<ClearanceOrder | null>(null)
const handleResult = ref('')

const openCompleteDialog = (order: ClearanceOrder) => {
  currentOrder.value = order
  handleResult.value = ''
  completeDialogVisible.value = true
}

const submitComplete = async () => {
  if (!currentOrder.value) return
  if (!handleResult.value.trim()) {
    ElMessage.warning('办结必须填写处理结果')
    return
  }
  completing.value = true
  try {
    await clearanceApi.completeOrder(currentOrder.value.id, handleResult.value.trim())
    ElMessage.success('清柜单已办结')
    completeDialogVisible.value = false
    await fetchOrders()
    if (activeTab.value === 'overview') {
      await fetchOverview()
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '办结失败')
  } finally {
    completing.value = false
  }
}

// ---------------- 当面催领 ----------------

const urgeDialogVisible = ref(false)
const urgeLoading = ref(false)
const urgeSubmitting = ref(false)
const urgeOrder = ref<ClearanceOrder | null>(null)
const urgeRecords = ref<ClearanceUrgeRecord[]>([])
const urgeForm = ref<{ urgeTime: string | null; operator: string }>(emptyUrgeForm())
const closeForm = ref<{ closeNote: string; closeOperator: string }>(emptyCloseForm())

function emptyUrgeForm() {
  return {
    // 默认当前时间，格式与后端 LocalDateTime 对齐
    urgeTime: formatLocalDateTime(new Date()),
    operator: ''
  }
}

function emptyCloseForm() {
  return { closeNote: '', closeOperator: '' }
}

/** 当前未关闭催领（同一张办理中的单同时最多一笔） */
const openUrgeRecord = computed(() =>
  urgeRecords.value.find((r) => r.status === 'OPEN') || null
)

const urgeFormDirty = computed(
  () =>
    !!urgeForm.value.operator.trim() ||
    !!closeForm.value.closeNote.trim() ||
    !!closeForm.value.closeOperator.trim()
)

/** 当前时间按本地时区格式化为 yyyy-MM-ddTHH:mm:ss，供 el-date-picker value-format 使用 */
function formatLocalDateTime(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  )
}

const openUrgeDialog = async (order: ClearanceOrder) => {
  urgeOrder.value = order
  urgeForm.value = emptyUrgeForm()
  closeForm.value = emptyCloseForm()
  urgeRecords.value = []
  urgeDialogVisible.value = true
  urgeLoading.value = true
  try {
    const res = await clearanceApi.getUrges(order.id)
    urgeRecords.value = res.data
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '获取催领记录失败')
  } finally {
    urgeLoading.value = false
  }
}

/**
 * 催领内容只保存在本窗口内，未提交前关闭不会写入任何数据；
 * 已填写经办人时关闭需二次确认，关掉窗口不会写出半条催领。
 */
const handleUrgeDialogClose = (done: () => void) => {
  if (!urgeFormDirty.value) {
    done()
    return
  }
  ElMessageBox.confirm('关闭后本次填写的催领内容将丢弃，且不会生成催领记录，确认关闭？', '提示', {
    type: 'warning',
    confirmButtonText: '丢弃并关闭',
    cancelButtonText: '继续填写'
  })
    .then(() => done())
    .catch(() => {})
}

const submitUrge = async () => {
  if (!urgeOrder.value) return
  if (!urgeForm.value.urgeTime) {
    ElMessage.warning('请选择催领时间')
    return
  }
  if (!urgeForm.value.operator.trim()) {
    ElMessage.warning('请填写经办人')
    return
  }
  urgeSubmitting.value = true
  try {
    await clearanceApi.createUrge(urgeOrder.value.id, {
      urgeTime: urgeForm.value.urgeTime,
      operator: urgeForm.value.operator.trim()
    })
    ElMessage.success('当面催领已登记')
    urgeForm.value = emptyUrgeForm()
    closeForm.value = emptyCloseForm()
    await refreshUrges()
    await fetchOrders()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '登记催领失败')
  } finally {
    urgeSubmitting.value = false
  }
}

const submitCloseUrge = async () => {
  const open = openUrgeRecord.value
  if (!open || !urgeOrder.value) return
  try {
    await ElMessageBox.confirm(
      '关闭后该单可再登记下一笔当面催领，确认关闭当前未关闭催领？',
      '关闭催领',
      { type: 'warning', confirmButtonText: '确认关闭' }
    )
  } catch {
    return
  }
  urgeSubmitting.value = true
  try {
    await clearanceApi.closeUrge(open.id, {
      closeNote: closeForm.value.closeNote.trim() || undefined,
      closeOperator: closeForm.value.closeOperator.trim() || undefined
    })
    ElMessage.success('催领已关闭')
    closeForm.value = emptyCloseForm()
    await refreshUrges()
    await fetchOrders()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '关闭催领失败')
  } finally {
    urgeSubmitting.value = false
  }
}

/** 提交/关闭后全部以后端台账为准刷新，次数与未关闭标记保持对得上 */
const refreshUrges = async () => {
  if (!urgeOrder.value) return
  try {
    const res = await clearanceApi.getUrges(urgeOrder.value.id)
    urgeRecords.value = res.data
    // 若详情抽屉正展示同一张单，同步刷新抽屉里的台账与次数
    if (detailDrawerVisible.value && detailOrder.value?.id === urgeOrder.value.id) {
      const detailRes = await clearanceApi.getOrder(urgeOrder.value.id)
      detailOrder.value = detailRes.data
    }
  } catch (error) {
    console.error('刷新催领记录失败', error)
  }
}

// ---------------- 详情 ----------------

const detailDrawerVisible = ref(false)
const detailOrder = ref<ClearanceOrder | null>(null)

const openDetail = async (order: ClearanceOrder) => {
  try {
    const res = await clearanceApi.getOrder(order.id)
    detailOrder.value = res.data
    detailDrawerVisible.value = true
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '获取清柜单详情失败')
  }
}

// ---------------- 展示辅助 ----------------

const formatTime = (t?: string | null) => (t ? t.replace('T', ' ') : '-')

/** 发现时间不晚于当前时间（历史补登可选过去时间） */
const disableFutureDate = (date: Date) => date.getTime() > Date.now()

onMounted(() => {
  fetchOrders()
})
</script>

<template>
  <div class="clearance-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="清柜单" name="orders" />
      <el-tab-pane label="按柜滞留一览" name="overview" />
    </el-tabs>

    <!-- ================= 清柜单列表 ================= -->
    <template v-if="activeTab === 'orders'">
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
            placeholder="单号 / 柜体编号 / 滞留格口"
            style="width: 240px"
            @keyup.enter="handleSearch"
          />
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <el-button type="success" @click="openCreateDialog()">登记清柜单</el-button>
      </div>

      <el-table :data="orders" border v-loading="ordersLoading">
        <el-table-column prop="orderNo" label="清柜单号" width="190">
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row)">{{ row.orderNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="柜体" width="200">
          <template #default="{ row }">
            <el-tag type="info">{{ row.lockerNo }}</el-tag>
            <span class="locker-location">{{ row.buildingName }} {{ row.unitName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="overdueCompartments" label="滞留格口" min-width="120" show-overflow-tooltip />
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
        <el-table-column label="催领" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.urgeCount > 0">
              <el-tag v-if="row.openUrge" type="warning" size="small">催领中</el-tag>
              <span v-else>{{ row.urgeCount }} 次</span>
            </span>
            <span v-else class="muted-text">-</span>
          </template>
        </el-table-column>
        <el-table-column label="办结时间" width="160">
          <template #default="{ row }">{{ formatTime(row.completeTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'PROCESSING'"
              size="small"
              type="primary"
              plain
              @click="openUrgeDialog(row)"
            >当面催领</el-button>
            <el-button
              v-if="row.status === 'PROCESSING'"
              size="small"
              type="warning"
              @click="openCompleteDialog(row)"
            >办结</el-button>
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
      <div v-else-if="!ordersLoading" class="empty-tip">暂无清柜单</div>
    </template>

    <!-- ================= 按柜滞留一览 ================= -->
    <template v-else>
      <el-alert type="info" :closable="false" class="overview-tip">
        正常柜全量列出：已登记滞留的柜体标记「滞留中」，未登记的显示「未登记」，
        发现柜内有超期未取件但列表显示未登记时，即为漏登，请立即补登。
      </el-alert>
      <el-table :data="overview" border v-loading="overviewLoading">
        <el-table-column prop="lockerNo" label="柜体编号" width="130">
          <template #default="{ row }">
            <el-tag type="info">{{ row.lockerNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="buildingName" label="所属楼栋" width="110" />
        <el-table-column prop="unitName" label="所属单元" width="100" />
        <el-table-column label="楼层" width="80">
          <template #default="{ row }">{{ row.floor || '-' }}</template>
        </el-table-column>
        <el-table-column label="滞留标记" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.overdue" type="danger">滞留中</el-tag>
            <el-tag v-else type="info">未登记</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="openOrderCount" label="在办单数" width="90" align="center" />
        <el-table-column prop="openPackageCount" label="滞留件数" width="90" align="center" />
        <el-table-column label="最近发现时间" width="160">
          <template #default="{ row }">{{ formatTime(row.lastFoundTime) }}</template>
        </el-table-column>
        <el-table-column prop="totalOrderCount" label="历史单数" width="90" align="center" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="openCreateDialog(row.lockerId)">
              登记清柜
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!overviewLoading && overview.length === 0" class="empty-tip">暂无正常柜体</div>
    </template>

    <!-- ================= 登记清柜单弹窗 ================= -->
    <el-dialog
      title="登记清柜单"
      v-model="createDialogVisible"
      width="560px"
      :close-on-click-modal="false"
      :before-close="handleCreateDialogClose"
    >
      <el-alert type="warning" :closable="false" class="dialog-tip">
        提交后柜体将标记为「滞留中」；永久停用柜可在此补登历史滞留。
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
              :label="`${opt.lockerNo}（${opt.buildingName} ${opt.unitName}）`"
            >
              <span>{{ opt.lockerNo }}（{{ opt.buildingName }} {{ opt.unitName }}）</span>
              <span class="option-tags">
                <el-tag v-if="opt.overdue" type="danger" size="small">滞留中</el-tag>
                <el-tag
                  v-if="opt.status !== 'ACTIVE'"
                  :type="opt.status === 'PERMANENTLY_DISABLED' ? 'info' : 'warning'"
                  size="small"
                >{{ opt.statusName }}</el-tag>
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="滞留格口" required>
          <el-input
            v-model="createForm.overdueCompartments"
            placeholder="如：A03,A07"
          />
        </el-form-item>
        <el-form-item label="滞留件数" required>
          <el-input-number v-model="createForm.packageCount" :min="1" :max="999" />
        </el-form-item>
        <el-form-item label="发现时间" required>
          <el-date-picker
            v-model="createForm.foundTime"
            type="datetime"
            placeholder="请选择发现时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD[T]HH:mm:ss"
            :disabled-date="disableFutureDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="处理人" required>
          <el-input v-model="createForm.handler" placeholder="请填写处理人" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="createForm.remark"
            type="textarea"
            :rows="2"
            placeholder="选填，如超期天数、已通知情况等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleCreateDialogClose(() => (createDialogVisible = false))">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">提交登记</el-button>
      </template>
    </el-dialog>

    <!-- ================= 办结弹窗 ================= -->
    <el-dialog title="办结清柜单" v-model="completeDialogVisible" width="520px">
      <template v-if="currentOrder">
        <el-descriptions :column="2" border class="dialog-tip">
          <el-descriptions-item label="清柜单号">{{ currentOrder.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="柜体">{{ currentOrder.lockerNo }}</el-descriptions-item>
          <el-descriptions-item label="滞留格口">{{ currentOrder.overdueCompartments }}</el-descriptions-item>
          <el-descriptions-item label="件数">{{ currentOrder.packageCount }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px">
          <el-form-item label="处理结果" required>
            <el-input
              v-model="handleResult"
              type="textarea"
              :rows="3"
              placeholder="必填，如：业主已取走滞留件，格口已清空消毒"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="completeDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="completing" @click="submitComplete">确认办结</el-button>
      </template>
    </el-dialog>

    <!-- ================= 当面催领弹窗 ================= -->
    <el-dialog
      title="当面催领"
      v-model="urgeDialogVisible"
      width="600px"
      :close-on-click-modal="false"
      :before-close="handleUrgeDialogClose"
    >
      <template v-if="urgeOrder">
        <el-descriptions :column="2" border class="dialog-tip">
          <el-descriptions-item label="清柜单号">{{ urgeOrder.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="柜体">{{ urgeOrder.lockerNo }}</el-descriptions-item>
          <el-descriptions-item label="滞留格口">{{ urgeOrder.overdueCompartments }}</el-descriptions-item>
          <el-descriptions-item label="件数">{{ urgeOrder.packageCount }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="openUrgeRecord"
          type="warning"
          :closable="false"
          class="dialog-tip"
          title="该单已有一笔未关闭催领，不能同时再挂第二笔；关闭后可再登记。"
        />

        <div v-loading="urgeLoading">
          <!-- 已有未关闭催领：展示并可关闭，不能再登记 -->
          <template v-if="openUrgeRecord">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="催领时间">
                {{ formatTime(openUrgeRecord.urgeTime) }}
              </el-descriptions-item>
              <el-descriptions-item label="经办人">
                {{ openUrgeRecord.operator }}
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag type="warning" size="small">未关闭</el-tag>
              </el-descriptions-item>
            </el-descriptions>
            <el-form label-width="90px" class="urge-close-form">
              <el-form-item label="关闭说明">
                <el-input
                  v-model="closeForm.closeNote"
                  type="textarea"
                  :rows="2"
                  placeholder="选填，如：业主承诺明日取件"
                />
              </el-form-item>
              <el-form-item label="关闭经办人">
                <el-input v-model="closeForm.closeOperator" placeholder="选填，默认系统管理员" />
              </el-form-item>
            </el-form>
          </template>

          <!-- 无未关闭催领：登记新一笔（内容仅存于本窗口，提交后才落库） -->
          <el-form v-else label-width="90px">
            <el-form-item label="催领时间" required>
              <el-date-picker
                v-model="urgeForm.urgeTime"
                type="datetime"
                placeholder="请选择催领时间"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DD[T]HH:mm:ss"
                :disabled-date="disableFutureDate"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="经办人" required>
              <el-input v-model="urgeForm.operator" placeholder="请填写当面催领的经办人" />
            </el-form-item>
          </el-form>

          <div class="urge-history">
            <div class="urge-history-title">
              催领台账（共 {{ urgeRecords.length }} 笔）
            </div>
            <el-table :data="urgeRecords" border size="small">
              <el-table-column label="催领时间" width="150">
                <template #default="{ row }">{{ formatTime(row.urgeTime) }}</template>
              </el-table-column>
              <el-table-column prop="operator" label="经办人" width="90" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag v-if="row.status === 'OPEN'" type="warning" size="small">未关闭</el-tag>
                  <el-tag v-else type="info" size="small">已关闭</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="关闭时间" width="150">
                <template #default="{ row }">{{ formatTime(row.closeTime) }}</template>
              </el-table-column>
              <el-table-column label="关闭说明/经办人" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">
                  <span v-if="row.status === 'CLOSED'">
                    {{ row.closeNote || (row.autoClosed ? '办结自动关闭' : '-') }}
                    <span class="muted-text">（{{ row.closeOperator || '系统管理员' }}）</span>
                  </span>
                  <span v-else class="muted-text">-</span>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="!urgeLoading && urgeRecords.length === 0" class="urge-empty">
              暂无催领记录
            </div>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="handleUrgeDialogClose(() => (urgeDialogVisible = false))">取消</el-button>
        <el-button
          v-if="openUrgeRecord"
          type="success"
          :loading="urgeSubmitting"
          @click="submitCloseUrge"
        >关闭催领</el-button>
        <el-button
          v-else
          type="primary"
          :loading="urgeSubmitting"
          :disabled="urgeLoading"
          @click="submitUrge"
        >提交催领</el-button>
      </template>
    </el-dialog>

    <!-- ================= 详情抽屉 ================= -->
    <el-drawer title="清柜单详情" v-model="detailDrawerVisible" size="480px">
      <el-descriptions v-if="detailOrder" :column="1" border>
        <el-descriptions-item label="清柜单号">{{ detailOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detailOrder.overdue" type="danger">滞留中</el-tag>
          <el-tag v-else type="success">已办结</el-tag>
          <el-tag
            v-if="detailOrder.openUrge"
            type="warning"
            size="small"
            style="margin-left: 8px"
          >催领中</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="柜体">
          {{ detailOrder.lockerNo }}
          <el-tag size="small" style="margin-left: 8px">{{ detailOrder.lockerStatusName }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="所属位置">
          {{ detailOrder.buildingName }} {{ detailOrder.unitName }} {{ detailOrder.floor || '' }}
        </el-descriptions-item>
        <el-descriptions-item label="滞留格口">{{ detailOrder.overdueCompartments }}</el-descriptions-item>
        <el-descriptions-item label="滞留件数">{{ detailOrder.packageCount }} 件</el-descriptions-item>
        <el-descriptions-item label="发现时间">{{ formatTime(detailOrder.foundTime) }}</el-descriptions-item>
        <el-descriptions-item label="处理人">{{ detailOrder.handler }}</el-descriptions-item>
        <el-descriptions-item label="催领次数">{{ detailOrder.urgeCount ?? 0 }} 次</el-descriptions-item>
        <el-descriptions-item label="处理结果">{{ detailOrder.handleResult || '-' }}</el-descriptions-item>
        <el-descriptions-item label="办结时间">{{ formatTime(detailOrder.completeTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailOrder.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="登记时间">{{ formatTime(detailOrder.createTime) }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="detailOrder" class="urge-history drawer-urge-history">
        <div class="urge-history-title">当面催领台账</div>
        <el-timeline v-if="(detailOrder.urgeRecords || []).length > 0">
          <el-timeline-item
            v-for="r in detailOrder.urgeRecords"
            :key="r.id"
            :type="r.status === 'OPEN' ? 'warning' : 'info'"
            :timestamp="formatTime(r.urgeTime)"
          >
            <div>经办人：{{ r.operator }}</div>
            <div>
              状态：
              <el-tag v-if="r.status === 'OPEN'" type="warning" size="small">未关闭</el-tag>
              <el-tag v-else type="info" size="small">已关闭</el-tag>
              <span v-if="r.status === 'CLOSED'" class="muted-text">
                {{ formatTime(r.closeTime) }} · {{ r.closeOperator || '系统管理员' }}
              </span>
            </div>
            <div v-if="r.closeNote" class="muted-text">{{ r.closeNote }}</div>
          </el-timeline-item>
        </el-timeline>
        <div v-else class="urge-empty">暂无催领记录</div>
        <el-button
          v-if="detailOrder.status === 'PROCESSING'"
          type="primary"
          plain
          size="small"
          style="margin-top: 8px"
          @click="openUrgeDialog(detailOrder)"
        >登记当面催领</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.clearance-page {
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

.muted-text {
  color: #999;
  font-size: 12px;
}

.urge-close-form {
  margin-top: 16px;
}

.urge-history {
  margin-top: 20px;
}

.drawer-urge-history {
  padding: 0 20px 20px;
}

.urge-history-title {
  font-weight: 600;
  margin-bottom: 10px;
}

.urge-empty {
  text-align: center;
  color: #999;
  font-size: 12px;
  padding: 16px;
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
