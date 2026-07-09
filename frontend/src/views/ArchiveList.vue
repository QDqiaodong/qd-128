<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Calendar, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { archiveApi } from '@/api/locker'
import type { Archive, LockerDTO } from '@/api/locker'

const archives = ref<Archive[]>([])
const showDetailDialog = ref(false)
const selectedArchive = ref<Archive | null>(null)
const archiveLockers = ref<LockerDTO[]>([])

onMounted(async () => {
  await fetchArchives()
})

const fetchArchives = async () => {
  try {
    const res = await archiveApi.getArchives()
    archives.value = res.data
  } catch (error) {
    console.error('获取归档列表失败', error)
  }
}

const handleViewDetail = async (archive: Archive) => {
  selectedArchive.value = archive
  try {
    const res = await archiveApi.getArchiveLockers(archive.id)
    archiveLockers.value = res.data
    showDetailDialog.value = true
  } catch (error) {
    console.error('获取归档详情失败', error)
  }
}

const handleDelete = async (id: number) => {
  try {
    await archiveApi.deleteArchive(id)
    ElMessage.success('删除成功')
    await fetchArchives()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

const formatFilterParams = (params: string) => {
  try {
    const json = JSON.parse(params)
    const parts: string[] = []
    if (json.buildingId) parts.push('楼栋')
    if (json.unitId) parts.push('单元')
    if (json.specType) parts.push('规格')
    if (json.startDate || json.endDate) parts.push('时间范围')
    return parts.length > 0 ? parts.join(' + ') : '全部'
  } catch {
    return '全部'
  }
}
</script>

<template>
  <div class="archive-list">
    <el-card title="归档管理">
      <el-table :data="archives" border>
        <el-table-column prop="id" label="归档ID" width="80" />
        <el-table-column prop="name" label="归档名称" />
        <el-table-column prop="filterParams" label="筛选条件" width="150">
          <template #default="{ row }">
            <el-tag type="info">{{ formatFilterParams(row.filterParams) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lockerCount" label="快递柜数量" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">
            <div class="date-item">
              <Calendar />
              <span>{{ row.createdAt }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建人" width="100">
          <template #default="{ row }">
            <div class="user-item">
              <User />
              <span>{{ row.createdBy || '系统' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="handleViewDetail(row)">详情</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="archives.length === 0" class="empty-tip">暂无归档记录</div>
    </el-card>

    <el-dialog title="归档详情" v-model="showDetailDialog" width="800px">
      <el-card v-if="selectedArchive">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="归档名称">{{ selectedArchive.name }}</el-descriptions-item>
          <el-descriptions-item label="快递柜数量">{{ selectedArchive.lockerCount }}台</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ selectedArchive.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ selectedArchive.createdBy || '系统' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card title="快递柜列表" style="margin-top: 20px;">
        <el-table :data="archiveLockers" border size="small">
          <el-table-column prop="lockerNo" label="柜体编号" width="100">
            <template #default="{ row }">
              <el-tag type="info">{{ row.lockerNo }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="specType" label="规格" width="100" />
          <el-table-column prop="compartmentCount" label="格口" width="80" />
          <el-table-column prop="buildingName" label="楼栋" width="100" />
          <el-table-column prop="unitName" label="单元" width="80" />
          <el-table-column prop="installLocation" label="位置" />
        </el-table>
        <div v-if="archiveLockers.length === 0" class="empty-tip">暂无数据</div>
      </el-card>
    </el-dialog>
  </div>
</template>

<style scoped>
.archive-list {
  padding: 16px 0;
}

.date-item, .user-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px;
}
</style>
