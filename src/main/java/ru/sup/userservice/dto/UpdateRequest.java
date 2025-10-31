package ru.sup.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Запрос на обновление данных пользователя")
public class UpdateRequest {

    @Schema(description = "Новый логин пользователя", example = "new_username")
    private String username;

    @Schema(description = "Новый пароль (опционально)", example = "new_password123")
    private String password;
}
