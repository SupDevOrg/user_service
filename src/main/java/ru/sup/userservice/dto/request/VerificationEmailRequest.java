package ru.sup.userservice.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerificationEmailRequest {
    private String code;
}
