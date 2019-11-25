CREATE TABLE ORDERS
(
    ORDER_ID          SERIAL PRIMARY KEY,
    CHAT_ID           INT NOT NULL,
    AMOUNT            INT NOT NULL,
    APPROVE           BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE cleaning_order IS 'Заявки';
COMMENT ON COLUMN cleaning_order.ORDER_ID IS 'ID заявки';
COMMENT ON COLUMN cleaning_order.CHAT_ID IS 'Чатид владельца заявки';
COMMENT ON COLUMN cleaning_order.AMOUNT IS 'Сумма заявки';
COMMENT ON COLUMN cleaning_order.APPROVE IS 'Статус проверки заявки';
