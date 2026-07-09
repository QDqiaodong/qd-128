<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { lockerApi } from '@/api/locker'
import { ElMessage } from 'element-plus'
import type { LockerDTO, PageResponse } from '@/api/locker'

const router = useRouter()
const tableData = ref<LockerDTO[]>([])
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

onMounted(async () => {
  await fetchLockers()
})

const fetchLockers = async () => {
  try {
    const res = await lockerApi.getLockers(currentPage.value, pageSize.value, searchKeyword.value)
    const data = res.data as PageResponse<LockerDTO>
    tableData.value = data.data
    total.value = data.total
  } catch (error) {
    console.error('获取快递柜列表失败', error)
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchLockers()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchLockers()
}

const handleEdit = (id: number) => {
  router.push(`/lockers/edit/${id}`)
}

const handleDelete = async (id: number) => {
  try {
    await lockerApi.deleteLocker(id)
    ElMessage.success('删除成功')
    fetchLockers()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

const handleView = (id: number) => {
  router.push(`/lockers/${id}`)
}
</script>

<template>
  <div class="locker-list">
    <div class="list-header">
      <div class="search-box">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索柜体编号"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>
      <el-button type="success" @click="router.push('/lockers/create')">新增快递柜</el-button>
    </div>

    <el-table :data="tableData" border>
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
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row.id)">编辑</el-button>
          <el-button size="small" @click="handleView(row.id)">详情</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
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
    <div v-else class="empty-tip">暂无数据</div>
  </div>
</template>

<style scoped>
.locker-list {
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

.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px;
}
</style>
