package com.myblog.post.web;

import com.myblog.common.security.CurrentMember;
import com.myblog.post.image.ImageService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ImageController {

    private final ImageService images;

    public ImageController(ImageService images) {
        this.images = images;
    }

    @PostMapping("/api/images")
    @ResponseStatus(HttpStatus.CREATED)
    public ImageService.Uploaded upload(@RequestParam("file") MultipartFile file) {
        return images.upload(CurrentMember.id(), file);
    }

    /** 이미지는 서버를 거쳐 보여 준다. 저장소는 바깥에 열지 않는다 (research R-08) */
    @GetMapping("/images/{key:[0-9a-f\\-]{36}\\.(?:jpg|png|gif|webp)}")
    public ResponseEntity<byte[]> image(@PathVariable String key) {
        ImageService.ImageFile file = images.load(key, CurrentMember.idIfPresent().orElse(null));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.bytes());
    }
}
