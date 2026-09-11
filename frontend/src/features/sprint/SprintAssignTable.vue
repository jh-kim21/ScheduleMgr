<script setup lang="ts">
import { computed, ref, watch, watchEffect } from 'vue'
import { useRowSelection } from '../../shared/useRowSelection'
import {
  assignSelectionSummary,
  filterAssignable,
  type AssignCandidate,
} from './assignFilter'

const props = defineProps<{
  candidates: AssignCandidate[]
  /** 배정 진행 중이면 컨트롤을 잠근다. */
  busy?: boolean
}>()

const emit = defineEmits<{ assign: [ids: number[]] }>()

const query = ref('')
/** 체크박스로 고른 id들. 검색어가 바뀌어도 유지한다 — 좁혀서 고르고, 지우고, 또 좁혀서 고르는
 * 흐름이 자연스럽기 때문이다. 그래서 요약은 항상 `props.candidates`(전체) 기준으로 계산한다. */
const selectedIds = ref<number[]>([])

const filtered = computed(() => filterAssignable(props.candidates, query.value))
const visibleIds = computed(() => filtered.value.map((c) => c.id))

const allVisibleSelected = computed(
  () => visibleIds.value.length > 0 && visibleIds.value.every((id) => selectedIds.value.includes(id)),
)
const someVisibleSelected = computed(
  () => !allVisibleSelected.value && visibleIds.value.some((id) => selectedIds.value.includes(id)),
)

const summary = computed(() => assignSelectionSummary(props.candidates, selectedIds.value))

/*
 * 배정이 성공하면(전부든 일부든) 부모의 `candidates`가 줄어든다 — `useSprints.assign`이 성공할
 * 때마다 Backlog를 다시 읽어 `assignable`을 갱신하기 때문이다. 그래서 선택을 비우는 신호를 부모가
 * 따로 보낼 필요 없이, 후보 목록에서 사라진 id를 이 watch가 그때그때 골라낸다. 실패로 남은
 * 후보는 그대로 candidates에 남아 있으므로 선택도 그대로 남는다 — "거부되면 입력값이 남아
 * 있어야 한다"는 규칙과 같은 결과를 부모의 별도 개입 없이 얻는다. (defineExpose(clearSelection)나
 * :key 재마운트 대신 이 방식을 골랐다 — 부분 성공에서 성공한 것만 정확히 빠지는 쪽은 이 방식뿐이고,
 * 전부 지우는 것보다 더 정밀하다.)
 */
watch(
  () => props.candidates,
  (list) => {
    const ids = new Set(list.map((c) => c.id))
    selectedIds.value = selectedIds.value.filter((id) => ids.has(id))
  },
)

function toggleOne(id: number) {
  const idx = selectedIds.value.indexOf(id)
  if (idx === -1) selectedIds.value = [...selectedIds.value, id]
  else selectedIds.value = selectedIds.value.filter((existing) => existing !== id)
}

/** 검색으로 걸러진 것만 토글한다 — 안 보이는 것을 함께 선택하면 사용자가 모르는 항목을 배정하게 된다. */
function toggleAllVisible() {
  if (allVisibleSelected.value) {
    selectedIds.value = selectedIds.value.filter((id) => !visibleIds.value.includes(id))
  } else {
    const merged = new Set(selectedIds.value)
    for (const id of visibleIds.value) merged.add(id)
    selectedIds.value = [...merged]
  }
}

/** 네이티브 체크박스에는 `indeterminate`용 속성이 없어 엘리먼트를 직접 건드려야 한다. */
const selectAllCheckbox = ref<HTMLInputElement | null>(null)
watchEffect(() => {
  if (selectAllCheckbox.value) selectAllCheckbox.value.indeterminate = someVisibleSelected.value
})

/** 클릭 선택 하이라이트·↑↓ 이동은 다른 표와 같은 컴포저블을 쓴다. */
const body = ref<HTMLElement | null>(null)
const selection = useRowSelection(() => visibleIds.value, body)

/**
 * 더블클릭 → 체크박스 토글. 단 더블클릭이 체크박스 자체에 떨어진 경우는 건드리지 않는다 — 그
 * 클릭 두 번은 이미 체크박스 자신의 네이티브 토글 두 번(원상태로 복귀)을 발생시키므로, 여기서
 * 또 토글하면 세 번째 토글이 되어 결과가 어긋난다. `useRowSelection`의 BUTTON/A 가드만으로는
 * INPUT을 걸러내지 못해 직접 검사한다.
 */
function handleRowDblClick(event: MouseEvent, id: number) {
  if ((event.target as HTMLElement).tagName === 'INPUT') return
  selection.onRowDblClick(event, () => toggleOne(id))
}

function handleAssign() {
  if (props.busy || summary.value.count === 0) return
  // candidates 순서(= Backlog 우선순위 순서)대로 순차 배정하도록 selectedIds가 아닌 candidates를
  // 기준으로 뽑는다.
  const ids = props.candidates.filter((c) => selectedIds.value.includes(c.id)).map((c) => c.id)
  emit('assign', ids)
}
</script>

<template>
  <div class="assign-table">
    <div class="controls">
      <input
        v-model="query"
        type="search"
        class="search"
        placeholder="제목 또는 유형으로 검색"
        :disabled="busy"
      />
      <div class="summary">
        <button type="button" :disabled="busy || summary.count === 0" @click="handleAssign">
          선택한 {{ summary.count }}건 배정
        </button>
        <span v-if="summary.count > 0" class="summary-detail">
          {{ summary.points }} SP
          <template v-if="summary.unestimated > 0"> · 추정 없음 {{ summary.unestimated }}건</template>
        </span>
      </div>
    </div>

    <p v-if="candidates.length === 0" class="empty">배정할 수 있는 항목이 없습니다</p>
    <p v-else-if="filtered.length === 0" class="empty">검색 결과가 없습니다</p>
    <div v-else class="table-scroll rows">
      <table>
        <thead>
          <tr>
            <th class="check">
              <input
                ref="selectAllCheckbox"
                type="checkbox"
                :checked="allVisibleSelected"
                :disabled="busy"
                @change="toggleAllVisible"
              />
            </th>
            <th class="type">유형</th>
            <th>제목</th>
            <th class="num">SP</th>
          </tr>
        </thead>
        <tbody ref="body" class="row-selectable" tabindex="0" @keydown="selection.onKeydown">
          <tr
            v-for="item in filtered"
            :key="item.id"
            :data-row-id="item.id"
            :class="{ selected: selection.isSelected(item.id) }"
            :aria-selected="selection.isSelected(item.id)"
            @click="selection.select(item.id)"
            @dblclick="handleRowDblClick($event, item.id)"
          >
            <td class="check">
              <input
                type="checkbox"
                :checked="selectedIds.includes(item.id)"
                :disabled="busy"
                @click.stop
                @change="toggleOne(item.id)"
              />
            </td>
            <td class="type">{{ item.typeLabel }}</td>
            <td><span class="cell-clip" :title="item.title">{{ item.title }}</span></td>
            <td class="num">{{ item.storyPoint ?? '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.assign-table {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.controls {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.search {
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
  font-size: 0.85rem;
  min-width: 14rem;
}

.summary {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

button {
  padding: 0.4rem 0.8rem;
  border-radius: 6px;
  border: 1px solid var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
  cursor: pointer;
  font: inherit;
  font-size: 0.82rem;
  white-space: nowrap;
}

button:disabled {
  background: var(--disabled-bg);
  color: var(--disabled-fg);
  border-color: var(--border-soft);
  cursor: not-allowed;
}

.summary-detail {
  font-size: 0.78rem;
  color: var(--text-faint);
  font-variant-numeric: tabular-nums;
}

.empty {
  font-size: 0.85rem;
  color: var(--text-faint);
  margin: 0;
}

/* 항목이 많은 경우가 이 표를 만든 이유이므로, 세로로 스크롤을 둬 Sprint 화면 전체를 밀어내지
   않게 한다. */
.rows {
  max-height: 18rem;
  overflow-y: auto;
  border: 1px solid var(--border-soft);
  border-radius: 6px;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  text-align: left;
  padding: 0.4rem 0.55rem;
  border-bottom: 1px solid var(--border-soft);
  font-size: 0.85rem;
  white-space: nowrap;
}

th {
  font-size: 0.75rem;
  color: var(--text-dim);
  font-weight: 600;
  /* 세로 스크롤 중에도 머리글이 보이도록 고정한다. */
  position: sticky;
  top: 0;
  background: var(--surface);
}

.check {
  width: 2rem;
}

.type {
  width: 5rem;
  color: var(--text-muted);
}

.num {
  width: 4rem;
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--text-muted);
}

tbody tr {
  cursor: pointer;
}

tbody tr:focus {
  outline: none;
}
</style>
