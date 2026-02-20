package ru.sup.userservice.data;

public enum FriendRequestAction {
    REQUEST_SENT,     // отправлен запрос
    REQUEST_ACCEPTED, // запрос принят
    REQUEST_REJECTED, // запрос отклонён
    REQUEST_CANCELLED,// запрос отменён
    FRIEND_REMOVED,   // друг удалён
    USER_BLOCKED      // пользователь заблокирован
}
