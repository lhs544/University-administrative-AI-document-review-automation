package com.cnu.docserver.department.controller;

import com.cnu.docserver.department.dto.DepartmentResponseDTO;
import com.cnu.docserver.department.entity.Department;
import com.cnu.docserver.department.repository.DepartmentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/departments")
@Tag(name = "Department", description = "부서 조회 API")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    @GetMapping
    @Operation(summary = "부서 목록 조회", description = "전체 부서 목록을 id 오름차순으로 반환합니다.")
    public List<DepartmentResponseDTO> list() {
        return departmentRepository.findAll(Sort.by(Sort.Direction.ASC, "departmentId"))
                .stream()
                .map(DepartmentController::toDto)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "부서 단건 조회", description = "부서 id로 단건을 조회합니다.")
    public DepartmentResponseDTO getOne(@PathVariable Integer id) {
        Department d = departmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "부서를 찾을 수 없습니다."));
        return toDto(d);
    }

    private static DepartmentResponseDTO toDto(Department d) {
        return DepartmentResponseDTO.builder()
                .id(d.getDepartmentId())
                .name(d.getName())
                .phone(d.getPhone())
                .build();
    }
}
