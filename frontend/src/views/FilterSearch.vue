<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { lockerApi, buildingApi, archiveApi } from '@/api/locker'
import { ElMessage } from 'element-plus'
import type { LockerDTO, BuildingTreeDTO, UnitDTO, FilterRequest } from '@/api/locker'

const buildingTree = ref<BuildingTreeDTO[]>([])
const specTypes = ref<Record<string, string>>({})
const units = ref<UnitDTO[]>([])
const tableData = ref<LockerDTO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const showArchiveDialog = ref(false)
const archiveName = ref('')

const filterForm = ref<FilterRequest>({
  buildingIds: [],
  unitIds: [],
  specTypes: [],
  startDate: '',
  endDate: ''
})

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

const handleSearch = async () => {
  try {
    const res = await lockerApi.filterLockers({
      ...filterForm.value,
      page: currentPage.value,
      size: pageSize.value
    })
    tableData.value = res.data.data
    total.value = res.data.total
  } catch (error) {
    console.error('筛选失败', error)
  }
}

const handleReset = () => {
  filterForm.value = {
    buildingIds: [],
    unitIds: [],
    specTypes: [],
    startDate: '',
    endDate: ''
  }
  units.value = []
  tableData.value = []
  total.value = 0
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  handleSearch()
}

const handleArchive = async () => {
  if (!archiveName.value) {
    ElMessage.warning('请输入归档名称')
    return
  }
  if (tableData.value.length === 0) {
    ElMessage.warning('当前筛选结果为空')
    return
  }
  try {
    await archiveApi.createArchive({
      name: archiveName.value,
      filterParams: JSON.stringify(filterForm.value),
      lockerCount: tableData.value.length,
      createdBy: 'admin',
      lockerIds: tableData.value.map(l => l.id)
    })
    ElMessage.success('归档成功')
    showArchiveDialog.value = false
    archiveName.value = ''
  } catch (error) {
    ElMessage.error('归档失败')
  }
}
</script>

<template>
  <div class="filter-search">
    <el-card title="多条件筛选">
      <el-form :model="filterForm" label-width="100px">
        <el-row :gutter="24">
          <el-col :span="8">
            <el-form-item label="楼栋">
              <el-select
                v-model="filterForm.buildingIds"
                multiple
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
          <el-col :span="12">
            <el-form-item label="录入时间段">
              <el-date-picker
                v-model="filterForm.startDate"
                type="date"
                placeholder="开始日期"
                style="width: 48%;"
              />
              <span style="margin: 0 8px;">至</span>
              <el-date-picker
                v-model="filterForm.endDate"
                type="date"
                placeholder="结束日期"
                style="width: 48%;"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <div style="margin-top: 20px; display: flex; gap: 12px;">
          <el-button type="primary" @click="handleSearch">执行筛选</el-button>
          <el-button @click="handleReset">重置条件</el-button>
          <el-button type="success" @click="showArchiveDialog = true">保存归档</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card title="筛选结果">
      <el-table :data="tableData" border v-if="tableData.length > 0">
        <el-table-column prop="lockerNo" label="柜体编号" width="120">
          <template #default="{ row }">
            <el-tag type="info">{{ row.lockerNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="specType" label="规格类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.specType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="compartmentCount" label="格口数量" width="100" />
        <el-table-column prop="buildingName" label="所属楼栋" width="120" />
        <el-table-column prop="unitName" label="所属单元" width="100" />
        <el-table-column prop="installLocation" label="安装位置" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">
              {{ row.status === 'ACTIVE' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
      </el-table>
      <div v-else class="empty-tip">暂无数据</div>

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
      <el-form :model="{ archiveName }" label-width="80px">
        <el-form-item label="归档名称">
          <el-input v-model="archiveName" placeholder="请输入归档名称" />
        </el-form-item>
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
