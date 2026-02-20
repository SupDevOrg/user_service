package ru.sup.userservice.data;

public enum FriendshipStatus {
    PENDING,    // запрос отправлен, ждёт подтверждения
    ACCEPTED,   // друзья
    REJECTED,   // запрос отклонён
    BLOCKED     // пользователь заблокирован
}
