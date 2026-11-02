<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { TodoItem, TodoItemInput } from '../api/todoApi'
import TodoForm from '../features/todo/TodoForm.vue'
import TodoList from '../features/todo/TodoList.vue'
import { isFiltered, overdueItems } from '../features/todo/todoFilter'
import { useTodos } from '../features/todo/useTodos'
import { useProjects } from '../features/projects/useProjects'
import { TODO_PRIORITY_LABELS, TODO_PRIORITY_ORDER } from '../shared/todo'
import { ensureSelection, selectedProjectId } from '../stores/projectSelection'
import { readOnly } from '../stores/commitView'

/** 화면 전체 공통 문구 — 화면마다 문구를 지어내지 않는다. */
const READONLY_HINT = '커밋 시점 조회 중에는 변경할 수 없습니다'

const { projects, error: projectsError, ensureLoaded: ensureProjects } = useProjects()
const {
  data,
  members,
  loading,
  error,
  noRecord,
  filters,
  visibleItems,
  ensureLoaded,
  create,
  update,
  setCompleted,
  remove,
  resetFilters,
} = useTodos()

const editing = ref<TodoItem | null>(null)
const formOpen = ref(false)

function openForm(item: TodoItem | null) {
  if (readOnly.value) return
  editing.value = item
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  editing.value = null
}

// 로드 경로는 이 watcher 하나다 — onMounted에서 또 부르면 같은 요청이 두 번 나간다.
watch(
  selectedProjectId,
  (id) => {
    closeForm()
    if (id !== null) ensureLoaded(id)
  },
  { immediate: true },
)

onMounted(async () => {
  await ensureProjects()
  ensureSelection(projects.value.map((project) => project.id))
})

/** 배너는 필터와 무관하게 전체를 센다 — "기한 초과 2건"은 프로젝트에 대한 사실이다. */
const overdue = computed(() => overdueItems(data.value.items))
const filterActive = computed(() => isFiltered(filters.value))
const hiddenCompleted = computed(() =>
  filters.value.includeCompleted ? 0 : data.value.items.filter((item) => item.completed).length,
)

/** 폼 제출 — 성공 여부를 폼에 돌려줘 성공했을 때만 닫게 한다. */
async function handleSubmit(input: TodoItemInput): Promise<boolean> {
  if (readOnly.value) return false
  const projectId = selectedProjectId.value
  if (projectId === null) return false
  return editing.value
    ? await update(projectId, editing.value.id, input)
    : await create(projectId, input)
}

async function handleToggle(item: TodoItem, completed: boolean) {
  if (readOnly.value) return
  const projectId = selectedProjectId.value
  if (projectId === null) return
  await setCompleted(projectId, item.id, completed)
}

async function handleRemove(item: TodoItem) {
  if (readOnly.value) return
  const projectId = selectedProjectId.value
  if (projectId === null) return
  if (editing.value?.id === item.id) closeForm()
  await remove(projectId, item.id)
}
</script>

<template>
  <section>
    <h1>TODO</h1>
    <p class="lede">
      WBS·Backlog·RAID에 맞지 않는 PM의 잡무를 적어 둡니다. 기한이 지난 항목은 표시되지만, 완료된
      항목은 초과로 보지 않습니다.
    </p>

    <p v-if="projectsError" class="error" role="alert">{{ projectsError }}</p>

    <p v-else-if="projects.length === 0" class="notice">
      먼저 프로젝트를 등록해야 TODO를 작성할 수 있습니다.
      <RouterLink to="/projects">프로젝트 화면으로 이동</RouterLink>
    </p>

    <template v-else>
      <div class="toolbar">
        <label class="project-picker">
          프로젝트
          <select v-model="selectedProjectId">
            <option v-for="project in projects" :key="project.id" :value="project.id">
              {{ project.name }}
            </option>
          </select>
        </label>

        <span v-if="data.referenceDate" class="reference">기준일 {{ data.referenceDate }}</span>
      </div>

      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <p v-if="noRecord" class="notice" role="status">이 커밋에는 TODO 기록이 없습니다.</p>

      <p v-if="overdue.length > 0" class="attention" aria-live="polite">
        <strong>기한 초과 {{ overdue.length }}건</strong> —
        {{ overdue.map((item) => `${item.title} (${item.overdueDays}일)`).join(', ') }}.
      </p>

      <div class="list-header">
        <!-- 필터는 읽기 동작이라 커밋 조회 중에도 잠그지 않는다. -->
        <div class="filters">
          <label class="check">
            <input v-model="filters.includeCompleted" type="checkbox" />
            완료 포함
          </label>

          <label class="check">
            <input v-model="filters.overdueOnly" type="checkbox" />
            기한 초과만
          </label>

          <select v-model="filters.priority" aria-label="우선순위 필터">
            <option value="ALL">전체 우선순위</option>
            <option v-for="priority in TODO_PRIORITY_ORDER" :key="priority" :value="priority">
              {{ TODO_PRIORITY_LABELS[priority] }}
            </option>
          </select>

          <select v-model="filters.assignee" aria-label="담당자 필터">
            <option value="ALL">전체 담당자</option>
            <option value="NONE">미지정</option>
            <option v-for="member in members" :key="member.id" :value="member.id">
              {{ member.name }}
            </option>
          </select>

          <button v-if="filterActive" type="button" class="reset" @click="resetFilters">
            필터 해제
          </button>
        </div>

        <button
          type="button"
          class="add"
          :disabled="readOnly"
          :title="readOnly ? READONLY_HINT : undefined"
          @click="openForm(null)"
        >＋ TODO 추가</button>
      </div>

      <TodoForm
        v-if="formOpen"
        :editing="editing"
        :members="members"
        :error="error"
        :on-submit="handleSubmit"
        @close="closeForm"
      />

      <p v-if="filterActive && data.items.length > 0" class="filter-note" aria-live="polite">
        {{ data.items.length }}건 중 {{ visibleItems.length }}건 표시 중.
      </p>

      <p v-if="loading" class="loading" aria-live="polite">불러오는 중…</p>
      <TodoList
        v-else
        :items="visibleItems"
        :editing-id="editing?.id ?? null"
        :filtered="filterActive && data.items.length > 0"
        :hidden-completed="hiddenCompleted"
        :read-only="readOnly"
        @edit="openForm($event)"
        @remove="handleRemove"
        @toggle="handleToggle"
      />
    </template>
  </section>
</template>

<style scoped>
h1 {
  font-size: 1.4rem;
  margin-bottom: 0.25rem;
}

.lede {
  font-size: 0.85rem;
  font-weight: normal;
  color: var(--text-muted);
  margin: 0 0 0.75rem;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 1rem;
  margin-bottom: 1rem;
}

.project-picker {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--text-muted);
}

.reference {
  font-size: 0.78rem;
  color: var(--text-faint);
}

.error {
  color: var(--danger);
  margin-bottom: 0.75rem;
}

.attention {
  font-size: 0.85rem;
  color: var(--warn-strong);
  background: var(--warn-weak);
  border-left: 3px solid var(--warn);
  border-radius: 6px;
  padding: 0.5rem 0.7rem;
  margin-bottom: 0.75rem;
  line-height: 1.5;
}

.list-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 0.75rem;
  margin: 0.25rem 0 0.75rem;
}

.filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem 0.75rem;
  flex: 1;
}

.check {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.filters select {
  padding: 0.3rem 0.5rem;
  font-size: 0.8rem;
}

.add {
  flex: none;
  margin-left: auto;
  padding: 0.35rem 0.8rem;
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
  font-size: 0.8rem;
  white-space: nowrap;
}

.reset {
  padding: 0.3rem 0.6rem;
  color: var(--text-muted);
  font-size: 0.78rem;
}

.filter-note {
  font-size: 0.78rem;
  color: var(--text-faint);
  margin-bottom: 0.5rem;
}

.loading {
  margin-top: 1rem;
}

.notice {
  color: var(--text-dim);
}
</style>
