package com.cnu.docserver.deadline.controller;


import com.cnu.docserver.deadline.dto.DeadlineRequestDTO;
import com.cnu.docserver.deadline.dto.DeadlineStatusDTO;
import com.cnu.docserver.deadline.entity.Deadline;
import com.cnu.docserver.deadline.service.DeadlineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/deadline")
@Tag(name="DeadLine", description = "마감일 설정 API")
public class DeadlineController {

    private final DeadlineService deadlineService;


    @GetMapping
    @Operation(summary = "부서별 마감일 조회", description = "부서 ID에 해당하는 문서별 마감일을 반환합니다.")
    public ResponseEntity<List<DeadlineStatusDTO>> getDeadlineByDepartment(@RequestParam Integer departmentId) {
        return ResponseEntity.ok(deadlineService.getDeadlineByDepartment(departmentId));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "마감일 등록/수정", description = "부서ID에 대해 마감일을 등록하거나 수정합니다. (오늘 이후 날짜만 허용)")
    public ResponseEntity<String> registerOrUpdateDeadline(@RequestBody @Valid DeadlineRequestDTO deadlineRequestDTO) {
        deadlineService.registerOrUpdateDeadline(deadlineRequestDTO);
        return ResponseEntity.ok("마감일 등록/수정 되었습니다");
    }

    @DeleteMapping("/{docTypeId}")
    @Operation(summary = "마감일 삭제", description = "해당 문서ID의 마감일을 삭제합니다.")
    public ResponseEntity<Void> deleteDeadline(@PathVariable Integer docTypeId) {
        deadlineService.deleteDeadlineByDocTypeId(docTypeId);
        return ResponseEntity.noContent().build();
    }
}
