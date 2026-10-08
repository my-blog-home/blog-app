package com.myblog.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 화면 주소(/blogs/1, /manage 등)로 바로 들어오거나 새로고침하면 index.html을 돌려준다.
 * API(/api)와 이미지(/images), 점이 들어간 파일 이름은 해당하지 않는다.
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/", "/{path:^(?!api$|images$)[^.]*}", "/{path:^(?!api$|images$)[^.]*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
