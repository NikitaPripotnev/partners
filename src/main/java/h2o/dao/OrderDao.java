package h2o.dao;

import h2o.model.Order;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@Repository
@AllArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
public class OrderDao {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public int saveOrder(Order order) {
        log.debug("Выполняю сохранение ордера '{}'", order);
        MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        parameterSource.addValue("chatId", order.getChatId());
        parameterSource.addValue("amount", order.getPaymentAmount());
        parameterSource.addValue("orderCreationTime", ZonedDateTime.now().withZoneSameInstant(ZoneId.of("+03:00:00")).toLocalDateTime());
        parameterSource.addValue("orderInfo", order.getOrderInfo());
        parameterSource.addValue("contactInfo", order.getContactInfo());

        jdbcTemplate.update("INSERT INTO cleaning_order(CHAT_ID, AMOUNT, ORDER_CREATION_TIME, ORDER_INFO, CONTACT_INFO)" +
                        " VALUES (:chatId, :amount, :orderCreationTime, :orderInfo, :contactInfo)",
                parameterSource);
        try{
            int orderId = jdbcTemplate.queryForObject("SELECT ORDER_ID FROM CLEANING_ORDER WHERE ORDER_CREATION_TIME = :orderCreationTime",
                    parameterSource, Integer.class);
            return orderId;
        }catch (NullPointerException| EmptyResultDataAccessException e){
            return 0;
        }
    }

    public boolean changeStatus(int orderId, String name){
        log.info("Меняю статус {} на TRUE у orderId {}", name, orderId);
            int updateCount = jdbcTemplate.update("UPDATE CLEANING_ORDER SET "+name+" = TRUE WHERE ORDER_ID = :orderId", new MapSqlParameterSource("orderId", orderId));
            if(updateCount==0){
                log.error("Не нашлась заявка для изменения");
                return false;
            }
            if(updateCount!=1){
                log.error("Изменилось количество строк отличное больше 1");
            }
            return true;
    }



}
