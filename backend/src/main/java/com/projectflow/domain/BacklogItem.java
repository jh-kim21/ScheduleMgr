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

import java.time.LocalDateTime;

/**
 * A Product Backlog entry — the execution side of a Work Package.
 *
 * <p>As with {@link WbsItem}, the parent and the linked Work Package are plain id columns rather
 * than JPA associations: a project's whole backlog is loaded at once and related in memory, so
 * lazy associations would only add N+1 queries.
 *
 * <p><b>귀속은 상위를 따른다.</b> An entry with a parent always carries the parent's
 * {@code wbsItemId}; {@code BacklogService} enforces that and cascades a re-attach down the
 * subtree. That is what makes "상하위 귀속 불일치" impossible rather than merely validated.
 */
@Entity
@Table(name = "backlog_items")
public class BacklogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** Owning Work Package, or {@code null} for an unlinked draft. */
    @Column(name = "wbs_item_id")
    private Long wbsItemId;

    /** Epic for a Story/Bug, or the Story/Bug for a Task. {@code null} for a top-level entry. */
    @Column(name = "parent_id")
    private Long parentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private BacklogItemType itemType;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BacklogPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BacklogStatus status;

    @Column(name = "assignee_member_id")
    private Long assigneeMemberId;

    @Column(name = "acceptance_criteria", length = 2000)
    private String acceptanceCriteria;

    /** Team's estimate. Never converted into {@link #progressWeight} (설계 §6.1). */
    @Column(name = "story_point")
    private Integer storyPoint;

    /** Share of progress inside the owning Work Package. A different quantity from story points. */
    @Column(name = "progress_weight")
    private Integer progressWeight;

    /** Set when the entry is put aside; keeps its {@link #status} untouched. */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    /**
     * Blocked on the board. Separate from {@link #status} on purpose (Step 4 지시서 6항): a blocked
     * card keeps the column it is in, and folding "차단" into the status would put it in the middle
     * of the To Do → In Progress → Review → Done transitions.
     */
    @Column(nullable = false)
    private boolean blocked;

    @Column(name = "blocked_reason", length = 500)
    private String blockedReason;

    /**
     * When the entry was accepted as done. The status alone cannot tell "Done now" from "was Done
     * and got re-opened", and re-opening has to leave the Sprint's recorded outcome alone.
     */
    @Column(name = "done_at")
    private LocalDateTime doneAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected BacklogItem() {
        // JPA
    }

    public BacklogItem(Long projectId, Long wbsItemId, Long parentId, BacklogItemType itemType,
                        String title, String description, BacklogPriority priority,
                        BacklogStatus status, Long assigneeMemberId, String acceptanceCriteria,
                        Integer storyPoint, Integer progressWeight, int sortOrder) {
        this.projectId = projectId;
        this.wbsItemId = wbsItemId;
        this.parentId = parentId;
        this.itemType = itemType;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.assigneeMemberId = assigneeMemberId;
        this.acceptanceCriteria = acceptanceCriteria;
        this.storyPoint = storyPoint;
        this.progressWeight = progressWeight;
        this.sortOrder = sortOrder;
    }

    /**
     * Restores an entry from an exported file, {@code archivedAt} included. 보관 상태는 저장된
     * 상태이므로 파일과 함께 넘어와야 한다 — 받은 쪽에서 접어둔 항목이 되살아나면 안 된다.
     */
    public BacklogItem(Long projectId, Long wbsItemId, Long parentId, BacklogItemType itemType,
                        String title, String description, BacklogPriority priority,
                        BacklogStatus status, Long assigneeMemberId, String acceptanceCriteria,
                        Integer storyPoint, Integer progressWeight, int sortOrder,
                        LocalDateTime archivedAt) {
        this(projectId, wbsItemId, parentId, itemType, title, description, priority, status,
                assigneeMemberId, acceptanceCriteria, storyPoint, progressWeight, sortOrder);
        this.archivedAt = archivedAt;
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

    public void update(Long wbsItemId, Long parentId, BacklogItemType itemType, String title,
                        String description, BacklogPriority priority, BacklogStatus status,
                        Long assigneeMemberId, String acceptanceCriteria,
                        Integer storyPoint, Integer progressWeight) {
        this.wbsItemId = wbsItemId;
        this.parentId = parentId;
        this.itemType = itemType;
        this.title = title;
        this.description = description;
        this.priority = priority;
        applyStatus(status);
        this.assigneeMemberId = assigneeMemberId;
        this.acceptanceCriteria = acceptanceCriteria;
        this.storyPoint = storyPoint;
        this.progressWeight = progressWeight;
    }

    /**
     * Board move. {@code doneAt} follows the status: reaching Done stamps it, leaving Done clears
     * it — the Sprint outcome recorded at close is deliberately untouched, because "지금 상태"와
     * "그 Sprint에서의 결과"는 다른 사실이다 (Step 4 지시서 10항).
     */
    public void changeStatus(BacklogStatus status) {
        applyStatus(status);
    }

    /**
     * Only an actual transition moves {@code doneAt}. Re-saving an entry that is already Done must
     * not push its completion time forward, and the edit form saves the whole entry every time.
     */
    private void applyStatus(BacklogStatus next) {
        if (this.status == next) {
            return;
        }
        this.status = next;
        this.doneAt = next == BacklogStatus.DONE ? LocalDateTime.now() : null;
    }

    public void block(String reason) {
        this.blocked = true;
        this.blockedReason = reason;
    }

    public void unblock() {
        this.blocked = false;
        this.blockedReason = null;
    }

    public boolean blocked() {
        return blocked;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public LocalDateTime getDoneAt() {
        return doneAt;
    }

    /** Restores execution state from an exported file. */
    public void restoreExecutionState(boolean blocked, String blockedReason, LocalDateTime doneAt) {
        this.blocked = blocked;
        this.blockedReason = blockedReason;
        this.doneAt = doneAt;
    }

    /** Re-attaches to another Work Package (or detaches, with {@code null}). */
    public void relinkTo(Long wbsItemId) {
        this.wbsItemId = wbsItemId;
    }

    /** Archiving keeps {@link #status}: what state the work was left in stays part of the record. */
    public void archive() {
        if (archivedAt == null) {
            this.archivedAt = LocalDateTime.now();
        }
    }

    public void restore() {
        this.archivedAt = null;
    }

    public boolean archived() {
        return archivedAt != null;
    }

    public boolean aggregated() {
        return itemType.aggregated();
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getWbsItemId() {
        return wbsItemId;
    }

    public Long getParentId() {
        return parentId;
    }

    public BacklogItemType getItemType() {
        return itemType;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BacklogPriority getPriority() {
        return priority;
    }

    public BacklogStatus getStatus() {
        return status;
    }

    public Long getAssigneeMemberId() {
        return assigneeMemberId;
    }

    public String getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public Integer getStoryPoint() {
        return storyPoint;
    }

    public Integer getProgressWeight() {
        return progressWeight;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
