import type { BacklogItem } from '../api/backlogApi'
import type { RaciMatrix } from '../api/raciApi'
import type { RaidItem } from '../api/raidApi'
import type { WbsNode } from '../api/wbsApi'
import {
  BACKLOG_PRIORITY_LABELS,
  BACKLOG_STATUS_LABELS,
  BACKLOG_TYPE_LABELS,
} from './backlog'
import { DELAY_LABELS } from './delay'
import { PROGRESS_BASIS_LABELS, progressText } from './progress'
import {
  NODE_TYPE_LABELS,
  executionModeLabel,
  executionModeSummaryText,
} from './executionMode'
import { RACI_LETTERS, RACI_ORDER, sortRoles } from './raci'
import { RAID_LEVEL_LABELS, RAID_STATUS_LABELS, RAID_TYPE_LABELS, raidLinkLabel } from './raid'

/**
 * Turns each screen's data into CSV header + rows.
 *
 * <p>Labels rather than enum names ("지연" not "DELAYED"): a CSV is for a person to read in Excel,
 * and the machine-readable form of the same data is the JSON export.
 *
 * <p>Judged values (delay status, exposure, overdue) *are* included here, unlike in the JSON
 * export. The difference is intent: the JSON is meant to be re-imported, where a stale verdict
 * would be a lie, while the CSV is a snapshot of what the screen showed on that day.
 */

export interface CsvTable {
  header: string[]
  rows: unknown[][]
}

/** Flattens the tree in display order so the CSV reads like the screen. */
export function wbsCsv(nodes: WbsNode[], referenceDate: string | null): CsvTable {
  const rows: unknown[][] = []

  const walk = (list: WbsNode[]) => {
    for (const node of list) {
      rows.push([
        node.code,
        node.level,
        node.name,
        node.description,
        node.startDate,
        node.endDate,
        progressText(node.computedProgress),
        node.progressBasis ? PROGRESS_BASIS_LABELS[node.progressBasis] : '',
        node.weight ?? '',
        NODE_TYPE_LABELS[node.nodeType],
        // 상위 항목은 자기 실행 방식을 쓰지 않으므로 하위 요약을 적는다 — 화면과 같은 값.
        node.children.length > 0
          ? executionModeSummaryText(node.executionModeSummary)
          : executionModeLabel(node.executionMode),
        DELAY_LABELS[node.delayStatus],
        node.delayDays > 0 ? node.delayDays : '',
        node.progressGap > 0 ? node.progressGap : '',
      ])
      walk(node.children)
    }
  }
  walk(nodes)

  return {
    header: [
      'WBS',
      '레벨',
      '업무명',
      '설명',
      '시작일',
      '종료일',
      '진척',
      '집계 기준',
      '가중치',
      '구분',
      '실행 방식',
      // 판정 기준일을 머리글에 박아 둔다 — 며칠 뒤 이 파일을 열었을 때 무엇 기준인지 알아야 한다.
      `지연 상태${referenceDate ? ` (기준일 ${referenceDate})` : ''}`,
      '지연(일)',
      '부족 진행률(%p)',
    ],
    rows,
  }
}

/**
 * One row per task, one column per member — the matrix as it appears on screen. Long form
 * (task, member, role) would be easier to generate but far harder to read in Excel, which is the
 * whole point of the CSV.
 */
export function raciCsv(matrix: RaciMatrix): CsvTable {
  const cellIndex = new Map(
    matrix.cells.map((cell) => [`${cell.wbsItemId}:${cell.memberId}`, cell.roles]),
  )

  const rows = matrix.tasks.map((task) => [
    task.code,
    task.name,
    task.summary ? 'Summary' : 'Leaf',
    ...matrix.members.map((member) => {
      const roles = cellIndex.get(`${task.id}:${member.id}`)
      return roles ? sortRoles(roles).map((role) => RACI_LETTERS[role]).join('') : ''
    }),
  ])

  return {
    header: ['WBS', '업무명', '구분', ...matrix.members.map((member) => member.name)],
    rows,
  }
}

/** The letters legend, appended so a recipient knows what R/A/C/I mean. */
export function raciLegend(): string {
  return RACI_ORDER.map((role) => `${RACI_LETTERS[role]}=${role}`).join(', ')
}

/**
 * The backlog as the screen shows it, including the judged link warnings — the CSV is a snapshot
 * for a person, so "미연결" belongs in it even though it never goes into the JSON export.
 */
export function backlogCsv(items: BacklogItem[]): CsvTable {
  return {
    header: [
      '유형',
      '제목',
      '설명',
      '상위 항목',
      '귀속 Work Package',
      '실행 방식',
      '우선순위',
      '상태',
      '담당',
      'Story Point',
      '진척 가중치',
      '집계 대상',
      '수용 조건',
      '보관',
      '차단',
      '확인 필요',
    ],
    rows: items.map((item) => [
      BACKLOG_TYPE_LABELS[item.itemType],
      item.title,
      item.description,
      item.parentTitle,
      item.wbsCode ? `${item.wbsCode} ${item.wbsName}` : '',
      executionModeLabel(item.wbsExecutionMode),
      BACKLOG_PRIORITY_LABELS[item.priority],
      BACKLOG_STATUS_LABELS[item.status],
      item.assigneeName,
      item.storyPoint ?? '',
      item.progressWeight ?? '',
      item.aggregated ? 'O' : '',
      item.acceptanceCriteria,
      item.archived ? 'O' : '',
      item.blocked ? (item.blockedReason ?? 'O') : '',
      backlogWarning(item),
    ]),
  }
}

function backlogWarning(item: BacklogItem): string {
  if (item.danglingLink) return '연결 끊김'
  if (item.linkedToSummary) return 'Summary 연결'
  if (item.unlinked) return '미연결'
  if (item.requiresExecutionModeChange) return '실행 방식 확인'
  return ''
}

export function raidCsv(items: RaidItem[], referenceDate: string | null): CsvTable {
  return {
    header: [
      '종류',
      '제목',
      '설명',
      '상태',
      '확률',
      '영향',
      '노출도',
      '소유자',
      '연결 대상',
      '기한',
      `기한 초과${referenceDate ? ` (기준일 ${referenceDate})` : ''}`,
      '초과(일)',
      '대응/확인 방안',
    ],
    rows: items.map((item) => [
      RAID_TYPE_LABELS[item.type],
      item.title,
      item.description,
      RAID_STATUS_LABELS[item.status],
      item.probability ? RAID_LEVEL_LABELS[item.probability] : '',
      item.impact ? RAID_LEVEL_LABELS[item.impact] : '',
      item.exposure ?? '',
      item.ownerName,
      item.links.map(raidLinkLabel).join(' / '),
      item.dueDate,
      item.overdue,
      item.overdueDays > 0 ? item.overdueDays : '',
      item.response,
    ]),
  }
}
