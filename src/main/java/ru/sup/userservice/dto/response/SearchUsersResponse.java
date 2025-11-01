package ru.sup.userservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.sup.userservice.dto.UserDto;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SearchUsersResponse {
    @JsonProperty("users")
    private List<UserDto> users = List.of();

    @JsonProperty("currentPage")
    private int currentPage;

    @JsonProperty("totalItems")
    private long totalItems;

    @JsonProperty("totalPages")
    private int totalPages;
}