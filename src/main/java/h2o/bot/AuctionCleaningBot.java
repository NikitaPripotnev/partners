package h2o.bot;

import h2o.dao.OperatorOrderDao;
import h2o.handler.message.Searcher;
import h2o.keyboard.InlineKeyboardBuilder;
import h2o.keyboard.ReplyKeyboardBuilder;
import h2o.model.OperatorOrder;
import h2o.model.TmpInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.groupadministration.ExportChatInviteLink;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AuctionCleaningBot extends TelegramLongPollingBot {

    private String token;
    private String username;
    private long mainOrderChannelId;
    private long operatorChatId;
    private long adminchaId;

    private Searcher searcher;
    private OperatorOrderDao operatorOrderDao;
    private ConcurrentHashMap<Long, TmpInfo> clientTmpInfo;


    @Autowired
    public AuctionCleaningBot(@Value("${auction.bot.token}") String token,
                              @Value("${auction.bot.username}") String username,
                              @Value("${main.order.channel.id}") long mainOrderChannelId,
                              @Value("${operator.id}") long operatorChatId,
                              @Value("${admin.id}") long adminChatId,
                              Searcher searcher,
                              OperatorOrderDao operatorOrderDao) {
        this.token = token;
        this.username = username;
        this.mainOrderChannelId = mainOrderChannelId;
        this.operatorChatId = operatorChatId;
        this.adminchaId = adminChatId;

        this.searcher = searcher;
        this.operatorOrderDao = operatorOrderDao;
        this.clientTmpInfo = new ConcurrentHashMap<>();

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

        long chatId = (update.hasMessage() ? update.getMessage().getFrom().getId() : update.getCallbackQuery().getFrom().getId());//getChat().getId()
        String message=null;
        if (update.hasMessage()) {
            if (update.getMessage().getText() != null) {
                message = update.getMessage().getText();
            } else {
                log.info("Сообщение некорректно от {}", chatId);
                deleteMessage(chatId, update.getMessage().getMessageId());
                return;
            }
        } else if (update.hasCallbackQuery()) {
            message = update.getCallbackQuery().getData();
        } else {
            log.info("Сообщение некорректно от {}", chatId);
        }
        log.info("Входящее сообщение от {}: {}", chatId, message);

        if(message.equals("/start")){
            String inviteMessage = inviteGroup();
            sendMessage(chatId, inviteMessage);
            return;
        }


        if (update.hasCallbackQuery()) {
            if (update.getCallbackQuery().getMessage().getChat().getId() == mainOrderChannelId) {
                int orderId = searcher.searchOrderId(message);
                if (orderId == 0) {
                    sendMessage(adminchaId, "Ошибка, напиши программисту\nDATE: " + LocalDateTime.now());
                    log.error("Не нашел orderId в сообщении {}", message);
                    return;
                }
                String command = message.substring(String.valueOf(orderId).length() + 1);
                if (command.equals("BOOK")) {
                    OperatorOrder order = operatorOrderDao.fetchOperatorOrder(orderId);
                    if (order.getChatIdOwner() == 0) {
                        editMessage(mainOrderChannelId, update.getCallbackQuery().getMessage().getMessageId(), constructOperatorOrderMessage(order, false, false),
                                InlineKeyboardBuilder.create().row().button("☑Забронировано", orderId + "_NONE_INFO").endRow().build());
                        operatorOrderDao.approveOrder(orderId, update.getCallbackQuery().getFrom().getId());
                        if (order.isFullOrder()) {
                            sendMessage(chatId, constructOperatorOrderMessage(order, true, false),
                                    InlineKeyboardBuilder.create().row().button("✅Выполнено", orderId + "_COMPLETE").endRow().row().button("❌Отмена", orderId + "_DENIED").endRow().build());

                        } else {
                            sendMessage(chatId, constructOperatorOrderMessage(order, true, false),
                                    InlineKeyboardBuilder.create().row().button("✏Указать точные дату и сумму", orderId + "_EDIT").endRow().row().button("❌Отмена", orderId + "_DENIED").endRow().build());
                        }
                        sendMessageWithRemoveKeyboard(chatId, "Вы <b>забронировали</b> заявку! Если вы сделали это <b>случайно</b>, нажмите кнопку \"❌Отмена\" и укажите причину. <b>Не забывайте</b> нажимать \"✅Выполнено\" после выполнения заявки");
                        sendMessage(operatorChatId, constructForOperator(order) + "\n\n\uD83D\uDD50<b>Заявка принята!</b>");

                    } else {
                        sendMessage(chatId, "Извините, заявка уже забронирована\uD83D\uDE14");
                    }
                    return;
                }
                if (command.equals("NONE_INFO")) {
                    return;
                }
            }
            //callback в личке. Нужно брать заявку из базы и проверять чат такой же, либо 0.
            else{
                int orderId = searcher.searchOrderId(message);
                if (orderId == 0) {
                    sendMessage(adminchaId, "Ошибка, напиши программисту\nDATE: " + LocalDateTime.now());
                    log.error("Не нашел orderId в сообщении {}", message);
                    return;
                }
                OperatorOrder order = operatorOrderDao.fetchOperatorOrder(orderId);
                if(order.getChatIdOwner()==0||order.getChatIdOwner()!=chatId) {
                    sendMessage(chatId, "Вы не забронировали данную заявку");
                    return;
                }
                if(order.isCompleted()){
                    sendMessage(chatId, "Данная заявка уже выполнена");
                }
                String command = message.substring(String.valueOf(orderId).length() + 1);
                if (command.equals("COMPLETE")) {
                    sendMessage(chatId, "Вы уверены?",
                            ReplyKeyboardBuilder.create().row().button("✅Да").button("❌Нет").endRow().build());
                    clientTmpInfo.put(chatId, new TmpInfo());
                    clientTmpInfo.get(chatId).setOrderId(orderId);
                    clientTmpInfo.get(chatId).setCompleteCheck(true);
                    clientTmpInfo.get(chatId).setMessageId(update.getCallbackQuery().getMessage().getMessageId());
                    return;
                }
                if (command.equals("EDIT")) {
                    sendMessage(chatId, "Введите дату и время уборки, на которое вы договорились с клиентом.\n\nПример: <code>12.12.2019 15:30</code>",
                            ReplyKeyboardBuilder.create().row().button("❌Отмена").endRow().build());
                    clientTmpInfo.put(chatId, new TmpInfo());
                    clientTmpInfo.get(chatId).setOrderId(orderId);
                    clientTmpInfo.get(chatId).setEditCheck(true);
                    clientTmpInfo.get(chatId).setMessageId(update.getCallbackQuery().getMessage().getMessageId());
                    clientTmpInfo.get(chatId).setOperatorOrder(new OperatorOrder());
                    return;

                }
                if (command.equals("DENIED")) {
                    sendMessage(chatId, "Укажите причину отказа",
                            ReplyKeyboardBuilder.create().row().button("❌Отмена").endRow().build());
                    clientTmpInfo.put(chatId, new TmpInfo());
                    clientTmpInfo.get(chatId).setDeniedCheck(true);
                    clientTmpInfo.get(chatId).setOrderId(orderId);
                    clientTmpInfo.get(chatId).setMessageId(update.getCallbackQuery().getMessage().getMessageId());
                    return;
                }
            }
        }
        //Если не колбэкквери
        if (chatId == mainOrderChannelId) {
            deleteMessage(chatId, update.getMessage().getMessageId());
            return;
        }

        //Пользователь пишет в бота
        if (clientTmpInfo.get(chatId) != null) {
            if (message.equals("❌Отмена")) {
                clientTmpInfo.remove(chatId);
                return;
            }
            if (clientTmpInfo.get(chatId).isDeniedCheck()) {
                operatorOrderDao.deniedOrder(clientTmpInfo.get(chatId).getOrderId(),  message);
                OperatorOrder order = operatorOrderDao.fetchOperatorOrder(clientTmpInfo.get(chatId).getOrderId());
                sendMessage(operatorChatId, "❌<b>Заявка отменена в общей группе!</b>\n\n" + "<b>Причина:</b> "+constructForOperator(order));
                editMessage(mainOrderChannelId, order.getMessageId(), constructOperatorOrderMessage(order, false,false),
                        InlineKeyboardBuilder.create().row().button("✅Забронировать", clientTmpInfo.get(chatId).getOrderId() + "_BOOK").endRow().build());
                deleteMessage(chatId, clientTmpInfo.get(chatId).getMessageId());
                sendMessageWithRemoveKeyboard(chatId, "Вы отменили бронирование заявки.");
                clientTmpInfo.remove(chatId);
                return;
            }
            if (clientTmpInfo.get(chatId).isCompleteCheck()) {
                if (message.equals("✅Да")) {
                    operatorOrderDao.changeStatus(clientTmpInfo.get(chatId).getOrderId(), "COMPLETE", true);
                    OperatorOrder order = operatorOrderDao.fetchOperatorOrder(clientTmpInfo.get(chatId).getOrderId());
                    sendMessageWithRemoveKeyboard(chatId, "Спасибо за сотрудничество❤");
                    sendMessage(operatorChatId, "✅<b>Заявка выполнена в общей группе!</b>\n\n"+constructForOperator(order));
                    deleteMessage(mainOrderChannelId, order.getMessageId());
                    deleteMessage(chatId, clientTmpInfo.get(chatId).getMessageId());
                }
                else{
                    sendMessageWithRemoveKeyboard(chatId, "Действие отменено");
                }
                clientTmpInfo.remove(chatId);
                return;
            }
            if (clientTmpInfo.get(chatId).isEditCheck()) {
                if (clientTmpInfo.get(chatId).getOperatorOrder().getCleaningDate() == null) {
                    LocalDateTime dateTime = parseLocalDateTime(message);
                    if(dateTime!=null){
                        clientTmpInfo.get(chatId).getOperatorOrder().setCleaningDate(dateTime);
                        sendMessage(chatId, "Теперь укажите сумму в рублях");
                    }
                    else{
                        sendMessage(chatId, "Неверный формат сообщения");
                    }
                    return;
                }
                if(clientTmpInfo.get(chatId).getOperatorOrder().getPaymentAmount()==0){
                    int payment;
                    try {
                        payment = Integer.parseInt(message);
                    } catch (Throwable e) {
                        sendMessage(chatId, "Можно вписать только число");
                        return;
                    }
                    clientTmpInfo.get(chatId).getOperatorOrder().setPaymentAmount(payment);
                    operatorOrderDao.updateDateAndPaymentAmount(clientTmpInfo.get(chatId).getOrderId(), clientTmpInfo.get(chatId).getOperatorOrder());
                    OperatorOrder order = operatorOrderDao.fetchOperatorOrder(clientTmpInfo.get(chatId).getOrderId());
                    sendMessage(operatorChatId, "<b>Партнер "+
                            (update.getMessage().getFrom().getUserName()!=null?("@"+update.getMessage().getFrom().getUserName()):"")
                            +" из Общей группы внес изменения в заявку</b>\n\n"
                            +constructForOperator(order)+"\n<b>Стоимость:</b> " + order.getPaymentAmount()+"р.");
                    editMessage(chatId, clientTmpInfo.get(chatId).getMessageId(), constructOperatorOrderMessage(order, true, false),
                            InlineKeyboardBuilder.create().row().button("✅Выполнено", clientTmpInfo.get(chatId).getOrderId() + "_COMPLETE").endRow()
                                    .row().button("❌Отмена", clientTmpInfo.get(chatId).getOrderId() + "_DENIED").endRow().build());
                    sendMessageWithRemoveKeyboard(chatId, "Изменения внесены успешно");
                    clientTmpInfo.remove(chatId);
                    return;
                }
            }

        }


    }


    private String constructOperatorOrderMessage(OperatorOrder order, boolean clientName, boolean orderAuthor) {
        String textDate, textPrice;
        String textNameAndNumber = "", textAuthor = "", textOrderDate = "";
        if (!order.isFullOrder()) {
            textDate = "<b>Желаемая дата и время:</b> <code>";
            textPrice = "<b>Желаемая стоимость:</b> ";
        } else {
            textDate = "<b>Дата уборки и время:</b> <code>";
            textPrice = "<b>Стоимость:</b> ";
        }
        if (clientName) {
            textNameAndNumber = "<b>Имя клиента: </b> " + order.getClientName() + "\n" +
                    "<b>Телефон клиента: </b> <code>" + order.getClientNumber() + "</code> \n";
        }
        if (orderAuthor) {
            textAuthor = "<b>Кто оформил заявку:</b> " + order.getOrderAuthor();
            textOrderDate = "<b>Дата оформления заявки:</b> " + formatDate(order.getOrderCreationTime()) + "\n";
        }
        return  (order.isFullOrder() ? "" : "<b>Заявка с обсуждаемой суммой и датой</b>\n") +
                textDate + formatDate(order.getCleaningDate()) + "</code>\n" +
                "<b>Адрес:</b> " + order.getAddress() + "\n" +
                "<b>Контрагент:</b> " + order.getCounterparty() + "\n" +
                "<b>Вид уборки:</b> " + order.getTypeCleaning() + "\n" +
                "<b>Вид объекта:</b> " + order.getTypeObject() + "\n" +
                textPrice + order.getPaymentAmount() + "р.\n" +
                textNameAndNumber +
                "<b>Способ оплаты:</b> " + order.getPaymentMethod() + "\n" +
                "<b>Комментарий:</b> " + order.getComment() + "\n" +
                textOrderDate +
                textAuthor;
    }

    public String constructForOperator(OperatorOrder order) {
        return "<b>Контрагент:</b> " + order.getCounterparty() + "\n<b>Имя клиента и телефон:</b> " + order.getClientName() +" "+ order.getClientNumber() + "\n<b>Дата и время:</b> " + formatDate(order.getCleaningDate());
    }

    private LocalDateTime parseLocalDateTime(String message) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        if (message.matches("[0-3]?[0-9][.][0-1][0-9][.][2][0][1-4][0-9][\\s][0-2][0-9][:][0-5][0-9]")) {
            if (message.substring(1, 2).equals(".")) {
                formatter = DateTimeFormatter.ofPattern("d.MM.yyyy HH:mm");
            }
            return LocalDateTime.parse(message, formatter);
        } else {
            return null;
        }
    }

    private String formatDate(LocalDateTime date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        return date.format(formatter);
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

    public int sendMessage(long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        sendMessage.setReplyMarkup(markup);
        sendMessage.setParseMode("HTML");
        sendMessage.disableWebPagePreview();
        try {
            log.info("send message with Inlinekeyboard \"{}\" to {}", text, chatId);
            return execute(sendMessage).getMessageId();
            //chatHistoryDAO.saveMessage(new MessageDAO(chatId, LocalDateTime.now(), text, "BOT"));
        } catch (TelegramApiException e) {
            log.error("Failed to send message with Inlinekeyboard \"{}\" to {}", text, chatId, e);
            return 0;
        }
    }

    public void sendMessageToAdmin(String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(operatorChatId);
        sendMessage.setText(text);
        sendMessage.setParseMode("HTML");
        sendMessage.disableWebPagePreview();
        try {
            execute(sendMessage);
            //chatHistoryDAO.saveMessage(new MessageDAO(chatId, LocalDateTime.now(), text, "BOT"));
            log.info("send message to Admin: {}", text);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to Admin: {}, {}", text, e.getMessage());
        }
    }

    private void editMessage(long chatId, int messageId, String text, InlineKeyboardMarkup markup) {
        EditMessageText editMessageText = new EditMessageText();
        editMessageText.setChatId(chatId);
        editMessageText.setMessageId(messageId);
        editMessageText.setParseMode("HTML");
        editMessageText.setText(text);
        editMessageText.setReplyMarkup(markup);


        try {
            execute(editMessageText);
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
            if (execute(deleteMessage))
            log.info("delete message to {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to delete message to {}", chatId, e);
        }
    }

    private String inviteGroup(){
        ExportChatInviteLink exportChatInviteLink = new ExportChatInviteLink();
        exportChatInviteLink.setChatId(mainOrderChannelId);
        try {
            String inviteMessage = execute(exportChatInviteLink);
            log.info("Приглашение в группу {}", mainOrderChannelId);
            return inviteMessage;
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить инвайт для {}", mainOrderChannelId, e);
        }
        return null;
    }

}
