package com.projectflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single WBS entry. The parent link is stored as a plain id rather than a JPA
 * association: WBS operations always load the whole project's items and build the
 * tree in memory ({@link WbsTreeAssembler}), so lazy associations would only add
 * N+1 queries for no benefit.
 *
 * <p>WBS code, summary dates and summary progress are <em>not</em> stored — they are
 * derived from tree position and children on every read, so they can never go stale
 * after a move or reorder.
 */
@Entity
@Table(name = "wbs_items")
public class WbsItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private int progress;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Stored rather than derived from child presence — see {@link WbsNodeType}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 20)
    private WbsNodeType nodeType;

    /**
     * How this Work Package is executed, or {@code null} for 미지정 — which is what every row
     * migrated from before Step 2 holds, and means "keep using the manually entered progress".
     *
     * <p>A {@code SUMMARY} entry never <em>uses</em> this value, but it can still hold one: when a
     * Work Package is converted to a summary the mode is kept rather than erased, so converting
     * back restores it. The tree response surfaces it so the screen can flag it for cleanup.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", length = 20)
    private ExecutionMode executionMode;

    /**
     * Share of progress among siblings (설계 §11.2). A different quantity from a Backlog entry's
     * {@code progressWeight} and from a Story Point; the three are never summed together.
     *
     * <p>{@code null} means "not entered", which is not 0. Zero would say this branch contributes
     * nothing to progress; null says nobody has decided yet, and {@link ProgressCalculator} treats
     * the two differently.
     */
    private Integer weight;

    /**
     * Hybrid's α as a percentage (설계 §6.3): the Agile element's share, the remainder being the
     * approval element. Required for {@code HYBRID} — without it the Work Package is 산정 전,
     * because a ratio nobody agreed is not a ratio.
     */
    @Column(name = "agile_ratio")
    private Integer agileRatio;

    /**
     * Formal acceptance, kept apart from progress (설계 §6.5). Execution can read 100% while
     * acceptance is outstanding, and calling that 완료 would overstate it. {@code null} means no
     * acceptance step applies.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "acceptance_status", length = 20)
    private AcceptanceStatus acceptanceStatus;

    /**
     * When work actually started and finished (설계 §7). Distinct from the planned dates above and
     * from the approved baseline: the Gantt has to show all three side by side, and none of them
     * can be derived from the others.
     *
     * <p>Never filled in from a Sprint's dates. A Sprint ending is not a Work Package's deliverable
     * being done, and one Sprint can span several Work Packages (지시서 6-A).
     */
    @Column(name = "actual_start_date")
    private LocalDate actualStartDate;

    @Column(name = "actual_end_date")
    private LocalDate actualEndDate;

    /**
     * When it now looks like it will finish. Separate from the planned end so a slip can be stated
     * without rewriting the plan — and that is what the baseline-exceeded warning compares.
     */
    @Column(name = "forecast_end_date")
    private LocalDate forecastEndDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WbsItem() {
        // JPA
    }

    /** A plain entry with no execution mode yet, i.e. what every entry looked like before Step 2. */
    public WbsItem(Long projectId, Long parentId, String name, String description,
                    LocalDate startDate, LocalDate endDate, int progress, int sortOrder) {
        this(projectId, parentId, name, description, startDate, endDate, progress, sortOrder,
                WbsNodeType.WORK_PACKAGE, null);
    }

    public WbsItem(Long projectId, Long parentId, String name, String description,
                    LocalDate startDate, LocalDate endDate, int progress, int sortOrder,
                    WbsNodeType nodeType, ExecutionMode executionMode) {
        this.projectId = projectId;
        this.parentId = parentId;
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.progress = progress;
        this.sortOrder = sortOrder;
        this.nodeType = nodeType;
        this.executionMode = executionMode;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates the item's own attributes; tree position is changed via {@link #moveTo}.
     *
     * <p>{@code nodeType} and {@code executionMode} are validated against the tree by
     * {@code WbsService} before this is called — a summary cannot be given a mode, and an entry
     * with children cannot become a Work Package.
     */
    public void update(String name, String description, LocalDate startDate, LocalDate endDate,
                        int progress, WbsNodeType nodeType, ExecutionMode executionMode,
                        Integer weight, Integer agileRatio, AcceptanceStatus acceptanceStatus,
                        LocalDate actualStartDate, LocalDate actualEndDate,
                        LocalDate forecastEndDate) {
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.progress = progress;
        this.nodeType = nodeType;
        this.executionMode = executionMode;
        this.weight = weight;
        this.agileRatio = agileRatio;
        this.acceptanceStatus = acceptanceStatus;
        this.actualStartDate = actualStartDate;
        this.actualEndDate = actualEndDate;
        this.forecastEndDate = forecastEndDate;
    }

    /** Restores the aggregation basis from an exported file. */
    public void restoreProgressBasis(Integer weight, Integer agileRatio,
                                       AcceptanceStatus acceptanceStatus) {
        this.weight = weight;
        this.agileRatio = agileRatio;
        this.acceptanceStatus = acceptanceStatus;
    }

    /**
     * Slides both dates by {@code days}, preserving duration. Used by schedule recalculation
     * (요구사항 6.6); a null date stays null.
     */
    public void shiftBy(long days) {
        if (startDate != null) {
            this.startDate = startDate.plusDays(days);
        }
        if (endDate != null) {
            this.endDate = endDate.plusDays(days);
        }
    }

    public void moveTo(Long parentId, int sortOrder) {
        this.parentId = parentId;
        this.sortOrder = sortOrder;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getProgress() {
        return progress;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public WbsNodeType getNodeType() {
        return nodeType;
    }

    /** {@code null} means 미지정. */
    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public boolean workPackage() {
        return nodeType == WbsNodeType.WORK_PACKAGE;
    }

    public Integer getWeight() {
        return weight;
    }

    public Integer getAgileRatio() {
        return agileRatio;
    }

    public AcceptanceStatus getAcceptanceStatus() {
        return acceptanceStatus;
    }

    public LocalDate getActualStartDate() {
        return actualStartDate;
    }

    public LocalDate getActualEndDate() {
        return actualEndDate;
    }

    public LocalDate getForecastEndDate() {
        return forecastEndDate;
    }

    /** Restores the actual/forecast dates from an exported file. */
    public void restoreActualDates(LocalDate actualStartDate, LocalDate actualEndDate,
                                     LocalDate forecastEndDate) {
        this.actualStartDate = actualStartDate;
        this.actualEndDate = actualEndDate;
        this.forecastEndDate = forecastEndDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
