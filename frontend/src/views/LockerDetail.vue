<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { lockerApi, buildingApi } from '@/api/locker'
import { ElMessage } from 'element-plus'
import type { LockerDTO, AdjustmentRecord, BuildingTreeDTO, UnitDTO, AdjustRequest } from '@/api/locker'

const router = useRouter()
const route = useRoute()
const lockerId = ref(Number(route.params.id))
const locker = ref<LockerDTO | null>(null)
const adjustmentRecords = ref<AdjustmentRecord[]>([])
const buildingTree = ref<BuildingTreeDTO[]>([])
const units = ref<UnitDTO[]>([])
const showAdjustDialog = ref(false)
const adjustForm = ref<AdjustRequest>({
  newBuildingId: 0,
  newUnitId: 0,
  reason: ''
})

onMounted(async () => {
  await fetchData()
})

const fetchData = async () => {
  try {
    const [lockerRes, recordsRes, treeRes] = await Promise.all([
      lockerApi.getLockerById(lockerId.value),
      lockerApi.getAdjustmentRecords(lockerId.value),
      buildingApi.getBuildingTree()
    ])
    locker.value = lockerRes.data
    adjustmentRecords.value = recordsRes.data
    buildingTree.value = treeRes.data
  } catch (error) {
    console.error('获取数据失败', error)
  }
}

const handleBuildingChange = async (buildingId: number) => {
  try {
    const res = await buildingApi.getUnitsByBuilding(buildingId)
    units.value = res.data
    adjustForm.value.newUnitId = 0
  } catch (error) {
    console.error('获取单元失败', error)
  }
}

const handleAdjust = async () => {
  if (!adjustForm.value.newBuildingId || !adjustForm.value.newUnitId) {
    ElMessage.warning('请选择新的楼栋和单元')
    return
  }
  try {
    await lockerApi.adjustLocker(lockerId.value, adjustForm.value)
    ElMessage.success('调整成功')
    showAdjustDialog.value = false
    await fetchData()
  } catch (error) {
    ElMessage.error('调整失败')
  }
}

const handleBack = () => {
  router.push('/lockers')
}
</script>

<template>
  <div class="locker-detail">
    <div class="detail-header">
      <el-button @click="handleBack">返回列表</el-button>
      <span class="detail-title">快递柜详情</span>
    </div>

    <el-card v-if="locker">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="柜体编号">
          <el-tag type="primary">{{ locker.lockerNo }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="规格类型">
          <el-tag>{{ locker.specType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="格口数量">
          {{ locker.compartmentCount }}格
        </el-descriptions-item>
        <el-descriptions-item label="所属楼栋">
          {{ locker.buildingName }}
        </el-descriptions-item>
        <el-descriptions-item label="所属单元">
          {{ locker.unitName }}
        </el-descriptions-item>
        <el-descriptions-item label="安装位置">
          {{ locker.installLocation || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="locker.status === 'ACTIVE' ? 'success' : 'danger'">
            {{ locker.status === 'ACTIVE' ? '正常' : '停用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注">
          {{ locker.remark || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ locker.createdAt }}
        </el-descriptions-item>
      </el-descriptions>

      <div style="margin-top: 20px;">
        <el-button type="primary" @click="showAdjustDialog = true">调整归属</el-button>
      </div>
    </el-card>

    <el-card title="归属调整历史" style="margin-top: 20px;">
      <el-table :data="adjustmentRecords" border v-if="adjustmentRecords.length > 0">
        <el-table-column prop="id" label="记录ID" width="80" />
        <el-table-column label="调整前">
          <template #default="{ row }">
            {{ row.oldBuildingName }} {{ row.oldUnitName }}
          </template>
        </el-table-column>
        <el-table-column label="调整后">
          <template #default="{ row }">
            {{ row.newBuildingName }} {{ row.newUnitName }}
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="调整原因" />
        <el-table-column prop="createdAt" label="调整时间" />
      </el-table>
      <div v-else class="empty-tip">暂无调整记录</div>
    </el-card>

    <el-dialog title="调整归属" v-model="showAdjustDialog" width="500px">
      <el-form :model="adjustForm" label-width="100px">
        <el-form-item label="新楼栋">
          <el-select
            v-model="adjustForm.newBuildingId"
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
        <el-form-item label="新单元">
          <el-select
            v-model="adjustForm.newUnitId"
            placeholder="请选择单元"
            :disabled="!adjustForm.newBuildingId"
          >
            <el-option
              v-for="unit in units"
              :key="unit.id"
              :label="unit.name"
              :value="unit.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input v-model="adjustForm.reason" type="textarea" placeholder="请输入调整原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdjustDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAdjust">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.locker-detail {
  padding: 16px 0;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.detail-title {
  font-size: 20px;
  font-weight: 600;
  color: #333;
}

.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px;
}
</style>
