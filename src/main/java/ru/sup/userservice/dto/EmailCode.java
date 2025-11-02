package ru.sup.userservice.dto;

public record EmailCode(
        String email,
        String code,
        long timestamp
) {
    public EmailCode(
            String email,
            String code
    ){
      this(email, code, System.currentTimeMillis());
    }
}
