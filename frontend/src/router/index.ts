import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '@/views/Dashboard.vue'
import LockerList from '@/views/LockerList.vue'
import LockerForm from '@/views/LockerForm.vue'
import LockerDetail from '@/views/LockerDetail.vue'
import FilterSearch from '@/views/FilterSearch.vue'
import ArchiveList from '@/views/ArchiveList.vue'
import HierarchyManage from '@/views/HierarchyManage.vue'

const routes = [
  { path: '/', name: 'Dashboard', component: Dashboard },
  { path: '/hierarchy', name: 'HierarchyManage', component: HierarchyManage },
  { path: '/lockers', name: 'LockerList', component: LockerList },
  { path: '/lockers/create', name: 'LockerCreate', component: LockerForm },
  { path: '/lockers/:id/edit', name: 'LockerEdit', component: LockerForm },
  { path: '/lockers/:id', name: 'LockerDetail', component: LockerDetail },
  { path: '/filter', name: 'FilterSearch', component: FilterSearch },
  { path: '/archives', name: 'ArchiveList', component: ArchiveList }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
