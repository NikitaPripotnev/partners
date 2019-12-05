package h2o.bot;

import h2o.dao.OperatorOrderDao;
import h2o.handler.message.Searcher;
import h2o.keyboard.InlineKeyboardBuilder;
import h2o.keyboard.ReplyKeyboardBuilder;
import h2o.model.OperatorOrder;
import h2o.model.TmpInfo;
import h2o.service.OperatorOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class OperatorBot extends TelegramLongPollingBot {

    private String token;
    private String username;
    private long adminchaId;
    private long operatorChatId;
    private long firstOrderChannelId;
    private long secondOrderChannelId;
    private String welcomeText;
    private ConcurrentHashMap<Long, OperatorOrder> clientOperatorOrders;
    private ConcurrentHashMap<Long, TmpInfo> groupTmpInfo;


    private OperatorOrderService operatorOrderService;
    private OperatorOrderDao operatorOrderDao;
    private Searcher searcher;

    @Autowired
    public OperatorBot(@Value("${operator.bot.token}") String token,
                       @Value("${operator.bot.username}") String username,
                       @Value("${admin.id}") long adminChatId,
                       @Value("${operator.id}") long operatorChatId,
                       @Value("${first.order.channel.id}") long firstOrderChannelId,
                       @Value("${second.order.channel.id}") long secondOrderChannelId,
                       Searcher searcher,
                       OperatorOrderService operatorOrderService,
                       OperatorOrderDao operatorOrderDao) {
        super();
        this.token = token;
        this.username = username;
        this.adminchaId = adminChatId;
        this.operatorChatId = operatorChatId;
        this.firstOrderChannelId = firstOrderChannelId;
        this.secondOrderChannelId = secondOrderChannelId;
        this.clientOperatorOrders = new ConcurrentHashMap<>();
        this.groupTmpInfo = new ConcurrentHashMap<>();
        this.welcomeText = "Привет! Я H2o бот помощник! Готов создать заявку?";
        this.operatorOrderDao = operatorOrderDao;
        this.operatorOrderService = operatorOrderService;
        this.searcher = searcher;


    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        long chatId = (update.hasMessage() ? update.getMessage().getFrom().getId() : update.getCallbackQuery().getFrom().getId());
        String message;
        if (update.hasMessage()) {
            if (update.getMessage().getText() != null) {
                message = update.getMessage().getText();
            } else {
                log.info("Сообщение некорректно от {}", chatId);
                return;
            }
        } else if (update.hasCallbackQuery()) {
            message = update.getCallbackQuery().getData();
        } else {
            log.info("Сообщение некорректно от {}", chatId);
            return;
        }
        log.info("Входящее сообщение от {}: {}", chatId, message);

        if (update.hasCallbackQuery()) {
            long groupId = update.getCallbackQuery().getMessage().getChat().getId();
            if (groupId == firstOrderChannelId || groupId == secondOrderChannelId) {
                int orderId = searcher.searchOrderId(message);
                if (orderId == 0) {
                    sendMessage(adminchaId, "Ошибка, напиши программисту\nDATE: " + LocalDateTime.now());
                    log.error("Не нашел orderId в сообщении {}", message);
                    return;
                }
                String command = message.substring(String.valueOf(orderId).length() + 1);
                if (command.equals("YES")) {
                    OperatorOrder order = operatorOrderDao.fetchOperatorOrder(orderId);
                    sendMessage(operatorChatId, constructForOperator(order) + "\n\n\uD83D\uDD50<b>Заявка принята!</b>");
                    operatorOrderDao.changeStatus(orderId, "APPROVE", true);
                    editMessage(update.getCallbackQuery().getMessage().getChat().getId(), update.getCallbackQuery().getMessage().getMessageId(), InlineKeyboardBuilder.create().row().button("✅Выполнено", orderId + "_COMPLETE").endRow().row().button("❌Отмена", orderId + "_CANCEL").endRow().build());
                    return;
                }
                if (command.equals("NO")) {
                    int messageId = sendMessage(groupId, "Укажите причину отказа",
                            ReplyKeyboardBuilder.create().row().button("❌Отмена").endRow().build());
                    groupTmpInfo.put(groupId, new TmpInfo());
                    groupTmpInfo.get(groupId).setOrderId(orderId);
                    groupTmpInfo.get(groupId).setMessageId(update.getCallbackQuery().getMessage().getMessageId());
                    groupTmpInfo.get(groupId).setDeniedCheck(true);
                    groupTmpInfo.get(groupId).addTrashMessage(messageId);
                    return;
                }
                if(command.equals("COMPLETE")){
                    int messageId = sendMessage(groupId, "Вы уверены?",
                            ReplyKeyboardBuilder.create().row().button("✅Да").button("❌Нет").endRow().build());
                    groupTmpInfo.put(groupId, new TmpInfo());
                    groupTmpInfo.get(groupId).setOrderId(orderId);
                    groupTmpInfo.get(groupId).setCompleteCheck(true);
                    groupTmpInfo.get(groupId).setMessageId(update.getCallbackQuery().getMessage().getMessageId());
                    groupTmpInfo.get(groupId).addTrashMessage(messageId);
                    return;
                }
                if(command.equals("DENIED")){
                    int messageId = sendMessage(groupId, "Укажите причину отказа",
                            ReplyKeyboardBuilder.create().row().button("❌Отмена").endRow().build());
                    groupTmpInfo.put(groupId, new TmpInfo());
                    groupTmpInfo.get(groupId).setDeniedCheck(true);
                    groupTmpInfo.get(groupId).setOrderId(orderId);
                    groupTmpInfo.get(groupId).setMessageId(update.getCallbackQuery().getMessage().getMessageId());
                    groupTmpInfo.get(groupId).addTrashMessage(messageId);
                    return;
                }
                if(command.equals("CANCEL")){
                    editMessage(groupId, update.getCallbackQuery().getMessage().getMessageId(),
                            InlineKeyboardBuilder.create().row().button("✅Да", orderId + "_YES").button("❌Нет", orderId + "_NO").endRow().build());
                    OperatorOrder order = operatorOrderDao.fetchOperatorOrder(orderId);
                    sendMessage(operatorChatId, constructForOperator(order) + "\n\n⚠Отмена принятие заявки");
                    return;
                }

            }else{
                if(message.equals("NONE_INFO")){
                    return;
                }

                String numberGroup = message.substring(0, 1);
                groupId = numberGroup.equals("1")?firstOrderChannelId:secondOrderChannelId;
                int orderId = Integer.parseInt(message.substring(2));
                OperatorOrder operatorOrder = operatorOrderDao.fetchOperatorOrder(orderId);
                sendMessage(groupId, constructOperatorOrderMessage(operatorOrder),
                        InlineKeyboardBuilder.create().row().button("✅Да", orderId + "_YES").button("❌Нет", orderId + "_NO").endRow().build());
                editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(),
                        InlineKeyboardBuilder.create().row().button("✅Заявка отправлена", "NONE_INFO").endRow().build());
                return;
            }

        }

        if(update.getMessage().getChat().getId() == firstOrderChannelId || update.getMessage().getChat().getId() == secondOrderChannelId){
            long groupId = update.getMessage().getChat().getId();
            if(groupTmpInfo.get(groupId)!=null){
                if(groupTmpInfo.get(groupId).isDeniedCheck()){
                    if(message.equals("❌Отмена")){
                        deleteTrashMessages(groupId, groupTmpInfo.get(groupId).getTrashMessages());
                        deleteMessage(groupId, update.getMessage().getMessageId());
                        groupTmpInfo.remove(groupId);
                        return;
                    }
                    operatorOrderDao.changeString(groupTmpInfo.get(groupId).getOrderId(), "DENIED_COMMENT", message);
                    operatorOrderDao.changeStatus(groupTmpInfo.get(groupId).getOrderId(), "DENIED", true);
                    operatorOrderDao.changeStatus(groupTmpInfo.get(groupId).getOrderId(), "APPROVE", false);
                    String numberGroup = groupId==firstOrderChannelId ? "2":"1";
                    sendMessage(operatorChatId, constructForOperator(operatorOrderDao.fetchOperatorOrder(groupTmpInfo.get(groupId).getOrderId()))+"\n\n❌<b>Заявка отменена!</b>\n"+"<b>Причина:</b> "+message,
                            InlineKeyboardBuilder.create().row().button("\uD83D\uDCE8Отправить в группу #"+numberGroup, numberGroup+"_"+groupTmpInfo.get(groupId).getOrderId()).endRow().build());
                    deleteTrashMessages(groupId, groupTmpInfo.get(groupId).getTrashMessages());
                    deleteMessage(groupId, groupTmpInfo.get(groupId).getMessageId());
                    deleteMessage(groupId, update.getMessage().getMessageId());
                    groupTmpInfo.remove(groupId);
                    return;
                }
                if(groupTmpInfo.get(groupId).isCompleteCheck()){
                    if(message.equals("✅Да")){
                        operatorOrderDao.changeStatus(groupTmpInfo.get(groupId).getOrderId(), "COMPLETED", true);
                        sendMessage(operatorChatId, constructForOperator(operatorOrderDao.fetchOperatorOrder(groupTmpInfo.get(groupId).getOrderId()))+"\n\n✅<b>Заявка выполнена!</b>");
                        deleteTrashMessages(groupId, groupTmpInfo.get(groupId).getTrashMessages());
                        deleteMessage(groupId, groupTmpInfo.get(groupId).getMessageId());
                        deleteMessage(groupId, update.getMessage().getMessageId());
                        groupTmpInfo.remove(groupId);
                        return;
                    }
                    else{
                        deleteTrashMessages(groupId, groupTmpInfo.get(groupId).getTrashMessages());
                        deleteMessage(groupId, update.getMessage().getMessageId());
                        groupTmpInfo.remove(groupId);
                        return;
                    }
                }
            }else{
                deleteMessage(groupId, update.getMessage().getMessageId());
            }
            return;
        }

        if (message.equals("/start") || message.equals("❌Отмена")) {
            clientOperatorOrders.remove(chatId);
            sendMessage(chatId, welcomeText, ReplyKeyboardBuilder.create().row().button("\uD83D\uDCDDСоздать заявку").endRow().build());
            return;
        }


        if (message.equals("\uD83D\uDCDDСоздать заявку") && clientOperatorOrders.get(chatId) == null) {
            clientOperatorOrders.put(chatId, new OperatorOrder());
            sendMessage(chatId, "Введите дату и время в формате 28.11.2019 14:48", ReplyKeyboardBuilder.create().row().button("❌Отмена").endRow().build());
            return;
        }

        if (clientOperatorOrders.get(chatId) != null) {
            if (clientOperatorOrders.get(chatId).getCleaningDate() == null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
                if (message.matches("[0-3]?[0-9][.][0-1][0-9][.][2][0][1-4][0-9][\\s][0-2][0-9][:][0-5][0-9]")) {
                    if (message.substring(1, 2).equals(".")) {
                        formatter = DateTimeFormatter.ofPattern("d.MM.yyyy HH:mm");
                    }
                    clientOperatorOrders.get(chatId).setCleaningDate(LocalDateTime.parse(message, formatter));
                    sendMessage(chatId, "Укажите адрес");
                    return;
                } else {
                    sendMessage(chatId, "Невереный формат сообщения");
                }
                return;
            }

            if (clientOperatorOrders.get(chatId).getAddress() == null) {
                clientOperatorOrders.get(chatId).setAddress(message);
                sendMessage(chatId, "Укажите контрагента",
                        ReplyKeyboardBuilder.create().row().button("Компания").endRow().row().button("Физ. лицо").endRow().row().button("❌Отмена").endRow().build());
                return;
            }

            if (clientOperatorOrders.get(chatId).getCounterparty() == null) {
                clientOperatorOrders.get(chatId).setCounterparty(message);
                sendMessage(chatId, "Укажите вид уборки",
                        ReplyKeyboardBuilder.create().row().button("Под. уб").endRow()
                                                    .row().button("Ген. уб").endRow()
                                .row().button("Послестрой (стандарт)").endRow()
                                .row().button("Послестрой (стандарт+)").endRow()
                                .row().button("Послестрой (комплексная)").endRow()
                                .row().button("Химчистка").endRow()
                                .row().button("Остекление").endRow()
                                .row().button("Роторная чистка").endRow()
                                .row().button("Фасады").endRow()
                                .row().button("Спец. работы").endRow()
                                .row().button("❌Отмена").endRow().build());

                return;
            }

            if (clientOperatorOrders.get(chatId).getTypeCleaning() == null) {
                clientOperatorOrders.get(chatId).setTypeCleaning(message);
                sendMessage(chatId, "Укажите Вид объекта", ReplyKeyboardBuilder.create().row().button("❌Отмена").endRow().build());
                return;
            }

            if (clientOperatorOrders.get(chatId).getTypeObject() == null) {
                clientOperatorOrders.get(chatId).setTypeObject(message);
                sendMessage(chatId, "Укажите стоимость");
                return;
            }

            if (clientOperatorOrders.get(chatId).getPaymentAmount() == 0) {
                int payment = 0;
                try{
                    payment = Integer.parseInt(message);
                }
                catch (Throwable e){
                    sendMessage(chatId, "Можно вписать только число");
                    return;
                }
                clientOperatorOrders.get(chatId).setPaymentAmount(payment);
                sendMessage(chatId, "Укажите имя клиента и номер телефона");
                return;
            }

            if (clientOperatorOrders.get(chatId).getClientName() == null) {
                clientOperatorOrders.get(chatId).setClientName(message);
                sendMessage(chatId, "Укажите способ оплаты",
                        ReplyKeyboardBuilder.create().row().button("Наличные").endRow()
                                .row().button("р/c (без НДС)").endRow()
                                .row().button("р/c (НДС)").endRow().row().button("❌Отмена").endRow().build());
                return;
            }

            if (clientOperatorOrders.get(chatId).getPaymentMethod() == null) {
                clientOperatorOrders.get(chatId).setPaymentMethod(message);
                sendMessage(chatId, "Укажите источник заявки",
                        ReplyKeyboardBuilder.create().row().button("Реклама(Поиск)").endRow()
                                .row().button("Авито").endRow()
                                .row().button("Instagram").endRow()
                                .row().button("VK").endRow()
                                .row().button("от Партнеров").endRow()
                                .row().button("Рекомендации").endRow()
                                .row().button("Повторный заказ").endRow()
                                .row().button("Постоянка").endRow()
                                .row().button("Абонемент").endRow()
                                .row().button("Сайт(SEO)").endRow()
                                .row().button("❌Отмена").endRow()
                                .build() );
                return;
            }

            if (clientOperatorOrders.get(chatId).getOrderSource() == null) {
                clientOperatorOrders.get(chatId).setOrderSource(message);
                sendMessage(chatId, "Напишите комментарий к заявке",
                        ReplyKeyboardBuilder.create().row().button("❌Отмена").endRow().build());
                return;
            }

            if (clientOperatorOrders.get(chatId).getComment() == null) {
                clientOperatorOrders.get(chatId).setComment(message);
                sendMessage(chatId, "Заявку оформил",
                        ReplyKeyboardBuilder.create()
                                .row().button("Екатерина").endRow()
                                .row().button("Вениамин").endRow()
                                .row().button("Олег").endRow()
                                .row().button("Илья").endRow()
                                .row().button("Настя").endRow()
                                .row().button("❌Отмена").endRow()
                                .build());
                return;
            }

            clientOperatorOrders.get(chatId).setOrderCreationTime(ZonedDateTime.now().withZoneSameInstant(ZoneId.of("+03:00:00")).toLocalDateTime());

            if (clientOperatorOrders.get(chatId).getOrderAuthor() == null) {
                clientOperatorOrders.get(chatId).setOrderAuthor(message);
                sendMessage(chatId, constructOperatorOrderMessage(clientOperatorOrders.get(chatId)),
                        ReplyKeyboardBuilder.create().row().button("1").button("2").endRow().row().button("❌Нет").endRow().build());
                return;
            }

            if(message.equals("1")||message.equals("2")){
                long groupId = message.equals("1") ? firstOrderChannelId : secondOrderChannelId;
                int orderId = operatorOrderService.createOrder(clientOperatorOrders.get(chatId));
                if (orderId == 0) {
                    sendMessage(chatId, "Не получилось создать заявку, обратитесь к программисту");
                    clientOperatorOrders.remove(chatId);
                    return;
                }
                sendMessage(groupId, constructOperatorOrderMessage(clientOperatorOrders.get(chatId)),
                        InlineKeyboardBuilder.create().row().button("✅Да", orderId + "_YES").button("❌Нет", orderId + "_NO").endRow().build());
                sendMessage(chatId, "Заявка отправлена!", ReplyKeyboardBuilder.create().row().button("\uD83D\uDCDDСоздать заявку").endRow().build());
                clientOperatorOrders.remove(chatId);
                return;
            }
            if (message.equals("❌Нет")) {
                clientOperatorOrders.remove(chatId);
                sendMessage(chatId, welcomeText, ReplyKeyboardBuilder.create().row().button("\uD83D\uDCDDСоздать заявку").endRow().build());
                return;
            }

        }

        if(message.matches("/.*")){
            if(message.equals("/deleteOrders")){

            }
        }


    }

    private String constructOperatorOrderMessage(OperatorOrder order) {
        return "<b>Дата уборки и время</b> " + formatDate(order.getCleaningDate()) + "\n" +
                "<b>Адрес:</b> " + order.getAddress() + "\n" +
                "<b>Контрагент:</b> " + order.getCounterparty() + "\n" +
                "<b>Вид уборки:</b> " + order.getTypeCleaning() + "\n" +
                "<b>Вид объекта:</b> " + order.getTypeObject() + "\n" +
                "<b>Стоимость:</b> " + order.getPaymentAmount() + "р.\n" +
                "<b>Имя клиента и телефон:</b> " + order.getClientName() + "\n" +
                "<b>Способ оплаты:</b> " + order.getPaymentMethod() + "\n" +
                "<b>Комментарий:</b> " + order.getComment() + "\n" +
                "<b>Дата оформления заявки:</b> " + formatDate(order.getOrderCreationTime()) + "\n" +
                "<b>Кто оформил заявку:</b> " + order.getOrderAuthor();
    }

    private String constructForOperator(OperatorOrder order){
        return "<b>Контрагент:</b> " + order.getCounterparty()+ "\n<b>Имя клиента и телефон:</b> " + order.getClientName();
    }

    private String formatDate(LocalDateTime date){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        return date.format(formatter);
    }

    public void deleteTrashMessages(long chatId, List<Integer> trashMessage){
        trashMessage.forEach((messageId -> deleteMessage(chatId, messageId)));
    }

    private int sendMessage(long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        sendMessage.setParseMode("HTML");
        sendMessage.disableWebPagePreview();
        try {
            Message message = execute(sendMessage);
            //chatHistoryDAO.saveMessage(new MessageDAO(chatId, LocalDateTime.now(), text, "BOT"));
            log.info("send message \"{}\" to {}\"", text, chatId);
            return message.getMessageId();
        } catch (TelegramApiException e) {
            log.error("Failed to send message \"{}\" to {}\"", text, chatId, e);
            return 0;
        }
    }

    private void sendMessageWithRemoveKeyboard(long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        sendMessage.setParseMode("HTML");
        sendMessage.setReplyMarkup(new ReplyKeyboardRemove());
        sendMessage.disableWebPagePreview();
        try {
            execute(sendMessage);
            //chatHistoryDAO.saveMessage(new MessageDAO(chatId, LocalDateTime.now(), text, "BOT"));
            log.info("send message with remove keyboard\"{}\" to {}\"", text, chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send message  with remove keyboard\"{}\" to {}\"", text, chatId, e);
        }
    }

    private int sendMessage(long chatId, String text, ReplyKeyboardMarkup markup) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        sendMessage.setReplyMarkup(markup);
        sendMessage.setParseMode("HTML");
        sendMessage.disableWebPagePreview();
        try {
            Message message = execute(sendMessage);
            //chatHistoryDAO.saveMessage(new MessageDAO(chatId, LocalDateTime.now(), text, "BOT"));
            log.info("send message with Replykeyboard \"{}\" to {}", text, chatId);
            return message.getMessageId();
        } catch (TelegramApiException e) {
            log.error("Failed to send message with Replykeyboard \"{}\" to {}", text, chatId, e);
            return 0;
        }
    }

    private void sendMessage(long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        sendMessage.setReplyMarkup(markup);
        sendMessage.setParseMode("HTML");
        sendMessage.disableWebPagePreview();
        try {
            execute(sendMessage);
            //chatHistoryDAO.saveMessage(new MessageDAO(chatId, LocalDateTime.now(), text, "BOT"));
            log.info("send message with Inlinekeyboard \"{}\" to {}", text, chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send message with Inlinekeyboard \"{}\" to {}", text, chatId, e);
        }
    }

    private void editMessage(long chatId, int messageId, InlineKeyboardMarkup markup) {
        EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();
        editMessageReplyMarkup.setChatId(chatId);
        editMessageReplyMarkup.setMessageId(messageId);
        editMessageReplyMarkup.setReplyMarkup(markup);
        try {
            execute(editMessageReplyMarkup);
            //chatHistoryDAO.saveMessage(new MessageDAO(chatId, LocalDateTime.now(), text, "BOT"));
            log.info("edit message with Inlinekeyboard to {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed edit message with Inlinekeyboard to {}", chatId, e);
        }
    }


    private void deleteMessage(long chatId, int messageId) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId);
        deleteMessage.setMessageId(messageId);

        try {
            if (!execute(deleteMessage)) {
                sendMessage(chatId, "Сообщение не было удалено. Возможно прошло 48 часов с момента отправки сообщения, либо неверен messageId");
            }
            log.info("delete message to {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to delete message to {}", chatId, e);
        }
    }
}
