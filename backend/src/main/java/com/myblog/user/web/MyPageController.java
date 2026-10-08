package com.myblog.user.web;

import com.myblog.common.security.CurrentMember;
import com.myblog.user.service.MyPageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MyPageController {

    public record ProfileRequest(String nickname, String bio) {
    }

    public record PasswordRequest(String currentPassword, String newPassword, String newPasswordConfirm) {
    }

    public record WithdrawRequest(String password, boolean agreed) {
    }

    private final MyPageService myPage;

    public MyPageController(MyPageService myPage) {
        this.myPage = myPage;
    }

    @GetMapping("/api/me")
    public MyPageService.MyInfo info() {
        return myPage.info(CurrentMember.id());
    }

    @PatchMapping("/api/me")
    public MyPageService.MyInfo update(@RequestBody ProfileRequest request) {
        return myPage.updateProfile(CurrentMember.id(), request.nickname(), request.bio());
    }

    @PutMapping("/api/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody PasswordRequest request, HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        myPage.changePassword(CurrentMember.id(), request.currentPassword(), request.newPassword(),
                request.newPasswordConfirm(), session == null ? null : session.getId());
    }

    @DeleteMapping("/api/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@RequestBody WithdrawRequest request, HttpServletRequest http) {
        myPage.withdraw(CurrentMember.id(), request.password(), request.agreed());
        HttpSession session = http.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyGone) {
                // 위에서 회원의 세션을 모두 지웠다
            }
        }
        SecurityContextHolder.clearContext();
    }
}
