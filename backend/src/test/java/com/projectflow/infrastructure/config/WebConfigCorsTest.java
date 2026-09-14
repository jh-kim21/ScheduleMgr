package com.projectflow.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * desktop 프로필의 CORS 허용 범위를 고정한다.
 *
 * <p>{@link WebConfig}는 지금까지 테스트가 하나도 없었다. 이 설정은 틀려도 조용히 틀린다 — 너무
 * 좁으면 GET은 통과하고 쓰기만 403이라 "화면은 뜨는데 저장만 안 되는" 형태로 나타나고(원인 추적이
 * 어렵다), 너무 넓으면 사용자가 악성 웹사이트를 여는 것만으로 그 사이트가
 * {@code http://localhost:8081/api/...}에 쓰기 요청을 보낼 수 있는데 아무 증상도 없다가 데이터가
 * 사라지는 식으로 드러난다. 그래서 허용되는 오리진뿐 아니라 거부되는 오리진도 함께 고정한다
 * (CLAUDE.md "허용되는 것만큼 거부되는 것이 중요한 설정").
 *
 * <p>패턴 문자열 자체는 복사하지 않는다 — 원본은 {@code application-desktop.yml}이고, 여기서는
 * {@code @ActiveProfiles("desktop")}로 그 파일을 그대로 로드해 실제 <em>동작</em>(어떤 오리진이
 * preflight를 통과하는가)만 고정한다. 패턴 문자열이 바뀌어도 이 테스트는 그대로 두고, 대신
 * 여기 적힌 허용/거부 결과가 계속 성립하는지만 본다.
 *
 * <p>{@code OPTIONS} preflight로 검증하는 이유: 브라우저가 GET이 아닌 요청(POST/PUT/DELETE) 앞에
 * 실제로 보내는 것이 preflight이고, CORS 설정이 틀렸을 때 막히는 것도 바로 이 요청이다(GET에는
 * {@code Origin} 헤더가 없어 CORS 필터를 그냥 통과한다). {@code @WebMvcTest} 슬라이스가 아니라
 * {@code @SpringBootTest}를 쓴 이유는 {@link WebConfig}가 {@code @WebMvcTest}가 기본으로 감지하지
 * 않는 평범한 {@code @Configuration} 빈이라, 실제 기동과 같은 방식으로 프로필 프로퍼티가 읽혀
 * {@code addCorsMappings}에 반영되는 것까지 확인하려면 전체 컨텍스트가 필요하기 때문이다.
 *
 * <p>{@code spring.datasource.url}을 인메모리 H2로 덮어써 실제 사용자 데이터
 * ({@code ~/.project-flow/data}에 있는 파일 기반 H2)를 절대 건드리지 않는다. {@code @SpringBootTest}의
 * {@code properties}는 {@code application-desktop.yml}보다 우선순위가 높아 값을 그대로 덮는다 —
 * CORS 패턴은 그 프로필 파일에서 그대로 읽히지만 데이터소스만 여기서 갈아 끼우는 셈이다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:webconfig-cors-test;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        })
@AutoConfigureMockMvc
@ActiveProfiles("desktop")
class WebConfigCorsTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0} 는 허용된다")
    @ValueSource(strings = {
            "http://localhost:5173",
            "http://localhost:5151",
            "http://127.0.0.1:5174",
            "http://192.168.60.70:5151",
            "http://192.168.100.186:5173",
            "http://192.168.0.5:80",
            "http://10.1.2.3:8080"
    })
    @DisplayName("루프백·사설 대역(192.168.*.*, 10.*.*.*) 오리진은 포트에 관계없이 허용된다")
    void allowsLoopbackAndPrivateNetworkOrigins(String origin) throws Exception {
        // Vite는 5173이 점유되면 다른 포트로 뜨고, 다른 기기의 LAN 주소는 DHCP로 바뀐다. 포트나
        // 정확한 주소를 하나로 고정하면 그 순간부터 그 화면의 쓰기 동작이 403이 되므로, 여기서
        // 통과해야 그런 상황에서도 저장이 계속 동작한다는 것이 보장된다.
        mockMvc.perform(preflight(origin))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
    }

    @Test
    @DisplayName("공인 IP는 거부된다 — 허용되면 인터넷의 아무 사이트나 사용자의 localhost API를 두드릴 수 있다")
    void rejectsPublicIpOrigin() throws Exception {
        assertRejected("http://8.8.8.8:5151");
    }

    @Test
    @DisplayName(
            "외부 도메인은 거부된다 — 허용되면 사용자가 악성 웹사이트를 여는 것만으로 그 사이트의 스크립트가 "
                    + "http://localhost:8081/api/... 에 DELETE 같은 쓰기 요청을 보낼 수 있다")
    void rejectsExternalDomainOrigin() throws Exception {
        assertRejected("http://evil.example.com");
    }

    @Test
    @DisplayName(
            "스킴이 다르면 거부된다 — 오리진은 (스킴, 호스트, 포트) 세 가지로 이루어지므로 호스트·포트가 같아도 "
                    + "https:// 는 http:// 패턴과 같은 오리진이 아니다")
    void rejectsSchemeMismatch() throws Exception {
        assertRejected("https://192.168.60.70:5151");
    }

    @Test
    @DisplayName(
            "172.16.0.0/12 대역은 거부된다 — 설정 주석대로 정확히 덮으려면 패턴 16줄이 필요해 가독성을 "
                    + "해치고 실사용도 드물다는 판단으로 일부러 뺐다. 이 대역이 허용으로 바뀌면 그 판단이 "
                    + "조용히 뒤집힌 것이다")
    void rejectsDockerBridgeRangeOrigin() throws Exception {
        assertRejected("http://172.16.0.9:3000");
    }

    private void assertRejected(String origin) throws Exception {
        mockMvc.perform(preflight(origin))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    /**
     * 쓰기 요청(POST) 앞에 브라우저가 보내는 것과 같은 형태의 preflight. 대상은
     * {@code POST /api/projects}이지만 CORS 매핑이 {@code /api/**} 전체에 걸리므로 어느
     * 엔드포인트를 골라도 결과는 같다 — 실제 화면이 저장할 때 겪는 것과 같은 경로를 택했을 뿐이다.
     */
    private MockHttpServletRequestBuilder preflight(String origin) {
        return options("/api/projects")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name());
    }
}
