import { createRouter, createWebHistory } from 'vue-router'
import BacklogView from '../views/BacklogView.vue'
import DashboardView from '../views/DashboardView.vue'
import GanttView from '../views/GanttView.vue'
import ProgressView from '../views/ProgressView.vue'
import ProjectsView from '../views/ProjectsView.vue'
import SprintView from '../views/SprintView.vue'
import RaciView from '../views/RaciView.vue'
import RaidView from '../views/RaidView.vue'
import WbsView from '../views/WbsView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/projects' },
    { path: '/projects', name: 'projects', component: ProjectsView },
    // 대시보드는 다른 화면의 숫자를 모아 보여줄 뿐 자기 데이터를 갖지 않는다.
    { path: '/dashboard', name: 'dashboard', component: DashboardView },
    { path: '/wbs', name: 'wbs', component: WbsView },
    // Agile 묶음: Backlog에서 계획하고, Sprint 화면에서 실행(Board)까지 다룬다.
    { path: '/backlog', name: 'backlog', component: BacklogView },
    { path: '/sprint', name: 'sprint', component: SprintView },
    { path: '/progress', name: 'progress', component: ProgressView },
    { path: '/gantt', name: 'gantt', component: GanttView },
    { path: '/raci', name: 'raci', component: RaciView },
    { path: '/raid', name: 'raid', component: RaidView },
  ],
})
