<script setup lang="ts">
import ModalDialog from '../../components/ModalDialog.vue'
import { WBS_COLUMNS, type PinLevel, type WbsColumnKey } from './wbsColumns'
import { columnPrefs, resetColumnPrefs, setColumnPrefs } from './wbsColumnPrefs'

/**
 * 열 표시·숨김과 고정 단계를 설정하는 대화상자(지시서 `wbs-tree-columns` 3-3, 2-4).
 *
 * 서버로 나가는 요청이 없으므로 즉시 반영한다 — [저장]/[취소]를 두지 않고 [닫기]만 둔다. CLAUDE.md
 * 화면 규칙의 "성공했을 때만 닫는다"는 해당 없다.
 */
defineEmits<{ close: [] }>()

const NAME_HINT =
  '업무명은 항상 표시됩니다 — 업무명이 없으면 그 행이 무슨 일인지 알 수 없습니다.'

function isChecked(key: WbsColumnKey): boolean {
  return !columnPrefs.value.hidden.includes(key)
}

function onToggle(key: WbsColumnKey, checked: boolean) {
  const hidden = new Set(columnPrefs.value.hidden)
  if (checked) {
    hidden.delete(key)
  } else {
    hidden.add(key)
  }
  setColumnPrefs({ ...columnPrefs.value, hidden: [...hidden] })
}

function onPin(pin: PinLevel) {
  setColumnPrefs({ ...columnPrefs.value, pin })
}
</script>

<template>
  <ModalDialog title="열 설정" @close="$emit('close')">
    <section class="settings">
      <fieldset class="group">
        <legend>표시할 열</legend>
        <ul class="column-list">
          <li v-for="column in WBS_COLUMNS" :key="column.key">
            <label :class="{ disabled: !column.hideable }">
              <input
                type="checkbox"
                :data-column="column.key"
                :checked="isChecked(column.key)"
                :disabled="!column.hideable"
                :title="!column.hideable ? NAME_HINT : undefined"
                @change="onToggle(column.key, ($event.target as HTMLInputElement).checked)"
              />
              {{ column.label }}
            </label>
          </li>
        </ul>
      </fieldset>

      <fieldset class="group">
        <legend>고정</legend>
        <p class="hint">가로로 스크롤해도 왼쪽에 남길 열입니다. 앞에서부터만 고정할 수 있습니다.</p>
        <ul class="pin-list">
          <li>
            <label>
              <input
                type="radio"
                name="wbs-pin"
                value="none"
                :checked="columnPrefs.pin === 'none'"
                @change="onPin('none')"
              />
              없음
            </label>
          </li>
          <li>
            <label>
              <input
                type="radio"
                name="wbs-pin"
                value="code"
                :checked="columnPrefs.pin === 'code'"
                @change="onPin('code')"
              />
              WBS
            </label>
          </li>
          <li>
            <label>
              <input
                type="radio"
                name="wbs-pin"
                value="name"
                :checked="columnPrefs.pin === 'name'"
                @change="onPin('name')"
              />
              WBS + 업무명
            </label>
          </li>
        </ul>
      </fieldset>

      <div class="dialog-actions">
        <button type="button" data-action="reset" @click="resetColumnPrefs">기본값으로</button>
        <button type="button" @click="$emit('close')">닫기</button>
      </div>
    </section>
  </ModalDialog>
</template>

<style scoped>
.settings {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.group {
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  padding: 0.75rem 0.9rem;
}

.group legend {
  padding: 0 0.3rem;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-dim);
}

.hint {
  margin: 0 0 0.5rem;
  font-size: 0.76rem;
  color: var(--text-faint);
}

.column-list,
.pin-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.column-list label,
.pin-list label {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.85rem;
  cursor: pointer;
}

.column-list label.disabled {
  color: var(--text-faint);
  cursor: not-allowed;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

.dialog-actions button {
  padding: 0.45rem 0.9rem;
  border-radius: 6px;
  border: 1px solid var(--border-input);
  background: var(--surface);
  color: var(--text-muted);
  cursor: pointer;
  font: inherit;
  font-size: 0.85rem;
}
</style>
