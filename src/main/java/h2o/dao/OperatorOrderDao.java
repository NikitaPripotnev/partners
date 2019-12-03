package h2o.dao;

import h2o.model.OperatorOrder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@AllArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
public class OperatorOrderDao {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public int saveOrder(OperatorOrder order) {
        log.debug("Выполняю сохранение ордера '{}'", order);
        MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        parameterSource.addValue("cleaningDate", order.getCleaningDate());
        parameterSource.addValue("address", order.getAddress());
        parameterSource.addValue("paymentMethod", order.getPaymentMethod());
        parameterSource.addValue("typeCleaning", order.getTypeCleaning());
        parameterSource.addValue("typeObject", order.getTypeObject());
        parameterSource.addValue("paymentAmount", order.getPaymentAmount());
        parameterSource.addValue("clientName", order.getClientName());
        parameterSource.addValue("counterparty", order.getCounterparty());
        parameterSource.addValue("orderSource", order.getOrderSource());
        parameterSource.addValue("comment", order.getComment());
        parameterSource.addValue("orderCreationTime", order.getOrderCreationTime());
        parameterSource.addValue("orderAuthor", order.getOrderAuthor());

        jdbcTemplate.update("INSERT INTO OPERATOR_ORDER(CLEANING_DATE, ADDRESS, PAYMENT_METHOD, TYPE_CLEANING, TYPE_OBJECT, PAYMENT_AMOUNT, CLIENT_NAME, COUNTERPARTY, ORDER_SOURCE, COMMENT, ORDER_CREATION_TIME, ORDER_AUTHOR)" +
                        " VALUES (:cleaningDate, :address, :paymentMethod, :typeCleaning, :typeObject, :paymentAmount, :clientName, :counterparty, :orderSource, :comment, :orderCreationTime, :orderAuthor)",
                parameterSource);
        try{
            int orderId = jdbcTemplate.queryForObject("SELECT ORDER_ID FROM OPERATOR_ORDER WHERE ORDER_CREATION_TIME = :orderCreationTime",
                    parameterSource, Integer.class);
            return orderId;
        }catch (NullPointerException| EmptyResultDataAccessException e){
            return 0;
        }
    }

    public OperatorOrder fetchOperatorOrder(int orderId){
        log.info("Получение информации о заявук из базы с orderId{}", orderId);
        try{
            OperatorOrder order = jdbcTemplate.queryForObject("SELECT * FROM OPERATOR_ORDER WHERE ORDER_ID = :orderId",
                    new MapSqlParameterSource("orderId", orderId), new BeanPropertyRowMapper<>(OperatorOrder.class));
            return order;
        }
        catch(EmptyResultDataAccessException e){
            return null;
        }

    }

    public boolean changeStatus(int orderId, String name, boolean status){
        log.info("Меняю статус {} на {} у orderId {}", name, status, orderId);
            int updateCount = jdbcTemplate.update("UPDATE OPERATOR_ORDER SET "+name+" = :status WHERE ORDER_ID = :orderId", new MapSqlParameterSource("orderId", orderId).addValue("status", status));
            if(updateCount==0){
                log.error("Не нашлась заявка для изменения");
                return false;
            }
            if(updateCount!=1){
                log.error("Изменилось количество строк отличное больше 1");
            }
            return true;
    }


    public boolean changeString(int orderId, String name, String parametr){
        log.info("Меняю поле {} на {} у orderId {}", name, parametr, orderId);
        int updateCount = jdbcTemplate.update("UPDATE OPERATOR_ORDER SET "+name+" = :parametr WHERE ORDER_ID = :orderId", new MapSqlParameterSource("orderId", orderId).addValue("parametr", parametr));
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
