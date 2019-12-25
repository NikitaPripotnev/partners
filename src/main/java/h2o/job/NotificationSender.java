package h2o.job;

import h2o.bot.OperatorBot;
import h2o.dao.OperatorOrderDao;
import h2o.model.OperatorOrder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Component
@Slf4j
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class NotificationSender {
    private final OperatorOrderDao operatorOrderDao;
    private final OperatorBot operatorBot;


    @Scheduled(cron = "${cron.notification.sender.before.day}")//Кажыдй час
    public void sendNotificationBeforeDay() {

        log.debug("Произвожу отправку уведомлений об принятых заявках, до сдачи которых сутки");
        LocalDateTime checkDate = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("+03:00:00")).toLocalDateTime().plusDays(1);
        List<OperatorOrder> orders = operatorOrderDao.findOrdersBeforeDate(checkDate);
        orders.forEach(order -> operatorBot.sendMessageToAdmin("Внимание, заявка назначена через сутки\n" +
                operatorBot.constructForOperator(order)));
    }

    @Scheduled(cron = "${cron.notification.sender.cleaning.day}")//в 14:00
    public void sendNotificationOnCleaningDay() {

        log.debug("Произвожу отправку уведомлений о выполненых заявках в день уборки");
        LocalDateTime checkDate = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("+03:00:00")).toLocalDateTime().withHour(0).withMinute(0).plusDays(1);
        List<OperatorOrder> orders = operatorOrderDao.findOrdersBeforeDate(checkDate);
        if(orders!=null){
            String message="<b>Список выполненых сегодня заявок:</b>\n";
            for(OperatorOrder order : orders){
                message+=operatorBot.constructForOperator(order)+"\n\n";
            }
            operatorBot.sendMessageToAdmin(message);
        }
    }

}