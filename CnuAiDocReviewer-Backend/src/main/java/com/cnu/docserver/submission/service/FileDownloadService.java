package com.cnu.docserver.submission.service;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class FileDownloadService {

    // 로컬 파일 시스템에 저장된 경우 예시
    public byte[] loadBytes(String fileUrl) throws Exception {
        // fileUrl이 file:///path/to/file 형태라면:
        if (fileUrl.startsWith("file:")) {
            return Files.readAllBytes(Path.of(URI.create(fileUrl)));
        }
        // http(s) 스토리지인 경우 RestTemplate/HttpClient로 GET 하여 bytes 반환
        // ...
        throw new IllegalStateException("지원하지 않는 URL: " + fileUrl);
    }
}