package h2o.bot;

import h2o.keyboard.InlineKeyboardBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@Component
public class AuctionCleaningBot extends TelegramLongPollingBot {

    private String token;
    private String username;

    @Autowired
    public AuctionCleaningBot(@Value("${operator.bot.token}") String token,
                              @Value("${operator.bot.username}") String username) {
        this.token = token;
        this.username = username;

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


        if (command.equals("BOOK")) {
            editMessage(groupId, update.getCallbackQuery().getMessage().getMessageId(), null,
                    InlineKeyboardBuilder.create().row().button("☑Забронировано", orderId + "_NONE_INFO").build());
            sendMessage(chatId, constructOperatorOrderMessage(operatorOrderDao.fetchOperatorOrder(orderId), false, false),
                    InlineKeyboardBuilder.create().row().button("✅Да", orderId + "_YES").button("❌Нет", orderId + "_NO").endRow().build());
            //TODO Придумать что-то про бронь
            return;
        }
    }
}
