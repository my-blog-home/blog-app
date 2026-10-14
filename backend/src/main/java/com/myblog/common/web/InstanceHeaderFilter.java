package com.myblog.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 어느 앱(컨테이너)이 답했는지 응답 헤더 X-Ylog-Instance로 알려 준다.
 * 배포 때 앱을 여러 개 띄우고 Nginx가 요청을 나눠 보내므로, 요청이 정말 번갈아 가는지 확인할 때 쓴다.
 * 이름(INSTANCE_NAME)은 deploy.sh가 컨테이너 이름으로 넣고, 없으면(로컬 개발) 헤더를 붙이지 않는다.
 */
@Component
public class InstanceHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Ylog-Instance";

    private final String instanceName;

    public InstanceHeaderFilter(@Value("${blog.instance-name:${INSTANCE_NAME:}}") String instanceName) {
        this.instanceName = instanceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!instanceName.isBlank()) {
            response.setHeader(HEADER, instanceName);
        }
        chain.doFilter(request, response);
    }
}
