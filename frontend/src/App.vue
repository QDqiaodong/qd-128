<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  DataBoard as DashboardIcon,
  Box as Package,
  Filter,
  Folder as Archive,
  OfficeBuilding as Building,
  CircleCheck,
  AlarmClock,
  Key,
  Odometer,
  Tools,
  Bell,
  Expand,
  Fold
} from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)

// 子页面（如巡检详情、发起页）也高亮所属一级菜单
const activeMenu = computed(() => {
  if (route.path.startsWith('/inspections')) return '/inspections'
  return route.path
})

const handleMenuSelect = (index: string) => {
  router.push(index)
}
</script>

<template>
  <el-container class="layout-container">
    <el-aside :width="collapsed ? '64px' : '200px'" class="sidebar">
      <div class="logo">
        <span v-if="!collapsed">快递柜管理系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="sidebar-menu"
        mode="vertical"
        @select="handleMenuSelect"
      >
        <el-menu-item index="/">
          <DashboardIcon />
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/hierarchy">
          <Building />
          <span>物业层级管理</span>
        </el-menu-item>
        <el-sub-menu index="/lockers">
          <template #title>
            <Package />
            <span>快递柜管理</span>
          </template>
          <el-menu-item-group title="操作">
            <el-menu-item index="/lockers">快递柜列表</el-menu-item>
            <el-menu-item index="/lockers/create">新增快递柜</el-menu-item>
          </el-menu-item-group>
        </el-sub-menu>
        <el-menu-item index="/filter">
          <Filter />
          <span>多条件筛选</span>
        </el-menu-item>
        <el-menu-item index="/archives">
          <Archive />
          <span>归档管理</span>
        </el-menu-item>
        <el-menu-item index="/inspections">
          <CircleCheck />
          <span>物业巡检</span>
        </el-menu-item>
        <el-menu-item index="/clearances">
          <AlarmClock />
          <span>滞留件清柜</span>
        </el-menu-item>
        <el-menu-item index="/key-borrows">
          <Key />
          <span>钥匙借用台账</span>
        </el-menu-item>
        <el-menu-item index="/meter-readings">
          <Odometer />
          <span>电表抄表</span>
        </el-menu-item>
        <el-menu-item index="/repairs">
          <Tools />
          <span>格口报修台账</span>
        </el-menu-item>
        <el-menu-item index="/door-alarms">
          <Bell />
          <span>柜门未关告警</span>
        </el-menu-item>
      </el-menu>>
    </el-aside>
    <el-container class="main-content">
      <el-header class="header">
        <div class="header-title">
          <template v-if="route.path === '/'">首页概览</template>
          <template v-else-if="route.path === '/hierarchy'">物业层级管理</template>
          <template v-else-if="route.path === '/lockers'">快递柜列表</template>
          <template v-else-if="route.path === '/lockers/create'">新增快递柜</template>
          <template v-else-if="route.path === '/lockers/edit'">编辑快递柜</template>
          <template v-else-if="route.path.includes('/lockers/')">快递柜详情</template>
          <template v-else-if="route.path === '/filter'">多条件筛选</template>
          <template v-else-if="route.path === '/archives'">归档管理</template>
          <template v-else-if="route.path === '/inspections'">物业巡检</template>
          <template v-else-if="route.path === '/inspections/create'">发起巡检任务</template>
          <template v-else-if="route.path.includes('/inspections/')">巡检任务详情</template>
          <template v-else-if="route.path === '/clearances'">滞留件清柜</template>
          <template v-else-if="route.path === '/key-borrows'">钥匙借用台账</template>
          <template v-else-if="route.path === '/meter-readings'">电表抄表</template>
          <template v-else-if="route.path === '/repairs'">格口报修台账</template>
          <template v-else-if="route.path === '/door-alarms'">柜门未关告警</template>
        </div>
        <div class="header-actions">
          <button class="collapse-btn" @click="collapsed = !collapsed">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </button>
        </div>
      </el-header>
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background: linear-gradient(180deg, #1e3a5f 0%, #152a45 100%);
  color: #fff;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.sidebar-menu {
  border-right: none;
}

.main-content {
  flex: 1;
  overflow: hidden;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.header-actions {
  display: flex;
  align-items: center;
}

.collapse-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 8px;
  color: #666;
  transition: color 0.3s;
}

.collapse-btn:hover {
  color: #1e3a5f;
}

.content {
  padding: 24px;
  background: #f5f5f5;
  overflow-y: auto;
}
</style>
