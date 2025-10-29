package ru.sup.userservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshRequest {

    private String accessToken;
    private String refreshToken;

}
