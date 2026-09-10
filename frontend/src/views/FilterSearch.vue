<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { lockerApi, buildingApi, archiveApi, STATUS_NAME_MAP } from '@/api/locker'
import { ElMessage } from 'element-plus'
import type {
  LockerDTO,
  BuildingTreeDTO,
  UnitDTO,
  FilterRequest,
  LockerStatusCode
} from '@/api/locker'

const router = useRouter()
const buildingTree = ref<BuildingTreeDTO[]>([])
const specTypes = ref<Record<string, string>>({})
const units = ref<UnitDTO[]>([])
const tableData = ref<LockerDTO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const showArchiveDialog = ref(false)
const archiveName = ref('')
const searched = ref(false)

const filterForm = ref<FilterRequest>({
  buildingIds: [],
  unitIds: [],
  specTypes: [],
  statuses: [],
  startDate: '',
  endDate: ''
})

const statusOptions = Object.entries(STATUS_NAME_MAP).map(([value, label]) => ({
  value: value as LockerStatusCode,
  label
}))

const statusTagType = (status?: string) => {
  if (status === 'ACTIVE') return 'success'
  if (status === 'TEMPORARILY_DISABLED') return 'warning'
  if (status === 'PERMANENTLY_DISABLED') return 'info'
  return 'info'
}

const statusLabel = (status?: string) =>
  STATUS_NAME_MAP[status as LockerStatusCode] || '-'

onMounted(async () => {
  await fetchInitData()
})

const fetchInitData = async () => {
  try {
    const [treeRes, specRes] = await Promise.all([
      buildingApi.getBuildingTree(),
      lockerApi.getSpecTypes()
    ])
    buildingTree.value = treeRes.data
    specTypes.value = specRes.data
  } catch (error) {
    console.error('初始化数据失败', error)
  }
}

const handleBuildingChange = async (buildingIds: number[]) => {
  units.value = []
  filterForm.value.unitIds = []
  if (buildingIds.length > 0) {
    try {
      const allUnits: UnitDTO[] = []
      for (const id of buildingIds) {
        const res = await buildingApi.getUnitsByBuilding(id)
        allUnits.push(...res.data)
      }
      units.value = allUnits
    } catch (error) {
      console.error('获取单元失败', error)
    }
  }
}

/**
 * 组装筛选条件：未显式勾选状态时，默认只筛选正常柜体（停用柜体不出现）。
 */
const buildRequest = (): FilterRequest => {
  const statuses = filterForm.value.statuses && filterForm.value.statuses.length > 0
    ? filterForm.value.statuses
    : (['ACTIVE'] as LockerStatusCode[])
  return {
    ...filterForm.value,
    statuses,
    page: currentPage.value,
    size: pageSize.value
  }
}

const handleSearch = async () => {
  searched.value = true
  currentPage.value = 1
  await doSearch()
}

const doSearch = async () => {
  try {
    const res = await lockerApi.filterLockers(buildRequest())
    tableData.value = res.data.data
    total.value = res.data.total
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '筛选失败')
  }
}

const handleReset = () => {
  filterForm.value = {
    buildingIds: [],
    unitIds: [],
    specTypes: [],
    statuses: [],
    startDate: '',
    endDate: ''
  }
  units.value = []
  tableData.value = []
  total.value = 0
  searched.value = false
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  doSearch()
}

const handleView = (id: number) => {
  router.push(`/lockers/${id}`)
}

const handleArchive = async () => {
  if (!archiveName.value.trim()) {
    ElMessage.warning('请输入归档名称')
    return
  }
  if (tableData.value.length === 0) {
    ElMessage.warning('当前筛选结果为空')
    return
  }
  try {
    // 归档时后端会对每个柜体保存当时状态快照
    await archiveApi.createArchive({
      archiveName: archiveName.value.trim(),
      filterConditions: JSON.stringify(buildRequest()),
      resultCount: tableData.value.length,
      operator: '系统管理员',
      lockerIds: tableData.value.map((l) => l.id)
    })
    ElMessage.success('归档成功')
    showArchiveDialog.value = false
    archiveName.value = ''
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '归档失败')
  }
}
</script>

<template>
  <div class="filter-search">
    <el-card>
      <template #header>多条件筛选</template>
      <el-form :model="filterForm" label-width="100px">
        <el-row :gutter="24">
          <el-col :span="8">
            <el-form-item label="楼栋">
              <el-select
                v-model="filterForm.buildingIds"
                multiple
                collapse-tags
                collapse-tags-tooltip
                placeholder="请选择楼栋"
                @change="handleBuildingChange"
              >
                <el-option
                  v-for="building in buildingTree"
                  :key="building.id"
                  :label="building.name"
                  :value="building.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单元">
              <el-select
                v-model="filterForm.unitIds"
                multiple
                collapse-tags
                collapse-tags-tooltip
                placeholder="请选择单元"
                :disabled="!filterForm.buildingIds || filterForm.buildingIds.length === 0"
              >
                <el-option
                  v-for="unit in units"
                  :key="unit.id"
                  :label="unit.name"
                  :value="unit.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="柜体规格">
              <el-select
                v-model="filterForm.specTypes"
                multiple
                collapse-tags
                collapse-tags-tooltip
                placeholder="请选择规格"
              >
                <el-option
                  v-for="(label, value) in specTypes"
                  :key="value"
                  :label="label"
                  :value="value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="24">
          <el-col :span="8">
            <el-form-item label="柜体状态">
              <el-select
                v-model="filterForm.statuses"
                multiple
                collapse-tags
                collapse-tags-tooltip
                placeholder="默认仅正常柜体"
              >
                <el-option
                  v-for="opt in statusOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="安装时间段">
              <el-date-picker
                v-model="filterForm.startDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="开始日期"
                style="width: 40%;"
              />
              <span style="margin: 0 8px;">至</span>
              <el-date-picker
                v-model="filterForm.endDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="结束日期"
                style="width: 40%;"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="不勾选柜体状态时，默认仅筛选「正常」柜体；如需在结果中包含停用柜体，请显式勾选对应状态。"
          style="margin-bottom: 16px;"
        />
        <div style="display: flex; gap: 12px;">
          <el-button type="primary" @click="handleSearch">执行筛选</el-button>
          <el-button @click="handleReset">重置条件</el-button>
          <el-button type="success" @click="showArchiveDialog = true">保存归档</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card style="margin-top: 20px;">
      <template #header>筛选结果</template>
      <el-table :data="tableData" border v-if="tableData.length > 0">
        <el-table-column prop="lockerNo" label="柜体编号" width="120">
          <template #default="{ row }">
            <el-tag type="info">{{ row.lockerNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规格类型" width="120">
          <template #default="{ row }">{{ row.specTypeName || row.specType }}</template>
        </el-table-column>
        <el-table-column prop="compartmentCount" label="格口数量" width="90" />
        <el-table-column prop="buildingName" label="所属楼栋" width="100" />
        <el-table-column prop="unitName" label="所属单元" width="90" />
        <el-table-column prop="floor" label="楼层" width="80">
          <template #default="{ row }">{{ row.floor || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="installationDate" label="安装日期" width="120">
          <template #default="{ row }">{{ row.installationDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleView(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-tip">{{ searched ? '暂无符合条件的数据' : '请设置条件后执行筛选' }}</div>

      <el-pagination
        v-if="total > 0"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        @current-change="handlePageChange"
        layout="total, prev, pager, next, jumper"
      />
    </el-card>

    <el-dialog title="保存归档" v-model="showArchiveDialog" width="400px">
      <el-form label-width="80px">
        <el-form-item label="归档名称">
          <el-input v-model="archiveName" placeholder="请输入归档名称" />
        </el-form-item>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="归档将冻结当前筛选结果及各柜体当时的状态，之后柜体状态变化不影响该快照。"
        />
      </el-form>
      <template #footer>
        <el-button @click="showArchiveDialog = false">取消</el-button>
        <el-button type="primary" @click="handleArchive">确认保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-search {
  padding: 16px 0;
}

.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px;
}
</style>
