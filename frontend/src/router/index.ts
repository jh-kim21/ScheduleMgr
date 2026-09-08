import { createRouter, createWebHistory } from 'vue-router'
import BacklogView from '../views/BacklogView.vue'
import BoardView from '../views/BoardView.vue'
import DashboardView from '../views/DashboardView.vue'
import GanttView from '../views/GanttView.vue'
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
    { path: '/wbs', name: 'wbs', component: WbsView },
    // Agile 묶음 (설계서 §2.2): Backlog 에서 계획하고, Sprint 로 담고, Board 에서 실행한다.
    { path: '/backlog', name: 'backlog', component: BacklogView },
    { path: '/sprint', name: 'sprint', component: SprintView },
    { path: '/board', name: 'board', component: BoardView },
    { path: '/gantt', name: 'gantt', component: GanttView },
    { path: '/raci', name: 'raci', component: RaciView },
    { path: '/raid', name: 'raid', component: RaidView },
    // 대시보드는 다른 화면의 숫자를 모아 보여줄 뿐 자기 데이터를 갖지 않는다.
    // 진척은 그 숫자의 근거를 입력하는 곳이라 같은 화면의 탭으로 둔다.
    { path: '/dashboard', name: 'dashboard', component: DashboardView },
    { path: '/progress', redirect: { path: '/dashboard', query: { tab: 'progress' } } },
  ],
})
