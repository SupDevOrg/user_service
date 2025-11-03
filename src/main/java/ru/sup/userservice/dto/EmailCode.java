package ru.sup.userservice.dto;

public record EmailCode(
        Long userID,
        String email,
        String code,
        String type,
        long timestamp
) {
    public EmailCode(
            Long userID,
            String email,
            String code,
            String type
    ){
      this(userID, email, code, type, System.currentTimeMillis());
    }
}
