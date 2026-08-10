package com.alnative.ozon.bot;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище сессий пользователей (в памяти) по chat_id.
 */
@Component
public class SessionStore {

    private final Map<Long, UserSession> sessions = new ConcurrentHashMap<>();

    public UserSession get(Long chatId) {
        return sessions.computeIfAbsent(chatId, k -> new UserSession());
    }

    /** Сбрасывает сессию пользователя (удаляет временные файлы). */
    public void reset(Long chatId) {
        UserSession session = sessions.remove(chatId);
        if (session != null) {
            session.cleanup();
        }
    }
}
