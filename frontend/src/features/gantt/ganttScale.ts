/**
 * Date ↔ pixel arithmetic for the Gantt timeline.
 *
 * Dates are handled as `YYYY-MM-DD` strings converted to UTC midnight, never as local-time
 * `Date` objects — local parsing would shift bars by a day across DST boundaries and in
 * timezones behind UTC.
 */

const MS_PER_DAY = 86_400_000

/** Blank days kept on each side so bars at the edges are not flush against the frame. */
export const PADDING_DAYS = 3

/** Row height in px, shared by the task pane and the SVG so the two stay aligned. */
export const ROW_HEIGHT = 32

export interface GanttScale {
  rangeStart: string
  rangeEnd: string
  totalDays: number
  dayWidth: number
  width: number
}

export interface DayTick {
  date: string
  dayOfMonth: number
  weekend: boolean
  x: number
}

export interface MonthBand {
  label: string
  x: number
  width: number
}

export interface BarGeometry {
  x: number
  width: number
}

function toUtcMillis(iso: string): number {
  const [year, month, day] = iso.split('-').map(Number)
  return Date.UTC(year, month - 1, day)
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

export function daysBetween(from: string, to: string): number {
  return Math.round((toUtcMillis(to) - toUtcMillis(from)) / MS_PER_DAY)
}

export function addDays(iso: string, days: number): string {
  const date = new Date(toUtcMillis(iso) + days * MS_PER_DAY)
  return `${date.getUTCFullYear()}-${pad2(date.getUTCMonth() + 1)}-${pad2(date.getUTCDate())}`
}

/**
 * Day columns get narrower as the project gets longer, so a multi-year plan stays scrollable
 * instead of becoming kilometres wide.
 */
export function dayWidthFor(totalDays: number): number {
  if (totalDays <= 60) return 26
  if (totalDays <= 120) return 16
  return 8
}

/** Day numbers are only legible once columns are wide enough. */
export function showsDayNumbers(scale: GanttScale): boolean {
  return scale.dayWidth >= 16
}

/** Returns null when the project has no dated task, i.e. there is nothing to plot. */
export function createScale(chartStart: string | null, chartEnd: string | null): GanttScale | null {
  if (!chartStart || !chartEnd) return null

  const rangeStart = addDays(chartStart, -PADDING_DAYS)
  const rangeEnd = addDays(chartEnd, PADDING_DAYS)
  const totalDays = daysBetween(rangeStart, rangeEnd) + 1
  const dayWidth = dayWidthFor(totalDays)

  return { rangeStart, rangeEnd, totalDays, dayWidth, width: totalDays * dayWidth }
}

export function xFor(scale: GanttScale, date: string): number {
  return daysBetween(scale.rangeStart, date) * scale.dayWidth
}

/**
 * Which day column a pixel offset falls in — the inverse of {@link xFor}, for reading a date off
 * the chart under the cursor. Returns null outside the plotted range so a stray pointer position
 * cannot report a date the chart never drew.
 */
export function dateAt(scale: GanttScale, x: number): string | null {
  const offset = Math.floor(x / scale.dayWidth)
  if (offset < 0 || offset >= scale.totalDays) return null
  return addDays(scale.rangeStart, offset)
}

const WEEKDAY_LABELS = ['일', '월', '화', '수', '목', '금', '토']

/** Korean single-letter weekday, so a tooltip can say why a column is shaded. */
export function weekdayLabel(iso: string): string {
  return WEEKDAY_LABELS[new Date(toUtcMillis(iso)).getUTCDay()]
}

/**
 * Bar geometry for an inclusive date range: a task ending on its start date still occupies one
 * full day column.
 */
export function barGeometry(
  scale: GanttScale,
  startDate: string | null,
  endDate: string | null,
): BarGeometry | null {
  if (!startDate || !endDate) return null
  const span = daysBetween(startDate, endDate) + 1
  return { x: xFor(scale, startDate), width: Math.max(span, 1) * scale.dayWidth }
}

export function dayTicks(scale: GanttScale): DayTick[] {
  const ticks: DayTick[] = []
  for (let offset = 0; offset < scale.totalDays; offset += 1) {
    const date = addDays(scale.rangeStart, offset)
    const weekday = new Date(toUtcMillis(date)).getUTCDay()
    ticks.push({
      date,
      dayOfMonth: Number(date.slice(8, 10)),
      weekend: weekday === 0 || weekday === 6,
      x: offset * scale.dayWidth,
    })
  }
  return ticks
}

/**
 * One band per calendar month covered by the range, for the axis header. The year is included
 * only when the range crosses one, so the common single-year case stays uncluttered.
 */
export function monthBands(scale: GanttScale): MonthBand[] {
  const spansYears = scale.rangeStart.slice(0, 4) !== scale.rangeEnd.slice(0, 4)
  const bands: MonthBand[] = []

  for (let offset = 0; offset < scale.totalDays; offset += 1) {
    const date = addDays(scale.rangeStart, offset)
    const month = Number(date.slice(5, 7))
    const label = spansYears ? `${date.slice(0, 4)}.${pad2(month)}` : `${month}월`
    const last = bands.at(-1)
    if (last && last.label === label) {
      last.width += scale.dayWidth
    } else {
      bands.push({ label, x: offset * scale.dayWidth, width: scale.dayWidth })
    }
  }
  return bands
}
