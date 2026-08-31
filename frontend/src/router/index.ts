import { createRouter, createWebHistory } from 'vue-router'
import GanttView from '../views/GanttView.vue'
import PlaceholderView from '../views/PlaceholderView.vue'
import ProjectsView from '../views/ProjectsView.vue'
import WbsView from '../views/WbsView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/projects' },
    { path: '/projects', name: 'projects', component: ProjectsView },
    { path: '/wbs', name: 'wbs', component: WbsView },
    { path: '/gantt', name: 'gantt', component: GanttView },
    {
      path: '/raci',
      name: 'raci',
      component: PlaceholderView,
      props: { title: 'RACI' },
    },
    {
      path: '/raid',
      name: 'raid',
      component: PlaceholderView,
      props: { title: 'RAID' },
    },
  ],
})
