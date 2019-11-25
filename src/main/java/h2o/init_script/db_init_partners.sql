CREATE TABLE PARTNERS
(
    CHAT_ID           INT PRIMARY KEY,
    COUNT_ORDERS      INT NOT NULL DEFAULT 0,
    BALANCE           INT NOT NULL DEFAULT 0,
    CASHOUT_STATUS    BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE PARTNERS IS 'Партнеры';
COMMENT ON COLUMN PARTNERS.CHAT_ID IS 'ChatId партнера';
COMMENT ON COLUMN PARTNERS.COUNT_ORDERS IS 'Количество предложеий';
COMMENT ON COLUMN PARTNERS.BALANCE IS 'Текущий баланс';
COMMENT ON COLUMN PARTNERS.CASHOUT_STATUS IS 'Готовность вывода средств';
