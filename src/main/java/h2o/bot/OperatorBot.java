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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class OperatorBot extends TelegramLongPollingBot {

    private String token;
    private String username;
    private long adminchaId;
    private long operatorChatId;
    private long firstOrderChannelId;
    private long secondOrderChannelId;
    private long mainOrderChannelId;
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
                       @Value("${main.order.channel.id}") long mainOrderChannelId,
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
        this.mainOrderChannelId = mainOrderChannelId;
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
        long chatId = (update.hasMessage() ? update.getMessage().getFrom().getId() : update.getCallbackQuery().getFrom().getId());//getChat().getId()
        String message;
        if (update.hasMessage()) {
            if (update.getMessage().getText() != null) {
                message = update.getMessage().getText();
            } else {
                deleteMessage(update.getMessage().getChat().getId(), update.getMessage().getMessageId());
                log.info("Сообщение некорректно от {}", chatId);
                return;
            }
        } else if (update.hasCallbackQuery()) {
            message = update.getCallbackQuery().getData();
        } else {
            log.info("Сообщение некорректно от {}", chatId);
            // deleteMessage(chatId, update..getMessage().getMessageId());
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
                    if (order.isFullOrder()) {
                        editMessage(update.getCallbackQuery().getMessage().getChat().getId(), update.getCallbackQuery().getMessage().getMessageId(), constructOperatorOrderMessage(order, true, false),
                                InlineKeyboardBuilder.create().row().button("✅Выполнено", orderId + "_COMPLETE").endRow().row().button("❌Отмена", orderId + "_CANCEL").endRow().build());

                    } else {
                        editMessage(update.getCallbackQuery().getMessage().getChat().getId(), update.getCallbackQuery().getMessage().getMessageId(), constructOperatorOrderMessage(order, true, false),
                                InlineKeyboardBuilder.create().row().button("✏Указать точные дату и сумму", orderId + "_EDIT").endRow().row().button("❌Отмена", orderId + "_CANCEL").endRow().build());

                    }
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
                if (command.equals("COMPLETE")) {
                    int messageId = sendMessage(groupId, "Вы уверены?",
                            ReplyKeyboardBuilder.create().row().button("✅Да").button("❌Нет").endRow().build());
                    groupTmpInfo.put(groupId, new TmpInfo());
                    groupTmpInfo.get(groupId).setOrderId(orderId);
                    groupTmpInfo.get(groupId).setCompleteCheck(true);
                    groupTmpInfo.get(groupId).setMessageId(update.getCallbackQuery().getMessage().getMessageId());
                    groupTmpInfo.get(groupId).addTrashMessage(messageId);
                    return;
                }
                if (command.equals("EDIT")) {
                    int messageId = sendMessage(groupId, "Введите дату и время уборки, на которое вы договорились с клиентом.\n\nПример: <code>12.12.2019 15:30</code>",
                            ReplyKeyboardBuilder.create().row().button("❌Отмена").endRow().build());
                    groupTmpInfo.put(groupId, new TmpInfo());
                    groupTmpInfo.get(groupId).setOrderId(orderId);
                    groupTmpInfo.get(groupId).setEditCheck(true);
                    groupTmpInfo.get(groupId).setMessageId(update.getCallbackQuery().getMessage().getMessageId());
                    groupTmpInfo.get(groupId).setOperatorOrder(new OperatorOrder());
                    groupTmpInfo.get(groupId).addTrashMessage(messageId);
                    return;

                }
                if (command.equals("DENIED")) {
                    int messageId = sendMessage(groupId, "Укажите причину отказа",
                            ReplyKeyboardBuilder.create().row().button("❌Отмена").endRow().build());
                    groupTmpInfo.put(groupId, new TmpInfo());
                    groupTmpInfo.get(groupId).setDeniedCheck(true);
                    groupTmpInfo.get(groupId).setOrderId(orderId);
                    groupTmpInfo.get(groupId).setMessageId(update.getCallbackQuery().getMessage().getMessageId());
                    groupTmpInfo.get(groupId).addTrashMessage(messageId);
                    return;
                }
                if (command.equals("CANCEL")) {
                    editMessage(groupId, update.getCallbackQuery().getMessage().getMessageId(), constructOperatorOrderMessage(operatorOrderDao.fetchOperatorOrder(orderId), false, false),
                            InlineKeyboardBuilder.create().row().button("✅Да", orderId + "_YES").button("❌Нет", orderId + "_NO").endRow().build());
                    OperatorOrder order = operatorOrderDao.fetchOperatorOrder(orderId);
                    sendMessage(operatorChatId, constructForOperator(order) + "\n\n⚠Отмена принятие заявки");
                    return;
                }

            } else if (chatId == operatorChatId){
                if (message.equals("NONE_INFO")) {
                    return;
                }
                String numberGroup = message.substring(0, 1);

                int orderId = Integer.parseInt(message.substring(2));
                OperatorOrder operatorOrder = operatorOrderDao.fetchOperatorOrder(orderId);
                int messageId;
                if(numberGroup.equals("1") || numberGroup.equals("2")){
                    groupId = numberGroup.equals("1") ? firstOrderChannelId: secondOrderChannelId;
                    messageId = sendMessage(groupId, constructOperatorOrderMessage(operatorOrder, false, false),
                            InlineKeyboardBuilder.create().row().button("✅Да", orderId + "_YES").button("❌Нет", orderId + "_NO").endRow().build());
                }
               else{
                    messageId =  auctionCleaningBot.sendMessage(mainOrderChannelId, constructOperatorOrderMessage(clientOperatorOrders.get(chatId), false, false),
                            InlineKeyboardBuilder.create().row().button("✅Забронировать", orderId + "_BOOK").endRow().build());
                }
                operatorOrderDao.updateMessageIdAndGroupNumber(orderId, messageId, Integer.parseInt(numberGroup));
                editMessage(chatId, update.getCallbackQuery().getMessage().getMessageId(), null,
                        InlineKeyboardBuilder.create().row().button("✅Заявка отправлена", "NONE_INFO").endRow().build());
                return;

            }

        }

        if (chatId == firstOrderChannelId || chatId == secondOrderChannelId) {
            long groupId = update.getMessage().getChat().getId();
            int groupNumber = chatId==firstOrderChannelId?1:2;

            if (groupTmpInfo.get(groupId) != null) {
                groupTmpInfo.get(groupId).addTrashMessage(update.getMessage().getMessageId());
                if (message.equals("❌Отмена")) {
                    deleteTrashMessages(groupId, groupTmpInfo.get(groupId).getTrashMessages());
                    groupTmpInfo.remove(groupId);
                    return;
                }
                if (groupTmpInfo.get(groupId).isDeniedCheck()) {
                    operatorOrderDao.changeString(groupTmpInfo.get(groupId).getOrderId(), "DENIED_COMMENT", message);
                    operatorOrderDao.changeStatus(groupTmpInfo.get(groupId).getOrderId(), "DENIED", true);
                    operatorOrderDao.changeStatus(groupTmpInfo.get(groupId).getOrderId(), "APPROVE", false);
                    int numberGroup = groupId == firstOrderChannelId ? 2 : 1;
                    sendMessage(operatorChatId, "❌<b>Заявка отменена в "+groupNumber+" группе!</b>\n\n" + "<b>Причина:</b> "+constructForOperator(operatorOrderDao.fetchOperatorOrder(groupTmpInfo.get(groupId).getOrderId())),
                            constructKeyboardResend(numberGroup, groupTmpInfo.get(groupId).getOrderId()));
                    deleteTrashMessages(groupId, groupTmpInfo.get(groupId).getTrashMessages());
                    deleteMessage(groupId, groupTmpInfo.get(groupId).getMessageId());
                    groupTmpInfo.remove(groupId);
                    return;
                }
                if (groupTmpInfo.get(groupId).isCompleteCheck()) {
                    if (message.equals("✅Да")) {
                        operatorOrderDao.changeStatus(groupTmpInfo.get(groupId).getOrderId(), "COMPLETE", true);
                        sendMessage(operatorChatId, "✅<b>Заявка выполнена в "+groupNumber+" группе!</b>\n\n"+constructForOperator(operatorOrderDao.fetchOperatorOrder(groupTmpInfo.get(groupId).getOrderId())));
                        deleteTrashMessages(groupId, groupTmpInfo.get(groupId).getTrashMessages());
                        deleteMessage(groupId, groupTmpInfo.get(groupId).getMessageId());
                        groupTmpInfo.remove(groupId);
                        return;
                    } else {
                        deleteTrashMessages(groupId, groupTmpInfo.get(groupId).getTrashMessages());
                        groupTmpInfo.remove(groupId);
                        return;
                    }
                }
                if (groupTmpInfo.get(groupId).isEditCheck()) {
                    if (groupTmpInfo.get(groupId).getOperatorOrder() == null) {
                        LocalDateTime dateTime = parseLocalDateTime(message);
                        if(dateTime!=null){
                            groupTmpInfo.get(groupId).getOperatorOrder().setCleaningDate(dateTime);
                            int messageId = sendMessage(groupId, "Теперь укажите сумму в рублях");
                            groupTmpInfo.get(groupId).addTrashMessage(messageId);
                        }
                        else{
                            int messageId = sendMessage(groupId, "Неверный формат сообщения");
                            groupTmpInfo.get(groupId).addTrashMessage(messageId);
                        }
                        return;
                    }
                    if(groupTmpInfo.get(groupId).getOperatorOrder().getCleaningDate()!=null){
                        int payment;
                        try {
                            payment = Integer.parseInt(message);
                        } catch (Throwable e) {
                            int messageId = sendMessage(groupId, "Можно вписать только число");
                            groupTmpInfo.get(groupId).addTrashMessage(messageId);
                            return;
                        }
                        groupTmpInfo.get(groupId).getOperatorOrder().setPaymentAmount(payment);
                        operatorOrderDao.updateDateAndPaymentAmount(groupTmpInfo.get(groupId).getOrderId(), groupTmpInfo.get(groupId).getOperatorOrder());//TODO менять на полную заявку
                        OperatorOrder order = operatorOrderDao.fetchOperatorOrder(groupTmpInfo.get(groupId).getOrderId());
                        sendMessage(operatorChatId, "<b>Партнер из "+groupId+" группы внес изменения в заявку</b>\n\n"
                                +constructForOperator(order)+"\n<b>Стоимость:</b> " + groupTmpInfo.get(groupId).getOperatorOrder().getPaymentAmount()+"р.");
                        editMessage(groupId, groupTmpInfo.get(groupId).getMessageId(), constructOperatorOrderMessage(order, true, false),
                                InlineKeyboardBuilder.create().row().button("✅Выполнено", groupTmpInfo.get(groupId).getOrderId() + "_COMPLETE").endRow()
                                        .row().button("❌Отмена", groupTmpInfo.get(groupId).getOrderId() + "_CANCEL").endRow().build());
                        deleteTrashMessages(groupId, groupTmpInfo.get(groupId).getTrashMessages());
                        groupTmpInfo.remove(groupId);
                        return;
                    }
                }
            } else {
                deleteMessage(groupId, update.getMessage().getMessageId());
            }
            return;
        }

        if (message.equals("/start") || message.equals("❌Отмена")) {
            clientOperatorOrders.remove(chatId);
            sendMessage(chatId, welcomeText, ReplyKeyboardBuilder.create().row().button("\uD83D\uDCDDСоздать заявку").endRow()
                    .row().button("Создать заявку(на рассчет)").endRow().build());
            return;
        }


        if (message.equals("\uD83D\uDCDDСоздать заявку") && clientOperatorOrders.get(chatId) == null) {
            clientOperatorOrders.put(chatId, new OperatorOrder());
            clientOperatorOrders.get(chatId).setFullOrder(true);
            sendMessage(chatId, "Введите дату и время в формате 28.11.2019 14:48", ReplyKeyboardBuilder.create().row().button("❌Отмена").endRow().build());
            return;
        }

        if (message.equals("Создать заявку(на рассчет)") && clientOperatorOrders.get(chatId) == null) {
            clientOperatorOrders.put(chatId, new OperatorOrder());
            String text = clientOperatorOrders.get(chatId).isFullOrder() ? "Введите точную дату и время уборки" : "Введите желаемую дату и время уборки";
            sendMessage(chatId, text + " в формате 28.11.2019 14:48", ReplyKeyboardBuilder.create().row().button("❌Отмена").endRow().build());
            return;
        }

        if (clientOperatorOrders.get(chatId) != null) {
            if (clientOperatorOrders.get(chatId).getCleaningDate() == null) {
                LocalDateTime dateTime = parseLocalDateTime(message);
                if (dateTime!=null) {
                    clientOperatorOrders.get(chatId).setCleaningDate(dateTime);
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
                sendMessage(chatId, "Укажите Вид объекта",
                        ReplyKeyboardBuilder.create().row().button("Квартира").endRow().
                                row().button("Коттетдж").endRow().
                                row().button("Офис").endRow().
                                row().button("Ресторан").endRow().
                                row().button("Магазин").endRow().
                                row().button("Другое").endRow().
                                row().button("❌Отмена").endRow().build());
                return;
            }

            if (clientOperatorOrders.get(chatId).getTypeObject() == null) {
                clientOperatorOrders.get(chatId).setTypeObject(message);
                String text = clientOperatorOrders.get(chatId).isFullOrder() ? "Укажите точную стоимость" : "Укажите желаемую сумму";
                sendMessage(chatId, text, ReplyKeyboardBuilder.create().row().button("❌Отмена").endRow().build());
                return;
            }

            if (clientOperatorOrders.get(chatId).getPaymentAmount() == 0) {
                int payment;
                try {
                    payment = Integer.parseInt(message);
                } catch (Throwable e) {
                    sendMessage(chatId, "Можно вписать только число");
                    return;
                }
                clientOperatorOrders.get(chatId).setPaymentAmount(payment);
                sendMessage(chatId, "Укажите имя клиента");
                return;
            }

            if (clientOperatorOrders.get(chatId).getClientName() == null) {
                clientOperatorOrders.get(chatId).setClientName(message);
                sendMessage(chatId, "Напишите номер телефона клиента");
                return;
            }

            if (clientOperatorOrders.get(chatId).getClientNumber() == null) {
                Matcher mathcer = Pattern.compile("[+]?[7,8][0-9]{10}").matcher(message);
                if (mathcer.find()) {
                    clientOperatorOrders.get(chatId).setClientNumber(mathcer.group());
                } else {
                    sendMessage(chatId, "Неправильный формат телефона");
                    return;
                }
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
                                .build());
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
                sendMessage(chatId, constructOperatorOrderMessage(clientOperatorOrders.get(chatId), true, true),
                        ReplyKeyboardBuilder.create().row().button("1").button("2").button("Общая").endRow().row().button("❌Отмена").endRow().build());
                return;
            }

            if (message.equals("1") || message.equals("2")) {
                long groupId = message.equals("1") ? firstOrderChannelId : secondOrderChannelId;
                int orderId = operatorOrderService.createOrder(clientOperatorOrders.get(chatId));
                if (orderId == 0) {
                    sendMessage(chatId, "Не получилось создать заявку, обратитесь к программисту");
                    clientOperatorOrders.remove(chatId);
                    return;
                }
                int messageId = sendMessage(groupId, constructOperatorOrderMessage(clientOperatorOrders.get(chatId), false, false),
                        InlineKeyboardBuilder.create().row().button("✅Да", orderId + "_YES").button("❌Нет", orderId + "_NO").endRow().build());
                operatorOrderDao.updateMessageIdAndGroupNumber(orderId, messageId, Integer.parseInt(message));
                sendMessage(chatId, "Заявка отправлена!", ReplyKeyboardBuilder.create().row().button("\uD83D\uDCDDСоздать заявку").endRow().build());
                clientOperatorOrders.remove(chatId);
                return;
            }
            if (message.equals("Общая")) {
                clientOperatorOrders.get(chatId).setGroupNumber(3);
                int orderId = operatorOrderService.createOrder(clientOperatorOrders.get(chatId));
                if (orderId == 0) {
                    sendMessage(chatId, "Не получилось создать заявку, обратитесь к программисту");
                    clientOperatorOrders.remove(chatId);
                    return;
                }
                int messageId = auctionCleaningBot.sendMessage(mainOrderChannelId, constructOperatorOrderMessage(clientOperatorOrders.get(chatId), false, false),
                        InlineKeyboardBuilder.create().row().button("✅Забронировать", orderId + "_BOOK").endRow().build());
                operatorOrderDao.updateMessageIdAndGroupNumber(orderId, messageId, 3);
                sendMessage(chatId, "Заявка отправлена!", ReplyKeyboardBuilder.create().row().button("\uD83D\uDCDDСоздать заявку").endRow().build());
                clientOperatorOrders.remove(chatId);
                return;
            }

        }

        if (message.matches("/.*")) {
            if (message.equals("/deleteOrders")) {

            }
        }


    }

    private String constructOperatorOrderMessage(OperatorOrder order, boolean clientName, boolean orderAuthor) {
        String textDate, textPrice;
        String textNameAndNumber = "", textAuthor = "", textOrderDate = "";
        if (order.isFullOrder()) {
            textDate = "<b>Желаемая дата и время:</b> ";
            textPrice = "<b>Желаемая стоимость:</b> ";
        } else {
            textDate = "<b>Дата уборки и время:</b> ";
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
        return (order.isFullOrder()) ? "" : "<b>Заявка с неточной суммой и датой</b>\n" +
                textDate + formatDate(order.getCleaningDate()) + "\n" +
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

    public InlineKeyboardMarkup constructKeyboardResend(int number, int orderId) {
        switch (number) {
            case 1:
                return InlineKeyboardBuilder.create().row().button("\uD83D\uDCE8Отправить в группу #2", 2 + "_" + orderId).endRow()
                        .row().button("\uD83D\uDCE8Отправить в Общую группу ", 3 + "_" + orderId).endRow().build();
            case 2:
                return InlineKeyboardBuilder.create().row().button("\uD83D\uDCE8Отправить в группу #1", 1 + "_" + orderId).endRow()
                        .row().button("\uD83D\uDCE8Отправить в Общую группу ", 3 + "_" + orderId).endRow().build();
        }
        return null;
    }

    public String constructForOperator(OperatorOrder order) {
        return "<b>Контрагент:</b> " + order.getCounterparty() + "\n<b>Имя клиента и телефон:</b> " + order.getClientName() + order.getClientNumber() + "\n<b>Дата и время:</b> " + formatDate(order.getCleaningDate());
    }

    private String formatDate(LocalDateTime date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        return date.format(formatter);
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

    public void deleteTrashMessages(long chatId, List<Integer> trashMessage) {
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

    private int sendMessage(long chatId, String text, InlineKeyboardMarkup markup) {
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
        if (text != null) editMessageText.setText(text);
        if (markup != null) editMessageText.setReplyMarkup(markup);


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
            if (!execute(deleteMessage)) {
                sendMessage(chatId, "Сообщение не было удалено. Возможно прошло 48 часов с момента отправки сообщения, либо неверен messageId");
            }
            log.info("delete message to {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to delete message to {}", chatId, e);
        }
    }
}
