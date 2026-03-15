package ru.sup.userservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Запрос на создание presigned URL для загрузки аватарки")
public class AvatarUploadUrlRequest {

    @Schema(description = "MIME-тип файла", example = "image/jpeg")
    private String contentType;

    @Schema(description = "Оригинальное имя файла (опционально)", example = "photo.jpg")
    private String fileName;
}
