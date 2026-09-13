import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '@/views/Dashboard.vue'
import LockerList from '@/views/LockerList.vue'
import LockerForm from '@/views/LockerForm.vue'
import LockerDetail from '@/views/LockerDetail.vue'
import FilterSearch from '@/views/FilterSearch.vue'
import ArchiveList from '@/views/ArchiveList.vue'
import HierarchyManage from '@/views/HierarchyManage.vue'
import InspectionList from '@/views/InspectionList.vue'
import InspectionCreate from '@/views/InspectionCreate.vue'
import InspectionDetail from '@/views/InspectionDetail.vue'
import ClearanceList from '@/views/ClearanceList.vue'
import KeyBorrowList from '@/views/KeyBorrowList.vue'
import MeterReadingList from '@/views/MeterReadingList.vue'
import RepairList from '@/views/RepairList.vue'

const routes = [
  { path: '/', name: 'Dashboard', component: Dashboard },
  { path: '/hierarchy', name: 'HierarchyManage', component: HierarchyManage },
  { path: '/lockers', name: 'LockerList', component: LockerList },
  { path: '/lockers/create', name: 'LockerCreate', component: LockerForm },
  { path: '/lockers/:id/edit', name: 'LockerEdit', component: LockerForm },
  { path: '/lockers/:id', name: 'LockerDetail', component: LockerDetail },
  { path: '/filter', name: 'FilterSearch', component: FilterSearch },
  { path: '/archives', name: 'ArchiveList', component: ArchiveList },
  { path: '/inspections', name: 'InspectionList', component: InspectionList },
  { path: '/inspections/create', name: 'InspectionCreate', component: InspectionCreate },
  { path: '/inspections/:id', name: 'InspectionDetail', component: InspectionDetail },
  { path: '/clearances', name: 'ClearanceList', component: ClearanceList },
  { path: '/key-borrows', name: 'KeyBorrowList', component: KeyBorrowList },
  { path: '/meter-readings', name: 'MeterReadingList', component: MeterReadingList },
  { path: '/repairs', name: 'RepairList', component: RepairList }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
