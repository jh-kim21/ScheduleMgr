<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ModalDialog from '../../components/ModalDialog.vue'
import TagChip from './TagChip.vue'
import { TAG_COLORS } from './tagColor'
import { useWbsTags } from './useWbsTags'
import type { WbsTag, WbsTagInput } from '../../api/wbsTagApi'

/**
 * 업무 분야(태그) 마스터 관리.
 *
 * 프로젝트 화면의 행에서 열린다 — 구성원 관리와 같은 자리, 같은 이유다. 태그는 프로젝트 스코프
 * 개념이라 WBS 화면에 종속될 이유가 없고, 이름이 프로젝트 안에서 유일해야 한다는 규칙도
 * `project_members`와 같다.
 *
 * `MemberEditor`처럼 **자기 제목을 계산해 스스로를 `ModalDialog`로 감싼다** (CLAUDE.md "화면 레이아웃
 * 규칙") — 뷰가 제목을 만들면 문맥이 붙는 제목이 두 곳에서 중복된다.
 *
 * 변경은 `MemberEditor`와 달리 이 컴포넌트가 직접 한다(`useWbsTags`) — `CheckpointList`가
 * `useProgress`를 직접 부르는 것과 같은 방식이다. 서버가 거부했는지를 알아야 "성공했을 때만 닫는다"를
 * 지킬 수 있는데, emit만 해서는 결과가 돌아오지 않는다.
 */
const props = defineProps<{
  projectId: number
  /** 대화상자 제목에 넣을 프로젝트 이름 — "분야 관리 — 웹사이트 개편"처럼 문맥을 붙인다. */
  projectName: string
}>()

const emit = defineEmits<{ close: [] }>()

const title = computed(() => `분야 관리 — ${props.projectName}`)

const { tags, loading, error, ensureLoaded, create, update, remove } = useWbsTags()

watch(() => props.projectId, (id) => ensureLoaded(id), { immediate: true })

interface Draft {
  name: string
  /** null이면 "자동" — 화면이 이름 해시로 고른다. */
  color: string | null
}

const blank = (): Draft => ({ name: '', color: null })

const draft = ref<Draft>(blank())
/** 추가 폼도 대화상자로 띄운다 — 이 화면의 주된 행위는 목록을 읽는 것이다(구성원 관리와 같다). */
const addOpen = ref(false)

/** 인라인으로 고치는 중인 행과 그 값. */
const editingId = ref<number | null>(null)
const editDraft = ref<Draft>(blank())

const submittable = computed(() => draft.value.name.trim().length > 0)
const editSubmittable = computed(() => editDraft.value.name.trim().length > 0)

/** 미리보기용 — 이름을 치는 동안 "자동"이 어떤 색이 되는지 바로 보인다. */
const draftPreview = computed(() => ({
  name: draft.value.name.trim() || '분야',
  color: draft.value.color,
}))

function payload(input: Draft, sortOrder: number | null): WbsTagInput {
  return { name: input.name.trim(), color: input.color, sortOrder }
}

function openAdd() {
  draft.value = blank()
  addOpen.value = true
}

/** 성공했을 때만 닫는다 — 거부되면 입력값과 사유가 그대로 남아 있어야 한다. */
async function onAdd() {
  if (!submittable.value) return
  // sortOrder는 서버가 정한다 — 새 분야는 목록 끝에 붙는 것이 자연스럽고, 클라이언트가 번호를
  // 지어내면 다른 브라우저에서 만든 것과 겹칠 수 있다.
  const ok = await create(props.projectId, payload(draft.value, null))
  if (!ok) return
  draft.value = blank()
  addOpen.value = false
}

function startEdit(tag: WbsTag) {
  editingId.value = tag.id
  editDraft.value = { name: tag.name, color: tag.color }
}

function cancelEdit() {
  editingId.value = null
}

async function onSave(tag: WbsTag) {
  if (!editSubmittable.value) return
  // 순서는 이 화면에서 다루지 않으므로 지금 값을 그대로 되돌려 보낸다 — 빼고 보내면 서버가
  // "지정하지 않음"으로 읽어 목록 순서가 저장할 때마다 흔들릴 수 있다.
  const ok = await update(props.projectId, tag.id, payload(editDraft.value, tag.sortOrder))
  if (ok) cancelEdit()
}

function onRemove(tag: WbsTag) {
  if (
    !confirm(
      `"${tag.name}" 분야를 삭제할까요?\n이 분야가 붙어 있던 WBS 항목은 그대로 남고 연결만 사라집니다.`,
    )
  ) {
    return
  }
  remove(props.projectId, tag.id)
}
</script>

<template>
  <ModalDialog :title="title" :error="error" @close="emit('close')">
    <section class="tags">
      <header class="section-head">
        <p class="rule">
          분야는 Service·Web처럼 <strong>업무의 성격</strong>을 적는 값입니다. 한 업무가 두 분야에
          걸치는 것이 정상이라 여러 개를 붙일 수 있고, 같은 프로젝트 안에서 이름은 겹칠 수 없습니다.
          항목에 붙이는 것은 WBS 화면의 수정 폼에서 합니다.
        </p>
        <button type="button" class="add" @click="openAdd">＋ 분야 추가</button>
      </header>

      <ModalDialog v-if="addOpen" title="분야 추가" :error="error" @close="addOpen = false">
        <form class="add-form" @submit.prevent="onAdd">
          <label>
            이름
            <input v-model="draft.name" type="text" placeholder="예: Service, Web, DB" />
          </label>

          <fieldset class="colors">
            <legend>색</legend>
            <label class="swatch auto" :class="{ picked: draft.color === null }">
              <input v-model="draft.color" type="radio" :value="null" />
              <span>자동</span>
            </label>
            <label
              v-for="color in TAG_COLORS"
              :key="color"
              class="swatch"
              :class="{ picked: draft.color === color }"
              :data-color="color"
              :title="color"
            >
              <input v-model="draft.color" type="radio" :value="color" />
              <span class="dot"></span>
            </label>
          </fieldset>

          <p class="preview">
            미리보기 <TagChip :tag="draftPreview" />
            <span v-if="draft.color === null" class="auto-note">
              자동은 이름으로 정해지므로 같은 이름이면 언제나 같은 색입니다.
            </span>
          </p>

          <div class="dialog-actions">
            <button type="submit" class="primary" :disabled="!submittable">추가</button>
            <button type="button" @click="addOpen = false">취소</button>
          </div>
        </form>
      </ModalDialog>

      <p v-if="loading">불러오는 중...</p>
      <template v-else>
        <ul v-if="tags.length > 0" class="list">
          <li v-for="tag in tags" :key="tag.id" :class="{ editing: editingId === tag.id }">
            <template v-if="editingId === tag.id">
              <input v-model="editDraft.name" type="text" aria-label="이름" />
              <span class="colors inline">
                <label class="swatch auto" :class="{ picked: editDraft.color === null }">
                  <input v-model="editDraft.color" type="radio" :value="null" />
                  <span>자동</span>
                </label>
                <label
                  v-for="color in TAG_COLORS"
                  :key="color"
                  class="swatch"
                  :class="{ picked: editDraft.color === color }"
                  :data-color="color"
                  :title="color"
                >
                  <input v-model="editDraft.color" type="radio" :value="color" />
                  <span class="dot"></span>
                </label>
              </span>

              <span class="actions">
                <button type="button" class="primary" :disabled="!editSubmittable" @click="onSave(tag)">
                  저장
                </button>
                <button type="button" @click="cancelEdit">취소</button>
              </span>
            </template>

            <template v-else>
              <TagChip :tag="tag" />
              <span v-if="!tag.color" class="auto-mark" title="이름으로 색이 정해집니다.">자동</span>

              <span class="actions">
                <button type="button" @click="startEdit(tag)">수정</button>
                <button type="button" class="danger" @click="onRemove(tag)">삭제</button>
              </span>
            </template>
          </li>
        </ul>
        <p v-else class="none">등록된 분야가 없습니다.</p>
      </template>
    </section>
  </ModalDialog>
</template>

<style scoped>
.section-head {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.rule {
  flex: 1;
  font-size: 0.78rem;
  color: var(--text-faint);
  line-height: 1.5;
  margin: 0;
}

.add {
  flex: none;
  padding: 0.35rem 0.7rem;
  border: 1px solid var(--border-input);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.78rem;
  cursor: pointer;
}

.add:hover {
  border-color: var(--accent-border);
  color: var(--text-h);
}

.add-form {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.8rem;
  color: var(--text-muted);
}

input[type='text'] {
  padding: 0.4rem 0.55rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
  font-size: 0.85rem;
}

.colors {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
  border: 1px solid var(--border-soft);
  border-radius: 6px;
  padding: 0.5rem;
  margin: 0;
}

.colors legend {
  font-size: 0.8rem;
  color: var(--text-muted);
  padding: 0 0.25rem;
}

.colors.inline {
  border: none;
  padding: 0;
}

/* 라디오 자체는 감추고 라벨을 견본으로 쓴다 — 네이티브 라디오 옆에 색 점을 두면 줄이 두 배가 된다. */
.swatch input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.swatch {
  flex-direction: row;
  align-items: center;
  padding: 0.15rem;
  border: 2px solid transparent;
  border-radius: 999px;
  cursor: pointer;
}

.swatch.picked {
  border-color: var(--accent);
}

.swatch:focus-within {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}

.swatch .dot {
  display: block;
  width: 1.1rem;
  height: 1.1rem;
  border-radius: 50%;
  background: var(--swatch);
}

.swatch.auto {
  padding: 0.05rem 0.45rem;
  border-radius: 999px;
  font-size: 0.72rem;
  color: var(--text-faint);
  background: var(--surface-sunken);
}

.swatch.auto.picked {
  color: var(--text-h);
}

/* 색값은 style.css 에만 있다 — 여기서는 어느 토큰을 쓸지만 고른다. */
.swatch[data-color='blue'] {
  --swatch: var(--tag-blue-fg);
}
.swatch[data-color='teal'] {
  --swatch: var(--tag-teal-fg);
}
.swatch[data-color='green'] {
  --swatch: var(--tag-green-fg);
}
.swatch[data-color='lime'] {
  --swatch: var(--tag-lime-fg);
}
.swatch[data-color='amber'] {
  --swatch: var(--tag-amber-fg);
}
.swatch[data-color='orange'] {
  --swatch: var(--tag-orange-fg);
}
.swatch[data-color='rose'] {
  --swatch: var(--tag-rose-fg);
}
.swatch[data-color='violet'] {
  --swatch: var(--tag-violet-fg);
}

.preview {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem;
  font-size: 0.78rem;
  color: var(--text-faint);
}

.auto-note {
  flex-basis: 100%;
}

.dialog-actions {
  display: flex;
  gap: 0.5rem;
}

button {
  padding: 0.45rem 0.9rem;
  border-radius: 6px;
  border: 1px solid var(--border-input);
  background: var(--surface);
  color: var(--text-muted);
  cursor: pointer;
  font-size: 0.85rem;
}

button.primary {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
}

button.primary:disabled {
  background: var(--disabled-bg);
  border-color: var(--disabled-border);
  color: var(--disabled-fg);
  cursor: not-allowed;
}

.list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.list li {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--border-soft);
  border-radius: 6px;
}

.list li.editing {
  border-color: var(--accent-border);
  background: var(--accent-weak);
  flex-wrap: wrap;
}

.list input[type='text'] {
  padding: 0.25rem 0.4rem;
  font-size: 0.8rem;
}

.auto-mark {
  font-size: 0.7rem;
  color: var(--text-faint);
}

.actions {
  margin-left: auto;
  display: flex;
  gap: 0.35rem;
}

.actions button {
  padding: 0.25rem 0.55rem;
  font-size: 0.75rem;
}

.actions button.danger {
  color: var(--danger);
  border-color: var(--danger-border);
}

.none {
  font-size: 0.85rem;
  color: var(--text-faint);
}
</style>
