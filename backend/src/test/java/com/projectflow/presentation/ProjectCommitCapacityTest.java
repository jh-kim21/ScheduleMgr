package com.projectflow.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The 1GB-per-project capacity gate (지시서 4.4), exercised with a much smaller override —
 * "1GB를 채울 수는 없다" is literally true in a test.
 *
 * <p>What this deliberately does <em>not</em> do is compute how many commits are needed to reach
 * 80% from the JSON payload size in bytes. That size depends on exactly how much a trivial test
 * project's WBS/RACI/RAID/Backlog/Sprint/Progress/Dashboard payloads serialize to, which is an
 * implementation detail none of the three concurrent implementers has fixed yet. Hard-coding a
 * byte count here would make this test either pass vacuously (limit far too generous) or fail on
 * the first commit (limit far too small) depending on choices this test has no business knowing
 * about. Instead it commits in a loop against a small-but-not-tiny limit and watches for the
 * transition itself: some commit is the first to report {@code warning: true} while still
 * succeeding, and some later commit is the first to be rejected with 409. That is the actual
 * behaviour 지시서 4.4 describes, independent of the exact byte count involved.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:project-commit-capacity-test;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                // 작지만 극단적으로 작지는 않은 한도. 트리비얼한 프로젝트의 커밋 하나가 이 값보다
                // 훨씬 크면(예: 수백 KB) 첫 커밋부터 곧바로 막혀 경고 구간을 관찰할 수 없다 —
                // 그런 경우는 아래 루프의 가드(MAX_ATTEMPTS)가 실패로 드러내고, 그 자체가
                // "한 프로젝트의 빈 커밋 하나가 이렇게 크다"는 보고할 만한 사실이 된다.
                "project-flow.commit.max-bytes-per-project=200000"
        })
@AutoConfigureMockMvc
@ActiveProfiles("desktop")
@DisplayName("커밋 용량 게이트 (프로젝트별 한도, 설정으로 축소해 검증)")
class ProjectCommitCapacityTest {

    /** Generous enough that a few-KB-per-commit project reaches 100% well before this many tries. */
    private static final int MAX_ATTEMPTS = 300;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private long createProject(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andReturn();
        return readTree(result).get("id").asLong();
    }

    private JsonNode readTree(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    /** Digs out a boolean {@code warning} flag regardless of whether it sits at the root or nested
     * under a {@code capacity} object — the exact envelope shape ({@code capacity: {...}} vs a
     * flattened response) is not pinned down by the contract message, only the field names inside
     * it are. */
    private Boolean findWarning(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.has("warning")) {
            return node.get("warning").asBoolean();
        }
        if (node.has("capacity")) {
            return findWarning(node.get("capacity"));
        }
        return null;
    }

    @Test
    @DisplayName("커밋을 반복하면 어느 시점부터 warning:true(>=80%)가 뜨고, 그 뒤 어느 시점에 409로 막힌다")
    void warnsAtEightyPercentThenBlocksOverTheLimit() throws Exception {
        long projectId = createProject("용량 게이트 테스트");

        boolean sawWarningWhileStillSucceeding = false;
        boolean sawBlock = false;
        JsonNode blockedBody = null;
        int successfulCommits = 0;

        for (int i = 0; i < MAX_ATTEMPTS && !sawBlock; i++) {
            MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/commits")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"committedBy\":\"tester\",\"message\":\"capacity probe " + i + "\"}"))
                    .andReturn();
            int status = result.getResponse().getStatus();
            JsonNode body = readTree(result);

            if (status == 409) {
                sawBlock = true;
                blockedBody = body;
                break;
            }

            assertThat(status)
                    .as("409이 아니면 성공(2xx)이어야 한다 — 그 사이의 상태 코드는 계약에 없다. 응답: " + body)
                    .isBetween(200, 299);
            successfulCommits++;
            Boolean warning = findWarning(body);
            if (Boolean.TRUE.equals(warning)) {
                sawWarningWhileStillSucceeding = true;
            }
        }

        assertThat(successfulCommits)
                .as("최소 한 번은 성공해야 관찰이 의미가 있다 — 첫 커밋부터 막히면 한도(200000 bytes)가 "
                        + "트리비얼한 프로젝트 커밋 하나보다도 작다는 뜻이고, 그러면 이 테스트는 경고 구간을 "
                        + "볼 수 없다 (버그 리포트 대상)")
                .isGreaterThan(0);
        assertThat(sawWarningWhileStillSucceeding)
                .as("80% 이상 사용 시 경고 플래그가 응답에 실려야 한다 (지시서 4.4). "
                        + MAX_ATTEMPTS + "번 시도 안에 한 번도 안 떴다면 경고 로직이 없거나 필드 경로가 다르다")
                .isTrue();
        assertThat(sawBlock)
                .as(MAX_ATTEMPTS + "번 안에 100% 초과로 막히는 커밋이 있어야 한다")
                .isTrue();

        assertThat(blockedBody.has("message"))
                .as("409 본문에 message가 있어야 한다 (지시서 확정 계약: { message, capacity, commits: [] })")
                .isTrue();
        JsonNode commits = blockedBody.get("commits");
        assertThat(commits)
                .as("409 본문에 기존 커밋 목록이 동봉되어야 한다 — 화면이 삭제 대화상자를 그 자리에서 그릴 수 있어야 하기 때문이다")
                .isNotNull();
        assertThat(commits.isArray()).isTrue();
        assertThat(commits.size())
                .as("이미 성공한 커밋이 " + successfulCommits + "건 있으므로 목록도 비어 있으면 안 된다")
                .isEqualTo(successfulCommits);
        assertThat(findWarning(blockedBody))
                .as("100% 초과 상태이므로 capacity.warning도 true여야 한다 (80% 이상의 부분집합)")
                .isTrue();
    }
}
