package com.example.demo.service;

import com.example.demo.domain.UserProfile;
import com.example.demo.domain.UserProfileRequest;
import com.example.demo.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** {@code user_profiles} 1:1 부가정보에 대한 서비스 계층. {@link UserService}와 동일한 구조를 따른다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserProfileMapper userProfileMapper;

    public List<UserProfile> findAll() {
        return userProfileMapper.findAll();
    }

    public UserProfile findByUserId(Long userId) {
        return userProfileMapper.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("프로필을 찾을 수 없습니다. userId=" + userId));
    }

    @Transactional
    public UserProfile create(UserProfileRequest request) {
        UserProfile profile = UserProfile.builder()
                .userId(request.getUserId())
                .phoneNumber(request.getPhoneNumber())
                .birthDate(request.getBirthDate())
                .addressLine1(request.getAddressLine1())
                .marketingOptIn(request.isMarketingOptIn())
                .build();
        userProfileMapper.insert(profile);
        return profile;
    }

    @Transactional
    public UserProfile update(Long userId, UserProfileRequest request) {
        findByUserId(userId);
        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .phoneNumber(request.getPhoneNumber())
                .birthDate(request.getBirthDate())
                .addressLine1(request.getAddressLine1())
                .marketingOptIn(request.isMarketingOptIn())
                .build();
        userProfileMapper.update(profile);
        return profile;
    }

}
