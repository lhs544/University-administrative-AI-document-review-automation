package com.cnu.docserver.submission.controller;

import com.cnu.docserver.deadline.dto.DeadlineStatusDTO;
import com.cnu.docserver.deadline.service.DeadlineService;
import com.cnu.docserver.department.dto.DepartmentResponseDTO;
import com.cnu.docserver.department.entity.Department;
import com.cnu.docserver.department.repository.DepartmentRepository;
import com.cnu.docserver.docmanger.dto.DocTypeResponseDTO;
import com.cnu.docserver.docmanger.dto.RequiredFieldDTO;
import com.cnu.docserver.docmanger.service.DocTypeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "Student Basic Lookup", description = "학생 제출 전 기본 조회 API")
public class StudentLookupController {


    private final DepartmentRepository departmentRepository;
    private final DocTypeService docTypeService;
    private final DeadlineService deadlineService;

    //부서 목록
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/departments")
    public List<DepartmentResponseDTO> getDepartments() {

        return departmentRepository.findAll(Sort.by(Sort.Direction.ASC, "departmentId"))
                .stream()
                .map(StudentLookupController::toDeptDto)
                .toList();
    }

    //부서별 문서 목록
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/departments/{departmentId}/doc-types")
    public List<DocTypeResponseDTO> getDocTypes(@PathVariable Integer departmentId) {
        return docTypeService.getDocTypesByDepartment(departmentId);
    }

    //필수항목 정의
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/doc-types/{docTypeId}/required-fields")
    public List<RequiredFieldDTO> getRequiredFields(@PathVariable Integer docTypeId) {
        return docTypeService.getRequiredFields(docTypeId);
    }

    //제출기한
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/doc-types/{docTypeId}/deadline")
    public DeadlineStatusDTO getDeadline(@PathVariable Integer docTypeId){
        return deadlineService.getDeadlineByDocTypeId(docTypeId);
    }

    private static DepartmentResponseDTO toDeptDto(Department department) {
        return DepartmentResponseDTO.builder()
                .id(department.getDepartmentId())
                .name(department.getName())
                .phone(department.getPhone())
                .build();
    }


}
