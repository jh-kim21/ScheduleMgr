/**
 * CSV building and downloading, shared by the screens that export a table.
 *
 * <p>Built on the client rather than on the server for one reason: the export should be what the
 * user is looking at. The RAID screen filters and sorts locally, so only the client knows which
 * rows and which order are on screen — a server-side CSV would quietly hand over the whole log
 * instead of the filtered view.
 */

/**
 * A field is quoted only when it has to be. Excel and most importers accept either form, and
 * leaving simple values bare keeps the file readable in a text editor.
 */
function escapeField(value: string): string {
  const needsQuotes = /[",\r\n]/.test(value)
  if (!needsQuotes) return value
  return `"${value.replace(/"/g, '""')}"`
}

/** Null and undefined become an empty field rather than the strings "null"/"undefined". */
function toField(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'boolean') return value ? 'Y' : 'N'
  return escapeField(String(value))
}

/**
 * CRLF line endings, because that is what Excel expects and what the CSV spec (RFC 4180) says.
 */
export function toCsv(header: string[], rows: unknown[][]): string {
  return [header, ...rows].map((row) => row.map(toField).join(',')).join('\r\n')
}

/**
 * Excel on Windows reads a CSV in the system codepage unless the file starts with a UTF-8 BOM —
 * without it, Korean text opens as mojibake. Every other tool tolerates the BOM, so it is always
 * written.
 *
 * <p>Written as an escape rather than a literal BOM character: an invisible character in source is
 * one careless edit away from vanishing with no visible diff, and the breakage it causes only
 * shows up when someone opens the file in Excel.
 */
const UTF8_BOM = '\uFEFF'

/** Sanitises a project or screen name for use in a filename. */
export function safeFileName(value: string): string {
  const cleaned = value.trim().replace(/[^\p{L}\p{N}._-]+/gu, '-').replace(/^-+|-+$/g, '')
  return cleaned.length > 0 ? cleaned : 'export'
}

export function csvFileName(projectName: string, screen: string, today = new Date()): string {
  const date = today.toISOString().slice(0, 10)
  return `${safeFileName(projectName)}-${screen}-${date}.csv`
}

/** Exported so the BOM is covered by a test rather than only by inspection. */
export function withBom(csv: string): string {
  return UTF8_BOM + csv
}

/**
 * Hands the file to the browser. A blob URL with a synthetic click is the only way to name a
 * client-generated download; the URL is revoked straight away so the blob is not held for the
 * life of the page.
 */
export function downloadCsv(fileName: string, csv: string) {
  const blob = new Blob([withBom(csv)], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
