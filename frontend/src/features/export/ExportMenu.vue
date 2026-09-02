<script setup lang="ts">
import { ref } from 'vue'
import { ApiError } from '../../api/http'
import type { Project } from '../../api/projectApi'
import { raciApi } from '../../api/raciApi'
import { raidApi } from '../../api/raidApi'
import { wbsApi } from '../../api/wbsApi'
import { csvFileName, downloadCsv, toCsv } from '../../shared/csv'
import { raciCsv, raciLegend, raidCsv, wbsCsv, type CsvTable } from '../../shared/exportRows'

/**
 * Export controls for one project.
 *
 * <p>Deliberately one control in one place. Export is a per-project act, so having a button on
 * every screen meant four copies of "전체 JSON" that all did the same thing, and it hid the fact
 * that the unit being exported is the project rather than the screen.
 *
 * <p>A select rather than a row of links: four formats as four buttons crowds a table row, and a
 * popover is a lot of machinery for a list of four items.
 */
const props = defineProps<{
  project: Project
}>()

type Format = 'json' | 'wbs' | 'raci' | 'raid'

const choice = ref<'' | Format>('')
const busy = ref(false)
const error = ref<string | null>(null)

/**
 * The CSVs are built from a fresh fetch rather than from whatever a screen happens to hold. That
 * is the trade for moving this off the screens: the file is the project's whole WBS/RACI/RAID, not
 * the filtered view someone was looking at.
 */
async function tableFor(format: Exclude<Format, 'json'>): Promise<{ table: CsvTable; note?: string }> {
  const id = props.project.id
  if (format === 'wbs') {
    const tree = await wbsApi.tree(id)
    return { table: wbsCsv(tree.nodes, tree.referenceDate) }
  }
  if (format === 'raci') {
    const matrix = await raciApi.matrix(id)
    return { table: raciCsv(matrix), note: `RACI: ${raciLegend()}` }
  }
  const log = await raidApi.log(id)
  return { table: raidCsv(log.items, log.referenceDate) }
}

async function onChange() {
  const format = choice.value
  choice.value = ''
  if (format === '') return

  error.value = null

  if (format === 'json') {
    // 서버가 Content-Disposition: attachment 로 내려주므로 링크를 따라가면 끝난다.
    window.location.href = `/api/projects/${props.project.id}/export`
    return
  }

  busy.value = true
  try {
    const { table, note } = await tableFor(format)
    let body = toCsv(table.header, table.rows)
    if (note) {
      // 빈 줄 하나를 두고 붙인다 — 표의 열 구조를 흐트러뜨리지 않는다.
      body += `\r\n\r\n${toCsv([note], [])}`
    }
    downloadCsv(csvFileName(props.project.name, format, new Date()), body)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '내보내지 못했습니다.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <span class="export">
    <select
      v-model="choice"
      :disabled="busy"
      :title="`${project.name} 내보내기`"
      :aria-label="`${project.name} 내보내기`"
      @change="onChange"
    >
      <option value="">{{ busy ? '내보내는 중...' : '내보내기' }}</option>
      <option value="json">프로젝트 전체 (JSON)</option>
      <option value="wbs">WBS (CSV)</option>
      <option value="raci">RACI (CSV)</option>
      <option value="raid">RAID (CSV)</option>
    </select>
    <span v-if="error" class="error" :title="error">!</span>
  </span>
</template>

<style scoped>
.export {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

select {
  padding: 0.25rem 0.4rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  background: var(--surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.75rem;
}

select:disabled {
  background: var(--disabled-bg);
  border-color: var(--disabled-border);
  color: var(--disabled-fg);
}

.error {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.05rem;
  height: 1.05rem;
  border-radius: 999px;
  background: var(--danger);
  color: var(--status-fg);
  font-size: 0.7rem;
  font-weight: 700;
  cursor: help;
}
</style>
