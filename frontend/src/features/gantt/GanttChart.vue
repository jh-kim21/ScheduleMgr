<script setup lang="ts">
import { computed, ref } from 'vue'
import type { GanttData, GanttTask } from '../../api/ganttApi'
import { DELAY_LABELS, delayBadge, delayDescription, type DelayStatus } from '../../shared/delay'
import {
  barGeometry,
  createScale,
  dateAt,
  daysBetween,
  dayTicks,
  monthBands,
  ROW_HEIGHT,
  showsDayNumbers,
  weekdayLabel,
  xFor,
  type BarGeometry,
} from './ganttScale'

const props = defineProps<{
  data: GanttData
  /** Emphasise the chain that drives the project end date, dimming everything else. */
  highlightCriticalPath?: boolean
}>()

const MONTH_BAND_HEIGHT = 20
const DAY_ROW_HEIGHT = 22
const AXIS_HEIGHT = MONTH_BAND_HEIGHT + DAY_ROW_HEIGHT

/** UNSCHEDULED is left out: those rows have no bar to colour. */
const LEGEND_STATUSES: DelayStatus[] = ['COMPLETED', 'ON_TRACK', 'AT_RISK', 'DELAYED', 'NOT_STARTED']

interface Row {
  task: GanttTask
  index: number
  bar: BarGeometry | null
}

const scale = computed(() => createScale(props.data.chartStart, props.data.chartEnd))
const ticks = computed(() => (scale.value ? dayTicks(scale.value) : []))
const bands = computed(() => (scale.value ? monthBands(scale.value) : []))
const dayNumbersVisible = computed(() => (scale.value ? showsDayNumbers(scale.value) : false))

const rows = computed<Row[]>(() =>
  props.data.tasks.map((task, index) => ({
    task,
    index,
    bar: scale.value ? barGeometry(scale.value, task.startDate, task.endDate) : null,
  })),
)

const rowsById = computed(() => new Map(rows.value.map((row) => [row.task.id, row])))
const bodyHeight = computed(() => Math.max(rows.value.length * ROW_HEIGHT, ROW_HEIGHT))

/**
 * Vertical marker at the server's reference date, when it falls inside the plotted range. It
 * deliberately uses the server's date rather than the browser's, so the line always sits where
 * the delay verdicts were measured from.
 */
const todayX = computed(() => {
  const current = scale.value
  const today = props.data.referenceDate
  if (!current || !today) return null
  if (today < current.rangeStart || today > current.rangeEnd) return null
  return xFor(current, today)
})

const centerY = (index: number) => index * ROW_HEIGHT + ROW_HEIGHT / 2

const criticalCount = computed(() => props.data.tasks.filter((task) => task.criticalPath).length)

/** Nothing to emphasise until dependencies exist, so the option goes quiet rather than dimming all. */
const criticalActive = computed(() => props.highlightCriticalPath === true && criticalCount.value > 0)

/** Reads as "여유 3일" / "여유 없음", or nothing at all for a task on no chain. */
function floatLabel(task: GanttTask) {
  if (task.floatDays === null) return '선후행 관계 없음'
  if (task.floatDays > 0) return `여유 ${task.floatDays}일`
  if (task.floatDays === 0) return '여유 없음 (임계 경로)'
  return `제약보다 ${-task.floatDays}일 늦음 (임계 경로)`
}

/**
 * Elbow connector from the predecessor's right edge to the successor's left edge. When the
 * successor starts too early the path runs backwards, which is exactly the shape that makes a
 * violated dependency obvious.
 */
const arrows = computed(() => {
  if (!scale.value) return []
  const stub = 8
  return props.data.dependencies.flatMap((dependency) => {
    const predecessor = rowsById.value.get(dependency.predecessorId)
    const successor = rowsById.value.get(dependency.successorId)
    if (!predecessor?.bar || !successor?.bar) return []

    const x1 = predecessor.bar.x + predecessor.bar.width
    const y1 = centerY(predecessor.index)
    const x2 = successor.bar.x
    const y2 = centerY(successor.index)
    const turn = Math.max(x1 + stub, x2 - stub)

    return [
      {
        id: dependency.id,
        violating: successor.task.scheduleViolation,
        critical: dependency.criticalPath,
        path: `M ${x1} ${y1} H ${turn} V ${y2} H ${x2 - 5}`,
      },
    ]
  })
})

/** Tick showing where a task violating its dependency would have to start. */
const earliestMarkers = computed(() => {
  const current = scale.value
  if (!current) return []
  return rows.value.flatMap((row) =>
    row.task.scheduleViolation && row.task.earliestStart
      ? [{ id: row.task.id, x: xFor(current, row.task.earliestStart), y: row.index * ROW_HEIGHT }]
      : [],
  )
})

/**
 * Tick inside an at-risk bar marking the progress the baseline expected by today — the gap
 * between it and the filled portion is the shortfall.
 */
const baselineMarkers = computed(() =>
  rows.value.flatMap((row) =>
    row.task.delayStatus === 'AT_RISK' && row.bar
      ? [
          {
            id: row.task.id,
            x: row.bar.x + (row.bar.width * row.task.expectedProgress) / 100,
            y: row.index * ROW_HEIGHT + ROW_HEIGHT / 2,
          },
        ]
      : [],
  ),
)

/**
 * What the pointer is over. The date is read from the x position, so it is available anywhere in
 * the plot — not just on a bar — which is the point: the chart draws day columns but only labels
 * some of them, and at narrow day widths it labels none.
 */
interface HoverState {
  date: string
  clientX: number
  clientY: number
  /** The bar under the pointer, or null when hovering empty timeline. */
  task: GanttTask | null
}

const hover = ref<HoverState | null>(null)

/**
 * One listener on the SVG covers both the date readout and the bar details. Per-bar listeners
 * would miss the gaps between bars, which is where "what date is this?" gets asked most.
 *
 * <p>Coordinates come from the SVG's own bounding box rather than `offsetX`: on SVG children the
 * offset is measured against the child, so it would jump as the pointer crossed a bar.
 */
function onTimelineMove(event: MouseEvent) {
  const current = scale.value
  if (!current) return

  const bounds = (event.currentTarget as SVGSVGElement).getBoundingClientRect()
  const x = event.clientX - bounds.left
  const date = dateAt(current, x)
  if (!date) {
    hover.value = null
    return
  }

  const row = rows.value[Math.floor((event.clientY - bounds.top) / ROW_HEIGHT)]
  const onBar =
    row?.bar !== null && row !== undefined && x >= row.bar!.x && x <= row.bar!.x + row.bar!.width

  hover.value = {
    date,
    clientX: event.clientX,
    clientY: event.clientY,
    task: onBar ? row.task : null,
  }
}

const hoverX = computed(() =>
  scale.value && hover.value ? xFor(scale.value, hover.value.date) : null,
)

/** True when the pointer sits on the day the delay verdicts were measured from. */
const hoverIsReferenceDate = computed(
  () => hover.value !== null && hover.value.date === props.data.referenceDate,
)

/** Inclusive, so a task starting and ending the same day lasts one day. */
function durationDays(task: GanttTask) {
  if (!task.startDate || !task.endDate) return 0
  return daysBetween(task.startDate, task.endDate) + 1
}

/**
 * Flip the tooltip to the other side of the cursor near the viewport edges. The width is the
 * CSS max-width below; an estimate is enough because the flip only needs to avoid clipping.
 */
const TOOLTIP_WIDTH = 260
const TOOLTIP_HEIGHT = 150

const tooltipStyle = computed(() => {
  if (!hover.value) return {}
  const flipX = hover.value.clientX + TOOLTIP_WIDTH + 24 > window.innerWidth
  const flipY = hover.value.clientY + TOOLTIP_HEIGHT + 24 > window.innerHeight
  return {
    left: `${hover.value.clientX + (flipX ? -TOOLTIP_WIDTH - 14 : 14)}px`,
    top: `${hover.value.clientY + (flipY ? -TOOLTIP_HEIGHT - 14 : 16)}px`,
  }
})

const chartVars = computed(() => ({
  '--row-height': `${ROW_HEIGHT}px`,
  '--axis-height': `${AXIS_HEIGHT}px`,
}))
</script>

<template>
  <div v-if="data.tasks.length === 0" class="empty">
    WBS 항목이 없습니다. WBS 화면에서 업무를 먼저 등록해 주세요.
  </div>

  <div v-else-if="!scale" class="empty">
    일정이 입력된 항목이 없습니다. WBS 화면에서 시작일과 종료일을 입력하면 차트가 표시됩니다.
  </div>

  <div v-else class="gantt" :class="{ 'focus-critical': criticalActive }" :style="chartVars">
    <div class="task-pane">
      <div class="axis-spacer">업무</div>
      <div
        v-for="row in rows"
        :key="row.task.id"
        class="task-row"
        :class="{ critical: criticalActive && row.task.criticalPath }"
      >
        <span class="code">{{ row.task.code }}</span>
        <span
          class="name"
          :class="{ summary: row.task.summary }"
          :style="{ paddingLeft: `${(row.task.level - 1) * 0.75}rem` }"
          :title="row.task.name"
        >{{ row.task.name }}</span>
        <span
          class="status"
          :data-status="row.task.delayStatus"
          :title="delayDescription(row.task)"
        >
          {{ delayBadge(row.task) }}
        </span>
      </div>
    </div>

    <div class="timeline-pane">
      <div :style="{ width: `${scale.width}px` }">
        <div class="axis">
          <div class="months">
            <div
              v-for="band in bands"
              :key="band.label"
              class="month"
              :style="{ left: `${band.x}px`, width: `${band.width}px` }"
            >{{ band.label }}</div>
          </div>
          <div class="days">
            <div
              v-for="tick in ticks"
              :key="tick.date"
              class="day"
              :class="{ weekend: tick.weekend }"
              :style="{ left: `${tick.x}px`, width: `${scale.dayWidth}px` }"
            >{{ dayNumbersVisible ? tick.dayOfMonth : '' }}</div>
          </div>
        </div>

        <svg
          class="body"
          :width="scale.width"
          :height="bodyHeight"
          role="img"
          aria-label="간트 차트 일정 막대"
          @mousemove="onTimelineMove"
          @mouseleave="hover = null"
        >
          <defs>
            <marker id="gantt-arrow" viewBox="0 0 8 8" refX="7" refY="4"
                    markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 1 L 7 4 L 0 7 z" class="arrow-head" />
            </marker>
            <!-- 화살촉은 선 색을 물려받지 못하므로 임계 경로용을 따로 둔다. -->
            <marker id="gantt-arrow-critical" viewBox="0 0 8 8" refX="7" refY="4"
                    markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 1 L 7 4 L 0 7 z" class="arrow-head critical" />
            </marker>
          </defs>

          <!-- 주말 음영 -->
          <rect
            v-for="tick in ticks.filter((t) => t.weekend)"
            :key="`weekend-${tick.date}`"
            class="weekend-band"
            :x="tick.x"
            y="0"
            :width="scale.dayWidth"
            :height="bodyHeight"
          />

          <!-- 커서가 가리키는 날짜 칸 -->
          <rect
            v-if="hoverX !== null"
            class="hover-band"
            :x="hoverX"
            y="0"
            :width="scale.dayWidth"
            :height="bodyHeight"
          />

          <!-- 행 구분 -->
          <line
            v-for="row in rows"
            :key="`rule-${row.task.id}`"
            class="row-rule"
            x1="0"
            :x2="scale.width"
            :y1="(row.index + 1) * ROW_HEIGHT"
            :y2="(row.index + 1) * ROW_HEIGHT"
          />

          <line
            v-if="todayX !== null"
            class="today"
            :x1="todayX"
            :x2="todayX"
            y1="0"
            :y2="bodyHeight"
          />

          <!-- 일정 막대. 채움 색은 지연 상태를, 점선 외곽선은 선후행 위반을 나타낸다. -->
          <g v-for="row in rows" :key="`bar-${row.task.id}`">
            <template v-if="row.bar">
              <rect
                class="bar"
                :class="{
                  summary: row.task.summary,
                  violating: row.task.scheduleViolation,
                  critical: row.task.criticalPath,
                }"
                :data-status="row.task.delayStatus"
                :x="row.bar.x"
                :y="row.index * ROW_HEIGHT + (row.task.summary ? ROW_HEIGHT / 2 - 4 : ROW_HEIGHT / 2 - 8)"
                :width="row.bar.width"
                :height="row.task.summary ? 8 : 16"
                :rx="row.task.summary ? 2 : 3"
                role="img"
                :aria-label="`${row.task.code} ${row.task.name} · ${row.task.startDate} ~ ${row.task.endDate} · ${DELAY_LABELS[row.task.delayStatus]} · ${delayDescription(row.task)} · ${floatLabel(row.task)}`"
              />
              <rect
                v-if="row.task.progress > 0"
                class="bar-progress"
                :class="{ critical: row.task.criticalPath }"
                :data-status="row.task.delayStatus"
                :x="row.bar.x"
                :y="row.index * ROW_HEIGHT + (row.task.summary ? ROW_HEIGHT / 2 - 4 : ROW_HEIGHT / 2 - 8)"
                :width="(row.bar.width * row.task.progress) / 100"
                :height="row.task.summary ? 8 : 16"
                :rx="row.task.summary ? 2 : 3"
              />
            </template>
          </g>

          <!-- 지연 위험 항목의 기대 진행률 위치 -->
          <line
            v-for="marker in baselineMarkers"
            :key="`baseline-${marker.id}`"
            class="baseline"
            :x1="marker.x"
            :x2="marker.x"
            :y1="marker.y - 9"
            :y2="marker.y + 9"
          />

          <!-- 위반 항목의 가장 이른 시작일 -->
          <line
            v-for="marker in earliestMarkers"
            :key="`earliest-${marker.id}`"
            class="earliest"
            :x1="marker.x"
            :x2="marker.x"
            :y1="marker.y + 4"
            :y2="marker.y + ROW_HEIGHT - 4"
          />

          <!-- 선후행 관계 -->
          <path
            v-for="arrow in arrows"
            :key="`arrow-${arrow.id}`"
            :class="['arrow', { violating: arrow.violating, critical: arrow.critical }]"
            :d="arrow.path"
            :marker-end="
              criticalActive && arrow.critical ? 'url(#gantt-arrow-critical)' : 'url(#gantt-arrow)'
            "
          />
        </svg>
      </div>
    </div>
  </div>

  <div v-if="hover" class="tooltip" :style="tooltipStyle" role="tooltip">
    <div class="tooltip-date">
      {{ hover.date }} ({{ weekdayLabel(hover.date) }})
      <span v-if="hoverIsReferenceDate" class="tooltip-today">기준일</span>
    </div>

    <template v-if="hover.task">
      <div class="tooltip-name">{{ hover.task.code }} {{ hover.task.name }}</div>
      <dl class="tooltip-rows">
        <dt>기간</dt>
        <dd>
          {{ hover.task.startDate }} ~ {{ hover.task.endDate }} ({{ durationDays(hover.task) }}일)
        </dd>

        <dt>진행률</dt>
        <dd>{{ hover.task.progress }}% · {{ DELAY_LABELS[hover.task.delayStatus] }}</dd>

        <dt>여유</dt>
        <dd>{{ floatLabel(hover.task) }}</dd>

        <template v-if="hover.task.scheduleViolation && hover.task.earliestStart">
          <dt>가장 이른 시작</dt>
          <dd class="tooltip-violation">{{ hover.task.earliestStart }}</dd>
        </template>
      </dl>
    </template>
  </div>

  <div v-if="scale" class="legend">
    <span v-for="status in LEGEND_STATUSES" :key="status" class="legend-item">
      <span class="swatch" :data-status="status"></span>{{ DELAY_LABELS[status] }}
    </span>
    <span class="legend-item"><span class="swatch violation-swatch"></span>선후행 위반</span>
    <span class="legend-item"><span class="swatch baseline-swatch"></span>기대 진행률</span>
    <span v-if="criticalActive" class="legend-item">
      <span class="swatch critical-swatch"></span>임계 경로
    </span>
    <span v-if="data.referenceDate" class="reference">기준일 {{ data.referenceDate }}</span>
  </div>
</template>

<style scoped>
.gantt {
  display: flex;
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
}

.task-pane {
  flex: none;
  width: 19rem;
  border-right: 1px solid var(--border);
  background: var(--surface-alt);
}

.axis-spacer {
  height: var(--axis-height);
  display: flex;
  align-items: flex-end;
  padding: 0 0.6rem 0.35rem;
  box-sizing: border-box;
  font-size: 0.75rem;
  color: var(--text-dim);
  font-weight: 600;
  border-bottom: 1px solid var(--border);
}

.task-row {
  height: var(--row-height);
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0 0.6rem;
  box-sizing: border-box;
  border-bottom: 1px solid var(--border-softer);
  font-size: 0.8rem;
}

.task-row .code {
  flex: none;
  color: var(--text-faint);
  font-variant-numeric: tabular-nums;
  font-size: 0.72rem;
  min-width: 2.4rem;
}

.task-row .name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-row .name.summary {
  font-weight: 600;
}

.task-row .status {
  margin-left: auto;
  flex: none;
  font-size: 0.68rem;
  padding: 0.1rem 0.4rem;
  border-radius: 999px;
  white-space: nowrap;
  color: var(--status-fg);
  background: var(--status-not-started);
}

.status[data-status='COMPLETED'] {
  background: var(--status-completed);
}

.status[data-status='ON_TRACK'] {
  background: var(--status-on-track);
}

.status[data-status='AT_RISK'] {
  background: var(--status-at-risk);
}

.status[data-status='DELAYED'] {
  background: var(--status-delayed);
}

.status[data-status='UNSCHEDULED'] {
  background: var(--status-unscheduled);
}

.timeline-pane {
  flex: 1;
  overflow-x: auto;
}

.axis {
  height: var(--axis-height);
  box-sizing: border-box;
  border-bottom: 1px solid var(--border);
  position: relative;
}

.months {
  position: relative;
  height: 20px;
}

.month {
  position: absolute;
  top: 0;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.72rem;
  color: var(--text-muted);
  border-right: 1px solid var(--border-soft);
  box-sizing: border-box;
}

.days {
  position: relative;
  height: 22px;
}

.day {
  position: absolute;
  top: 0;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.62rem;
  color: var(--text-faint);
  box-sizing: border-box;
}

.day.weekend {
  background: var(--surface-sunken);
}

.body {
  display: block;
}

.weekend-band {
  fill: var(--surface-weekend);
}

/* 커서가 가리키는 날짜 칸. 툴팁의 날짜가 차트의 어느 칸인지 눈으로 잇는 역할이다. */
.hover-band {
  fill: var(--accent);
  fill-opacity: 0.1;
  pointer-events: none;
}

/*
 * 위치는 커서를 따라 fixed로 잡는다. 타임라인이 가로 스크롤되는 컨테이너 안에 있어서,
 * 컨테이너 기준 절대 좌표로 두면 스크롤할 때마다 어긋나거나 잘린다.
 */
.tooltip {
  position: fixed;
  z-index: 50;
  max-width: 260px;
  padding: 0.5rem 0.65rem;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface);
  color: var(--text);
  box-shadow: 0 4px 14px rgb(0 0 0 / 18%);
  font-size: 0.78rem;
  line-height: 1.45;
  pointer-events: none;
}

.tooltip-date {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-weight: 600;
  color: var(--text-h);
  font-variant-numeric: tabular-nums;
}

.tooltip-today {
  font-size: 0.68rem;
  font-weight: 500;
  color: var(--status-fg);
  background: var(--today-line);
  border-radius: 999px;
  padding: 0.05rem 0.4rem;
}

.tooltip-name {
  margin-top: 0.35rem;
  padding-top: 0.35rem;
  border-top: 1px solid var(--border-soft);
  font-weight: 600;
}

.tooltip-rows {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.1rem 0.5rem;
  margin: 0.25rem 0 0;
}

.tooltip-rows dt {
  color: var(--text-faint);
  white-space: nowrap;
}

.tooltip-rows dd {
  margin: 0;
  font-variant-numeric: tabular-nums;
}

.tooltip-violation {
  color: var(--violation-text);
}

.row-rule {
  stroke: var(--border-softer);
  stroke-width: 1;
}

.today {
  stroke: var(--today-line);
  stroke-width: 1;
  stroke-dasharray: 3 3;
}

/*
 * 임계 경로 강조. 색을 새로 쓰지 않고 대비로 구분한다 — 채움은 지연 상태가, 점선 외곽선은
 * 선후행 위반이 이미 쓰고 있어서, 색 계열을 하나 더 얹으면 서로 헷갈린다.
 * 강조 대상: 임계 막대의 실선 외곽선, 임계 화살표의 굵은 선, 업무 행의 왼쪽 표시.
 * 나머지는 반투명으로 물러난다.
 */
/*
 * Summary 막대는 흐리게 하지 않는다. 일정이 하위에서 계산되는 구조 표시이고, 선후행 관계가
 * 걸려 있지 않으면 임계 판정 대상도 아니다 — 임계인 자식 바로 위에서 흐려지면 잘못 읽힌다.
 */
.focus-critical .bar:not(.critical):not(.summary),
.focus-critical .bar-progress:not(.critical),
.focus-critical .arrow:not(.critical) {
  opacity: 0.4;
}

.focus-critical .bar.critical {
  stroke: var(--critical);
  stroke-width: 1.5;
}

/* 위반은 점선 외곽선으로 이미 말하고 있으니 그 표시를 임계 실선으로 덮지 않는다. */
.focus-critical .bar.critical.violating {
  stroke: var(--violation);
}

.focus-critical .arrow.critical {
  stroke: var(--critical);
  stroke-width: 2.2;
}

.arrow-head.critical {
  fill: var(--critical);
}

.task-row.critical {
  box-shadow: inset 3px 0 0 var(--critical);
}

.task-row.critical .code {
  color: var(--text-h);
  font-weight: 600;
}

.critical-swatch {
  background: var(--status-not-started);
  border: 1.5px solid var(--critical);
}

/* 막대는 상태 색의 옅은 배경, 진행률 채움은 같은 색의 진한 톤을 쓴다. */
.bar {
  fill: var(--status-not-started);
  fill-opacity: 0.32;
}

.bar[data-status='COMPLETED'] {
  fill: var(--status-completed);
}

.bar[data-status='ON_TRACK'] {
  fill: var(--status-on-track);
}

.bar[data-status='AT_RISK'] {
  fill: var(--status-at-risk);
}

.bar[data-status='DELAYED'] {
  fill: var(--status-delayed);
}

.bar.summary {
  fill-opacity: 0.5;
}

.bar.violating {
  stroke: var(--violation);
  stroke-width: 1.5;
  stroke-dasharray: 3 2;
  fill-opacity: 0.32;
}

.bar-progress {
  fill: var(--status-not-started);
  pointer-events: none;
}

.bar-progress[data-status='COMPLETED'] {
  fill: var(--status-completed);
}

.bar-progress[data-status='ON_TRACK'] {
  fill: var(--status-on-track);
}

.bar-progress[data-status='AT_RISK'] {
  fill: var(--status-at-risk);
}

.bar-progress[data-status='DELAYED'] {
  fill: var(--status-delayed);
}

.earliest {
  stroke: var(--violation);
  stroke-width: 2;
}

.baseline {
  stroke: var(--baseline);
  stroke-width: 1.5;
}

.arrow {
  fill: none;
  stroke: var(--arrow);
  stroke-width: 1.2;
}

.arrow.violating {
  stroke: var(--violation);
}

.arrow-head {
  fill: var(--arrow);
}

.empty {
  padding: 2.5rem 1rem;
  text-align: center;
  color: var(--text-faint);
  border: 1px dashed var(--border-dashed);
  border-radius: 8px;
}

.legend {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.85rem;
  margin-top: 0.6rem;
  font-size: 0.75rem;
  color: var(--text-faint);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.swatch {
  width: 0.7rem;
  height: 0.7rem;
  border-radius: 2px;
  background: var(--status-not-started);
}

.swatch[data-status='COMPLETED'] {
  background: var(--status-completed);
}

.swatch[data-status='ON_TRACK'] {
  background: var(--status-on-track);
}

.swatch[data-status='AT_RISK'] {
  background: var(--status-at-risk);
}

.swatch[data-status='DELAYED'] {
  background: var(--status-delayed);
}

.violation-swatch {
  background: transparent;
  border: 1.5px dashed var(--violation);
}

.baseline-swatch {
  width: 0;
  height: 0.85rem;
  border-radius: 0;
  background: transparent;
  border-left: 2px solid var(--baseline);
}

.reference {
  margin-left: auto;
  color: var(--text-faint);
}
</style>
