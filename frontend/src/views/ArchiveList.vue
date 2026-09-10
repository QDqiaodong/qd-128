<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Calendar, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { archiveApi, STATUS_NAME_MAP } from '@/api/locker'
import type { Archive, LockerDTO, LockerStatusCode } from '@/api/locker'

const archives = ref<Archive[]>([])
const showDetailDialog = ref(false)
const selectedArchive = ref<Archive | null>(null)
const archiveLockers = ref<LockerDTO[]>([])

const statusTagType = (status?: string) => {
  if (status === 'ACTIVE') return 'success'
  if (status === 'TEMPORARILY_DISABLED') return 'warning'
  if (status === 'PERMANENTLY_DISABLED') return 'info'
  return 'info'
}

const statusLabel = (status?: string) =>
  STATUS_NAME_MAP[status as LockerStatusCode] || '-'

onMounted(async () => {
  await fetchArchives()
})

const fetchArchives = async () => {
  try {
    const res = await archiveApi.getArchives()
    // 后端返回 Spring 分页结构
    archives.value = (res.data as any).content || res.data || []
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

const formatFilterParams = (params?: string) => {
  if (!params) return '全部'
  try {
    const json = JSON.parse(params)
    const parts: string[] = []
    if (json.buildingIds?.length) parts.push('楼栋')
    if (json.unitIds?.length) parts.push('单元')
    if (json.specTypes?.length) parts.push('规格')
    if (json.statuses?.length) parts.push('状态')
    else if (!json.statuses || json.statuses.length === 0) parts.push('仅正常')
    if (json.startDate || json.endDate) parts.push('安装时间')
    return parts.length > 0 ? parts.join(' + ') : '全部'
  } catch {
    return '全部'
  }
}
</script>

<template>
  <div class="archive-list">
    <el-card>
      <template #header>归档管理</template>
      <el-table :data="archives" border>
        <el-table-column prop="id" label="归档ID" width="80" />
        <el-table-column prop="archiveName" label="归档名称" min-width="180" />
        <el-table-column label="筛选条件" width="180">
          <template #default="{ row }">
            <el-tag type="info">{{ formatFilterParams(row.filterConditions) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resultCount" label="快递柜数量" width="110" />
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            <div class="date-item">
              <Calendar />
              <span>{{ row.createTime }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="创建人" width="120">
          <template #default="{ row }">
            <div class="user-item">
              <User />
              <span>{{ row.operator || '系统管理员' }}</span>
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

    <el-dialog title="归档详情" v-model="showDetailDialog" width="860px">
      <el-card v-if="selectedArchive" shadow="never">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="归档名称">{{ selectedArchive.archiveName }}</el-descriptions-item>
          <el-descriptions-item label="快递柜数量">{{ selectedArchive.resultCount }}台</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ selectedArchive.createTime }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ selectedArchive.operator || '系统管理员' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" style="margin-top: 20px;">
        <template #header>
          <div class="snapshot-header">
            <span>快递柜列表（状态为归档时快照）</span>
            <el-tag type="warning" size="small">快照状态可能与柜体当前状态不同</el-tag>
          </div>
        </template>
        <el-table :data="archiveLockers" border size="small">
          <el-table-column prop="lockerNo" label="柜体编号" width="110">
            <template #default="{ row }">
              <el-tag type="info">{{ row.lockerNo }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="规格" width="100">
            <template #default="{ row }">{{ row.specTypeName || row.specType }}</template>
          </el-table-column>
          <el-table-column prop="compartmentCount" label="格口" width="70" />
          <el-table-column prop="buildingName" label="楼栋" width="90" />
          <el-table-column prop="unitName" label="单元" width="80" />
          <el-table-column prop="floor" label="楼层" width="70">
            <template #default="{ row }">{{ row.floor || '-' }}</template>
          </el-table-column>
          <el-table-column label="归档时状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.snapshotStatus)">
                {{ statusLabel(row.snapshotStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="当前状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.currentStatus || row.status)" effect="plain">
                {{ statusLabel(row.currentStatus || row.status) }}
              </el-tag>
            </template>
          </el-table-column>
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

.snapshot-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px;
}
</style>
