package com.myblog.user.web;

import com.myblog.user.service.SignupService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SignupController {

    public record CodeRequest(String nickname, String email) {
    }

    public record ConfirmRequest(String email, String code) {
    }

    public record SignupRequest(String nickname, String email, String password, String passwordConfirm) {
    }

    private final SignupService signup;
    private final TestCodeResponse testCode;

    public SignupController(SignupService signup, TestCodeResponse testCode) {
        this.signup = signup;
        this.testCode = testCode;
    }

    @PostMapping("/api/auth/signup/verification")
    public ResponseEntity<Map<String, String>> requestCode(@RequestBody CodeRequest request) {
        return testCode.of(signup.requestCode(request.nickname(), request.email()));
    }

    @PostMapping("/api/auth/signup/verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@RequestBody ConfirmRequest request) {
        signup.confirmCode(request.email(), request.code());
    }

    @PostMapping("/api/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> signup(@RequestBody SignupRequest request) {
        long id = signup.signup(request.nickname(), request.email(), request.password(), request.passwordConfirm());
        return Map.of("id", id);
    }

    @GetMapping("/api/members/nickname-availability")
    public Map<String, Boolean> nicknameAvailability(@RequestParam String nickname) {
        return Map.of("available", signup.isNicknameAvailable(nickname));
    }
}
