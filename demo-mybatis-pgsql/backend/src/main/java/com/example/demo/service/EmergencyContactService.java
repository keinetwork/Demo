package com.example.demo.service;

import com.example.demo.domain.EmergencyContact;
import com.example.demo.domain.EmergencyContactRequest;
import com.example.demo.mapper.EmergencyContactMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** {@code emergency_contacts}에 대한 서비스 계층. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmergencyContactService {

    private final EmergencyContactMapper emergencyContactMapper;

    public List<EmergencyContact> findByUserId(Long userId) {
        return emergencyContactMapper.findByUserId(userId);
    }

    @Transactional
    public EmergencyContact create(EmergencyContactRequest request) {
        EmergencyContact contact = EmergencyContact.builder()
                .userId(request.getUserId())
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .relation(request.getRelation())
                .primary(request.isPrimary())
                .build();
        emergencyContactMapper.insert(contact);
        return contact;
    }

}
