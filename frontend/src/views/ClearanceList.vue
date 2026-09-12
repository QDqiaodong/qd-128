<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { clearanceApi, CLEARANCE_STATUS_NAME_MAP } from '@/api/clearance'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  ClearanceOrder,
  ClearanceLockerOption,
  LockerClearanceOverview,
  ClearanceStatusCode,
  ClearanceOrderCreateRequest
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
        <el-table-column label="办结时间" width="160">
          <template #default="{ row }">{{ formatTime(row.completeTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
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

    <!-- ================= 详情抽屉 ================= -->
    <el-drawer title="清柜单详情" v-model="detailDrawerVisible" size="480px">
      <el-descriptions v-if="detailOrder" :column="1" border>
        <el-descriptions-item label="清柜单号">{{ detailOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detailOrder.overdue" type="danger">滞留中</el-tag>
          <el-tag v-else type="success">已办结</el-tag>
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
        <el-descriptions-item label="处理结果">{{ detailOrder.handleResult || '-' }}</el-descriptions-item>
        <el-descriptions-item label="办结时间">{{ formatTime(detailOrder.completeTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailOrder.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="登记时间">{{ formatTime(detailOrder.createTime) }}</el-descriptions-item>
      </el-descriptions>
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

.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px;
}

.el-pagination {
  margin-top: 16px;
}
</style>
