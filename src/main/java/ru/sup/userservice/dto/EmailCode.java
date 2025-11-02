package ru.sup.userservice.dto;

public record EmailCode(
        String email,
        String code
) {
    public EmailCode(
            String email,
            String code
    ){
        this.email = email;
        this.code = code;
    }
}
