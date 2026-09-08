package com.projectflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * One WBS entry as it stood when a {@link Baseline} was approved.
 *
 * <p>Everything is copied, including {@code code} and {@code name}. The code is derived from tree
 * position, so once the tree moves it cannot be reconstructed — and a baseline you cannot read is
 * not a baseline. {@code wbsItemId} is kept as a plain column with no foreign key so the record
 * survives the entry being deleted, which is exactly the case where the comparison matters most.
 */
@Entity
@Table(name = "baseline_items")
public class BaselineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "baseline_id", nullable = false)
    private Long baselineId;

    @Column(name = "wbs_item_id", nullable = false)
    private Long wbsItemId;

    @Column(length = 100)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 20)
    private WbsNodeType nodeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", length = 20)
    private ExecutionMode executionMode;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    private Integer weight;

    @Column(name = "completion_criteria", length = 2000)
    private String completionCriteria;

    protected BaselineItem() {
        // JPA
    }

    public BaselineItem(Long baselineId, Long wbsItemId, String code, String name,
                         WbsNodeType nodeType, ExecutionMode executionMode,
                         LocalDate startDate, LocalDate endDate, Integer weight,
                         String completionCriteria) {
        this.baselineId = baselineId;
        this.wbsItemId = wbsItemId;
        this.code = code;
        this.name = name;
        this.nodeType = nodeType;
        this.executionMode = executionMode;
        this.startDate = startDate;
        this.endDate = endDate;
        this.weight = weight;
        this.completionCriteria = completionCriteria;
    }

    public Long getId() {
        return id;
    }

    public Long getBaselineId() {
        return baselineId;
    }

    public Long getWbsItemId() {
        return wbsItemId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public WbsNodeType getNodeType() {
        return nodeType;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Integer getWeight() {
        return weight;
    }

    public String getCompletionCriteria() {
        return completionCriteria;
    }
}
