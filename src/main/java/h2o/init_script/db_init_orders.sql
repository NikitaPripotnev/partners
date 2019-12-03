CREATE TABLE CLEANING_ORDER
(
    ORDER_ID          SERIAL PRIMARY KEY,
    CHAT_ID           INT NOT NULL,
    AMOUNT            INT NOT NULL,
    APPROVE           BOOLEAN NOT NULL DEFAULT FALSE,
    ORDER_INFO        VARCHAR (300),
    CONTACT_INFO      VARCHAR (300),
    DATE_CLEANING     timestamp,
    ADMIN_COMMENT     VARCHAR (300)
);

COMMENT ON TABLE cleaning_order IS 'Заявки';
COMMENT ON COLUMN cleaning_order.ORDER_ID IS 'ID заявки';
COMMENT ON COLUMN cleaning_order.CHAT_ID IS 'Чатид владельца заявки';
COMMENT ON COLUMN cleaning_order.AMOUNT IS 'Сумма заявки';
COMMENT ON COLUMN cleaning_order.APPROVE IS 'Статус проверки заявки';
COMMENT ON COLUMN cleaning_order.ORDER_INFO IS 'Информация о заказе';
COMMENT ON COLUMN cleaning_order.CONTACT_INFO IS 'Контактная информация человека, которому предоставляется услуга';
COMMENT ON COLUMN cleaning_order.DATE_CLEANING IS 'Предполагаемая дата уборки';
COMMENT ON COLUMN cleaning_order.ADMIN_COMMENT IS 'Комментарий админа к заказу';


