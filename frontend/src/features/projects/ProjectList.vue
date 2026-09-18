<script setup lang="ts">
import { ref } from 'vue'
import type { Project } from '../../api/projectApi'
import { readOnly } from '../../stores/commitView'
import { useRowSelection } from '../../shared/useRowSelection'
import ExportMenu from '../export/ExportMenu.vue'
import { STATUS_LABELS } from './statusLabels'

const props = defineProps<{
  projects: Project[]
}>()

const emit = defineEmits<{
  edit: [project: Project]
  remove: [project: Project]
  members: [project: Project]
  tags: [project: Project]
  commits: [project: Project]
}>()

/** 클릭 선택·방향키 이동·더블클릭 편집은 WBS 표와 같은 컴포저블을 쓴다 (모든 표가 공유). */
const body = ref<HTMLElement | null>(null)
const selection = useRowSelection(() => props.projects.map((project) => project.id), body)
</script>

<template>
  <div class="table-scroll">
    <table class="project-list">
      <thead>
        <tr>
          <th scope="col">이름</th>
          <th scope="col">상태</th>
          <th scope="col">시작일</th>
          <th scope="col">종료일</th>
          <th scope="col"></th>
        </tr>
      </thead>
      <tbody ref="body" class="row-selectable" tabindex="0" @keydown="selection.onKeydown">
        <tr v-if="projects.length === 0">
          <td colspan="5" class="empty">등록된 프로젝트가 없습니다.</td>
        </tr>
        <tr
          v-for="project in projects"
          :key="project.id"
          :data-row-id="project.id"
          :class="{ selected: selection.isSelected(project.id) }"
          :aria-selected="selection.isSelected(project.id)"
          @click="selection.select(project.id)"
          @dblclick="selection.onRowDblClick($event, () => !readOnly && emit('edit', project))"
        >
          <td>
            <div class="name">{{ project.name }}</div>
            <div v-if="project.description" class="desc">{{ project.description }}</div>
          </td>
          <td><span class="badge" :data-status="project.status">{{ STATUS_LABELS[project.status] }}</span></td>
          <td>{{ project.startDate ?? '-' }}</td>
          <td>{{ project.endDate ?? '-' }}</td>
          <td class="actions">
            <ExportMenu :project="project" />
            <button class="ghost" @click="emit('commits', project)">커밋</button>
            <button
              class="ghost"
              :disabled="readOnly"
              :title="readOnly ? '커밋 시점을 보는 동안에는 구성원을 바꿀 수 없습니다.' : undefined"
              @click="emit('members', project)"
            >
              구성원
            </button>
            <!--
              업무 분야(태그) 마스터. 구성원 옆에 두는 이유는 같다 — 프로젝트 스코프 개념이라
              WBS 화면에 종속될 이유가 없고, 이름이 프로젝트 안에서 유일해야 하는 것도 같다.
            -->
            <button
              class="ghost"
              :disabled="readOnly"
              :title="readOnly ? '커밋 시점을 보는 동안에는 분야를 바꿀 수 없습니다.' : undefined"
              @click="emit('tags', project)"
            >
              분야
            </button>
            <button
              class="ghost"
              :disabled="readOnly"
              :title="readOnly ? '커밋 시점을 보는 동안에는 수정할 수 없습니다.' : undefined"
              @click="emit('edit', project)"
            >
              수정
            </button>
            <button
              class="danger"
              :disabled="readOnly"
              :title="readOnly ? '커밋 시점을 보는 동안에는 삭제할 수 없습니다.' : undefined"
              @click="emit('remove', project)"
            >
              삭제
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
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

.actions {
  display: flex;
  gap: 0.4rem;
  white-space: nowrap;
}

/* border-radius·border·background·cursor는 전역 기본과 같은 값이라 지웠다. `.danger`·:disabled
   블록도 전역 계약과 완전히 같아 그대로 지웠다 — 표 행 안이라 작게 쓰는 크기(padding·font-size)만
   남는다. */
button {
  padding: 0.35rem 0.7rem;
  font-size: 0.8rem;
}
</style>
