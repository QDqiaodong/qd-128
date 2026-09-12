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
  InspectionCreateRequest,
  InspectionScope,
  InspectionLockerOption
} from '@/api/inspection'
import { ElMessage } from 'element-plus'

const router = useRouter()

const buildingTree = ref<BuildingTreeDTO[]>([])
const units = ref<UnitDTO[]>([])
const scope = ref<InspectionScope | null>(null)
const scopeLockers = ref<InspectionLockerOption[]>([])
const scopeLoading = ref(false)
let scopeRequestSeq = 0
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

const resetScope = () => {
  scopeRequestSeq++
  scopeLoading.value = false
  scope.value = null
  scopeLockers.value = []
}

const loadScope = async (buildingId: number, unitId: number | null = null) => {
  if (!buildingId) {
    resetScope()
    return
  }
  const requestSeq = ++scopeRequestSeq
  scopeLoading.value = true
  try {
    const res = await inspectionApi.getScope(buildingId, unitId)
    if (requestSeq !== scopeRequestSeq) return
    scope.value = res.data
    scopeLockers.value = res.data.lockers
  } catch (error: any) {
    if (requestSeq !== scopeRequestSeq) return
    resetScope()
    ElMessage.error(error.response?.data?.message || '获取可巡检柜体范围失败')
  } finally {
    if (requestSeq === scopeRequestSeq) {
      scopeLoading.value = false
    }
  }
}

const handleBuildingChange = async (buildingId: number) => {
  form.value.unitId = null
  units.value = []
  resetScope()
  if (!buildingId) return
  try {
    const [unitResult, scopeResult] = await Promise.allSettled([
      buildingApi.getUnitsByBuilding(buildingId),
      loadScope(buildingId)
    ])
    if (unitResult.status === 'fulfilled') {
      units.value = unitResult.value.data
    } else {
      console.error('获取单元失败', unitResult.reason)
    }
    if (scopeResult.status === 'rejected') {
      console.error('获取可巡检范围失败', scopeResult.reason)
    }
  } catch (error) {
    console.error('获取单元失败', error)
  }
}

const handleUnitChange = (unitId: number | null) => {
  if (form.value.buildingId) {
    loadScope(form.value.buildingId, unitId || null)
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
  if (scopeLoading.value) {
    ElMessage.warning('可巡检范围正在刷新，请稍后再提交')
    return
  }
  if (!scope.value || scope.value.totalLockers === 0) {
    ElMessage.warning('所选范围内暂无可巡检的正常快递柜')
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
    lockerIds: scopeLockers.value.map((locker) => locker.id),
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
            placeholder="不选则巡检整栋楼的正常快递柜"
            clearable
            style="width: 100%"
            :disabled="!form.buildingId"
            @change="handleUnitChange"
          >
            <el-option
              v-for="u in units"
              :key="u.id"
              :label="u.name + (u.code ? `（${u.code}）` : '')"
              :value="u.id"
            />
          </el-select>
          <el-table
            v-if="form.buildingId"
            v-loading="scopeLoading"
            :data="scopeLockers"
            size="small"
            border
            style="margin-top: 8px; width: 100%"
          >
            <el-table-column prop="lockerNo" label="柜体编号" min-width="130" />
            <el-table-column prop="unitName" label="单元" min-width="100" />
            <el-table-column prop="floor" label="楼层" min-width="80" />
            <el-table-column prop="specTypeName" label="规格" min-width="100" />
            <el-table-column prop="compartmentCount" label="格口数" width="90" align="right" />
            <template #empty>暂无可巡检的正常快递柜</template>
          </el-table>
          <div class="form-tip">
            不选择单元时，将纳入该楼栋下全部正常快递柜逐台巡检；临时停用、永久停用柜不会纳入新任务。
            <template v-if="scope">当前应检 <strong>{{ scope.totalLockers }}</strong> 台。</template>
          </div>
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
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="scopeLoading || !scope?.totalLockers"
            @click="handleSubmit"
          >发起任务</el-button>
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
