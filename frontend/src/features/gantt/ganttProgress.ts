/**
 * What the chart draws for a task's progress fill (결함 5).
 *
 * <p>The tooltip already shows `computedProgress` — the common aggregation's figure — but the bar
 * fill used to read the stored `progress` column instead, so a Story-backed Agile task could show
 * "70%" in the tooltip while its bar filled to 20%. Pulled into its own module (rather than staying
 * inline in {@code GanttChart.vue}) so the fallback rule can be unit tested the same way the rest
 * of the chart's date math is, without mounting the component.
 *
 * <p>`computedProgress` is `null` only when the aggregation truly has nothing to measure (산정 전);
 * under `MANUAL` (실행 방식 미지정) it already equals `progress`, so this fallback changes nothing
 * for a project that has not adopted execution modes yet.
 */
export function displayProgress(task: { computedProgress: number | null; progress: number }): number {
  return task.computedProgress ?? task.progress
}
