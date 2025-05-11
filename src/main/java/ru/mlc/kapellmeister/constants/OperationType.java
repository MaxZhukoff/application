package ru.mlc.kapellmeister.constants;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OperationType {

    /**
     * Обновление данных в БД
     */
    LOCAL_UPDATE(false),
    /**
     * Публикация (без обработки ответа) актуального состояния, при появлении новых операций, старые утрачивают актуальность
     */
    PUBLISH_ACTUAL_STATE(false),
    /**
     * Публикация (без обработки ответа) последовательности изменений, каждое сообщение обязательно, порядок критичен
     */
    PUBLISH_DIFF_LOG(false),
    /**
     * Отправка сообщения в Кафке
     */
    SEND_KAFKA_MESSAGE(false),
    /**
     * Запрос с синхронной обработкой ответа
     */
    SYNC_REQUEST(false),
    /**
     * Запрос с асинхронной обработкой ответа
     */
    ASYNC_REQUEST(true);

    private final boolean async;
}
