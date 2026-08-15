package com.example.demo.controller;

import com.example.demo.domain.UserProfile;
import com.example.demo.domain.UserProfileRequest;
import com.example.demo.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/** {@code user_profiles} 리소스에 대한 REST API. 기본 경로는 {@code /api/user-profiles}. */
@RestController
@RequestMapping("/api/user-profiles")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public List<UserProfile> findAll() {
        return userProfileService.findAll();
    }

    @GetMapping("/user/{userId}")
    public UserProfile findByUserId(@PathVariable Long userId) {
        return userProfileService.findByUserId(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfile create(@Valid @RequestBody UserProfileRequest request) {
        return userProfileService.create(request);
    }

    @PutMapping("/user/{userId}")
    public UserProfile update(@PathVariable Long userId, @Valid @RequestBody UserProfileRequest request) {
        return userProfileService.update(userId, request);
    }

}
