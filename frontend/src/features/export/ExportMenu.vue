<script setup lang="ts">
import { csvFileName, downloadCsv, toCsv } from '../../shared/csv'
import type { CsvTable } from '../../shared/exportRows'

const props = defineProps<{
  projectId: number
  projectName: string
  /** Which screen this is, used in the CSV filename. Omit to offer JSON only. */
  screen?: string
  /**
   * Builds the table at click time rather than as a prop, so the CSV always reflects the current
   * filter and sort instead of whatever was on screen when this component rendered.
   */
  csv?: () => CsvTable
  /** Appended below the table, e.g. the RACI letter legend. */
  note?: string
}>()

function onCsv() {
  if (!props.csv || !props.screen) return
  const table = props.csv()
  let body = toCsv(table.header, table.rows)
  if (props.note) {
    // 빈 줄 하나를 두고 뒤에 붙인다 — 표의 열 구조를 흐트러뜨리지 않는다.
    body += `\r\n\r\n${toCsv([props.note], [])}`
  }
  downloadCsv(csvFileName(props.projectName, props.screen, new Date()), body)
}
</script>

<template>
  <span class="export">
    <button
      v-if="csv && screen"
      type="button"
      title="화면에 보이는 표를 CSV로 내려받습니다 (Excel에서 열립니다)"
      @click="onCsv"
    >CSV</button>

    <!--
      평범한 링크다. 서버가 Content-Disposition: attachment 로 내려주므로 fetch·blob 코드가
      필요 없고, 개발 프록시와 설치본에서 똑같이 동작한다.
    -->
    <a
      :href="`/api/projects/${projectId}/export`"
      download
      title="프로젝트 전체(구성원·WBS·선후행·RACI·RAID)를 JSON 한 파일로 내려받습니다"
    >전체 JSON</a>
  </span>
</template>

<style scoped>
.export {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

button,
a {
  padding: 0.3rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  background: var(--surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.78rem;
  cursor: pointer;
  text-decoration: none;
  white-space: nowrap;
}

button:hover,
a:hover {
  border-color: var(--accent-border);
  color: var(--text-h);
}
</style>
