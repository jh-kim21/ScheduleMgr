import type { WbsMoveInput, WbsNode } from '../../api/wbsApi'
import { findParent, resolveDropPosition } from './wbsTree'
import { NO_SELECTION_HINT, READONLY_HINT } from './wbsToolbar'

/**
 * Keyboard/non-drag alternative to the tree's drag-and-drop reordering and re-parenting
 * (WCAG 2.5.7 Dragging Movements, 2.1.1 Keyboard — HTML5 native DnD does not fire on touch either).
 * Judgement lives here as pure functions and `WbsTree.vue` only turns the result into button state
 * and an `emit('move', …)` — the same split as `wbsToolbar.ts` (하위 추가·수정·삭제 대신 이번엔
 * 위로·아래로·들여쓰기·내어쓰기).
 *
 * All four actions reuse the existing `move` API (`WbsMoveInput { parentId, position }`) — the
 * server already expresses every reorder/re-parent with just those two fields, so there is no new
 * endpoint to add.
 */

export interface MoveAvailability {
  up: boolean
  down: boolean
  indent: boolean
  outdent: boolean
}

export interface MoveState extends MoveAvailability {
  upHint: string | null
  downHint: string | null
  indentHint: string | null
  outdentHint: string | null
}

export const FIRST_ROW_HINT = '이미 첫 행입니다.'
export const LAST_ROW_HINT = '이미 마지막 행입니다.'
export const NO_PARENT_HINT = '이미 최상위 항목입니다.'
export const NO_PREVIOUS_SIBLING_HINT = '바로 위에 형제 항목이 없습니다.'
export const WORK_PACKAGE_INDENT_HINT =
  'Work Package에는 하위 항목을 둘 수 없습니다. 바로 위 형제의 구분을 Summary로 바꾸면 들여쓸 수 있습니다.'

interface SiblingContext {
  siblings: WbsNode[]
  index: number
  parent: WbsNode | null
}

/** The sibling list this node lives in (the tree itself at the top level) and its index there. */
function siblingContext(tree: WbsNode[], nodeId: number): SiblingContext {
  const parent = findParent(tree, nodeId)
  const siblings = parent ? parent.children : tree
  return { siblings, index: siblings.findIndex((node) => node.id === nodeId), parent }
}

/**
 * Whether each of the four actions is available, with no title text. `up`/`down` only ask whether
 * this is the first/last sibling; `indent` needs a previous sibling that is not a Work Package
 * (Work Package에는 하위를 둘 수 없다 — the same rule the server enforces with a 400); `outdent`
 * needs a parent to step out of.
 */
export function moveAvailability(tree: WbsNode[], nodeId: number): MoveAvailability {
  const { siblings, index, parent } = siblingContext(tree, nodeId)
  if (index === -1) return { up: false, down: false, indent: false, outdent: false }
  const previousSibling = index > 0 ? siblings[index - 1] : null
  return {
    up: index > 0,
    down: index < siblings.length - 1,
    indent: previousSibling !== null && previousSibling.nodeType !== 'WORK_PACKAGE',
    outdent: parent !== null,
  }
}

function uniformHint(hint: string): MoveState {
  return {
    up: false,
    down: false,
    indent: false,
    outdent: false,
    upHint: hint,
    downHint: hint,
    indentHint: hint,
    outdentHint: hint,
  }
}

/**
 * For the toolbar: availability plus a `title` for each disabled button — `readOnly` and "no
 * selection" are handled here so `WbsTree.vue` does not re-check the same two conditions four
 * times (같은 이유로 나뉜 `wbsToolbar.ts`의 `toolbarState`/`actionHint`).
 */
export function moveState(tree: WbsNode[], nodeId: number | null, readOnly: boolean): MoveState {
  if (readOnly) return uniformHint(READONLY_HINT)
  if (nodeId === null) return uniformHint(NO_SELECTION_HINT)

  const { siblings, index, parent } = siblingContext(tree, nodeId)
  if (index === -1) return uniformHint(NO_SELECTION_HINT)

  const previousSibling = index > 0 ? siblings[index - 1] : null
  const up = index > 0
  const down = index < siblings.length - 1
  const indent = previousSibling !== null && previousSibling.nodeType !== 'WORK_PACKAGE'
  const outdent = parent !== null

  return {
    up,
    down,
    indent,
    outdent,
    upHint: up ? null : FIRST_ROW_HINT,
    downHint: down ? null : LAST_ROW_HINT,
    indentHint: indent
      ? null
      : previousSibling === null
        ? NO_PREVIOUS_SIBLING_HINT
        : WORK_PACKAGE_INDENT_HINT,
    outdentHint: outdent ? null : NO_PARENT_HINT,
  }
}

/**
 * One step up among siblings. `null` when already first — callers check `moveAvailability`/
 * `moveState` first, this is the defensive fallback.
 */
export function moveUpInput(tree: WbsNode[], nodeId: number): WbsMoveInput | null {
  const { siblings, index, parent } = siblingContext(tree, nodeId)
  if (index <= 0) return null
  return { parentId: parent?.id ?? null, position: resolveDropPosition(nodeId, siblings, index - 1, 'before') }
}

/** One step down among siblings. */
export function moveDownInput(tree: WbsNode[], nodeId: number): WbsMoveInput | null {
  const { siblings, index, parent } = siblingContext(tree, nodeId)
  if (index === -1 || index >= siblings.length - 1) return null
  return { parentId: parent?.id ?? null, position: resolveDropPosition(nodeId, siblings, index + 1, 'after') }
}

/** Indent — becomes the last child of the previous sibling. `null` if that sibling is a Work Package. */
export function indentInput(tree: WbsNode[], nodeId: number): WbsMoveInput | null {
  const { siblings, index } = siblingContext(tree, nodeId)
  if (index <= 0) return null
  const newParent = siblings[index - 1]
  if (newParent.nodeType === 'WORK_PACKAGE') return null
  return {
    parentId: newParent.id,
    position: resolveDropPosition(nodeId, newParent.children, newParent.children.length, 'before'),
  }
}

/** Outdent — becomes the parent's next sibling (steps up one level). `null` if already top-level. */
export function outdentInput(tree: WbsNode[], nodeId: number): WbsMoveInput | null {
  const parent = findParent(tree, nodeId)
  if (parent === null) return null
  const grandparent = findParent(tree, parent.id)
  const grandSiblings = grandparent ? grandparent.children : tree
  const parentIndex = grandSiblings.findIndex((node) => node.id === parent.id)
  return {
    parentId: grandparent?.id ?? null,
    position: resolveDropPosition(nodeId, grandSiblings, parentIndex, 'after'),
  }
}
