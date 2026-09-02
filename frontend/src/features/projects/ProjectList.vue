<script setup lang="ts">
import type { Project } from '../../api/projectApi'
import { STATUS_LABELS } from './statusLabels'

defineProps<{
  projects: Project[]
}>()

const emit = defineEmits<{
  edit: [project: Project]
  remove: [project: Project]
}>()
</script>

<template>
  <table class="project-list">
    <thead>
      <tr>
        <th>이름</th>
        <th>상태</th>
        <th>시작일</th>
        <th>종료일</th>
        <th></th>
      </tr>
    </thead>
    <tbody>
      <tr v-if="projects.length === 0">
        <td colspan="5" class="empty">등록된 프로젝트가 없습니다.</td>
      </tr>
      <tr v-for="project in projects" :key="project.id">
        <td>
          <div class="name">{{ project.name }}</div>
          <div v-if="project.description" class="desc">{{ project.description }}</div>
        </td>
        <td><span class="badge" :data-status="project.status">{{ STATUS_LABELS[project.status] }}</span></td>
        <td>{{ project.startDate ?? '-' }}</td>
        <td>{{ project.endDate ?? '-' }}</td>
        <td class="actions">
          <!--
            평범한 링크다. 서버가 Content-Disposition: attachment 로 내려주므로 fetch·blob
            코드가 필요 없다. 화면별 CSV와 달리 이건 프로젝트 전체 스냅샷이다.
          -->
          <a
            class="ghost export"
            :href="`/api/projects/${project.id}/export`"
            download
            title="이 프로젝트 전체를 JSON 한 파일로 내려받습니다"
          >내보내기</a>
          <button class="ghost" @click="emit('edit', project)">수정</button>
          <button class="danger" @click="emit('remove', project)">삭제</button>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<style scoped>
.project-list {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  text-align: left;
  padding: 0.6rem 0.75rem;
  border-bottom: 1px solid var(--border-soft);
  vertical-align: top;
}

.name {
  font-weight: 600;
}

.desc {
  font-size: 0.8rem;
  color: var(--text-dim);
  margin-top: 0.15rem;
}

.empty {
  text-align: center;
  color: var(--text-faint);
  padding: 2rem 0;
}

.badge {
  display: inline-block;
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  font-size: 0.75rem;
  background: var(--badge-planned-bg);
  color: var(--badge-planned-fg);
}

.badge[data-status='IN_PROGRESS'] {
  background: var(--success-weak);
  color: var(--success);
}

.badge[data-status='COMPLETED'] {
  background: var(--badge-neutral-bg);
  color: var(--badge-neutral-fg);
}

.badge[data-status='ON_HOLD'] {
  background: var(--warn-badge-bg);
  color: var(--warn-badge-fg);
}

.actions a.export {
  text-decoration: none;
  display: inline-block;
}

.actions {
  display: flex;
  gap: 0.4rem;
  white-space: nowrap;
}

button {
  padding: 0.35rem 0.7rem;
  border-radius: 6px;
  border: 1px solid var(--border-input);
  background: var(--surface);
  cursor: pointer;
  font-size: 0.8rem;
}

button.danger {
  color: var(--danger);
  border-color: var(--danger-border);
}
</style>
