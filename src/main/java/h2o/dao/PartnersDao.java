package h2o.dao;

import h2o.model.Partner;
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
public class PartnersDao {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void savePartner(Partner partner) {
        log.debug("Выполняю сохранение клиента в базу '{}'", partner);
        MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        parameterSource.addValue("chatId", partner.getChatId());
        parameterSource.addValue("positionClient", partner.getPosition());
        parameterSource.addValue("fullName", partner.getFullName());
        parameterSource.addValue("phoneNumber", partner.getPhoneNumber());
        parameterSource.addValue("email", partner.getEmail());

        jdbcTemplate.update("INSERT INTO partners(CHAT_ID, POSITION, FULL_NAME, PHONE_NUMBER, EMAIL)" +
                        " VALUES (:chatId, :positionClient, :fullName, :phoneNumber, :email)",
                parameterSource);
    }

    public Partner fetchPartner(long chatId){
        log.info("Получение информации о партнере из базы с chatId {}", chatId);
        try{
            Partner partner = jdbcTemplate.queryForObject("SELECT * FROM PARTNERS WHERE CHAT_ID = :chatId",
                    new MapSqlParameterSource("chatId", chatId), new BeanPropertyRowMapper<>(Partner.class));
            return partner;
        }
        catch(EmptyResultDataAccessException e){
            return null;
        }

    }

    public void forgetPartner(long chatId){
        log.debug("Удаляю информацию о партнере с chatId '{}'", chatId);
        try {
            int deletedCount = jdbcTemplate.update("DELETE FROM PARTNERS WHERE CHAT_ID = :chatId",
                    new MapSqlParameterSource("chatId", chatId));

            if (deletedCount != 1) {
                log.error("На запрос информации о партнере удалилось количество строк, отличное от 1го. deletedCount '{}', chatId '{}'", deletedCount, chatId);
            }

        } catch (EmptyResultDataAccessException e) {
            log.error("Ошибка при удалении заявки");
        }
    }

    public void changePosition(long chatId, String positionClient){
        log.debug("Меняю статус партнера с chatId '{}'", chatId);
        int updateCount = jdbcTemplate.update("UPDATE PARTNERS SET POSITION = :positionClient WHERE CHAT_ID = :chatId",
                new MapSqlParameterSource("chatId", chatId).addValue("positionClient", positionClient));
        if (updateCount != 1) {
            log.error("На запрос информации о партнере удалилось количество строк, отличное от 1го. deletedCount '{}', chatId '{}'", updateCount, chatId);
        }
    }


}
