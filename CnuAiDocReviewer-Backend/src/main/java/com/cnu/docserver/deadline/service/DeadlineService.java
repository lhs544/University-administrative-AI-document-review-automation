package com.cnu.docserver.deadline.service;

import com.cnu.docserver.deadline.dto.DeadlineRequestDTO;
import com.cnu.docserver.deadline.dto.DeadlineStatusDTO;
import com.cnu.docserver.deadline.entity.Deadline;
import com.cnu.docserver.deadline.repository.DeadlineRepository;
import com.cnu.docserver.department.entity.Department;
import com.cnu.docserver.docmanger.entity.DocType;
import com.cnu.docserver.department.repository.DepartmentRepository;
import com.cnu.docserver.docmanger.repository.DocTypeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeadlineService {

    private final DepartmentRepository departmentRepository;
    private final DocTypeRepository docTypeRepository;
    private final DeadlineRepository deadlineRepository;



    // 부서별 마감일 조회
    @Transactional
    public List<DeadlineStatusDTO> getDeadlineByDepartment(Integer departmentId){
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(()->new RuntimeException("부서를 찾을 수 없습니다."));

        List<DocType> docTypes = docTypeRepository.findByDepartment(department);

        return docTypes.stream()
                .map(docType-> {
                    Optional<Deadline> deadlineOptional = deadlineRepository.findByDocType(docType);
                    return DeadlineStatusDTO.builder()
                            .docTypeId(docType.getDocTypeId())
                            .title(docType.getTitle())
                            .deadline(deadlineOptional.map(Deadline::getDeadline).orElse(null))
                            .build();
                })
                .toList();
    }
    //단건 조회
    @Transactional
    public DeadlineStatusDTO getDeadlineByDocTypeId(Integer docTypeId) {
        DocType docType = docTypeRepository.findById(docTypeId)
                .orElseThrow(() -> new RuntimeException("문서를 찾을 수 없습니다."));
        return deadlineRepository.findByDocType(docType)
                .map(d -> DeadlineStatusDTO.builder()
                        .docTypeId(docType.getDocTypeId())
                        .title(docType.getTitle())
                        .deadline(d.getDeadline())
                        .build())
                .orElse(DeadlineStatusDTO.builder()
                        .docTypeId(docType.getDocTypeId())
                        .title(docType.getTitle())
                        .deadline(null)
                        .build());
    }


    //등록 + 수정 registerOrUpdateDeadline()
    @Transactional
    public void registerOrUpdateDeadline(DeadlineRequestDTO deadlineRequestDTO){
        DocType docType = docTypeRepository.findById(deadlineRequestDTO.getDocTypeId())
                .orElseThrow(()->new RuntimeException("문서를 찾을 수 없습니다."));

        // 오늘 이후만 날짜 등록 허용
        if (deadlineRequestDTO.getDeadline() != null && !deadlineRequestDTO.getDeadline().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("마감일은 오늘 이후 날짜만 설정할 수 있습니다.");
        }

        Optional<Deadline> existingDeadline = deadlineRepository.findByDocType(docType);

        //deadline 존재시 수정, 없으면 새로 저장
        Deadline deadline = existingDeadline.orElseGet(()->Deadline.builder().docType(docType).build());
        deadline.setDeadline(deadlineRequestDTO.getDeadline());
        deadlineRepository.save(deadline);
    }

    //삭제 deleteDeadlineByDocTypeId
    @Transactional
    public void deleteDeadlineByDocTypeId(Integer docTypeId) {
        DocType docType = docTypeRepository.findById(docTypeId)
                .orElseThrow(() -> new RuntimeException("문서를 찾을 수 없습니다."));
        deadlineRepository.findByDocType(docType)
                .ifPresent(deadlineRepository::delete);
    }
}
