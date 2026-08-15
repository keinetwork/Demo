package com.example.demo.controller;

import com.example.demo.domain.EmergencyContact;
import com.example.demo.domain.EmergencyContactRequest;
import com.example.demo.service.EmergencyContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/** {@code emergency_contacts} 리소스에 대한 REST API. 기본 경로는 {@code /api/emergency-contacts}. */
@RestController
@RequestMapping("/api/emergency-contacts")
@RequiredArgsConstructor
public class EmergencyContactController {

    private final EmergencyContactService emergencyContactService;

    @GetMapping("/user/{userId}")
    public List<EmergencyContact> findByUserId(@PathVariable Long userId) {
        return emergencyContactService.findByUserId(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmergencyContact create(@Valid @RequestBody EmergencyContactRequest request) {
        return emergencyContactService.create(request);
    }

}
