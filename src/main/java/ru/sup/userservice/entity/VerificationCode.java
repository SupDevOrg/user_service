package ru.sup.userservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
public class VerificationCode {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private Long user_id;

    private String email;

    private String code;

    private boolean revoked;

}
