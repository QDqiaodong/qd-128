<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { doorAlarmApi, DOOR_ALARM_STATUS_NAME_MAP } from '@/api/doorAlarm'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  DoorAlarmRecord,
  LockerDoorAlarmOverview,
  DoorAlarmStatusCode,
  DoorAlarmCloseRequest
} from '@/api/doorAlarm'

const router = useRouter()

// ---------------- 告警台账列表 ----------------

const activeTab = ref<'alarms' | 'overview'>('alarms')
const alarms = ref<DoorAlarmRecord[]>([])
const alarmsLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const statusFilter = ref<DoorAlarmStatusCode | ''>('OPEN')
const keyword = ref('')

const statusOptions = Object.entries(DOOR_ALARM_STATUS_NAME_MAP).map(([value, label]) => ({
  value: value as DoorAlarmStatusCode,
  label
}))

const fetchAlarms = async () => {
  alarmsLoading.value = true
  try {
    const res = await doorAlarmApi.getAlarms({
      page: currentPage.value,
      size: pageSize.value,
      status: statusFilter.value,
      keyword: keyword.value.trim() || undefined
    })
    alarms.value = res.data.data
    total.value = res.data.total
  } catch (error) {
    console.error('获取柜门未关告警台账失败', error)
  } finally {
    alarmsLoading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchAlarms()
}

const handleReset = () => {
  statusFilter.value = ''
  keyword.value = ''
  currentPage.value = 1
  fetchAlarms()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchAlarms()
}

// ---------------- 按柜门状态一览 ----------------

const overview = ref<LockerDoorAlarmOverview[]>([])
const overviewLoading = ref(false)
/** 一览是否已成功加载过：未加载前页头不显示具体未处理条数，避免把「加载中/加载失败」误判为全部关严 */
const overviewLoaded = ref(false)

const fetchOverview = async () => {
  overviewLoading.value = true
  try {
    const res = await doorAlarmApi.getLockerOverview()
    overview.value = res.data
    overviewLoaded.value = true
  } catch (error) {
    console.error('获取按柜门状态一览失败', error)
    ElMessage.error('获取按柜门状态一览失败，未处理条数暂未更新，请重新切换或刷新')
  } finally {
    overviewLoading.value = false
  }
}

/** 当前未处理总条数：与一览中各柜未处理条数同源，刷新后保持一致 */
const openAlarmTotal = computed(() =>
  overview.value.reduce((sum, item) => sum + (item.openAlarmCount || 0), 0)
)

/** 当前已超约定分钟的条数：超过约定分钟仍未关严的柜必须一眼可见 */
const overtimeTotal = computed(() =>
  overview.value.reduce((sum, item) => sum + (item.overtime ? 1 : 0), 0)
)

const handleTabChange = () => {
  if (activeTab.value === 'overview') {
    fetchOverview()
  } else {
    fetchAlarms()
  }
}

// ---------------- 确认已关闭 ----------------

const closeDialogVisible = ref(false)
const closing = ref(false)
const currentAlarm = ref<DoorAlarmRecord | null>(null)
const closeForm = ref<DoorAlarmCloseRequest>({ closeOperator: '', closeNote: '' })

const openCloseDialog = (alarm: DoorAlarmRecord) => {
  currentAlarm.value = alarm
  closeForm.value = { closeOperator: '', closeNote: '' }
  closeDialogVisible.value = true
}

/** 关闭内容只保存在本窗口内，未提交前关闭不会写入任何数据；已填写内容时关闭需二次确认 */
const closeFormDirty = computed(
  () => !!((closeForm.value.closeOperator || '').trim() || (closeForm.value.closeNote || '').trim())
)

const handleCloseDialogClose = (done: () => void) => {
  if (!closeFormDirty.value) {
    done()
    return
  }
  ElMessageBox.confirm('关闭后本次填写的内容将丢弃，告警仍为未处理，确认关闭？', '提示', {
    type: 'warning',
    confirmButtonText: '丢弃并关闭',
    cancelButtonText: '继续填写'
  })
    .then(() => done())
    .catch(() => {})
}

const submitClose = async () => {
  if (!currentAlarm.value) return
  closing.value = true
  try {
    await doorAlarmApi.closeAlarm(currentAlarm.value.id, {
      closeOperator: closeForm.value.closeOperator?.trim() || undefined,
      closeNote: closeForm.value.closeNote?.trim() || undefined
    })
    ElMessage.success('已确认柜门关严，柜体未关标记恢复')
    closeDialogVisible.value = false
    await fetchAlarms()
    // 无论当前在哪个页签都同步刷新按柜一览，保证未处理条数与柜体页未关标记实时一致
    await fetchOverview()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '确认关闭失败')
  } finally {
    closing.value = false
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
  fetchAlarms()
  // 进入页面即预取按柜一览，保证第一次切到一览时未处理条数已与台账、柜体页标记一致
  fetchOverview()
})
</script>

<template>
  <div class="door-alarm-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="告警台账" name="alarms" />
      <el-tab-pane label="按柜门状态" name="overview" />
    </el-tabs>

    <!-- ================= 告警台账列表 ================= -->
    <template v-if="activeTab === 'alarms'">
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
            placeholder="告警编号 / 上报人 / 柜体编号"
            style="width: 260px"
            @keyup.enter="handleSearch"
          />
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <el-alert type="info" :closable="false" class="header-tip">
          在「快递柜详情」页登记柜门未关
        </el-alert>
      </div>

      <el-table :data="alarms" border v-loading="alarmsLoading">
        <el-table-column prop="alarmNo" label="告警编号" width="190" />
        <el-table-column label="柜体" width="150">
          <template #default="{ row }">
            <el-tag type="info">{{ row.lockerNo }}</el-tag>
            <span class="locker-location">{{ row.buildingName }} {{ row.unitName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发现未关时间" width="160">
          <template #default="{ row }">{{ formatTime(row.doorOpenTime) }}</template>
        </el-table-column>
        <el-table-column label="约定分钟" width="90" align="center">
          <template #default="{ row }">{{ row.thresholdMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="已持续" width="120">
          <template #default="{ row }">{{ formatElapsed(row.elapsedMinutes) }}</template>
        </el-table-column>
        <el-table-column prop="reporter" label="上报人" width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.open" type="danger">未处理</el-tag>
            <el-tag v-else type="success">已关闭</el-tag>
            <el-tag v-if="row.overtime" type="danger" effect="dark" style="margin-left: 4px">
              已超时
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认关闭" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip
              v-if="row.closeTime"
              :content="`确认关闭时间：${formatTime(row.closeTime)}`"
              placement="top"
            >
              <span>
                {{ row.closeOperator || '-' }}
                <template v-if="row.closeNote">（{{ row.closeNote }}）</template>
              </span>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.open"
              size="small"
              type="warning"
              @click="openCloseDialog(row)"
            >确认已关闭</el-button>
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
      <div v-else-if="!alarmsLoading" class="empty-tip">暂无柜门未关告警记录</div>
    </template>

    <!-- ================= 按柜门状态一览 ================= -->
    <template v-else>
      <el-alert type="info" :closable="false" class="overview-tip">
        全部柜体（含停用柜）列出：存在未处理告警的柜体标记「柜门未关」，其余为「正常」，
        <template v-if="overviewLoaded">
          当前共有 <b>{{ openAlarmTotal }}</b> 条未处理告警
          <template v-if="overtimeTotal > 0">
            ，其中 <b>{{ overtimeTotal }}</b> 台已超约定分钟
          </template>
          ；
        </template>
        <template v-else>未处理条数统计中…；</template>
        登记柜门未关请进入对应柜体详情页。
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
        <el-table-column label="柜门标记" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.doorAjar" type="danger">柜门未关</el-tag>
            <el-tag v-else type="success">正常</el-tag>
            <el-tag v-if="row.overtime" type="danger" effect="dark" style="margin-left: 4px">
              已超时
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="openAlarmCount" label="未处理条数" width="100" align="center" />
        <el-table-column prop="totalAlarmCount" label="历史条数" width="90" align="center" />
        <el-table-column label="最近发现未关" width="160">
          <template #default="{ row }">{{ formatTime(row.lastDoorOpenTime) }}</template>
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

    <!-- ================= 确认已关闭弹窗 ================= -->
    <el-dialog
      title="确认柜门已关闭"
      v-model="closeDialogVisible"
      width="520px"
      :close-on-click-modal="false"
      :before-close="handleCloseDialogClose"
    >
      <template v-if="currentAlarm">
        <el-alert type="warning" :closable="false" class="dialog-tip">
          确认后该告警状态变为「已关闭」，柜体「柜门未关」标记恢复；台账中仍是同一条记录，按已关闭可查到。
        </el-alert>
        <el-descriptions :column="2" border class="dialog-tip">
          <el-descriptions-item label="告警编号">{{ currentAlarm.alarmNo }}</el-descriptions-item>
          <el-descriptions-item label="柜体">{{ currentAlarm.lockerNo }}</el-descriptions-item>
          <el-descriptions-item label="发现未关时间">{{ formatTime(currentAlarm.doorOpenTime) }}</el-descriptions-item>
          <el-descriptions-item label="约定分钟">{{ currentAlarm.thresholdMinutes }} 分钟</el-descriptions-item>
          <el-descriptions-item label="上报人">{{ currentAlarm.reporter }}</el-descriptions-item>
          <el-descriptions-item label="已持续">{{ formatElapsed(currentAlarm.elapsedMinutes) }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px">
          <el-form-item label="确认关闭人">
            <el-input v-model="closeForm.closeOperator" placeholder="选填，默认系统管理员" />
          </el-form-item>
          <el-form-item label="关闭说明">
            <el-input
              v-model="closeForm.closeNote"
              type="textarea"
              :rows="3"
              placeholder="选填，如：现场核实柜门已关严，格口无遗留件"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="handleCloseDialogClose(() => (closeDialogVisible = false))">取消</el-button>
        <el-button type="warning" :loading="closing" @click="submitClose">确认已关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.door-alarm-page {
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
