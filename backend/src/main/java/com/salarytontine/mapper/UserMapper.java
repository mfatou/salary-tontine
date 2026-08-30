package com.salarytontine.mapper;

import com.salarytontine.dto.response.UserResponse;
import com.salarytontine.entity.User;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getBaseSalary(),
                user.getCreatedAt());
    }

    public List<UserResponse> toResponses(List<User> users) {
        return users.stream().map(this::toResponse).toList();
    }
}
