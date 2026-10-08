package com.myblog.user.web;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 메일 계정을 정하기 전 시험용: 설정이 켜져 있으면 인증번호를 화면에 돌려준다.
 * 실제 서비스에서는 꺼야 한다. 켜 두면 남의 이메일로도 가입할 수 있다.
 * 비밀번호 찾기는 가입하지 않은 이메일에도 (아무도 모르는) 번호를 돌려주므로 가입 여부가 드러나지 않는다.
 */
@Component
public class TestCodeResponse {

    private final boolean showCode;

    public TestCodeResponse(@Value("${blog.mail.show-code-on-screen:false}") boolean showCode) {
        this.showCode = showCode;
    }

    public ResponseEntity<Map<String, String>> of(String code) {
        return showCode ? ResponseEntity.ok(Map.of("testCode", code)) : ResponseEntity.noContent().build();
    }
}
