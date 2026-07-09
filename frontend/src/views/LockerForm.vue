<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { lockerApi, buildingApi } from '@/api/locker'
import { ElMessage } from 'element-plus'
import type { BuildingTreeDTO, UnitDTO, LockerCreateRequest, LockerUpdateRequest } from '@/api/locker'

const router = useRouter()
const route = useRoute()
const isEdit = ref(false)
const lockerId = ref<number | null>(null)

const buildingTree = ref<BuildingTreeDTO[]>([])
const specTypes = ref<Record<string, string>>({})
const units = ref<UnitDTO[]>([])

const form = ref({
  lockerNo: '',
  compartmentCount: 48,
  specType: 'STANDARD',
  buildingId: null as number | null,
  unitId: null as number | null,
  floor: '',
  installationDate: '',
  remark: ''
})

onMounted(async () => {
  await fetchData()
  if (route.params.id) {
    isEdit.value = true
    lockerId.value = Number(route.params.id)
    await fetchLockerData()
  }
})

const fetchData = async () => {
  try {
    const [treeRes, specRes] = await Promise.all([
      buildingApi.getBuildingTree(),
      lockerApi.getSpecTypes()
    ])
    buildingTree.value = treeRes.data
    specTypes.value = specRes.data
  } catch (error) {
    console.error('获取数据失败', error)
  }
}

const fetchLockerData = async () => {
  if (!lockerId.value) return
  try {
    const res = await lockerApi.getLockerById(lockerId.value)
    const data = res.data
    form.value = {
      lockerNo: data.lockerNo,
      compartmentCount: data.compartmentCount,
      specType: data.specType,
      buildingId: data.buildingId,
      unitId: data.unitId,
      floor: data.floor || '',
      installationDate: data.installationDate || '',
      remark: data.remark || ''
    }
    await loadUnits(data.buildingId)
  } catch (error) {
    console.error('获取快递柜数据失败', error)
  }
}

const loadUnits = async (buildingId: number) => {
  try {
    const res = await buildingApi.getUnitsByBuilding(buildingId)
    units.value = res.data
  } catch (error) {
    console.error('获取单元列表失败', error)
  }
}

const handleBuildingChange = async (value: number) => {
  form.value.unitId = null
  await loadUnits(value)
}

const handleSubmit = async () => {
  if (!form.value.lockerNo || !form.value.buildingId || !form.value.unitId) {
    ElMessage.warning('请填写必填字段')
    return
  }

  try {
    if (isEdit.value && lockerId.value) {
      const request: LockerUpdateRequest = {
        lockerNo: form.value.lockerNo,
        compartmentCount: form.value.compartmentCount,
        specType: form.value.specType,
        buildingId: form.value.buildingId,
        unitId: form.value.unitId,
        floor: form.value.floor || undefined,
        installationDate: form.value.installationDate || undefined,
        remark: form.value.remark || undefined
      }
      await lockerApi.updateLocker(lockerId.value, request)
      ElMessage.success('更新成功')
    } else {
      const request: LockerCreateRequest = {
        lockerNo: form.value.lockerNo,
        compartmentCount: form.value.compartmentCount,
        specType: form.value.specType,
        buildingId: form.value.buildingId,
        unitId: form.value.unitId,
        floor: form.value.floor || undefined,
        installationDate: form.value.installationDate || undefined,
        remark: form.value.remark || undefined
      }
      await lockerApi.createLocker(request)
      ElMessage.success('创建成功')
    }
    router.push('/lockers')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '操作失败')
  }
}

const handleCancel = () => {
  router.push('/lockers')
}
</script>

<template>
  <div class="locker-form">
    <el-form :model="form" label-width="120px">
      <el-form-item label="柜体编号" required>
        <el-input v-model="form.lockerNo" placeholder="请输入柜体编号" />
      </el-form-item>

      <el-form-item label="柜体规格" required>
        <el-select v-model="form.specType" placeholder="请选择规格">
          <el-option v-for="(label, value) in specTypes" :key="value" :value="value">
            {{ label }}
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="格口数量" required>
        <el-input type="number" v-model="form.compartmentCount" placeholder="请输入格口数量" />
      </el-form-item>

      <el-form-item label="所属楼栋" required>
        <el-select v-model="form.buildingId" placeholder="请选择楼栋" @change="handleBuildingChange">
          <el-option v-for="building in buildingTree" :key="building.id" :value="building.id">
            {{ building.name }}
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="所属单元" required>
        <el-select v-model="form.unitId" placeholder="请选择单元" :disabled="!form.buildingId">
          <el-option v-for="unit in units" :key="unit.id" :value="unit.id">
            {{ unit.name }}
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="楼层">
        <el-input v-model="form.floor" placeholder="例如：1F" />
      </el-form-item>

      <el-form-item label="安装日期">
        <el-date-picker v-model="form.installationDate" type="date" placeholder="选择安装日期" />
      </el-form-item>

      <el-form-item label="备注">
        <el-input type="textarea" v-model="form.remark" :rows="3" placeholder="请输入备注信息" />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="handleSubmit">
          {{ isEdit ? '保存修改' : '创建快递柜' }}
        </el-button>
        <el-button @click="handleCancel">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.locker-form {
  background: #fff;
  border-radius: 8px;
  padding: 32px;
  max-width: 600px;
}
</style>
