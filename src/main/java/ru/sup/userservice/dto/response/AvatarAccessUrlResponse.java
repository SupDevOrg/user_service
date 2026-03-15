package ru.sup.userservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ответ с данными для доступа к приватной аватарке")
public class AvatarAccessUrlResponse {

    @Schema(description = "Presigned URL для GET чтения аватарки")
    private String accessUrl;

    @Schema(description = "Срок действия ссылки в секундах")
    private long expiresInSeconds;
}
