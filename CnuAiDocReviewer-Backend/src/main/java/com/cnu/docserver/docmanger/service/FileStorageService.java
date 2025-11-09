package com.cnu.docserver.docmanger.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
@Service
public class FileStorageService {

    private final Path uploadDir = Paths.get("uploads");

    // 문서유형 파일 (기존)
    public String save(Integer docTypeId, MultipartFile file) {
        return saveInto(uploadDir.resolve("doctype").resolve(String.valueOf(docTypeId)), "/uploads/doctype/" + docTypeId + "/", file);
    }

    // 제출 파일 (신규)
    public String saveSubmission(Integer submissionId, MultipartFile file) {
        return saveInto(uploadDir.resolve("submissions").resolve(String.valueOf(submissionId)), "/uploads/submissions/" + submissionId + "/", file);
    }

    // 공통 내부 로직
    private String saveInto(Path dir, String urlPrefix, MultipartFile file) {
        try {
            Files.createDirectories(dir);

            String original = Optional.ofNullable(file.getOriginalFilename()).orElse("unknown");
            original = original.replace("\\", "/");
            original = original.substring(original.lastIndexOf('/') + 1);
            if (original.isBlank()) original = "unknown";
            if (original.length() > 255) original = original.substring(original.length() - 255);

            Path target = dir.resolve(original).normalize();
            if (!target.startsWith(dir)) throw new SecurityException("Invalid path");
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String encoded = java.net.URLEncoder.encode(original, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
            return urlPrefix + encoded;
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패: " + e.getMessage(), e);
        }
    }

    public void deleteByUrl(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith("/uploads/")) return;
        try {
            String relative = java.net.URLDecoder.decode(
                    fileUrl.substring("/uploads/".length()),
                    java.nio.charset.StandardCharsets.UTF_8
            );
            Path target = uploadDir.resolve(relative).normalize();
            // uploads 폴더 밖이면 차단
            if (!target.startsWith(uploadDir)) return;
            Files.deleteIfExists(target);
        } catch (IOException ignored) {}
    }

    /** FastAPI 전송용: 저장 파일 바이트 */
    public byte[] readBytes(String fileUrl) throws FileReadException {
        if (fileUrl == null || !fileUrl.startsWith("/uploads/")) {
            throw new FileReadException("잘못된 파일 URL: " + fileUrl);
        }
        try {
            String relative = java.net.URLDecoder.decode(
                    fileUrl.substring("/uploads/".length()),
                    java.nio.charset.StandardCharsets.UTF_8
            );
            Path target = uploadDir.resolve(relative).normalize();
            if (!target.startsWith(uploadDir)) {
                throw new FileReadException("Invalid path traversal");
            }
            return Files.readAllBytes(target);
        } catch (IOException e) {
            // 구체적인 예외 메시지를 포함하여 새로운 사용자 정의 예외를 던짐
            throw new FileReadException("파일 읽기 실패: " + e.getMessage(), e);
        }
    }

    /** FastAPI 전송용: 사용자 친화적 파일명 추출 */
    public String getFilename(String fileUrl) {
        if (fileUrl == null) return "upload.bin";
        int i = fileUrl.lastIndexOf('/');
        String enc = (i >= 0 ? fileUrl.substring(i + 1) : fileUrl);
        try {
            return java.net.URLDecoder.decode(enc, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignore) {
            return enc;
        }
    }
    // 파일 읽기 오류를 위한 사용자 정의 예외
    public static class FileReadException extends RuntimeException {
        public FileReadException(String message) {
            super(message);
        }
        public FileReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
