package h2o;

import h2o.calculator.Calculator;
import h2o.handler.message.CallbackQueryHandler;
import h2o.handler.message.Searcher;
import h2o.keyboard.InlineKeyboardBuilder;
import h2o.keyboard.ReplyKeyboardBuilder;
import h2o.model.AdminOrderInfo;
import h2o.model.Order;
import h2o.model.Partner;
import h2o.service.OrderService;
import h2o.service.PartnersService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CleaningPartnerBot extends TelegramLongPollingBot {

    private String token;
    private String username;
    private long adminchaId;
    private long orderChannelId;
    private long registrationChannelId;
    private String welcomeText;
    private ConcurrentHashMap<Long, Order> clientOrders;
    private ConcurrentHashMap<Long, Partner> partners;
    private AdminOrderInfo adminOrderInfo;

    private OrderService orderService;
    private PartnersService partnersService;
    private Searcher searcher;
    private Calculator calculator;
    private CallbackQueryHandler callbackQueryHandler;

    @Autowired
    public CleaningPartnerBot(@Value("${bot.token}") String token,
                              @Value("${bot.username}") String username,
                              @Value("${admin.id}") long adminChatId,
                              @Value("${order.channel.id}") long orderChannelId,
                              @Value("${registration.channel.id}") long registrationChannelId,
                              Calculator calculator,
                              Searcher searcher,
                              CallbackQueryHandler callbackQueryHandler,
                              OrderService orderService,
                              PartnersService partnersService) {
        super();
        this.token = token;
        this.username = username;
        this.adminchaId = adminChatId;
        this.orderChannelId = orderChannelId;
        this.registrationChannelId = registrationChannelId;
        this.clientOrders = new ConcurrentHashMap<>();
        this.partners = new ConcurrentHashMap<>();
        this.adminOrderInfo =null;
        this.welcomeText = "Привет! Я H2o бот помощник! Что будем делать?";
        this.calculator = calculator;
        this.orderService = orderService;
        this.partnersService = partnersService;
        this.searcher = searcher;
        this.callbackQueryHandler = callbackQueryHandler;

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
        long chatId = (update.hasMessage() ? update.getMessage().getChat().getId() : update.getCallbackQuery().getFrom().getId());
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

        Partner partner = partnersService.fecthInfoAboutPartner(chatId);


        if (update.hasCallbackQuery()) {
            if (update.getCallbackQuery().getMessage().getChat().getId() == orderChannelId) {
                int orderId = searcher.searchOrderId(message);
                if (orderId == 0) {
                    sendMessage(adminchaId, "Ошибка, напиши программисту\nDATE: " + LocalDateTime.now());
                    log.error("Не нашел orderId в сообщении {}", message);
                    return;
                }
                String command = message.substring(String.valueOf(orderId).length() + 1);
                if (command.equals("YES")) {
//                    if (orderService.changeStatusApprove(orderId)) {
//                        deleteMessage(update.getCallbackQuery().getMessage().getChat().getId(), update.getCallbackQuery().getMessage().getMessageId());
//                    } else {
//                        sendMessage(chatId, "Не найдено ордера");
//                    }
                    adminOrderInfo = new AdminOrderInfo();
                    adminOrderInfo.setApprove(true);
                    adminOrderInfo.setOrderId(orderId);
                    int messageId = sendMessage(chatId, "Уточните итоговую сумму", ReplyKeyboardBuilder.create().row().button("↪Пропустить").endRow().row().button("Отмена").endRow().build());
                    if(messageId!=0){
                        adminOrderInfo.getTrashMessages().add(messageId);
                    }
                    //TODO  ОСТАНОВИЛСЯ ЗДЕСЬ
                    return;
                }
                if (command.equals("NO")) {
                    if (orderService.changeStatusDenied(orderId)) {
                        deleteMessage(update.getCallbackQuery().getMessage().getChat().getId(), update.getCallbackQuery().getMessage().getMessageId());
                    } else {
                        sendMessage(chatId, "Не найдено ордера " + orderId);
                    }
                    return;
                }

            }
            if (update.getCallbackQuery().getMessage().getChat().getId() == registrationChannelId) {
                long chatIdClient = searcher.searchChatId(message);
                if (chatIdClient == 0) {
                    sendMessage(adminchaId, "Ошибка, напиши программисту\nDATE: " + LocalDateTime.now());
                    log.error("Не нашел chatId в сообщении {}", message);
                    return;
                }
                String command = message.substring(String.valueOf(chatIdClient).length() + 1);
                if (command.equals("YES")) {
                    partnersService.changePosition(chatIdClient, "PARTNER");
                    deleteMessage(update.getCallbackQuery().getMessage().getChat().getId(), update.getCallbackQuery().getMessage().getMessageId());
                    sendMessage(chatIdClient, "Вы успешно зарегистрированы!", startKeyboard("PARTNER"));
                    return;
                }
                if (command.equals("NO")) {
                    partnersService.removePartner(chatIdClient);
                    deleteMessage(update.getCallbackQuery().getMessage().getChat().getId(), update.getCallbackQuery().getMessage().getMessageId());
                    sendMessage(chatIdClient, "В регистрации отказано, свяжитесь с оператором для подробностей", startKeyboard("NONAME"));
                    return;
                }
            }

        }


        if (partner == null) {
            if (message.equals("/start") | message.equals("❌Отмена")) {
                partners.remove(chatId);
                sendMessage(chatId, welcomeText, startKeyboard("NONAME"));
                return;
            }
            if (message.equals("Зарегистрироваться")) {
                partners.put(chatId, new Partner());
                sendMessage(chatId, "Введите ваше ФИО\n\n<b>Пример:</b> Шпак Валентин Арханович");
                return;
            }
            if (partners.get(chatId) != null) {
                if (partners.get(chatId).getFullName() == null) {
                    String fullName = searcher.searchFullName(message);
                    if (fullName == null) {
                        sendMessage(chatId, "Впишите фамилию и имя(отчество) как показано в примере:\n\n<b>Пример:</b> Шпак Валентин Арханович");
                        return;
                    } else {
                        partners.get(chatId).setFullName(fullName);
                        sendMessage(chatId, "Хорошо! Теперь укажите ваш номер телефона\n\n<b>Пример:</b> 9145901488");
                        return;
                    }
                }
                if (partners.get(chatId).getPhoneNumber() == null) {
                    String phoneNumber = searcher.searchPhone(message);
                    if (phoneNumber == null) {
                        sendMessage(chatId, "Впишите номер телефона как показано в примере:\n\n<b>Пример:</b> 9145901488");
                        return;
                    } else {
                        partners.get(chatId).setPhoneNumber(phoneNumber);
                        sendMessage(chatId, "Остался последний шаг! Укажите ваш email\n\n<b>Пример:</b> shpak@gmail.com");
                        return;
                    }
                }
                if (partners.get(chatId).getEmail() == null) {
                    String email = searcher.searchEmail(message);
                    if (email == null) {
                        sendMessage(chatId, "Впишите email как показано в примере:\n\n<b>Пример:</b> shpak@gmail.com");
                        return;
                    } else {
                        partners.get(chatId).setEmail(email);
                        sendMessage(chatId, constructPartnerInfo(partners.get(chatId)), ReplyKeyboardBuilder.create().row().button("✅Да").button("❌Нет").endRow().build());
                        return;
                    }
                }
                if (message.equals("✅Да")) {
                    partners.get(chatId).setChatId(chatId);
                    partners.get(chatId).setPosition("CLIENT");
                    if (!partnersService.createPartner(partners.get(chatId))) {
                        partners.remove(chatId);
                        sendMessage(chatId, "Не получилось отправить заявку на регистрацию. Обратитесь к оператору", startKeyboard("NONAME"));
                        return;
                    }
                    sendMessage(registrationChannelId, "<b>CHAT ID</b>: " + chatId + "\n" +
                                    (update.getMessage().getForwardFrom() != null && update.getMessage().getForwardFrom().getUserName() != null ? "<b>Username:</b> @" + update.getMessage().getForwardFrom().getUserName() + "\n" : "") +
                                    constructPartnerInfo(partners.get(chatId)),
                            InlineKeyboardBuilder.create().row().button("✅Да", chatId + "_YES").button("❌Нет", chatId + "_NO").endRow().build());
                    sendMessageWithRemoveKeyboard(chatId, "Спасибо!❤Заявка на регистрацию отправлена!");
                    partners.remove(chatId);
                    return;
                }
                if (message.equals("❌Нет")) {
                    partners.remove(chatId);
                    sendMessage(chatId, welcomeText, startKeyboard("NONAME"));
                    return;
                }

            }


            sendMessage(chatId, welcomeText, startKeyboard("NONAME"));
            return;
        }

        if(partner.getPosition().equals("CLIENT")){
            sendMessageWithRemoveKeyboard(chatId, "Извините! Дождитесь завершения процедуры регистрации");
        }

        if(partner.getPosition().equals("PARTNER")){
            if (message.equals("/start") || message.equals("❌Отмена")) {
                clientOrders.remove(chatId);
                sendMessage(chatId, welcomeText, startKeyboard("PARTNER"));
                return;
            }

            if (message.equals("\uD83D\uDCB0Узнать баланс")) {
                sendMessage(chatId, "Ваш балик: 0р.");
                return;
            }


            if (message.equals("\uD83D\uDCBBПосчитать стоимость") && clientOrders.get(chatId) == null) {
                clientOrders.put(chatId, new Order(chatId));
                sendMessage(chatId, "Выберете тип объекта, которое необходимо убрать:\n\n" +
                        "<b>Квартира</b> - Бла бла бла\n" +
                        "<b>Офис</b> - бла бла бла\n" +
                        "<b>Дом/Коттедж</b> - бла", ReplyKeyboardBuilder.create().row().button("Квартира").endRow().row().button("❌Отмена").endRow().build());
                return;
            }

            if (clientOrders.get(chatId) != null) {
                if (clientOrders.get(chatId).getTypeObject() == null) {

                    if (message.equals("Квартира")) {
                        clientOrders.get(chatId).setTypeObject(message);
                        sendMessage(chatId, "Выберете тип уборки:\n\n" +
                                "<b>Генеральная</b> - Просто уборка\n" +
                                "<b>Генеральная (с окнами)</b> - Просто уборка + окна(+30%)\n" +
                                "<b>Генеральная (люкс)</b> - Полная уборка всего, что только можно(+70%)", ReplyKeyboardBuilder.create().row().button("Генеральная").endRow()
                                .row().button("Генеральная (с окнами)").endRow()
                                .row().button("Генеральная (люкс)").endRow()
                                .row().button("❌Отмена").endRow().build());
                    } else {
                        sendMessage(chatId, "Выберете вариант, нажав на нужную кнопку");
                    }

                    return;
                }
                if (clientOrders.get(chatId).getTypeCleaning() == null) {

                    if (message.equals("Генеральная") || message.equals("Генеральная (с окнами)") || message.equals("Генеральная (люкс)")) {
                        clientOrders.get(chatId).setTypeCleaning(message);
                        sendMessage(chatId, "Выберете количество комнат",
                                ReplyKeyboardBuilder.create().row().button("Студия").endRow()
                                        .row().button("1").button("2").button("3").button("4").endRow().row().button("❌Отмена").endRow().build());
                    } else {
                        sendMessage(chatId, "Выберете вариант, нажав на нужную кнопку");
                    }
                    return;
                }
                if (clientOrders.get(chatId).getNumberRooms() == -1) {
                    if (message.equals("Студия") || message.equals("1") || message.equals("2") || message.equals("3") || message.equals("4")) {
                        if (message.equals("Студия")) {
                            clientOrders.get(chatId).setNumberRooms(0);
                        } else {
                            clientOrders.get(chatId).setNumberRooms(Integer.parseInt(message));
                        }
                        sendMessage(chatId, "Укажите количество квадратных метров", ReplyKeyboardBuilder.create().row().button("❌Отмена").endRow().build());
                    } else {
                        sendMessage(chatId, "Выберете вариант, нажав на нужную кнопку");
                    }
                    return;
                }
                if (clientOrders.get(chatId).getArea() == 0) {
                    int area = Integer.parseInt(message);
                    if (area < 1000 && area != 0) {
                        clientOrders.get(chatId).setArea(area);
                        sendMessage(chatId, "Укажите количество санузлов");
                    } else {
                        sendMessage(chatId, "Извините, но чет дохуя у ваc метров");
                    }
                    return;
                }
                if (clientOrders.get(chatId).getNumberBathroom() == 0) {
                    int numberBathrooms = Integer.parseInt(message);
                    if (numberBathrooms < 10 && numberBathrooms != 0) {
                        clientOrders.get(chatId).setNumberBathroom(numberBathrooms);
                        clientOrders.get(chatId).setPaymentAmount(calculator.calculateFlat(clientOrders.get(chatId)));
                        clientOrders.get(chatId).setOrderInfo(constructOrderMessage(clientOrders.get(chatId)));
                        sendMessage(chatId, constructOrderMessage(clientOrders.get(chatId)),
                                ReplyKeyboardBuilder.create().row().button("❌Отмена").endRow().build());
                        sendMessage(chatId, "Уккажите контактную информацию клиента, чтобы мы могли с ним связаться, для уточнения информации");
                    } else {
                        sendMessage(chatId, "Извините, слишком много санузлов");
                    }
                    return;
                }

                if (clientOrders.get(chatId).getContactInfo()==null){
                    if(message.length()>300){
                        sendMessage(chatId, "Слишком большое сообщение, сократите пожалуйста");
                        return;
                    }
                    clientOrders.get(chatId).setContactInfo(message);
                    sendMessage(chatId, constructOrderMessage(clientOrders.get(chatId))+"\n<b>Кантакты:</b> "+message,
                            ReplyKeyboardBuilder.create().row().button("✅Да").button("❌Нет").endRow().build());
                    return;
                }


                if (message.equals("✅Да")) {
                    int orderId = orderService.createOrder(clientOrders.get(chatId));
                    if (orderId == 0) {
                        sendMessage(chatId, "Не получилось создать заявку, обратитесь к оператору ...");
                        clientOrders.remove(chatId);
                        return;
                    }
                    sendMessage(orderChannelId,"<b>CHAT ID</b>: " + chatId + "\n" + constructOrderMessage(clientOrders.get(chatId))+"\n<b>Кантакты:</b> "+clientOrders.get(chatId).getContactInfo(),
                            InlineKeyboardBuilder.create().row().button("✅Да", orderId + "_YES").button("❌Нет", orderId + "_NO").endRow().build());
                    sendMessage(chatId, "Заявка отправлена!", ReplyKeyboardBuilder.create().row().button("\uD83D\uDCBBПосчитать стоимость").endRow().row().button("\uD83D\uDCB0Узнать баланс").endRow().build());
                    clientOrders.remove(chatId);
                    return;
                }
                if (message.equals("❌Нет")) {
                    clientOrders.remove(chatId);
                    sendMessage(chatId, welcomeText, ReplyKeyboardBuilder.create().row().button("\uD83D\uDCBBПосчитать стоимость").endRow().row().button("\uD83D\uDCB0Узнать баланс").endRow().build());
                    return;
                }


            }
        }



    }

    private String constructOrderMessage(Order order) {
        return "<b>Вид объекта:</b> " + order.getTypeObject() + "\n" +
                "<b>Вид уборки:</b> " + order.getTypeCleaning() + "\n" +
                "<b>Количество комнат:</b> " + (order.getNumberRooms() == 0 ? "Студия" : order.getNumberRooms()) + "\n" +
                "<b>Количество кв.м.:</b> " + order.getArea() + "м.кв.\n" +
                "<b>Высота потолков:</b> " + "добавлю потом\n" +
                "<b>Количество санузлов:</b> " + order.getNumberBathroom() + "\n" +
                "<b>Итоговая стоимость:</b> " + order.getPaymentAmount() + "р.";
    }

    private String constructPartnerInfo(Partner partner) {
        return "<b>ФИО:</b> " + partner.getFullName() + "\n" +
                "<b>Телефон:</b> +7(" + partner.getPhoneNumber().substring(0, 3) + ")" + partner.getPhoneNumber().substring(3) + "\n" +
                "<b>Email:</b> " + partner.getEmail();
    }

    private ReplyKeyboardMarkup startKeyboard(String position) {
        switch (position) {
            case "ADMIN":
                return ReplyKeyboardBuilder.create().row().button("Команды для админа").endRow().build();
            case "PARTNER":
                return ReplyKeyboardBuilder.create().row().button("\uD83D\uDCBBПосчитать стоимость").endRow().row().button("\uD83D\uDCB0Узнать баланс").endRow().build();
            case "CLIENT":
                return null;
            default:
                return ReplyKeyboardBuilder.create().row().button("Зарегистрироваться").endRow().build();
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        sendMessage.setParseMode("HTML");
        sendMessage.disableWebPagePreview();
        try {
            execute(sendMessage);
            //chatHistoryDAO.saveMessage(new MessageDAO(chatId, LocalDateTime.now(), text, "BOT"));
            log.info("send message \"{}\" to {}\"", text, chatId);
        } catch (TelegramApiException e) {
            log.error("Failed to send message \"{}\" to {}\"", text, chatId, e);
        }
    }

    private void sendMessageWithRemoveKeyboard(long chatId, String text){
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
