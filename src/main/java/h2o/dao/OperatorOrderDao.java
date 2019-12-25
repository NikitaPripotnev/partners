package h2o.dao;

import h2o.model.OperatorOrder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

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
        parameterSource.addValue("fullOrder", order.isFullOrder());
        parameterSource.addValue("clientNumber", order.getClientNumber());

        jdbcTemplate.update("INSERT INTO OPERATOR_ORDER(FULL_ORDER, CLEANING_DATE, ADDRESS, PAYMENT_METHOD, TYPE_CLEANING, TYPE_OBJECT, PAYMENT_AMOUNT, CLIENT_NAME, CLIENT_NUMBER, COUNTERPARTY, ORDER_SOURCE, COMMENT, ORDER_CREATION_TIME, ORDER_AUTHOR)" +
                        " VALUES (:fullOrder, :cleaningDate, :address, :paymentMethod, :typeCleaning, :typeObject, :paymentAmount, :clientName, :clientNumber, :counterparty, :orderSource, :comment, :orderCreationTime, :orderAuthor)",
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

    public List<OperatorOrder> findOrdersBeforeDate(LocalDateTime date){
        log.info("Получение списка принятых заявок до указанной даты: {}", date);
        try{
            List<OperatorOrder> orders = jdbcTemplate.query("SELECT * FROM OPERATOR_ORDER WHERE CLEANING_DATE <= :dateCheck AND APPROVE = TRUE",
                    new MapSqlParameterSource("dateCheck", date), new BeanPropertyRowMapper<>(OperatorOrder.class));
            return orders;
        }
        catch(EmptyResultDataAccessException e){
            return null;
        }

    }

    public void updateDateAndPaymentAmount(int orderId, OperatorOrder order){
        log.debug("Меняю время и цену в заявке {}, c ценой: {}, датой: {}", orderId, order.getPaymentAmount(), order.getCleaningDate());
        try{
            int updateCount = jdbcTemplate.update("UPDATE OPERATOR_ORDER SET CLEANING_DATE = :dateTime, PAYMENT_AMOUNT = :payment, FULL_ORDER = true WHERE ORDER_ID = :orderId",
                    new MapSqlParameterSource("dateTime", order.getCleaningDate()).addValue("payment", order.getPaymentAmount()).addValue("orderId", orderId));
        }catch(DataAccessException e){
            log.error(e.getMessage());
        }
    }

    public void updateMessageIdAndGroupNumber(int orderId, int messageId, int groupNumber){
        log.debug("Устанавливаю messageId {}, в заявке {}", messageId, orderId);
        try{
            int updateCount = jdbcTemplate.update("UPDATE OPERATOR_ORDER SET MESSAGE_ID = :messageId, GROUP_NUMBER = :groupNumber WHERE ORDER_ID = :orderId",
                    new MapSqlParameterSource("messageId", messageId).addValue("orderId", orderId).addValue("groupNumber", groupNumber));
        }catch(DataAccessException e){
            log.error(e.getMessage());
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
