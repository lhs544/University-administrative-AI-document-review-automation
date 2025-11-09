package com.cnu.docserver.ocr;

import com.cnu.docserver.ocr.dto.Finding;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException; // Import 추가
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class OcrClient {

    private final RestTemplate ocrRestTemplate;

    public OcrClient(@Qualifier("ocrRestTemplate") RestTemplate ocrRestTemplate) {
        this.ocrRestTemplate = ocrRestTemplate;
    }

    @Value("${ocr.base-url:http://localhost:8000}")
    private String baseUrl;

    /**
     * OCR 서비스에 파일을 보내 검토를 요청하고 결과를 반환합니다.
     * @param fileBytes 검토할 파일의 바이트 배열
     * @param filename 파일명
     * @return OCR 검토 결과
     * @throws OcrException OCR 서버 통신 중 오류 발생 시
     */
    public OcrResult review(byte[] fileBytes, String filename) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            var body = new LinkedMultiValueMap<String, Object>();

            // 파일 파트 헤더는 body.add() 시 자동으로 생성되므로 별도 필요 없음.
            ByteArrayResource filePart = new ByteArrayResource(fileBytes) {
                @Override public String getFilename() { return filename; }
            };
            body.add("file", filePart);

            var req = new HttpEntity<>(body, headers);
            ResponseEntity<OcrResult> res =
                    ocrRestTemplate.postForEntity(baseUrl + "/ocr/review", req, OcrResult.class);

            if (res.getBody() == null) {
                throw new OcrException("OCR 응답이 비어있습니다.");
            }
            return res.getBody();

        } catch (ResourceAccessException e) {
            // 타임아웃, 연결 거부 등 네트워크 관련 예외를 별도로 처리
            throw new OcrException("OCR 서버 연결/응답 타임아웃 오류", e);
        } catch (Exception e) {
            // 그 외 일반적인 오류 처리 (HTTP 상태 코드 오류 등)
            throw new OcrException("OCR 호출 중 예상치 못한 오류 발생", e);
        }
    }

    // OCR 호출 오류를 명확히 구분하기 위한 커스텀 예외 클래스
    public static class OcrException extends RuntimeException {
        public OcrException(String message, Throwable cause) {
            super(message, cause);
        }
        public OcrException(String message) {
            super(message);
        }
    }

    @Data
    public static class OcrResult {
        private String verdict;
        private List<Finding> findings;
        private String reason;
        private Map<String,Object> details;
        @JsonProperty("section_counts") private List<Map<String,Object>> sectionCounts;
        @JsonProperty("processing_time") private String processingTime;
        @JsonProperty("debug_text") private String debugText;
    }
}