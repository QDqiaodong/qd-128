<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { repairApi, REPAIR_STATUS_NAME_MAP } from '@/api/repair'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  RepairTicket,
  LockerRepairOverview,
  RepairStatusCode,
  RepairTicketCompleteRequest
} from '@/api/repair'

const router = useRouter()

// ---------------- 报修台账列表 ----------------

const activeTab = ref<'tickets' | 'overview'>('tickets')
const tickets = ref<RepairTicket[]>([])
const ticketsLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const statusFilter = ref<RepairStatusCode | ''>('PROCESSING')
const keyword = ref('')

const statusOptions = Object.entries(REPAIR_STATUS_NAME_MAP).map(([value, label]) => ({
  value: value as RepairStatusCode,
  label
}))

const fetchTickets = async () => {
  ticketsLoading.value = true
  try {
    const res = await repairApi.getTickets({
      page: currentPage.value,
      size: pageSize.value,
      status: statusFilter.value,
      keyword: keyword.value.trim() || undefined
    })
    tickets.value = res.data.data
    total.value = res.data.total
  } catch (error) {
    console.error('获取报修台账失败', error)
  } finally {
    ticketsLoading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchTickets()
}

const handleReset = () => {
  statusFilter.value = ''
  keyword.value = ''
  currentPage.value = 1
  fetchTickets()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchTickets()
}

// ---------------- 按柜报修状态一览 ----------------

const overview = ref<LockerRepairOverview[]>([])
const overviewLoading = ref(false)
/** 一览是否已成功加载过：未加载前页头不显示具体处理中条数，避免把「加载中/加载失败」误判为已全部修好 */
const overviewLoaded = ref(false)

const fetchOverview = async () => {
  overviewLoading.value = true
  try {
    const res = await repairApi.getLockerOverview()
    overview.value = res.data
    overviewLoaded.value = true
  } catch (error) {
    console.error('获取按柜报修状态一览失败', error)
    ElMessage.error('获取按柜报修状态一览失败，处理中条数暂未更新，请重新切换或刷新')
  } finally {
    overviewLoading.value = false
  }
}

/** 当前处理中总条数：与一览中各柜处理中条数同源，刷新后保持一致 */
const openTicketTotal = computed(() =>
  overview.value.reduce((sum, item) => sum + (item.openTicketCount || 0), 0)
)

const handleTabChange = () => {
  if (activeTab.value === 'overview') {
    fetchOverview()
  } else {
    fetchTickets()
  }
}

// ---------------- 完工 ----------------

const completeDialogVisible = ref(false)
const completing = ref(false)
const currentTicket = ref<RepairTicket | null>(null)
const completeForm = ref<RepairTicketCompleteRequest>({ handler: '', repairResult: '' })

const openCompleteDialog = (ticket: RepairTicket) => {
  currentTicket.value = ticket
  completeForm.value = { handler: '', repairResult: '' }
  completeDialogVisible.value = true
}

/** 完工内容只保存在本窗口内，未提交前关闭不会写入任何数据；已填写内容时关闭需二次确认 */
const completeFormDirty = computed(
  () => !!(completeForm.value.handler.trim() || completeForm.value.repairResult.trim())
)

const handleCompleteDialogClose = (done: () => void) => {
  if (!completeFormDirty.value) {
    done()
    return
  }
  ElMessageBox.confirm('关闭后本次填写的完工内容将丢弃，报修单仍为处理中，确认关闭？', '提示', {
    type: 'warning',
    confirmButtonText: '丢弃并关闭',
    cancelButtonText: '继续填写'
  })
    .then(() => done())
    .catch(() => {})
}

const submitComplete = async () => {
  if (!currentTicket.value) return
  if (!completeForm.value.handler.trim()) {
    ElMessage.warning('完工必须填写处理人')
    return
  }
  if (!completeForm.value.repairResult.trim()) {
    ElMessage.warning('完工必须填写处理结果')
    return
  }
  completing.value = true
  try {
    await repairApi.completeTicket(currentTicket.value.id, {
      handler: completeForm.value.handler.trim(),
      repairResult: completeForm.value.repairResult.trim()
    })
    ElMessage.success('完工登记成功，报修单已修好')
    completeDialogVisible.value = false
    await fetchTickets()
    // 无论当前在哪个页签都同步刷新按柜一览，保证处理中条数与柜体可用标记实时一致
    await fetchOverview()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '完工登记失败')
  } finally {
    completing.value = false
  }
}

// ---------------- 展示辅助 ----------------

const formatTime = (t?: string | null) => (t ? t.replace('T', ' ') : '-')

const goLockerDetail = (lockerId: number) => {
  router.push(`/lockers/${lockerId}`)
}

onMounted(() => {
  fetchTickets()
  // 进入页面即预取按柜一览，保证第一次切到一览时处理中条数已与台账、柜体页标记一致
  fetchOverview()
})
</script>

<template>
  <div class="repair-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="报修台账" name="tickets" />
      <el-tab-pane label="按柜报修状态" name="overview" />
    </el-tabs>

    <!-- ================= 报修台账列表 ================= -->
    <template v-if="activeTab === 'tickets'">
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
            placeholder="单号 / 格口 / 现象 / 报修人 / 柜体编号"
            style="width: 260px"
            @keyup.enter="handleSearch"
          />
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <el-alert type="info" :closable="false" class="header-tip">
          在「快递柜详情」页登记故障格口报修
        </el-alert>
      </div>

      <el-table :data="tickets" border v-loading="ticketsLoading">
        <el-table-column prop="ticketNo" label="报修单号" width="190" />
        <el-table-column label="柜体" width="150">
          <template #default="{ row }">
            <el-tag type="info">{{ row.lockerNo }}</el-tag>
            <span class="locker-location">{{ row.buildingName }} {{ row.unitName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="故障格口" width="90" align="center">
          <template #default="{ row }">{{ row.compartmentNo }} 号</template>
        </el-table-column>
        <el-table-column prop="symptom" label="故障现象" min-width="160" show-overflow-tooltip />
        <el-table-column prop="reporter" label="报修人" width="100" />
        <el-table-column label="报修时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.processing" type="danger">处理中</el-tag>
            <el-tag v-else type="success">已修好</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handler" label="处理人" width="100">
          <template #default="{ row }">{{ row.handler || '-' }}</template>
        </el-table-column>
        <el-table-column label="处理结果" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip
              v-if="row.fixedTime"
              :content="`完工时间：${formatTime(row.fixedTime)}`"
              placement="top"
            >
              <span>{{ row.repairResult || '-' }}</span>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.processing"
              size="small"
              type="warning"
              @click="openCompleteDialog(row)"
            >完工</el-button>
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
      <div v-else-if="!ticketsLoading" class="empty-tip">暂无报修记录</div>
    </template>

    <!-- ================= 按柜报修状态一览 ================= -->
    <template v-else>
      <el-alert type="info" :closable="false" class="overview-tip">
        全部柜体（含停用柜）列出：存在处理中报修单的柜体标记「维修中」，其余为「可用」，
        <template v-if="overviewLoaded">当前共有 <b>{{ openTicketTotal }}</b> 条处理中报修；</template>
        <template v-else>处理中条数统计中…；</template>
        登记报修请进入对应柜体详情页。
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
        <el-table-column label="可用标记" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.repairing" type="danger">维修中</el-tag>
            <el-tag v-else type="success">可用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="openTicketCount" label="处理中条数" width="100" align="center" />
        <el-table-column prop="totalTicketCount" label="历史条数" width="90" align="center" />
        <el-table-column label="最近报修时间" width="160">
          <template #default="{ row }">{{ formatTime(row.lastReportTime) }}</template>
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

    <!-- ================= 完工弹窗 ================= -->
    <el-dialog
      title="报修完工"
      v-model="completeDialogVisible"
      width="520px"
      :close-on-click-modal="false"
      :before-close="handleCompleteDialogClose"
    >
      <template v-if="currentTicket">
        <el-alert type="warning" :closable="false" class="dialog-tip">
          完工后报修单状态变为「已修好」；该柜处理中条数相应减少，全部修好后柜体恢复「可用」。
        </el-alert>
        <el-descriptions :column="2" border class="dialog-tip">
          <el-descriptions-item label="报修单号">{{ currentTicket.ticketNo }}</el-descriptions-item>
          <el-descriptions-item label="柜体">{{ currentTicket.lockerNo }}</el-descriptions-item>
          <el-descriptions-item label="故障格口">{{ currentTicket.compartmentNo }} 号</el-descriptions-item>
          <el-descriptions-item label="报修人">{{ currentTicket.reporter }}</el-descriptions-item>
          <el-descriptions-item label="故障现象" :span="2">{{ currentTicket.symptom }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px">
          <el-form-item label="处理人" required>
            <el-input v-model="completeForm.handler" placeholder="必填，请填写处理人" />
          </el-form-item>
          <el-form-item label="处理结果" required>
            <el-input
              v-model="completeForm.repairResult"
              type="textarea"
              :rows="3"
              placeholder="必填，如：更换锁芯并调试，开关恢复正常"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="handleCompleteDialogClose(() => (completeDialogVisible = false))">取消</el-button>
        <el-button type="warning" :loading="completing" @click="submitComplete">确认完工</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.repair-page {
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
