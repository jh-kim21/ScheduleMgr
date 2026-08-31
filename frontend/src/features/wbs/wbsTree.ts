import type { WbsNode } from '../../api/wbsApi'

/** One rendered row: the node plus the sibling context needed for drop position math. */
export interface WbsRow {
  node: WbsNode
  siblings: WbsNode[]
  index: number
}

/**
 * Flattens the tree into table rows, skipping the children of collapsed nodes.
 * A table can't nest rows, so depth is carried on each row via `node.level`.
 */
export function flattenTree(nodes: WbsNode[], collapsed: Set<number>): WbsRow[] {
  const rows: WbsRow[] = []
  const visit = (siblings: WbsNode[]) => {
    siblings.forEach((node, index) => {
      rows.push({ node, siblings, index })
      if (!collapsed.has(node.id)) {
        visit(node.children)
      }
    })
  }
  visit(nodes)
  return rows
}

/** Where a drop lands relative to the row under the cursor. */
export type DropPlacement = 'before' | 'after' | 'inside'

/**
 * Translates a drop onto `target` into the zero-based `position` the move API expects.
 *
 * The server reorders by removing the dragged item from its sibling list and reinserting it
 * at `position`, so when the item already sits *earlier* among the same siblings, every index
 * after it shifts down by one — hence the adjustment.
 */
export function resolveDropPosition(
  draggedId: number,
  siblings: WbsNode[],
  targetIndex: number,
  placement: Exclude<DropPlacement, 'inside'>,
): number {
  let position = placement === 'before' ? targetIndex : targetIndex + 1
  const currentIndex = siblings.findIndex((node) => node.id === draggedId)
  if (currentIndex !== -1 && currentIndex < position) {
    position -= 1
  }
  return position
}

/** True when `candidate` sits anywhere beneath `node` — such a drop would create a cycle. */
export function containsDescendant(node: WbsNode, candidateId: number): boolean {
  return node.children.some(
    (child) => child.id === candidateId || containsDescendant(child, candidateId),
  )
}
