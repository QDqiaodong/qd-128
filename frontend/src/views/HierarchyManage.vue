<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { ElMessage, ElMessageBox, ElTag, ElButton } from 'element-plus'
import { buildingApi } from '@/api/locker'
import type { BuildingTreeDTO, UnitDTO, HierarchyReference } from '@/api/locker'

type NodeKind = 'BUILDING' | 'UNIT'

interface TreeNode {
  key: string
  kind: NodeKind
  id: number
  name: string
  code: string
  buildingId?: number
  children?: TreeNode[]
}

const treeData = ref<TreeNode[]>([])
const treeRef = ref()
const loading = ref(false)

// ---------------- 数据加载 ----------------

onMounted(async () => {
  await fetchTree()
})

const fetchTree = async () => {
  loading.value = true
  try {
    const res = await buildingApi.getBuildingTree()
    treeData.value = (res.data || []).map((b: BuildingTreeDTO) => ({
      key: `b-${b.id}`,
      kind: 'BUILDING' as NodeKind,
      id: b.id,
      name: b.name,
      code: b.code || '',
      children: (b.children || []).map((u: UnitDTO) => ({
        key: `u-${u.id}`,
        kind: 'UNIT' as NodeKind,
        id: u.id,
        name: u.name,
        code: u.code || '',
        buildingId: u.buildingId
      }))
    }))
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '获取楼栋单元树失败')
  } finally {
    loading.value = false
  }
}

// ---------------- 树节点渲染 ----------------

const treeProps = {
  children: 'children',
  label: 'name'
}

const renderNode = ({ data }: { data: TreeNode }) => {
  return h('div', { class: 'tree-node' }, [
    h(
      'span',
      { class: 'node-label' },
      data.kind === 'BUILDING' ? '🏢 ' : '🚪 '
    ),
    h('span', { class: 'node-name' }, data.name),
    data.code
      ? h(ElTag, { size: 'small', type: data.kind === 'BUILDING' ? 'primary' : 'success', class: 'node-code' },
          () => data.code)
      : h(ElTag, { size: 'small', type: 'info', class: 'node-code' }, () => '无编码'),
    h('span', { class: 'node-actions' }, [
      h(
        ElButton,
        {
          size: 'small',
          type: 'primary',
          link: true,
          onClick: (e: Event) => {
            e.stopPropagation()
            openCreateUnit(data)
          }
        },
        () => '新增单元'
      ),
      h(
        ElButton,
        {
          size: 'small',
          type: 'primary',
          link: true,
          onClick: (e: Event) => {
            e.stopPropagation()
            openEdit(data)
          }
        },
        () => '改名/编码'
      ),
      h(
        ElButton,
        {
          size: 'small',
          type: 'danger',
          link: true,
          onClick: (e: Event) => {
            e.stopPropagation()
            handleDelete(data)
          }
        },
        () => '删除'
      )
    ])
  ])
}

// ---------------- 新增 / 编辑弹窗 ----------------

interface EditForm {
  name: string
  code: string
  sortOrder?: number
}

const dialogVisible = ref(false)
const dialogMode = ref<'create-building' | 'create-unit' | 'edit'>('edit')
const editingKind = ref<NodeKind>('BUILDING')
const editingId = ref(0)
const parentBuildingId = ref(0)
const form = ref<EditForm>({ name: '', code: '' })

const dialogTitle = computed(() => {
  switch (dialogMode.value) {
    case 'create-building':
      return '新增楼栋'
    case 'create-unit':
      return '新增单元'
    default:
      return editingKind.value === 'BUILDING' ? '编辑楼栋' : '编辑单元'
  }
})

const codePlaceholder = computed(() =>
  editingKind.value === 'BUILDING' ? '如：B001（楼栋内全局唯一，可留空）' : '如：U001（同一楼栋下唯一，可留空）'
)

const resetForm = () => {
  form.value = { name: '', code: '' }
}

const openCreateBuilding = () => {
  dialogMode.value = 'create-building'
  editingKind.value = 'BUILDING'
  editingId.value = 0
  resetForm()
  dialogVisible.value = true
}

const openCreateUnit = (buildingNode: TreeNode) => {
  dialogMode.value = 'create-unit'
  editingKind.value = 'UNIT'
  editingId.value = 0
  parentBuildingId.value = buildingNode.id
  resetForm()
  dialogVisible.value = true
}

const openEdit = (node: TreeNode) => {
  dialogMode.value = 'edit'
  editingKind.value = node.kind
  editingId.value = node.id
  parentBuildingId.value = node.buildingId || 0
  form.value = { name: node.name, code: node.code }
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!form.value.name.trim()) {
    ElMessage.warning('名称不能为空')
    return
  }
  const payload = {
    name: form.value.name.trim(),
    code: form.value.code.trim() || undefined
  }
  try {
    if (dialogMode.value === 'create-building') {
      await buildingApi.createBuilding(payload)
      ElMessage.success('楼栋新增成功')
    } else if (dialogMode.value === 'create-unit') {
      await buildingApi.createUnit({ buildingId: parentBuildingId.value, ...payload })
      ElMessage.success('单元新增成功')
    } else if (editingKind.value === 'BUILDING') {
      await buildingApi.updateBuilding(editingId.value, payload)
      ElMessage.success('楼栋信息已更新')
    } else {
      await buildingApi.updateUnit(editingId.value, payload)
      ElMessage.success('单元信息已更新')
    }
    dialogVisible.value = false
    await fetchTree()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '操作失败')
  }
}

// ---------------- 删除（删除前校验关联） ----------------

const handleDelete = async (node: TreeNode) => {
  const kindLabel = node.kind === 'BUILDING' ? '楼栋' : '单元'
  let ref: HierarchyReference
  try {
    const res = node.kind === 'BUILDING'
      ? await buildingApi.getBuildingReferences(node.id)
      : await buildingApi.getUnitReferences(node.id)
    ref = res.data
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '关联检查失败')
    return
  }

  if (!ref.deletable) {
    // 存在关联快递柜或归档快照：禁止删除并展示影响数量
    await ElMessageBox.alert(
      `「${node.name}」存在关联数据，禁止删除：\n` +
        `· 关联快递柜：${ref.lockerCount} 台\n` +
        `· 归档快照：${ref.archiveCount} 条\n\n` +
        `请先将关联快递柜调整至其他${node.kind === 'BUILDING' ? '楼栋' : '单元'}或删除后再操作。`,
      `无法删除${kindLabel}`,
      { type: 'error', confirmButtonText: '我知道了' }
    ).catch(() => {})
    return
  }

  const extraTip =
    node.kind === 'BUILDING' && ref.unitCount > 0
      ? `该楼栋下还有 ${ref.unitCount} 个无关联快递柜的单元，将一并删除。\n`
      : ''

  try {
    await ElMessageBox.confirm(
      `确认删除${kindLabel}「${node.name}」？\n${extraTip}删除后不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    if (node.kind === 'BUILDING') {
      await buildingApi.deleteBuilding(node.id)
    } else {
      await buildingApi.deleteUnit(node.id)
    }
    ElMessage.success('删除成功')
    await fetchTree()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '删除失败')
  }
}
</script>

<template>
  <div class="hierarchy-manage">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>物业层级维护（楼栋 / 单元）</span>
          <el-button type="primary" @click="openCreateBuilding">新增楼栋</el-button>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="支持楼栋及单元的新增、改名、编码调整和删除。删除前会检查关联快递柜及归档快照；名称或编码变更后，快递柜列表、详情、筛选结果及归属调整历史将同步显示最新信息。"
        style="margin-bottom: 16px;"
      />

      <el-tree
        ref="treeRef"
        :data="treeData"
        :props="treeProps"
        node-key="key"
        default-expand-all
        :expand-on-click-node="false"
        :render-content="renderNode"
      />
      <div v-if="!loading && treeData.length === 0" class="empty-tip">
        暂无楼栋数据，请点击右上角「新增楼栋」
      </div>
    </el-card>

    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item v-if="dialogMode === 'create-unit'" label="所属楼栋">
          <el-tag type="primary">
            {{ treeData.find((b) => b.id === parentBuildingId)?.name || '-' }}
          </el-tag>
        </el-form-item>
        <el-form-item :label="editingKind === 'BUILDING' ? '楼栋名称' : '单元名称'" required>
          <el-input v-model="form.name" :placeholder="editingKind === 'BUILDING' ? '如：1号楼' : '如：1单元'" />
        </el-form-item>
        <el-form-item :label="editingKind === 'BUILDING' ? '楼栋编码' : '单元编码'">
          <el-input v-model="form.code" :placeholder="codePlaceholder" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hierarchy-manage {
  padding: 16px 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tree-node {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 8px;
}

.node-label {
  flex-shrink: 0;
}

.node-name {
  font-weight: 500;
  color: #303133;
}

.node-code {
  margin-left: 4px;
}

.node-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
  padding-right: 8px;
}

.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px;
}
</style>
