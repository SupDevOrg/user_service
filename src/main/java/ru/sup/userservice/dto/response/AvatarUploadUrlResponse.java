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
@Schema(description = "Ответ с данными для загрузки аватарки")
public class AvatarUploadUrlResponse {

    @Schema(description = "Presigned URL для PUT загрузки файла")
    private String uploadUrl;

    @Schema(description = "Публичный URL аватарки после загрузки")
    private String avatarUrl;

    @Schema(description = "Ключ объекта в бакете")
    private String objectKey;

    @Schema(description = "Срок действия presigned URL в секундах")
    private long expiresInSeconds;
}
