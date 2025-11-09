package com.cnu.docserver.docmanger.service;

import com.cnu.docserver.docmanger.dto.DocTypeEditResponseDTO;
import com.cnu.docserver.docmanger.dto.DocTypeResponseDTO;
import com.cnu.docserver.department.entity.Department;
import com.cnu.docserver.docmanger.dto.RequiredFieldDTO;
import com.cnu.docserver.docmanger.entity.DocType;
import com.cnu.docserver.docmanger.entity.OriginalFile;
import com.cnu.docserver.docmanger.entity.RequiredField;
import com.cnu.docserver.department.repository.DepartmentRepository;
import com.cnu.docserver.docmanger.repository.DocTypeRepository;
import com.cnu.docserver.docmanger.repository.OriginalFileRepository;
import com.cnu.docserver.docmanger.repository.RequiredFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocTypeService {

    private final DocTypeRepository docTypeRepository;
    private final DepartmentRepository departmentRepository;
    private final RequiredFieldRepository requiredFieldRepository;
    private final OriginalFileRepository originalFileRepository;
    private final FileStorageService fileStorageService;

    //서류 등록
    @Transactional
    public DocType registerDocType(
            Integer departmentId,
            String title,
            List<String> requiredFields,
            List<String> exampleValues,
            MultipartFile file
    ) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("부서를 찾을 수 없습니다."));

        DocType docType = DocType.builder()
                .department(department)
                .title(title)
                .build();
        docTypeRepository.save(docType);

        saveRequiredFields(docType, requiredFields, exampleValues);

        // 파일 1개만 유지 (기존 있으면 교체)
        if (file != null && !file.isEmpty()) {
            upsertFile(docType, file);
        }
        return docType;
    }

    //서류 수정
    @Transactional
    public void updateDocType(
            Integer docTypeId,
            String title,
            List<String> requiredFields,
            List<String> exampleValues,
            MultipartFile file) {

        DocType docType = docTypeRepository.findById(docTypeId)
                .orElseThrow(() -> new RuntimeException("문서를 찾을 수 없습니다."));

        // 1. 제목 수정
        docType.setTitle(title);

        // 2. 단일 파일 교체
        if (file != null && !file.isEmpty()) {
            upsertFile(docType, file);
        }

        // 3. 필수 항목 업데이트
        syncRequiredFields(docType, requiredFields, exampleValues);
    }


    //부서별 전체 문서 조회
    @Transactional
    public List<DocTypeResponseDTO> getDocTypesByDepartment(Integer departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("부서를 찾을 수 없습니다."));

        return docTypeRepository.findByDepartment(department).stream()
                .map(docType -> {

                    String fileUrl = originalFileRepository.findByDocType(docType)
                            .map(OriginalFile::getFileUrl)
                            .orElse(null);

                    List<String> fields = requiredFieldRepository.findByDocType(docType).stream()
                            .map(RequiredField::getFieldName)
                            .toList();

                    return new DocTypeResponseDTO(
                            docType.getDocTypeId(),
                            docType.getTitle(),
                            fields,
                            fileUrl
                    );
                })
                .toList();
    }

    //수정용 단건 조회
    @Transactional
    public DocTypeEditResponseDTO getDocTypeForEdit(Integer docTypeId) {
        DocType docType = docTypeRepository.findById(docTypeId)
                .orElseThrow(() -> new RuntimeException("문서를 찾을 수 없습니다."));

        List<RequiredField> requiredFields = requiredFieldRepository.findByDocType(docType);

        String fileUrl = originalFileRepository.findByDocType(docType)
                .map(OriginalFile::getFileUrl)
                .orElse(null);

        return DocTypeEditResponseDTO.builder()
                .title(docType.getTitle())
                .fileUrl(fileUrl)
                .requiredFields(requiredFields.stream().map(RequiredField::getFieldName).toList())
                .exampleValues(requiredFields.stream().map(RequiredField::getExampleValue).toList())
                .build();
    }

    @Transactional
    public List<RequiredFieldDTO> getRequiredFields(Integer docTypeId) {
        DocType docType = docTypeRepository.findById(docTypeId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다."));

        // 정렬 컬럼이 없으면 findByDocType() 그대로 사용
        List<RequiredField> fields = requiredFieldRepository.findByDocType(docType);

        return fields.stream()
                .map(f -> RequiredFieldDTO.builder()
                        .requiredFieldId(f.getRequiredFieldId()) // PK
                        .label(f.getFieldName())                 // 화면 라벨
                        .example(f.getExampleValue())            // 예시값
                        .required(true)                          // 컬럼 없으면 true 고정
                        .orderNo(null)                           // 정렬 없으면 null
                        .build())
                .toList();
    }

    /** 문서 유형 ID로 단일 원본파일 조회 (문서당 파일 1개 정책) */
    @Transactional
    public Optional<OriginalFile> getOriginalFileByDocTypeId(Integer docTypeId) {
        DocType docType = docTypeRepository.findById(docTypeId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다."));
        return originalFileRepository.findByDocType(docType);
    }

    /** 저장소에서 파일 바이트 읽기 */
    public byte[] readBytes(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일 URL이 없습니다.");
        }
        return fileStorageService.readBytes(fileUrl);
    }




    //---내부 메서드 ---
    // 필수 항목 초기 저장 (null-safe)
    private void saveRequiredFields(DocType docType, List<String> names, List<String> examples) {
        List<RequiredField> fields = buildFieldPairs(docType, names, examples);
        if (!fields.isEmpty()) {
            requiredFieldRepository.saveAll(fields);
        }
    }

    // 필수 항목 동기화: 삭제/추가 + 기존 예시값 업데이트
    private void syncRequiredFields(DocType docType, List<String> newNames, List<String> newExamples) {
        List<RequiredField> current = requiredFieldRepository.findByDocType(docType);

        // null-safe
        newNames = newNames != null ? newNames : List.of();
        newExamples = newExamples != null ? newExamples : List.of();

        // 이름→예시값 매핑
        Map<String, String> newMap = new HashMap<>();
        for (int i = 0; i < newNames.size(); i++) {
            String name = newNames.get(i);
            String ex   = i < newExamples.size() ? newExamples.get(i) : null;
            if (name != null && !name.isBlank()) newMap.put(name, ex);
        }

        // 삭제
        Set<String> newNameSet = newMap.keySet();
        List<RequiredField> toDelete = current.stream()
                .filter(f -> !newNameSet.contains(f.getFieldName()))
                .toList();
        if (!toDelete.isEmpty()) requiredFieldRepository.deleteAll(toDelete);

        // 추가 & 업데이트
        Map<String, RequiredField> currentMap = current.stream()
                .collect(Collectors.toMap(RequiredField::getFieldName, f -> f));

        List<RequiredField> toAdd = new ArrayList<>();
        for (Map.Entry<String, String> e : newMap.entrySet()) {
            String name = e.getKey();
            String ex   = e.getValue();
            RequiredField exist = currentMap.get(name);
            if (exist == null) {
                toAdd.add(RequiredField.builder()
                        .docType(docType)
                        .fieldName(name)
                        .exampleValue(ex)
                        .build());
            } else {
                // 예시값 변경 반영
                exist.setExampleValue(ex);
            }
        }
        if (!toAdd.isEmpty()) requiredFieldRepository.saveAll(toAdd);
    }

    // 파일 upsert: 기존 있으면 교체, 없으면 생성 (항상 1개만 유지)
    // 파일 upsert: 기존은 UPDATE, 없으면 INSERT (항상 1개 유지 + UNIQUE 충돌 방지)
    private void upsertFile(DocType docType, MultipartFile file) {
        // 1) 새 파일을 먼저 디스크에 저장 (경로 확보)
        String newUrl = fileStorageService.save(docType.getDocTypeId(), file);

        try {
            originalFileRepository.findByDocType(docType).ifPresentOrElse(old -> {
                // 2-a) 기존 물리 파일 삭제하고, DB는 UPDATE만 수행
                fileStorageService.deleteByUrl(old.getFileUrl());
                old.setFileUrl(newUrl);          // UPDATE
                // old.setUploadedAt(LocalDateTime.now()); // 필드 있다면 갱신
                originalFileRepository.save(old);
            }, () -> {
                // 2-b) 기존 레코드가 없으면 INSERT
                originalFileRepository.save(
                        OriginalFile.builder()
                                .docType(docType)
                                .fileUrl(newUrl)
                                .build()
                );
            });
        } catch (RuntimeException ex) {
            // DB 실패 시 방금 저장한 새 물리 파일 롤백 삭제 (고아 파일 방지)
            fileStorageService.deleteByUrl(newUrl);
            throw ex;
        }
    }

    // 이름/예시 페어 빌드 (null-safe, 길이 불일치 허용)
    private List<RequiredField> buildFieldPairs(DocType docType, List<String> names, List<String> examples) {
        List<RequiredField> out = new ArrayList<>();
        if (names == null || names.isEmpty()) return out;
        int n = names.size();
        int m = (examples == null) ? 0 : examples.size();
        for (int i = 0; i < n; i++) {
            String name = names.get(i);
            if (name == null || name.isBlank()) continue;
            String ex = i < m ? examples.get(i) : null;
            out.add(RequiredField.builder()
                    .docType(docType)
                    .fieldName(name)
                    .exampleValue(ex)
                    .build());
        }
        return out;
    }
}