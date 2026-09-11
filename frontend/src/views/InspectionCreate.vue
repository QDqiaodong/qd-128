<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { buildingApi } from '@/api/locker'
import {
  inspectionApi,
  CYCLE_NAME_MAP
} from '@/api/inspection'
import type {
  BuildingTreeDTO,
  UnitDTO,
  InspectionCycleCode,
  InspectionCreateRequest
} from '@/api/inspection'
import { ElMessage } from 'element-plus'

const router = useRouter()

const buildingTree = ref<BuildingTreeDTO[]>([])
const units = ref<UnitDTO[]>([])
const submitting = ref(false)

const cycleOptions = Object.entries(CYCLE_NAME_MAP).map(([value, label]) => ({
  value: value as InspectionCycleCode,
  label
}))

const form = ref<InspectionCreateRequest>({
  taskName: '',
  buildingId: 0,
  unitId: null,
  cycle: 'ONCE',
  assignee: '',
  deadline: null,
  creator: ''
})

/** 截止时间使用日期时间选择器，值为 Date */
const deadlineDate = ref<Date | null>(null)

onMounted(async () => {
  try {
    const res = await buildingApi.getBuildingTree()
    buildingTree.value = res.data
  } catch (error) {
    console.error('获取物业层级失败', error)
  }
})

const handleBuildingChange = async (buildingId: number) => {
  form.value.unitId = null
  units.value = []
  if (!buildingId) return
  try {
    const res = await buildingApi.getUnitsByBuilding(buildingId)
    units.value = res.data
  } catch (error) {
    console.error('获取单元失败', error)
  }
}

const handleSubmit = async () => {
  if (!form.value.taskName.trim()) {
    ElMessage.warning('请填写巡检任务名称')
    return
  }
  if (!form.value.buildingId) {
    ElMessage.warning('请选择巡检楼栋')
    return
  }
  if (!form.value.assignee?.trim()) {
    ElMessage.warning('请选择/填写负责人')
    return
  }

  const payload: InspectionCreateRequest = {
    ...form.value,
    taskName: form.value.taskName.trim(),
    unitId: form.value.unitId || null,
    assignee: form.value.assignee.trim(),
    creator: form.value.creator?.trim() || undefined,
    deadline: deadlineDate.value ? toLocalIso(deadlineDate.value) : null
  }

  submitting.value = true
  try {
    const res = await inspectionApi.createTask(payload)
    ElMessage.success(`巡检任务已发起，共纳入 ${res.data.totalLockers} 台快递柜`)
    router.push(`/inspections/${res.data.id}`)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '发起巡检任务失败')
  } finally {
    submitting.value = false
  }
}

const handleCancel = () => {
  router.push('/inspections')
}

/** el-date-picker 的 Date 转为本地时间 ISO（不含时区偏移），后端按 LocalDateTime 解析 */
const toLocalIso = (d: Date) => {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(
    d.getHours()
  )}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
</script>

<template>
  <div class="inspection-create">
    <el-page-header content="发起巡检任务" @back="handleCancel" />

    <el-card style="margin-top: 20px;">
      <el-form :model="form" label-width="100px" style="max-width: 640px;">
        <el-form-item label="任务名称" required>
          <el-input
            v-model="form.taskName"
            maxlength="200"
            show-word-limit
            placeholder="如：1号楼1单元月度快递柜巡检"
          />
        </el-form-item>

        <el-form-item label="巡检楼栋" required>
          <el-select
            v-model="form.buildingId"
            placeholder="请选择楼栋"
            style="width: 100%"
            @change="handleBuildingChange"
          >
            <el-option
              v-for="b in buildingTree"
              :key="b.id"
              :label="b.name + (b.code ? `（${b.code}）` : '')"
              :value="b.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="巡检单元">
          <el-select
            v-model="form.unitId"
            placeholder="不选则巡检整栋楼的所有快递柜"
            clearable
            style="width: 100%"
            :disabled="!form.buildingId"
          >
            <el-option
              v-for="u in units"
              :key="u.id"
              :label="u.name + (u.code ? `（${u.code}）` : '')"
              :value="u.id"
            />
          </el-select>
          <div class="form-tip">不选择单元时，将纳入该楼栋下全部快递柜逐台巡检。</div>
        </el-form-item>

        <el-form-item label="巡检周期" required>
          <el-radio-group v-model="form.cycle">
            <el-radio-button
              v-for="opt in cycleOptions"
              :key="opt.value"
              :value="opt.value"
            >{{ opt.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="负责人" required>
          <el-input v-model="form.assignee" maxlength="50" placeholder="请填写负责人姓名" />
        </el-form-item>

        <el-form-item label="截止时间">
          <el-date-picker
            v-model="deadlineDate"
            type="datetime"
            placeholder="请选择截止时间"
            format="YYYY-MM-DD HH:mm"
            style="width: 100%"
          />
          <div class="form-tip">截止时间过后仍未完成的任务将标记为「已逾期」。</div>
        </el-form-item>

        <el-form-item label="创建人">
          <el-input v-model="form.creator" maxlength="50" placeholder="留空则不记录" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">发起任务</el-button>
          <el-button @click="handleCancel">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.inspection-create {
  padding: 16px 0;
}

.form-tip {
  font-size: 12px;
  color: #999;
  line-height: 1.6;
}
</style>
