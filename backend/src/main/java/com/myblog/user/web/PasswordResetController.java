package com.myblog.user.web;

import com.myblog.user.service.PasswordResetService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PasswordResetController {

    public record EmailRequest(String email) {
    }

    public record ConfirmRequest(String email, String code) {
    }

    public record ResetRequest(String email, String newPassword, String newPasswordConfirm) {
    }

    private final PasswordResetService reset;
    private final TestCodeResponse testCode;

    public PasswordResetController(PasswordResetService reset, TestCodeResponse testCode) {
        this.reset = reset;
        this.testCode = testCode;
    }

    @PostMapping("/api/auth/password-reset/verification")
    public ResponseEntity<Map<String, String>> requestCode(@RequestBody EmailRequest request) {
        return testCode.of(reset.requestCode(request.email()));
    }

    @PostMapping("/api/auth/password-reset/verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@RequestBody ConfirmRequest request) {
        reset.confirmCode(request.email(), request.code());
    }

    @PostMapping("/api/auth/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@RequestBody ResetRequest request) {
        reset.reset(request.email(), request.newPassword(), request.newPasswordConfirm());
    }
}
