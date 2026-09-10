<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Box as Package, HomeFilled as Home, Refresh, ArrowUp } from '@element-plus/icons-vue'
import { lockerApi, buildingApi, STATUS_NAME_MAP } from '@/api/locker'
import type { LockerDTO, BuildingTreeDTO, LockerStatusCode } from '@/api/locker'

const activeCount = ref(0)

const lockerCount = ref(0)
const buildingCount = ref(0)
const recentLockers = ref<LockerDTO[]>([])
const buildingTree = ref<BuildingTreeDTO[]>([])
const buildingStats = ref<{ name: string; count: number }[]>([])

onMounted(async () => {
  await fetchData()
})

const fetchData = async () => {
  try {
    const [countRes, treeRes, recentRes, activeRes] = await Promise.all([
      lockerApi.countLockers(),
      buildingApi.getBuildingTree(),
      lockerApi.getLockers(1, 5),
      lockerApi.getLockers(1, 1, ['ACTIVE'])
    ])
    lockerCount.value = countRes.data
    buildingTree.value = treeRes.data
    buildingCount.value = treeRes.data.length
    recentLockers.value = recentRes.data.data
    activeCount.value = Number(activeRes.data.total || 0)

    buildingStats.value = await Promise.all(
      buildingTree.value.map(async (b) => ({
        name: b.name,
        count: (await lockerApi.countLockersByBuilding(b.id)).data
      }))
    )
  } catch (error) {
    console.error('获取数据失败', error)
  }
}
</script>

<template>
  <div class="dashboard">
    <el-row :gutter="24">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon blue">
              <Package />
            </div>
            <div class="stat-info">
              <el-statistic title="快递柜总数" :value="lockerCount" suffix="台" />
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon green">
              <Home />
            </div>
            <div class="stat-info">
              <el-statistic title="楼栋数量" :value="buildingCount" suffix="栋" />
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon orange">
              <Refresh />
            </div>
            <div class="stat-info">
              <el-statistic title="近期变动" :value="recentLockers.length" suffix="条" />
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon purple">
              <ArrowUp />
            </div>
            <div class="stat-info">
              <el-statistic title="正常运营柜体" :value="activeCount" suffix="台" />
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="24" style="margin-top: 24px;">
      <el-col :span="14">
        <el-card title="楼栋快递柜分布">
          <div class="distribution-list">
            <div
              v-for="stat in buildingStats"
              :key="stat.name"
              class="distribution-item"
            >
              <div class="distribution-label">
                <el-avatar :size="28" class="distribution-avatar">{{ stat.name.charAt(0) }}</el-avatar>
                <span>{{ stat.name }}</span>
              </div>
              <div class="distribution-bar">
                <div
                  class="distribution-fill"
                  :style="{ width: `${(stat.count / Math.max(...buildingStats.map(s => s.count), 1)) * 100}%` }"
                ></div>
              </div>
              <span class="distribution-count">{{ stat.count }}台</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card title="最近新增快递柜">
          <el-table :data="recentLockers" :show-header="false" size="small">
            <el-table-column prop="lockerNo" label="编号">
              <template #default="{ row }">
                <el-tag type="primary">{{ row.lockerNo }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="buildingName" label="楼栋">
              <template #default="{ row }">
                {{ row.buildingName }} {{ row.unitName }}
              </template>
            </el-table-column>
            <el-table-column prop="compartmentCount" label="格口">
              <template #default="{ row }">
                {{ row.compartmentCount }}格
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag
                  :type="
                    row.status === 'ACTIVE'
                      ? 'success'
                      : row.status === 'TEMPORARILY_DISABLED'
                        ? 'warning'
                        : 'info'
                  "
                  size="small"
                >
                  {{ STATUS_NAME_MAP[row.status as LockerStatusCode] || '-' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard {
  padding: 16px 0;
}

.stat-card {
  height: 120px;
}

.stat-content {
  display: flex;
  align-items: center;
  height: 100%;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #fff;
  margin-right: 16px;
}

.stat-icon.blue {
  background: linear-gradient(135deg, #1e3a5f 0%, #2d5a87 100%);
}

.stat-icon.green {
  background: linear-gradient(135deg, #00bcd4 0%, #0097a7 100%);
}

.stat-icon.orange {
  background: linear-gradient(135deg, #ff9800 0%, #f57c00 100%);
}

.stat-icon.purple {
  background: linear-gradient(135deg, #9c27b0 0%, #7b1fa2 100%);
}

.distribution-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.distribution-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.distribution-label {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100px;
  flex-shrink: 0;
}

.distribution-bar {
  flex: 1;
  height: 8px;
  background: #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
}

.distribution-fill {
  height: 100%;
  background: linear-gradient(90deg, #1e3a5f 0%, #00bcd4 100%);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.distribution-count {
  width: 60px;
  text-align: right;
  font-weight: 600;
  color: #1e3a5f;
}

.distribution-avatar {
  background: linear-gradient(135deg, #1e3a5f 0%, #00bcd4 100%);
}
</style>
