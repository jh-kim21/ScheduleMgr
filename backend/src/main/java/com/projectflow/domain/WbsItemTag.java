package com.projectflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * One tag on one WBS entry.
 *
 * <p>The pair <em>is</em> the identity — there is nothing else to record about a link, and both
 * sides cascade on delete, so a surrogate key would only add a column nobody reads. Unlike
 * {@code raid_links} this table carries no {@code created_at}: a tag is a label, not a piece of
 * history, and nothing ever asks when it was attached.
 *
 * <p>No {@code project_id} either. Every read here starts from the project's WBS entries, which
 * are already loaded in one go, so links are fetched by those ids
 * ({@link WbsItemTagRepository#findByWbsItemIdIn}).
 */
@Entity
@Table(name = "wbs_item_tags")
@IdClass(WbsItemTag.Key.class)
public class WbsItemTag {

    @Id
    @Column(name = "wbs_item_id", nullable = false)
    private Long wbsItemId;

    @Id
    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    protected WbsItemTag() {
        // JPA
    }

    public WbsItemTag(Long wbsItemId, Long tagId) {
        this.wbsItemId = wbsItemId;
        this.tagId = tagId;
    }

    public Long getWbsItemId() {
        return wbsItemId;
    }

    public Long getTagId() {
        return tagId;
    }

    /** Composite key holder; JPA needs a class with value equality for {@code @IdClass}. */
    public static class Key implements Serializable {

        private Long wbsItemId;
        private Long tagId;

        public Key() {
        }

        public Key(Long wbsItemId, Long tagId) {
            this.wbsItemId = wbsItemId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(wbsItemId, key.wbsItemId) && Objects.equals(tagId, key.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(wbsItemId, tagId);
        }
    }
}
