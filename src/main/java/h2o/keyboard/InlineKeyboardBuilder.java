package h2o.keyboard;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class InlineKeyboardBuilder {
    private List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
    private List<InlineKeyboardButton> row = null;

    private InlineKeyboardBuilder() {
    }

    public static InlineKeyboardBuilder create() {
        InlineKeyboardBuilder builder = new InlineKeyboardBuilder();
        return builder;
    }

    public InlineKeyboardBuilder row() {
        this.row = new ArrayList<>();
        return this;
    }

    public InlineKeyboardBuilder button(String name, String query) {
        InlineKeyboardButton button = new InlineKeyboardButton().setText(name).setCallbackData(query);
        row.add(button);
        return this;
    }

    public InlineKeyboardBuilder endRow() {
        this.keyboard.add(this.row);
        this.row = null;
        return this;
    }

    public InlineKeyboardMarkup build() {

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();

        keyboardMarkup.setKeyboard(keyboard);

        return keyboardMarkup;
    }


}
